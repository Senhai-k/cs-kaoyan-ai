import { createHash } from 'node:crypto';
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';

process.on('uncaughtException', (error) => {
  console.error(error instanceof Error ? (error.stack ?? error.message) : String(error));
  process.exitCode = 1;
});

const BASE_URL = 'https://yz.chsi.com.cn';
const BASE_HOST = new URL(BASE_URL).hostname;
const BROWSER_USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) '
  + 'AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36';
const DEFAULT_DISCIPLINES = [
  { mldm: '07', yjxkdm: '0775', name: '计算机科学与技术（理学）' },
  { mldm: '08', yjxkdm: '0812', name: '计算机科学与技术' },
  { mldm: '08', yjxkdm: '0835', name: '软件工程' },
  { mldm: '08', yjxkdm: '0839', name: '网络空间安全' },
  { mldm: '08', yjxkdm: '0854', name: '电子信息' },
  { mldm: '14', yjxkdm: '1405', name: '智能科学与技术' }
];
const DEFAULT_MAJOR_CODES = [
  '077500', '077501', '077502', '077503',
  '081200', '081201', '081202', '081203',
  '083500', '083900',
  '085400', '085404', '085405', '085410', '085411', '085412',
  '140500'
];
const CROSS_DISCIPLINE_MAJOR_CODES = [
  '085401', '085402', '085403', '085406', '085407', '085408', '085409'
];

const cli = parseArgs(process.argv.slice(2));
if (cli.help) {
  printUsage();
  process.exit(0);
}
if (cli.verifyFile) {
  const verifyPath = resolve(String(cli.verifyFile));
  const payload = JSON.parse(await readFile(verifyPath, 'utf8'));
  const expected = String(payload.sha256 ?? '').toLowerCase();
  delete payload.sha256;
  const actual = sha256(stableJson(payload));
  if (!/^[0-9a-f]{64}$/.test(expected) || actual !== expected) {
    throw new Error(`批次 SHA-256 校验失败: ${verifyPath}`);
  }
  console.log(`[校验通过] ${verifyPath} ${actual}`);
  process.exit(0);
}
const outputPath = resolve(cli.output ?? 'database/catalog-408-2026.json');
const cacheDir = resolve(cli.cacheDir ?? `${tmpdir()}/cs-kaoyan-ai/chsi-408`);
const delayMs = positiveInteger(cli.delayMs, 1800);
const maxSchools = cli.maxSchools === undefined ? Infinity : positiveInteger(cli.maxSchools, 1);
const pageLimit = cli.pageLimit === undefined ? Infinity : positiveInteger(cli.pageLimit, 1);
const refresh = cli.refresh === true;
const suppliedCookiePairs = [];
const responseCookieJar = new Map();
let lastRequestAt = 0;

await mkdir(cacheDir, { recursive: true });
await loadSuppliedCookies(cli.cookieFile, cli.cookieHeader ?? process.env.CHSI_COOKIE);
if (maxSchools === Infinity && pageLimit === Infinity && suppliedCookiePairs.length === 0) {
  throw new Error('完整采集必须通过 --cookie-file 或 CHSI_COOKIE 提供本人合法研招网登录会话');
}
await getText('/zsml/', 'home.html');
const homeHtml = await readFile(`${cacheDir}/home.html`, 'utf8');
const catalogYear = Number(homeHtml.match(/curYear:\s*"(\d{4})"/)?.[1]);
if (!Number.isInteger(catalogYear)) {
  throw new Error('无法从研招网公开目录识别招生年份');
}
if (cli.year && Number(cli.year) !== catalogYear) {
  throw new Error(`研招网当前公开目录为 ${catalogYear} 年，不是请求的 ${cli.year} 年`);
}

const majorCodes = cli.majorCodes
  ? cli.majorCodes.split(',').map((item) => item.trim()).filter(Boolean)
  : [...DEFAULT_MAJOR_CODES, ...(cli.includeCrossDisciplines ? CROSS_DISCIPLINE_MAJOR_CODES : [])];
if (majorCodes.length === 0) throw new Error('没有可采集的专业代码');

const professionalEntries = [];
let authenticatedSessionVerified = false;
for (const majorCode of majorCodes) {
  const entries = await fetchProfessionalEntries(majorCode);
  professionalEntries.push(...entries);
  console.log(`[专业发现] ${majorCode}: ${entries.length} 个专业条目`);
  if (maxSchools === Infinity && pageLimit === Infinity && !authenticatedSessionVerified) {
    authenticatedSessionVerified = await probeAuthenticatedSession(entries);
  }
}
if (maxSchools === Infinity && pageLimit === Infinity && !authenticatedSessionVerified) {
  throw new Error('无法找到需要登录的招生单位第二页，未能验证研招网登录会话');
}

const uniqueProfessionalEntries = deduplicate(
  professionalEntries,
  (item) => `${item.zydm}|${item.zymc}|${item.xwlx}|${item.sign}`
);
const raw408Rows = [];
let schoolsVisited = 0;

for (const [index, professional] of uniqueProfessionalEntries.entries()) {
  if (schoolsVisited >= maxSchools) break;
  const sourceUrl = buildProfessionalDetailUrl(professional);
  await getText(sourceUrl, `detail-${safeKey(professional.zydm)}-${safeKey(professional.sign)}.html`);
  const schools = await fetchAllSchools(professional);
  console.log(`[院校发现 ${index + 1}/${uniqueProfessionalEntries.length}] ${professional.zydm} ${professional.zymc}: ${schools.length} 所`);

  for (const [schoolIndex, school] of schools.entries()) {
    if (schoolsVisited >= maxSchools) break;
    schoolsVisited += 1;
    const directions = await fetchAllDirections(professional, school);
    for (const direction of directions) {
      const combinations = Array.isArray(direction.kskmz) ? direction.kskmz : [];
      combinations.forEach((combination, combinationIndex) => {
        const subjects = normalizeSubjects(combination);
        if (subjects.professional.code !== '408') return;
        raw408Rows.push(normalize408Row({
          catalogYear,
          professional,
          school,
          direction,
          subjects,
          sourceUrl,
          combinationIndex
        }));
      });
    }
    console.log(`  [科目核验 ${schoolIndex + 1}/${schools.length}] ${school.dwmc}: ${directions.length} 个方向`);
  }
}

const records = aggregateRows(raw408Rows);
const retrievedAt = new Date().toISOString();
const payload = {
  schemaVersion: 1,
  collectorVersion: '1.0.0',
  year: catalogYear,
  retrievedAt,
  scope: {
    description: '计算机核心学科中，第四门初试科目代码明确为 408 的招生目录记录',
    focus: cli.includeCrossDisciplines ? 'COMPUTER_WITH_CROSS_DISCIPLINES' : 'COMPUTER_CORE',
    disciplines: DEFAULT_DISCIPLINES,
    majorCodes,
    source: '中国研究生招生信息网硕士专业目录',
    sourceUrl: `${BASE_URL}/zsml/`
  },
  stats: {
    complete: maxSchools === Infinity && pageLimit === Infinity,
    professionalEntries: uniqueProfessionalEntries.length,
    schoolsVisited,
    raw408Directions: raw408Rows.length,
    records: records.length,
    schools: new Set(records.map((item) => item.school.code)).size
  },
  records
};
payload.sha256 = sha256(stableJson(payload));

await writeJsonAtomic(outputPath, payload);
console.log(`[完成] ${payload.stats.schools} 所院校，${records.length} 条 408 记录`);
console.log(`[输出] ${outputPath}`);

async function fetchProfessionalEntries(majorCode) {
  const response = await postForm('/zsml/rs/zys.do', {
    zydm: majorCode, zymc: '', xwlx: '', mldm: '', yjxkdm: '',
    xxfs: '', tydxs: '', jsggjh: '', start: 0, curPage: 1,
    pageSize: 20, totalPage: 0, totalCount: 0
  }, `professionals-${safeKey(majorCode)}.json`, `${BASE_URL}/zsml/`);
  assertSuccessful(response, `专业发现 ${majorCode}`);
  return response.msg.list.filter((item) => item.zydm === majorCode);
}

async function probeAuthenticatedSession(professionals) {
  for (const professional of professionals) {
    const firstPage = await fetchSchoolsPage(professional, 0, 20);
    assertSuccessful(firstPage, `登录探针 ${professional.zydm} ${professional.zymc} 第 1 页`);
    if (!firstPage.msg.nextPageAvailable) continue;

    const secondPage = await fetchSchoolsPage(
      professional,
      firstPage.msg.startOfNextPage,
      firstPage.msg.pageCount
    );
    assertSuccessful(secondPage, `登录探针 ${professional.zydm} ${professional.zymc} 第 2 页`);
    console.log(`[会话校验] ${professional.zydm} ${professional.zymc} 招生单位第 2 页访问成功`);
    return true;
  }
  return false;
}

async function fetchAllSchools(professional) {
  const all = [];
  let start = 0;
  let pageSize = 20;
  let pagesRead = 0;
  while (true) {
    const response = await fetchSchoolsPage(professional, start, pageSize);
    assertSuccessful(response, `招生单位 ${professional.zydm} ${professional.zymc}`);
    all.push(...response.msg.list);
    pagesRead += 1;
    if (!response.msg.nextPageAvailable || pagesRead >= pageLimit) break;
    pageSize = response.msg.pageCount;
    start = response.msg.startOfNextPage;
  }
  return deduplicate(all, (item) => `${item.dwdm}|${item.zydm}|${item.sign}`);
}

async function fetchSchoolsPage(professional, start, pageSize) {
  return postForm('/zsml/rs/zydws.do', {
    zydm: professional.zydm,
    zymc: professional.zymc,
    dwmc: '', dwdm: '', ssdm: '', xxfs: professional.mxxfs ?? '',
    'dwlxs[0]': 'all', tydxs: professional.mtydxs ?? '', jsggjh: professional.mjsggjh ?? '',
    start, curPage: Math.floor(start / pageSize) + 1, pageSize, totalPage: 0, totalCount: 0
  }, `schools-${safeKey(professional.zydm)}-${safeKey(professional.sign)}-${start}.json`,
  buildProfessionalDetailUrl(professional));
}

async function fetchAllDirections(professional, school) {
  const all = [];
  let start = 0;
  let pageSize = 10;
  let pagesRead = 0;
  while (true) {
    const form = {
      zydm: professional.zydm,
      zymc: professional.zymc,
      dwdm: school.dwdm,
      xxfs: school.mxxfs ?? '',
      tydxs: school.mtydxs ?? '',
      jsggjh: school.mjsggjh ?? '',
      start,
      pageSize,
      totalCount: 0
    };
    const schoolTypes = Array.isArray(school.mdwlxs) && school.mdwlxs.length ? school.mdwlxs : ['all'];
    schoolTypes.forEach((value, index) => { form[`dwlxs[${index}]`] = value; });
    const response = await postForm('/zsml/rs/yjfxs.do', form,
      `directions-${safeKey(professional.zydm)}-${safeKey(school.dwdm)}-${start}.json`,
      buildProfessionalDetailUrl(professional));
    assertSuccessful(response, `专业方向 ${school.dwmc} ${professional.zydm}`);
    all.push(...response.msg.list);
    pagesRead += 1;
    if (!response.msg.nextPageAvailable || pagesRead >= pageLimit) break;
    pageSize = response.msg.pageCount;
    start = response.msg.startOfNextPage;
  }
  return all;
}

function normalize408Row({ catalogYear, professional, school, direction, subjects, sourceUrl, combinationIndex }) {
  const degreeType = professional.xwlx === 'zyxw' ? '专硕' : '学硕';
  const studyMode = direction.xxfs === '2' ? '非全日制' : '全日制';
  const rawEvidence = {
    catalogYear,
    recordId: direction.id,
    schoolCode: direction.dwdm,
    schoolName: direction.dwmc,
    collegeCode: direction.yxsdm,
    collegeName: direction.yxsmc,
    majorCode: direction.zydm,
    majorName: direction.zymc,
    directionCode: direction.yjfxdm,
    directionName: direction.yjfxmc,
    studyMode,
    quotaText: direction.nzsrsstr ?? null,
    subjects
  };
  return {
    key: [direction.dwdm, direction.yxsdm, direction.zydm, studyMode,
      subjects.politics.code, subjects.foreignLanguage.code, subjects.math.code,
      subjects.professional.code].join('|'),
    catalogRecordId: `${direction.id}:${combinationIndex}`,
    school: {
      code: direction.dwdm,
      chsiId: direction.schId,
      name: direction.dwmc,
      provinceCode: direction.szssm,
      province: direction.szss,
      is985: direction.b985 === '1',
      is211: direction.b211 === '1',
      isDoubleFirstClass: direction.syl === '1'
    },
    college: { code: direction.yxsdm, name: direction.yxsmc },
    major: {
      code: direction.zydm,
      name: direction.zymc,
      degreeType,
      studyMode
    },
    direction: { code: direction.yjfxdm, name: direction.yjfxmc },
    subjects,
    quotaText: direction.nzsrsstr ?? null,
    majorRemark: direction.zybz ?? null,
    source: {
      title: `${catalogYear}年全国硕士研究生招生考试专业目录 - ${direction.dwmc} ${direction.zydm}`,
      type: '研招网招生专业目录',
      url: sourceUrl,
      official: true,
      publisher: '中国研究生招生信息网',
      rawEvidence,
      sha256: sha256(stableJson(rawEvidence))
    }
  };
}

function aggregateRows(rows) {
  const grouped = new Map();
  for (const row of rows) {
    const current = grouped.get(row.key);
    if (!current) {
      grouped.set(row.key, {
        ...row,
        catalogRecordIds: [row.catalogRecordId],
        directions: [row.direction],
        quotaTexts: row.quotaText ? [row.quotaText] : [],
        majorRemarks: row.majorRemark ? [row.majorRemark] : []
      });
      continue;
    }
    current.catalogRecordIds.push(row.catalogRecordId);
    if (!current.directions.some((item) => item.code === row.direction.code && item.name === row.direction.name)) {
      current.directions.push(row.direction);
    }
    if (row.quotaText && !current.quotaTexts.includes(row.quotaText)) current.quotaTexts.push(row.quotaText);
    if (row.majorRemark && !current.majorRemarks.includes(row.majorRemark)) current.majorRemarks.push(row.majorRemark);
  }
  return [...grouped.values()].map(({ key, catalogRecordId, direction, quotaText, majorRemark, ...row }) => {
    const rawEvidence = {
      ...row.source.rawEvidence,
      catalogRecordIds: row.catalogRecordIds,
      directions: row.directions,
      quotaTexts: row.quotaTexts,
      majorRemarks: row.majorRemarks
    };
    return {
      ...row,
      source: { ...row.source, rawEvidence, sha256: sha256(stableJson(rawEvidence)) }
    };
  }).sort((a, b) => a.school.code.localeCompare(b.school.code, 'zh-CN')
    || a.college.code.localeCompare(b.college.code, 'zh-CN')
    || a.major.code.localeCompare(b.major.code, 'zh-CN'));
}

function normalizeSubjects(combination) {
  const subject = (value) => ({
    code: String(value?.kskmdm ?? '').trim(),
    name: String(value?.kskmmc ?? '').trim(),
    note: value?.cksm ? String(value.cksm).trim() : null
  });
  return {
    politics: subject(combination.km1Vo),
    foreignLanguage: subject(combination.km2Vo),
    math: subject(combination.km3Vo),
    professional: subject(combination.km4Vo)
  };
}

function buildProfessionalDetailUrl(item) {
  const params = new URLSearchParams({
    zydm: item.zydm ?? '', zymc: item.zymc ?? '', xwlx: item.xwlx ?? '',
    mldm: item.mldm ?? '', mlmc: item.mlmc ?? '', yjxkdm: item.yjxkdm ?? '',
    yjxkmc: item.yjxkmc ?? '', xxfs: item.mxxfs ?? '', tydxs: item.mtydxs ?? '',
    jsggjh: item.mjsggjh ?? '', sign: item.sign ?? ''
  });
  return `${BASE_URL}/zsml/zydetail.do?${params}`;
}

async function postForm(path, form, cacheName, referer) {
  const cachePath = `${cacheDir}/${cacheName}`;
  if (!refresh) {
    try {
      const cached = JSON.parse(await readFile(cachePath, 'utf8'));
      if (cached?.flag) return cached;
    } catch (error) {
      if (error.code !== 'ENOENT' && !(error instanceof SyntaxError)) throw error;
    }
  }
  const body = new URLSearchParams();
  Object.entries(form).forEach(([key, value]) => body.set(key, String(value ?? '')));
  let response;
  for (let attempt = 1; attempt <= 5; attempt += 1) {
    const text = await request(path, {
      method: 'POST',
      headers: {
        Accept: 'application/json, text/plain, */*',
        'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
        'Content-Type': 'application/x-www-form-urlencoded;charset=utf-8',
        Origin: BASE_URL,
        'X-Requested-With': 'XMLHttpRequest',
        Referer: referer ?? `${BASE_URL}/zsml/`
      },
      body: body.toString()
    });
    response = await resolveAsyncResponse(JSON.parse(text));
    if (response?.flag) {
      await writeFile(cachePath, `${JSON.stringify(response)}\n`, 'utf8');
      return response;
    }
    if (!String(response?.msg ?? '').includes('频繁') && !String(response?.msg ?? '').includes('人数较多')) {
      return response;
    }
    await sleep(3000 * attempt);
  }
  return response;
}

async function resolveAsyncResponse(response) {
  if (!response?.taskId || String(response.invokeStatus ?? '').toUpperCase() !== 'WAIT') return response;
  const taskId = response.taskId;
  for (let attempt = 1; attempt <= 10; attempt += 1) {
    const pollingDelay = 1000 + 300 * (attempt - 1);
    await sleep(pollingDelay);
    const params = new URLSearchParams({
      taskId,
      _v: String(Date.now()),
      _s: String(pollingDelay)
    });
    const progressText = await request(`/zsml/asynProgress.do?${params}`, {
      headers: { Accept: 'application/json, text/plain, */*', 'X-Requested-With': 'XMLHttpRequest' }
    });
    const progress = JSON.parse(progressText);
    const state = String(progress?.state ?? '').toUpperCase();
    if (state === 'WAIT' || state === 'UPDATE') continue;
    if (state === 'SUCCESS') {
      const resultParams = new URLSearchParams({ taskId });
      const resultText = await request(`/zsml/ajaxRs.do?${resultParams}`, {
        headers: { Accept: 'application/json, text/plain, */*', 'X-Requested-With': 'XMLHttpRequest' }
      });
      return JSON.parse(resultText);
    }
    const messages = {
      ERROR: '参数非法，请求失败',
      REJECTED: '系统繁忙，请稍后重试',
      EXCEPTION: '服务异常，请稍后重试',
      EXPIRED: '请求过期，请稍后重试',
      NONE: '请求过期，请稍后重试',
      LOCKFAIL: '系统正在处理，请稍候重试'
    };
    return { flag: false, msg: messages[state] ?? `异步任务状态异常: ${state || 'UNKNOWN'}` };
  }
  return { flag: false, msg: '异步任务轮询超时' };
}

async function getText(pathOrUrl, cacheName) {
  return cachedRequest(cacheName, () => request(pathOrUrl, {
    headers: { Accept: 'text/html,application/xhtml+xml', Referer: `${BASE_URL}/zsml/` }
  }));
}

async function cachedRequest(cacheName, loader) {
  const path = `${cacheDir}/${cacheName}`;
  if (!refresh) {
    try { return await readFile(path, 'utf8'); } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
  }
  const content = await loader();
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, content, 'utf8');
  return content;
}

async function request(pathOrUrl, options = {}) {
  const url = pathOrUrl.startsWith('http') ? pathOrUrl : `${BASE_URL}${pathOrUrl}`;
  let lastError;
  for (let attempt = 1; attempt <= 4; attempt += 1) {
    const waitMs = Math.max(0, delayMs - (Date.now() - lastRequestAt));
    if (waitMs) await sleep(waitMs);
    lastRequestAt = Date.now();
    let timeoutHandle;
    try {
      const controller = new AbortController();
      timeoutHandle = setTimeout(() => controller.abort(), 45_000);
      const response = await fetch(url, {
        ...options,
        redirect: 'follow',
        headers: {
          'User-Agent': BROWSER_USER_AGENT,
          ...(cookieHeader() ? { Cookie: cookieHeader() } : {}),
          ...options.headers
        },
        signal: controller.signal
      });
      updateCookies(response.headers);
      const text = await response.text();
      if (response.ok) return text;
      if (![429, 500, 502, 503, 504].includes(response.status)) {
        throw new Error(`HTTP ${response.status}: ${url}`);
      }
      lastError = new Error(`HTTP ${response.status}: ${url}`);
    } catch (error) {
      lastError = error;
    } finally {
      clearTimeout(timeoutHandle);
    }
    await sleep(1000 * attempt);
  }
  throw lastError;
}

function updateCookies(headers) {
  const values = typeof headers.getSetCookie === 'function'
    ? headers.getSetCookie()
    : (headers.get('set-cookie') ? [headers.get('set-cookie')] : []);
  values.forEach((header) => {
    const pair = header.split(';', 1)[0];
    const separator = pair.indexOf('=');
    if (separator > 0) responseCookieJar.set(pair.slice(0, separator), pair.slice(separator + 1));
  });
}

function cookieHeader() {
  const suppliedNames = new Set(suppliedCookiePairs.map((item) => item.name));
  const responsePairs = [...responseCookieJar.entries()]
    .filter(([name]) => !suppliedNames.has(name))
    .map(([name, value]) => ({ name, value }));
  return [...suppliedCookiePairs, ...responsePairs]
    .map(({ name, value }) => `${name}=${value}`)
    .join('; ');
}

function assertSuccessful(response, context) {
  if (!response?.flag || !response?.msg || !Array.isArray(response.msg.list)) {
    if (response?.msg2 || response?.msg === '请登录') {
      throw new Error(`${context} 的研招网登录会话无效或已过期。请先确认浏览器能打开目录第二页，再重新复制该 XHR 请求的 Cookie 值。`);
    }
    throw new Error(`${context} 失败: ${response?.msg ?? '返回格式错误'}`);
  }
}

async function loadSuppliedCookies(cookieFile, cookieHeaderValue) {
  if (cookieHeaderValue) {
    String(cookieHeaderValue).trim().replace(/^cookie\s*:\s*/i, '').split(';').forEach((part) => {
      const separator = part.indexOf('=');
      if (separator > 0) suppliedCookiePairs.push({
        name: part.slice(0, separator).trim(),
        value: part.slice(separator + 1).trim()
      });
    });
  }
  if (!cookieFile) return;
  const content = await readFile(resolve(cookieFile), 'utf8');
  const filePairs = [];
  for (const line of content.split(/\r?\n/)) {
    if (!line || (line.startsWith('#') && !line.startsWith('#HttpOnly_'))) continue;
    const fields = line.replace(/^#HttpOnly_/, '').split('\t');
    if (fields.length < 7 || !cookieDomainMatches(fields[0], BASE_HOST)) continue;
    const expiresAt = Number(fields[4]);
    if (Number.isFinite(expiresAt) && expiresAt > 0 && expiresAt * 1000 <= Date.now()) continue;
    filePairs.push({ name: fields[5], value: fields[6], path: fields[2] || '/' });
  }
  filePairs.sort((left, right) => right.path.length - left.path.length);
  suppliedCookiePairs.push(...filePairs);
}

function cookieDomainMatches(cookieDomain, host) {
  const normalized = String(cookieDomain ?? '').trim().replace(/^\./, '').toLowerCase();
  return normalized !== '' && (host === normalized || host.endsWith(`.${normalized}`));
}

function deduplicate(items, keyOf) {
  const seen = new Set();
  return items.filter((item) => {
    const key = keyOf(item);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function stableJson(value) {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableJson(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

function sha256(value) {
  return createHash('sha256').update(value, 'utf8').digest('hex');
}

async function writeJsonAtomic(path, value) {
  await mkdir(dirname(path), { recursive: true });
  const temporaryPath = `${path}.tmp`;
  await writeFile(temporaryPath, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
  await rename(temporaryPath, path);
}

function safeKey(value) {
  return String(value ?? '').replace(/[^a-zA-Z0-9_-]/g, '_');
}

function positiveInteger(value, fallback) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function sleep(ms) {
  return new Promise((resolvePromise) => setTimeout(resolvePromise, ms));
}

function parseArgs(args) {
  const result = {};
  for (const arg of args) {
    if (!arg.startsWith('--')) continue;
    const [rawKey, ...rawValue] = arg.slice(2).split('=');
    const key = rawKey.replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
    result[key] = rawValue.length ? rawValue.join('=') : true;
  }
  return result;
}

function printUsage() {
  console.log(`用法：
  node scripts/catalog/chsi-408-collector.mjs --page-limit=1 --output=database/catalog-408-2026.json
  node scripts/catalog/chsi-408-collector.mjs --cookie-file=<Netscape Cookie 文件> --refresh --output=<候选文件>

参数：
  --cookie-file=<path>   本人已登录研招网会话导出的 Netscape Cookie 文件
  --cookie-header=<text> 当前进程使用的 Cookie 请求头，也可设置 CHSI_COOKIE
  --major-codes=<codes>  逗号分隔的专业代码；默认仅覆盖计算机核心专业
  --include-cross-disciplines 额外采集通信、控制、仪器、光电、生医等考 408 的交叉专业
  --page-limit=<n>       每个列表最多读取页数；设置后批次一定标记为不完整
  --max-schools=<n>      最多访问院校数；设置后批次一定标记为不完整
  --delay-ms=<n>         请求最小间隔，默认 1800ms
  --year=<yyyy>          要求研招网当前目录年份必须匹配
  --refresh              忽略本地响应缓存
  --output=<path>        输出 JSON 路径
  --verify-file=<path>   离线重算并校验已有批次 SHA-256
  --help                 显示帮助，不发起网络请求`);
}
