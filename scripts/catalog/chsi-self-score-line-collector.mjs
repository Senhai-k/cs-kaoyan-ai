import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const PORTAL_URL = 'https://yz.chsi.com.cn/kyzx/zt/kyfs.shtml';
const USER_AGENT = 'cs-kaoyan-ai/1.0 official-evidence-collector';

export function parseSelfScoringLinks(html, year) {
  const marker = 'var zhxList = [';
  const start = html.indexOf(marker);
  if (start < 0) throw new Error('CHSI self-scoring list was not found');
  const section = html.slice(start);
  const schools = [...section.matchAll(/yxmc:\s*'([^']+)'/g)];
  return schools.map((match, index) => {
    const blockStart = match.index ?? 0;
    const blockEnd = index + 1 < schools.length ? schools[index + 1].index : section.indexOf('];', blockStart);
    const block = section.slice(blockStart, blockEnd < 0 ? undefined : blockEnd);
    const yearPattern = new RegExp(`year:\\s*'${year}'[\\s\\S]*?url:\\s*'([^']+)'`);
    const yearMatch = block.match(yearPattern);
    return yearMatch ? { schoolName: match[1].trim(), articleUrl: yearMatch[1] } : null;
  }).filter(Boolean);
}

export function parseArticle(html, articleUrl) {
  const title = decodeHtml(matchOne(html, /<title>([\s\S]*?)<\/title>/i));
  const publishedDate = normalizeDate(html.match(/(20\d{2})年(\d{1,2})月(\d{1,2})日/));
  const sourceName = decodeHtml(matchOne(html, /来源[：:]\s*<[^>]*>([\s\S]*?)<\/[^>]+>/i)
    || matchOne(html, /来源[：:]\s*([^<\s][^<]{0,50})/i));
  const detailHtml = matchOne(html, /<div class="detail"[^>]*>([\s\S]*?)<div id="dzz">/i)
    || matchOne(html, /<div class="detail"[^>]*>([\s\S]*?)<\/div>/i);
  if (!detailHtml) throw new Error(`article body was not found: ${articleUrl}`);
  const images = [...detailHtml.matchAll(/<img[^>]+>/gi)].map((match, index) => {
    const tag = match[0];
    const imageUrl = matchOne(tag, /(?:src|_src)=["']([^"']+)["']/i);
    const alt = decodeHtml(matchOne(tag, /alt=["']([^"']*)["']/i));
    return imageUrl && /^https:\/\/t\d\.chei\.com\.cn\/news\/img\//i.test(imageUrl)
      ? { index, imageUrl, alt }
      : null;
  }).filter(Boolean);
  return {
    title: cleanText(title),
    articleUrl,
    publishedDate,
    sourceName: cleanText(sourceName),
    bodyText: htmlToText(detailHtml),
    images,
  };
}

export function htmlToText(html) {
  return cleanText(decodeHtml(html
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<br\s*\/?\s*>/gi, '\n')
    .replace(/<\/p>/gi, '\n')
    .replace(/<[^>]+>/g, ' ')));
}

function matchOne(text, pattern) {
  return text.match(pattern)?.[1]?.trim() ?? '';
}

function cleanText(value) {
  return value.replace(/[ \t]+/g, ' ').replace(/\s*\n\s*/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
}

function decodeHtml(value) {
  return value
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/&#(\d+);/g, (_, code) => String.fromCodePoint(Number(code)));
}

function normalizeDate(match) {
  if (!match) return null;
  return `${match[1]}-${String(match[2]).padStart(2, '0')}-${String(match[3]).padStart(2, '0')}`;
}

async function fetchBytes(url, attempts = 3) {
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetch(url, { headers: { 'user-agent': USER_AGENT }, signal: AbortSignal.timeout(30_000) });
      if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
      return Buffer.from(await response.arrayBuffer());
    } catch (error) {
      lastError = error;
      if (attempt < attempts) await new Promise((resolve) => setTimeout(resolve, attempt * 1000));
    }
  }
  throw new Error(`failed to fetch ${url}: ${lastError?.message ?? lastError}`);
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function parseArgs(argv) {
  const options = { year: 2026, output: null, cacheDir: null, concurrency: 4 };
  for (const argument of argv) {
    const [name, value] = argument.split('=', 2);
    if (name === '--year') options.year = Number(value);
    else if (name === '--output') options.output = value;
    else if (name === '--cache-dir') options.cacheDir = value;
    else if (name === '--concurrency') options.concurrency = Math.max(1, Math.min(8, Number(value)));
    else if (name === '--help') options.help = true;
    else throw new Error(`unknown argument: ${argument}`);
  }
  return options;
}

async function mapLimit(items, limit, task) {
  const result = new Array(items.length);
  let cursor = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (cursor < items.length) {
      const index = cursor++;
      result[index] = await task(items[index], index);
    }
  });
  await Promise.all(workers);
  return result;
}

async function collect(options) {
  const scriptDir = path.dirname(fileURLToPath(import.meta.url));
  const repoRoot = path.resolve(scriptDir, '..', '..');
  const outputPath = path.resolve(repoRoot, options.output ?? `database/self-score-lines-${options.year}-sources.json`);
  const cacheDir = path.resolve(repoRoot, options.cacheDir ?? `backend/target/self-score-lines-${options.year}`);
  await mkdir(path.dirname(outputPath), { recursive: true });
  await mkdir(cacheDir, { recursive: true });

  const portalBytes = await fetchBytes(PORTAL_URL);
  const portalHtml = portalBytes.toString('utf8');
  const schools = parseSelfScoringLinks(portalHtml, options.year);
  if (schools.length !== 34) throw new Error(`expected 34 schools, received ${schools.length}`);

  const records = await mapLimit(schools, options.concurrency, async (school, schoolIndex) => {
    const articleBytes = await fetchBytes(school.articleUrl);
    const article = parseArticle(articleBytes.toString('utf8'), school.articleUrl);
    const images = await mapLimit(article.images, 2, async (image, imageIndex) => {
      const bytes = await fetchBytes(image.imageUrl);
      const extension = path.extname(new URL(image.imageUrl).pathname) || '.bin';
      const filename = `${String(schoolIndex + 1).padStart(2, '0')}-${String(imageIndex + 1).padStart(2, '0')}${extension}`;
      await writeFile(path.join(cacheDir, filename), bytes);
      return { ...image, filename, bytes: bytes.length, sha256: sha256(bytes) };
    });
    return {
      schoolName: school.schoolName,
      ...article,
      articleSha256: sha256(articleBytes),
      images,
    };
  });

  const payload = {
    schemaVersion: 1,
    year: options.year,
    publisher: '中国研究生招生信息网',
    portalUrl: PORTAL_URL,
    retrievedAt: new Date().toISOString(),
    stats: {
      schools: records.length,
      articles: records.length,
      images: records.reduce((sum, record) => sum + record.images.length, 0),
    },
    records,
  };
  payload.sha256 = sha256(Buffer.from(JSON.stringify(payload.records)));
  await writeFile(outputPath, `${JSON.stringify(payload, null, 2)}\n`, 'utf8');
  return { outputPath, cacheDir, payload };
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    console.log('Usage: node chsi-self-score-line-collector.mjs [--year=2026] [--output=path] [--cache-dir=path] [--concurrency=4]');
    return;
  }
  const { outputPath, cacheDir, payload } = await collect(options);
  console.log(JSON.stringify({ output: outputPath, cacheDir, ...payload.stats, sha256: payload.sha256 }, null, 2));
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    console.error(error.stack || error.message);
    process.exitCode = 1;
  });
}
