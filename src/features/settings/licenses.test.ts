import components from './licenses.json';
import texts from './licenseTexts.json';

test('every attributed component has an identity, project URL and offline license', () => {
  expect(components.length).toBeGreaterThan(100);
  const identities = components.map(component => {
    expect(Object.keys(component).sort()).toEqual([
      'license',
      'name',
      'url',
      'version',
    ]);
    expect(component.name.trim()).not.toBe('');
    expect(component.version.trim()).not.toBe('');
    expect(component.url).toMatch(/^https:\/\/[^\s]+$/);
    expect(
      (texts as Record<string, string>)[component.license]?.length,
    ).toBeGreaterThan(100);
    return `${component.name}@${component.version}`;
  });
  expect(new Set(identities).size).toBe(identities.length);
});

test('the inventory covers JavaScript, native and embedded runtimes', () => {
  const names = components.map(component => component.name);
  expect(names).toEqual(
    expect.arrayContaining([
      'react',
      'react-native',
      '@babel/runtime',
      'androidx.biometric:biometric',
      'androidx.room3:room3-runtime-android',
      'net.zetetic:sqlcipher-android',
      'com.facebook.hermes:hermes-android',
      'com.facebook.fbjni:fbjni',
      'Yoga',
      'Protocol Buffers (shaded in Tink)',
      'libjpeg-turbo',
      'com.android.tools:desugar_jdk_libs',
    ]),
  );
  for (const name of [
    'jest',
    'typescript',
    'hermes-compiler',
    '@react-native/codegen',
  ]) {
    expect(names).not.toContain(name);
  }
});

test('the bundled notices retain required embedded-software acknowledgements', () => {
  expect(texts.NOTICE).toContain('ZETETIC LLC');
  expect(texts.NOTICE).toContain('Public Suffix List');
  expect(texts.NOTICE).toContain(
    'This software is based in part on the work of the Independent JPEG Group.',
  );
  expect(texts.NOTICE).toContain(
    'This product includes software developed by the OpenSSL Project',
  );
});
