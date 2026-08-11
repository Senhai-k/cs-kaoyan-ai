import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const sourcePath = resolve(root, 'database', 'self-score-lines-2026-sources.json');
const outputPath = resolve(root, 'database', 'self-score-lines-2026-reviewed.json');

const detailed = (schoolName, totalScore, politicsScore, foreignLanguageScore, subjectOneScore, subjectTwoScore, extra = {}) => ({
  schoolName, totalScore, politicsScore, foreignLanguageScore, subjectOneScore, subjectTwoScore, ...extra
});
const generic = (schoolName, totalScore, score100, scoreOver100, extra = {}) => ({
  schoolName, totalScore, score100, scoreOver100, ...extra
});

const reviewedValues = [
  detailed('北京大学', 300, 55, 55, 90, 90),
  detailed('中国人民大学', 325, 45, 45, 85, 85),
  detailed('清华大学', 330, 50, 50, 80, 70, { province: '北京', city: '北京' }),
  generic('北京航空航天大学', 310, 40, 60),
  detailed('北京理工大学', 300, 40, 40, 60, 60),
  detailed('中国农业大学', 310, 50, 50, 80, 80),
  detailed('北京师范大学', 280, 40, 40, 60, 90),
  detailed('南开大学', 290, 50, 45, 70, 75),
  detailed('天津大学', 315, 50, 50, 80, 80),
  detailed('大连理工大学', 300, 45, 45, 70, 75),
  generic('东北大学', 315, 45, 75, { scopeNote: '工学[08]学术学位普通考生学校最低基本要求，不含工学照顾专业' }),
  detailed('吉林大学', 300, 45, 45, 68, 80),
  detailed('哈尔滨工业大学', 310, 50, 45, 70, 70),
  generic('复旦大学', 300, 50, 75),
  detailed('同济大学', 315, 50, 50, 75, 75),
  detailed('上海交通大学', 315, 50, 50, 75, 75),
  detailed('南京大学', 295, 50, 50, 75, 75, { scopeNote: '工学[08]学术学位普通考生学校最低基本要求，不含工学照顾专业' }),
  detailed('东南大学', 320, 50, 50, 70, 70, { scopeNote: '工学[08]学术学位普通考生学校最低基本要求，不含力学' }),
  detailed('浙江大学', 310, 50, 50, 75, 75),
  detailed('中国科学技术大学', 310, 50, 50, 80, 80),
  generic('厦门大学', 270, 40, 65, { categoryName: '其他工学', scopeNote: '工学[08]学术学位中“其他工学”普通考生学校最低基本要求' }),
  generic('山东大学', 310, 40, 60),
  {
    schoolName: '武汉大学',
    availabilityStatus: 'NOT_PUBLISHED',
    imageIndex: 3,
    scopeNote: '学校按培养单位和专业逐项公布；计算机学院及相关计算机专业在当前官方汇总表中尚未填写分数',
    remark: '保留缺失状态，禁止用其他学院分数或国家线代替。'
  },
  detailed('华中科技大学', 300, 50, 50, 70, 70),
  generic('湖南大学', 305, 50, 75),
  generic('中南大学', 305, 45, 75, { scopeNote: '工学[08]学术学位普通考生学校最低基本要求，不含工学照顾专业' }),
  generic('中山大学', 280, 45, 60),
  generic('华南理工大学', 305, 50, 70),
  generic('重庆大学', 310, 45, 70, { scopeNote: '工学[08]学术学位普通考生学校最低基本要求，不含工学照顾专业' }),
  generic('四川大学', 305, 50, 75, { scopeNote: '工学[08]学术学位普通考生学校最低基本要求，不含工学照顾专业' }),
  detailed('电子科技大学', 315, 50, 50, 75, 75),
  detailed('西安交通大学', 320, 50, 50, 80, 80),
  generic('西北工业大学', 310, 45, 70, { scopeNote: '工学[08]学术学位普通考生学校最低基本要求，不含工学照顾学科' }),
  generic('兰州大学', 275, 40, 65, { province: '甘肃', city: '兰州' })
];

const sources = JSON.parse(await readFile(sourcePath, 'utf8'));
const sourceBySchool = new Map(sources.records.map((record) => [record.schoolName, record]));
const records = reviewedValues.map((value) => {
  const source = sourceBySchool.get(value.schoolName);
  if (!source) throw new Error(`Missing official source for ${value.schoolName}`);
  const image = source.images[value.imageIndex ?? 0];
  if (!image) throw new Error(`Missing reviewed image for ${value.schoolName}`);
  return {
    schoolName: value.schoolName,
    province: value.province ?? null,
    city: value.city ?? null,
    schoolLevel: value.province ? '985/211/双一流' : null,
    is985: value.province ? true : null,
    is211: value.province ? true : null,
    isDoubleFirstClass: value.province ? true : null,
    title: source.title,
    articleUrl: source.articleUrl,
    publishedDate: source.publishedDate,
    articleSha256: source.articleSha256,
    imageUrl: image.imageUrl,
    imageSha256: image.sha256,
    categoryCode: '08',
    categoryName: value.categoryName ?? '工学',
    degreeType: '学硕',
    totalScore: value.totalScore ?? null,
    politicsScore: value.politicsScore ?? null,
    foreignLanguageScore: value.foreignLanguageScore ?? null,
    subjectOneScore: value.subjectOneScore ?? null,
    subjectTwoScore: value.subjectTwoScore ?? null,
    score100: value.score100 ?? null,
    scoreOver100: value.scoreOver100 ?? null,
    availabilityStatus: value.availabilityStatus ?? 'AVAILABLE',
    scopeNote: value.scopeNote ?? '工学[08]学术学位普通考生学校最低基本要求',
    remark: value.remark ?? '学校基本线不等于学院或具体专业实际复试线。'
  };
});

if (records.length !== 34 || new Set(records.map((record) => record.schoolName)).size !== 34) {
  throw new Error('Reviewed batch must contain exactly 34 self-scoring universities');
}
const available = records.filter((record) => record.availabilityStatus === 'AVAILABLE').length;
const batchSha256 = createHash('sha256').update(JSON.stringify(records)).digest('hex');
const output = {
  schemaVersion: 1,
  year: sources.year,
  publisher: sources.publisher,
  portalUrl: sources.portalUrl,
  retrievedAt: sources.retrievedAt,
  reviewedAt: '2026-07-24T04:00:00.000Z',
  sourceBatchSha256: sources.sha256,
  batchSha256,
  stats: { schools: 34, available, unavailable: records.length - available },
  records
};

await writeFile(outputPath, `${JSON.stringify(output, null, 2)}\n`, 'utf8');
console.log(JSON.stringify({ outputPath, batchSha256, stats: output.stats }));
