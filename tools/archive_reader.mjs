/** Independent, bounded TEST reader for ADR-031 synthetic vectors. Never a product crypto API.
 * No Tink/protobuf/third-party dependency. Construction: Tink's AES-GCM-HKDF streaming spec;
 * wire fields: tink-java v1.24.0 proto/tink.proto and aes_gcm_hkdf_streaming.proto.
 */
import { createDecipheriv, createHash, hkdfSync } from 'node:crypto';
import { readFileSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const SEGMENT = 1_048_576;
const MAX_PLAIN = 4 * SEGMENT;
const MAX_CIPHER = MAX_PLAIN + 16_384;
const TYPE = 'type.googleapis.com/google.crypto.tink.AesGcmHkdfStreamingKey';
const fail = () => {
  throw new Error('Invalid synthetic envelope');
};
const requireValue = value => {
  if (!value) fail();
};
const hash = bytes => createHash('sha256').update(bytes).digest('hex');

/** Small strict protobuf decoder for just the vector schema. Bounds before slices/allocation. */
function proto(bytes, schema) {
  requireValue(bytes.length <= 4096);
  let offset = 0;
  const fields = new Map();
  function varint() {
    let value = 0n;
    for (let i = 0; i < 10; i++) {
      requireValue(offset < bytes.length);
      const byte = bytes[offset++];
      value |= BigInt(byte & 127) << BigInt(7 * i);
      if (!(byte & 128)) {
        requireValue(value <= 0xffffffffn && (i === 0 || byte !== 0));
        return Number(value);
      }
    }
    fail();
  }
  while (offset < bytes.length) {
    const tag = varint();
    const id = Math.floor(tag / 8);
    const wire = tag & 7;
    requireValue(schema[id] === wire && !fields.has(id));
    if (wire === 0) fields.set(id, varint());
    else {
      const length = varint();
      requireValue(length <= bytes.length - offset);
      fields.set(id, bytes.subarray(offset, offset + length));
      offset += length;
    }
  }
  return fields;
}

function decryptGcm(key, nonce, bytes, aad = Buffer.alloc(0)) {
  requireValue(bytes.length >= 16 && nonce.length === 12);
  const cipher = createDecipheriv('aes-256-gcm', key, nonce, {
    authTagLength: 16,
  });
  cipher.setAAD(aad);
  cipher.setAuthTag(bytes.subarray(-16));
  return Buffer.concat([cipher.update(bytes.subarray(0, -16)), cipher.final()]);
}

function streamKey(keyset) {
  const set = proto(keyset, { 1: 0, 2: 2 });
  const key = proto(set.get(2) ?? fail(), { 1: 2, 2: 0, 3: 0, 4: 0 });
  requireValue(
    key.get(2) === 1 && key.get(3) === set.get(1) && key.get(4) === 3,
  );
  const data = proto(key.get(1) ?? fail(), { 1: 2, 2: 2, 3: 0 });
  requireValue(data.get(1)?.toString('utf8') === TYPE && data.get(3) === 1);
  const value = proto(data.get(2) ?? fail(), { 1: 0, 2: 2, 3: 2 });
  requireValue((value.get(1) ?? 0) === 0 && value.get(3)?.length === 32);
  const params = proto(value.get(2) ?? fail(), { 1: 0, 2: 0, 3: 0 });
  requireValue(
    params.get(1) === SEGMENT && params.get(2) === 32 && params.get(3) === 3,
  );
  return Buffer.from(value.get(3));
}

export function readEnvelope(input, credential, plaintextLimit = MAX_PLAIN) {
  requireValue(input.length >= 65 && input.length <= MAX_CIPHER);
  requireValue(
    Number.isInteger(plaintextLimit) &&
      plaintextLimit >= 0 &&
      plaintextLimit <= MAX_PLAIN,
  );
  const bytes = Buffer.from(input); // Exclusively owned bounded fixture snapshot.
  requireValue(
    bytes.subarray(0, 8).equals(Buffer.from('4154544e41524300', 'hex')),
  );
  requireValue(bytes.readUInt16BE(8) === 1 && bytes.readUInt16BE(10) === 1);
  requireValue(
    bytes[12] === 1 && bytes[13] === 0 && bytes.readUInt16BE(14) === 0,
  );
  requireValue(bytes.subarray(16, 28).equals(Buffer.alloc(12)));
  const length = bytes.readUInt32BE(60);
  requireValue(length >= 1 && length <= 4096 && length <= bytes.length - 64);
  requireValue(
    typeof credential === 'string' && /^[A-Za-z0-9_-]{43}$/.test(credential),
  );
  const raw = Buffer.from(credential, 'base64url');
  requireValue(raw.length === 32 && raw.toString('base64url') === credential);
  const wrap = Buffer.from(
    hkdfSync(
      'sha256',
      raw,
      bytes.subarray(28, 44),
      Buffer.from('AttentionStats/archive/v1/wrap', 'ascii'),
      32,
    ),
  );
  const prefix = bytes.subarray(0, 60);
  let keyset;
  let key;
  let derived;
  try {
    const wrapped = proto(bytes.subarray(64, 64 + length), { 2: 2, 3: 2 });
    // Outer KeysetInfo is deliberately not algorithm authority. Raw W is authenticated by C.
    const encrypted = wrapped.get(2) ?? fail();
    keyset = decryptGcm(
      wrap,
      encrypted.subarray(0, 12),
      encrypted.subarray(12),
      Buffer.concat([
        Buffer.from('AttentionStats/archive/v1/keyset\0', 'ascii'),
        prefix,
      ]),
    );
    key = streamKey(keyset);
    const stream = bytes.subarray(64 + length);
    requireValue(stream.length >= 56 && stream[0] === 40);
    const aad = Buffer.concat([
      Buffer.from('AttentionStats/archive/v1/payload\0', 'ascii'),
      bytes.subarray(0, 64 + length),
    ]);
    derived = Buffer.from(
      hkdfSync('sha256', key, stream.subarray(1, 33), aad, 32),
    );
    const output = [];
    let total = 0;
    let index = 0;
    let offset = 40;
    while (offset < stream.length) {
      const capacity = index === 0 ? SEGMENT - 40 : SEGMENT;
      const count = Math.min(capacity, stream.length - offset);
      const last = offset + count === stream.length;
      const nonce = Buffer.alloc(12);
      stream.copy(nonce, 0, 33, 40);
      nonce.writeUInt32BE(index, 7);
      nonce[11] = last ? 1 : 0;
      const plain = decryptGcm(
        derived,
        nonce,
        stream.subarray(offset, offset + count),
      );
      total += plain.length;
      requireValue(total <= plaintextLimit);
      output.push(plain);
      offset += count;
      index++;
    }
    requireValue(index > 0);
    // No caller receives a partially authenticated prefix.
    return Buffer.concat(output, total);
  } finally {
    raw.fill(0);
    wrap.fill(0);
    keyset?.fill(0);
    key?.fill(0);
    derived?.fill(0);
  }
}

export function verifyVectors(path) {
  requireValue(statSync(path).size <= 6 * 1024 * 1024);
  const vectors = JSON.parse(readFileSync(path, 'utf8'));
  requireValue(
    vectors.scope ===
      'PUBLIC SYNTHETIC TEST KEYS ONLY; NEVER USE FOR REAL DATA',
  );
  requireValue(
    Array.isArray(vectors.cases) &&
      vectors.cases.length >= 1 &&
      vectors.cases.length <= 8,
  );
  for (const entry of vectors.cases) {
    requireValue(
      typeof entry.envelopeBase64 === 'string' &&
        entry.envelopeBase64.length <= 6 * 1024 * 1024,
    );
    const bytes = Buffer.from(entry.envelopeBase64, 'base64');
    requireValue(
      bytes.toString('base64') === entry.envelopeBase64 &&
        hash(bytes) === entry.envelopeSha256,
    );
    const plaintext = readEnvelope(bytes, entry.syntheticCredential);
    requireValue(hash(plaintext) === entry.plaintextSha256);
    if (entry.plaintextBase64 !== undefined)
      requireValue(plaintext.toString('base64') === entry.plaintextBase64);
    else requireValue(plaintext.length === entry.plaintextLength);
  }
  return vectors.cases.length;
}

if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  const paths = process.argv.slice(2);
  requireValue(paths.length > 0);
  for (const path of paths)
    console.log(
      `PASS: independent reader; ${verifyVectors(path)} synthetic vectors`,
    );
}
