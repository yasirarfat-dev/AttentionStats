import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir:'./tests', fullyParallel:true, workers:2, retries:0,
  reporter:'list', timeout:30000,
  use:{ baseURL:'http://127.0.0.1:4179', viewport:{ width:1280, height:1000 }, trace:'retain-on-failure' },
  webServer:{ command:'node server.mjs', url:'http://127.0.0.1:4179', reuseExistingServer:false, timeout:10000 },
});
