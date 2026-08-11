import { expect, test } from '@playwright/test';

const adminUsername = process.env.E2E_ADMIN_USERNAME ?? 'admin';
const adminPassword = process.env.E2E_ADMIN_PASSWORD;
if (!adminPassword) throw new Error('E2E_ADMIN_PASSWORD is required');

test('shortlist and comparison selections survive a reload', async ({ page }) => {
  await page.goto('/schools');
  const rows = page.locator('.school-result-list article');
  await expect(rows.first()).toBeVisible();

  await rows.nth(0).locator('.school-select').click();
  await rows.nth(1).locator('.school-select').click();
  await rows.nth(0).locator('.favorite-icon').click();
  await expect(rows.nth(0).locator('.favorite-icon')).toHaveAttribute('aria-label', '取消收藏');

  await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem('kaoyanComparedSchoolIds') ?? '[]').length)).toBe(2);
  await page.reload();
  await expect(rows.first()).toBeVisible({ timeout: 15_000 });
  await expect(rows.nth(0).locator('.school-select input')).toBeChecked();
  await expect(rows.nth(1).locator('.school-select input')).toBeChecked();
  await expect(rows.nth(0).locator('.favorite-icon')).toHaveAttribute('aria-label', '取消收藏');
});

test('recommendation profile persists and generates real results', async ({ page }) => {
  await page.goto('/recommendations');
  await page.getByRole('button', { name: '调整条件' }).click();
  const scoreInput = page.locator('.profile-form input').first();
  await scoreInput.fill('385');
  await page.getByLabel('添加省份偏好').selectOption('浙江');
  await page.getByRole('button', { name: '生成推荐' }).click();
  await expect(page.locator('.recommendation-card').first()).toBeVisible();

  await page.reload();
  await page.getByRole('button', { name: '调整条件' }).click();
  await expect(scoreInput).toHaveValue('385');
  await expect(page.getByRole('button', { name: '浙江', exact: true })).toHaveClass(/selected/);
});

test('personal decision note survives a reload', async ({ page }, testInfo) => {
  await page.goto('/schools');
  const firstSchool = page.locator('.school-result-list article').first();
  await expect(firstSchool).toBeVisible();
  await firstSchool.locator('.favorite-icon').click();
  await page.goto('/favorites');

  const note = page.locator('.favorite-note textarea').first();
  await note.fill('优先核验复试线口径，确认后再决定是否进入冲刺组');
  await expect(page.locator('.favorite-note small').first()).toContainText('已保存到本机');
  await page.reload();
  await expect(note).toHaveValue('优先核验复试线口径，确认后再决定是否进入冲刺组');
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('personal-decision-note.png'), fullPage: true });
});

test('school detail states when yearly trends cannot be inferred', async ({ page }, testInfo) => {
  await page.goto('/schools');
  await page.locator('.school-result-list article').first().locator('.result-title').click();
  await expect(page.locator('.detail-view')).toBeVisible();
  await page.getByRole('tab', { name: '招生与分数' }).click();
  await expect(page.locator('.national-baseline')).toBeVisible();
  await expect(page.locator('.national-baseline')).toContainText('国家线');
  await expect(page.getByRole('link', { name: '教育部官方 PDF' })).toBeVisible();
  await expect(page.locator('.school-baseline')).toContainText('2026 学校基本线');
  await expect(page.locator('.school-baseline')).toContainText('工学 300');
  await expect(page.locator('.school-baseline')).toContainText('学校基本线只是最低门槛');
  await expect(page.getByRole('link', { name: '查看官方表格' })).toBeVisible();
  await expect(page.getByText('不能用前两级分数替代', { exact: false })).toBeVisible();
  await expect(page.getByLabel('招生计划变化趋势结论')).toContainText('数据不足');
  await expect(page.getByLabel('学院或专业复试线变化趋势结论')).toContainText('数据不足');
  await expect(page.getByText('至少需要两个年度才能判断趋势')).toHaveCount(2);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('yearly-trend-boundary.png'), fullPage: true });
});

test('school detail preserves a verified unavailable score-line state', async ({ page }, testInfo) => {
  const schoolsResponse = await page.request.get('/api/schools');
  const payload = await schoolsResponse.json();
  const wuhan = payload.data.find((school: { name: string }) => school.name === '武汉大学');
  expect(wuhan).toBeTruthy();

  await page.goto(`/schools/${wuhan.id}`);
  await page.getByRole('tab', { name: '招生与分数' }).click();
  const baseline = page.locator('.school-baseline');
  await expect(baseline).toContainText('尚未公布');
  await expect(baseline).toContainText('不进行推断');
  await expect(baseline).toContainText('计算机学院');
  await expect(page.getByText('不能用前两级分数替代', { exact: false })).toBeVisible();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('wuhan-unavailable-baseline.png'), fullPage: true });
});

test('school detail exposes all verified 408 program combinations', async ({ page }, testInfo) => {
  await page.goto('/schools');
  const school = page.locator('.school-result-list article').filter({ hasText: '天津师范大学' });
  await expect(school).toHaveCount(1);
  await school.locator('.result-title').click();

  await expect(page.getByText('2026 计算机类 408 专业目录', { exact: true })).toBeVisible();
  await expect(page.locator('.program-list article')).toHaveCount(4);
  await expect(page.locator('.program-list')).toContainText('077500 计算机科学与技术');
  await expect(page.locator('.program-list .field-evidence').first()).toBeVisible();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('verified-program-catalog.png'), fullPage: true });
});

test('administrator can sign in and load the management workspace', async ({ page }, testInfo) => {
  await page.goto('/admin');
  await page.getByPlaceholder('用户名').fill(adminUsername);
  await page.getByPlaceholder('密码').fill(adminPassword);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByText('已登录', { exact: true })).toBeVisible();
  await expect(page.getByText(`${adminUsername} · 系统管理员`, { exact: true })).toBeVisible();
  await page.locator('.password-change summary').click();
  await expect(page.getByPlaceholder('当前密码')).toBeVisible();
  await expect(page.getByPlaceholder('新密码（至少 12 位）')).toBeVisible();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.locator('.password-change summary').click();
  await expect(page.locator('.admin-layout')).toBeVisible();
  await expect(page.getByText('真实字段覆盖', { exact: true })).toBeVisible();
  const firstTask = page.locator('.collection-task-list article').first();
  await expect(firstTask).toBeVisible();
  await firstTask.scrollIntoViewIfNeeded();
  await page.screenshot({ path: testInfo.outputPath('collection-task-queue.png'), fullPage: false });
  await firstTask.locator('summary').click();
  await expect(firstTask.getByText('负责人', { exact: true })).toBeVisible();
  await expect(firstTask.getByRole('button', { name: '保存任务' })).toBeEnabled();
  await expect(firstTask.getByText('官方 URL 待办', { exact: true })).toBeVisible();
  await expect(firstTask.getByLabel('新增资料 URL')).toBeVisible();
  await expect(firstTask.getByText('最近操作', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: '基础档案' }).click();
  await expect(page.locator('#admin-schools .admin-list')).toContainText('北京');

  await page.getByRole('button', { name: '知识资料' }).click();
  const versionHistoryButton = page.locator('#admin-documents button[title="查看版本历史"]').first();
  await expect(versionHistoryButton).toBeVisible();
  await versionHistoryButton.click();
  await expect(page.locator('#admin-documents').getByText('版本历史', { exact: true })).toBeVisible();
  await expect(page.locator('#admin-documents .document-version-history .version-history-head')).toContainText('个快照');
  await page.getByRole('button', { name: '采集与发布' }).click();
  await expect(page.locator('#admin-documents').getByText('最近解析任务', { exact: true })).toBeVisible();
  await expect(page.locator('#admin-documents .parse-task-list:not(.web-capture-list)')).toBeVisible();
  await expect(page.getByLabel('官方网页采集目标')).toBeVisible();
  await expect(page.getByRole('button', { name: '抓取网页草稿' })).toBeDisabled();
  await expect(page.locator('#admin-documents').getByText('最近网页采集', { exact: true })).toBeVisible();
  await expect(page.locator('#admin-documents').getByText('官网内容变化', { exact: true })).toBeVisible();
  await expect(page.getByLabel('官网变化运营摘要')).toContainText('累计变化');
  await expect(page.getByLabel('官网变化运营摘要')).toContainText('最久等待');
  await expect(page.getByLabel('网页变更复核说明')).toBeVisible();
  await expect(page.locator('#admin-documents .web-change-list')).toBeVisible();
  await expect(page.locator('#admin-documents').getByText('官网定时监测', { exact: true })).toBeVisible();
  await expect(page.getByLabel('官网监测目标')).toBeVisible();
  await expect(page.getByLabel('监测间隔小时')).toBeVisible();
  await expect(page.locator('#admin-documents').getByText('尚未配置定时监测', { exact: true })).toBeVisible();
  await expect(page.locator('#admin-documents').getByText('审核发布批次', { exact: true })).toBeVisible();
  await expect(page.getByLabel('发布或回滚说明')).toBeVisible();
  await expect(page.getByRole('button', { name: '原子发布' })).toBeDisabled();
  await expect(page.locator('#admin-documents').getByText('最近批次', { exact: true })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('knowledge-workspace.png'), fullPage: true });

  await page.getByRole('button', { name: '招生数据' }).click();
  await expect(page.locator('#admin-admission-import')).toBeVisible();
  await expect(page.locator('#admin-admission-import').getByText('拟录取名单导入', { exact: true })).toBeVisible();
  await expect(page.getByLabel('匿名批次 JSON')).toBeVisible();
  await expect(page.locator('#admin-admission-import').getByText('暂无导入批次', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '复试数据' }).click();
  const retestBlock = page.locator('#admin-retest');
  await expect(retestBlock).toBeVisible();
  await retestBlock.getByRole('button', { name: '新增记录' }).click();
  await expect(retestBlock.getByLabel('复试规则作用域')).toBeVisible();
  await expect(retestBlock.getByRole('option', { name: '学校级通用规则' })).toHaveCount(1);
  await expect(retestBlock.getByRole('option', { name: '专业级规则' })).toHaveCount(1);
  await expect(retestBlock.getByRole('combobox').nth(1)).toBeVisible();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('admin-workspace.png'), fullPage: true });
});

test('administrator can access the autonomous evidence workflow', async ({ page }, testInfo) => {
  await page.goto('/admin');
  await page.getByPlaceholder('用户名').fill(adminUsername);
  await page.getByPlaceholder('密码').fill(adminPassword);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByText('已登录', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Agent 运维' }).click();
  await expect(page.getByText('索引与评估', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '验证策略' })).toBeEnabled();
  await expect(page.getByRole('button', { name: '审计知识库' })).toBeEnabled();
  await expect(page.getByRole('button', { name: '重排基准' })).toBeEnabled();
  await expect(page.locator('.agent-status-title')).toContainText('feature reranker');
  await page.getByRole('button', { name: '验证策略' }).click();
  await expect(page.getByText('异步任务已提交', { exact: true })).toBeVisible();
  await expect(page.locator('.operation-job-queue')).toContainText('证据策略');
  await page.locator('.operation-job-queue article').first().getByTitle('查看任务 trace').click();
  await expect(page.locator('.operation-trace')).toBeVisible();

  await page.getByRole('button', { name: '诊断记录' }).click();
  await expect(page.locator('.agent-diagnostics')).toBeVisible();
  await expect(page.getByText('失败、拒绝与过滤记录', { exact: true })).toBeVisible();
  await expect(page.getByPlaceholder('搜索院校、任务、原因或 trace')).toBeVisible();
  await page.getByLabel('诊断类别').selectOption('KNOWLEDGE_AUDIT');
  await page.getByTitle('应用诊断筛选').click();
  await expect.poll(async () => {
    if (await page.locator('.diagnostic-empty').count()) return true;
    const labels = await page.locator('.diagnostic-list article em').allTextContents();
    return labels.length > 0 && labels.every((label) => label.includes('知识审计'));
  }).toBe(true);

  await page.getByRole('button', { name: '证据工作流' }).click();
  await expect(page.locator('.coverage-workflow-console')).toBeVisible();
  await expect(page.getByText('官方证据工作流', { exact: true })).toBeVisible();
  await expect(page.getByPlaceholder('输入数据采集任务中的院校全称')).toHaveValue('浙江大学');
  await expect(page.getByRole('button', { name: '启动工作流' })).toBeEnabled();
  await expect(page.getByText('历史运行', { exact: true })).toBeVisible();
  await expect(page.locator('.workflow-empty')).toBeVisible();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('agent-coverage-workflow.png'), fullPage: true });
});
