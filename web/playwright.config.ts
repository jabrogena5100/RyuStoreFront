import { defineConfig } from "@playwright/test";

// Design review: screenshots and accessibility scans of the running production build.
export default defineConfig({
  testDir: "./e2e",
  // Design-proposal screenshots run only when asked for: SPEC=e2e/proposal.spec.ts scripts/design-shots.sh
  testIgnore: process.env.SPEC ? [] : ["**/proposal.spec.ts"],
  reporter: [["list"]],
  workers: 2,
  use: { baseURL: process.env.BASE_URL ?? "http://localhost:3000" },
});
