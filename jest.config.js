module.exports = {
  preset: '@react-native/jest-preset',
  roots: ['<rootDir>/src'],
  transformIgnorePatterns: [
    'node_modules/(?!\\.pnpm/|react-native/|@react-native/)',
  ],
};
