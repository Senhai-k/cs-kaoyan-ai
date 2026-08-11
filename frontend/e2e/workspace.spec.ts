import { expect, test } from '@playwright/test';

const routes = [
  { path: '/', title: '决策概览' },
  { path: '/recommendations', title: '智能推荐' },
  { path: '/ai', title: '资料问答' },
  { path: '/admin', title: '数据管理' }
];

for (const route of routes) {
  test(`${route.title} renders without horizontal overflow`, async ({ page }) => {
    await page.goto(route.path);
    await expect(page.locator('.workspace')).toBeVisible();
    await expect(page.locator('.topbar h1')).toHaveText(route.title);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  });
}

test('captures the workspace for visual inspection', async ({ page }, testInfo) => {
  await page.goto('/');
  await expect(page.locator('.workspace')).toBeVisible();
  await expect(page.locator('.decision-profile-bar')).toBeVisible({ timeout: 15_000 });
  await expect(page.locator('.focus-school-list article').first()).toBeVisible({ timeout: 15_000 });
  await page.screenshot({ path: testInfo.outputPath('workspace.png'), fullPage: true });
});
