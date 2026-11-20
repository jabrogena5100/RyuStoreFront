// Screenshot every review sheet (evidence-raw/images/<dir>/*.html) to a PNG beside it. Run in the Playwright image.
import { chromium } from "@playwright/test";
import { readdirSync } from "node:fs";
const dir = process.argv[2] ?? "/workspace/evidence-raw/images/review";
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1600, height: 900 } });
for (const f of readdirSync(dir).filter((x) => x.endsWith(".html")).sort()) {
  await page.goto(`file://${dir}/${f}`);
  await page.waitForLoadState("load");
  await page.screenshot({ path: `${dir}/${f.replace(".html", ".png")}`, fullPage: true });
}
await browser.close();
console.log("done");
