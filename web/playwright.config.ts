import { defineConfig } from "@playwright/test";

// Design review: screenshots and accessibility scans of the running production build.
export default defineConfig({
  testDir: "./e2e",
  reporter: [["list"]],
  workers: 2,
  use: { baseURL: process.env.BASE_URL ?? "http://localhost:3000" },
});
