import { defineConfig } from "@playwright/test";

// Checks against the REAL API (not the mock): scripts/web-live-check.sh. Kept apart from the screenshot run in e2e/.
export default defineConfig({
  testDir: "./live",
  reporter: [["list"]],
  workers: 1,
  use: { baseURL: process.env.BASE_URL ?? "http://localhost:3000" },
});
