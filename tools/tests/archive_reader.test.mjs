import assert from 'node:assert/strict';
import { hkdfSync } from 'node:crypto';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { readEnvelope, verifyVectors } from '../archive_reader.mjs';

const path = new URL(
  '../../native/archive-qualification/vectors/generated-profile.json',
  import.meta.url,
);
const vectors = JSON.parse(readFileSync(path, 'utf8'));
const entry = vectors.cases.find(value => value.name === 'synthetic-records');
const archive = Buffer.from(entry.envelopeBase64, 'base64');
const credential = entry.syntheticCredential;
const offset = 64 + archive.readUInt32BE(60);
const rejects = bytes => assert.throws(() => readEnvelope(bytes, credential));

test('RFC 5869 SHA-256 cases 1, 2 and 3 with Node crypto', () => {
  const repeat = Buffer.alloc(22, 11);
  const range = start =>
    Buffer.from(Array.from({ length: 80 }, (_, i) => start + i));
  for (const [ikm, salt, info, expected] of [
    [
      repeat,
      Buffer.from('000102030405060708090a0b0c', 'hex'),
      Buffer.from('f0f1f2f3f4f5f6f7f8f9', 'hex'),
      '3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865',
    ],
    [
      range(0),
      range(0x60),
      range(0xb0),
      'b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87',
    ],
    [
      repeat,
      Buffer.alloc(0),
      Buffer.alloc(0),
      '8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8',
    ],
  ]) {
    assert.equal(
      Buffer.from(
        hkdfSync('sha256', ikm, salt, info, expected.length / 2),
      ).toString('hex'),
      expected,
    );
  }
});

test('three public Tink vectors readable without Tink or a source Keystore', () => {
  assert.equal(verifyVectors(path), 3);
});

test('reject every single-byte mutation of header, keyset and one-segment stream', () => {
  for (let i = 0; i < archive.length; i++) {
    const mutated = Buffer.from(archive);
    mutated[i] ^= 1;
    rejects(mutated);
  }
});

test('reject every truncation and appended bytes, including another complete archive', () => {
  for (let i = 0; i < archive.length; i++) rejects(archive.subarray(0, i));
  for (const extra of [Buffer.from([0]), Buffer.alloc(16), archive])
    rejects(Buffer.concat([archive, extra]));
});

test('reject wrong and noncanonical recovery credentials', () => {
  const zero = Buffer.alloc(32).toString('base64url');
  for (const text of [
    zero,
    '',
    credential + '=',
    credential + '\n',
    ' ' + credential,
    credential.slice(0, -1),
    '!'.repeat(43),
    zero.slice(0, -1) + 'B',
  ]) {
    assert.throws(() => readEnvelope(archive, text));
  }
});

test('reject unavailable Argon profile and unsigned oversized wrapped-keyset lengths', () => {
  for (const length of [0, 4097, 0xffffffff]) {
    const mutated = Buffer.from(archive);
    mutated.writeUInt32BE(length, 60);
    rejects(mutated);
  }
  const argon = Buffer.from(archive);
  argon[12] = 2;
  argon.writeUInt32BE(65536, 16);
  argon.writeUInt32BE(3, 20);
  argon.writeUInt32BE(4, 24);
  rejects(argon);
});

test('bound ciphertext and authenticated plaintext without releasing a prefix', () => {
  rejects(Buffer.alloc(4 * 1_048_576 + 16_385));
  assert.throws(() => readEnvelope(archive, credential, 0));
  assert.throws(() => readEnvelope(archive, credential, -1));
});

test('payload binds raw outer metadata even though it is not key authority', () => {
  // Flip every byte in the serialized keyset, including its untrusted KeysetInfo.
  for (let i = 64; i < offset; i++) {
    const mutated = Buffer.from(archive);
    mutated[i] ^= 128;
    rejects(mutated);
  }
});

test('framed artificial records survive complete authenticated read', () => {
  const plaintext = readEnvelope(archive, credential);
  const kinds = [];
  let position = 0;
  while (position < plaintext.length) {
    const kind = plaintext[position];
    const length = plaintext.readUInt32BE(position + 1);
    position += 5;
    assert.ok(length <= plaintext.length - position);
    const object = JSON.parse(
      new TextDecoder('utf-8', { fatal: true }).decode(
        plaintext.subarray(position, position + length),
      ),
    );
    assert.equal(typeof object, 'object');
    kinds.push(kind);
    position += length;
  }
  assert.deepEqual(kinds, [1, 2, 3]);
});
