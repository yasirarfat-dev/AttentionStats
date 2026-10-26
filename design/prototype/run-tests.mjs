import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
const require = createRequire(import.meta.url);
const install = process.argv[2] === 'install';
const result = spawnSync(process.execPath, [require.resolve('@playwright/test/cli'),
  ...(install ? ['install', 'chromium'] : ['test', ...process.argv.slice(2)]),
], {
  stdio:'inherit', cwd:fileURLToPath(new URL('.', import.meta.url)),
  env:{ ...process.env, PLAYWRIGHT_BROWSERS_PATH:fileURLToPath(new URL('./.cache/browsers', import.meta.url)) },
});
if (result.error) console.error(result.error.message);
process.exitCode = result.status ?? 1;
