import { mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import playwright from '../frontend/node_modules/@playwright/test/index.js';

const { chromium } = playwright;

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const outputDir = resolve(root, 'docs', 'images');
const baseUrl = process.env.SCREENSHOT_BASE_URL ?? 'http://127.0.0.1:5173';

const pages = [
  { path: '/', file: 'overview.png', heading: '决策概览', ready: '.focus-school-list article' },
  { path: '/schools', file: 'school-search.png', heading: '查找院校', ready: '.query-workbench' },
  { path: '/recommendations', file: 'recommendations.png', heading: '智能推荐', ready: '.recommendation-layout' },
];

await mkdir(outputDir, { recursive: true });
const browser = await chromium.launch({ channel: 'chrome' });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });

try {
  for (const item of pages) {
    await page.goto(`${baseUrl}${item.path}`, { waitUntil: 'networkidle' });
    await page.locator('.topbar h1').filter({ hasText: item.heading }).waitFor();
    await page.locator(item.ready).first().waitFor({ timeout: 20_000 });
    await page.screenshot({ path: resolve(outputDir, item.file), fullPage: false });
  }
} finally {
  await browser.close();
}
