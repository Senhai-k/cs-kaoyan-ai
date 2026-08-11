-- Portable production baseline for the official focus-school evidence already verified in H2 tests.
-- Natural keys are used because catalog imports allocate different numeric IDs in each database.

INSERT INTO school (name, province, city, region, school_level, is_985, is_211, is_double_first_class, official_site, graduate_site, remark)
VALUES
  ('北京大学', '北京', '北京', '华北', '985/211/双一流', 1, 1, 1, 'https://www.pku.edu.cn', 'https://grs.pku.edu.cn', '基础信息来自学校官网，招生字段以当年官方目录为准。'),
  ('南京大学', '江苏', '南京', '华东', '985/211/双一流', 1, 1, 1, 'https://www.nju.edu.cn', 'https://yzb.nju.edu.cn', '官方研招入口已于 2026-07-10 在线核验。'),
  ('杭州电子科技大学', '浙江', '杭州', '华东', '普通院校', 0, 0, 0, 'https://www.hdu.edu.cn', 'https://grs.hdu.edu.cn', '基础信息来自学校官网，招生字段以当年官方目录为准。'),
  ('浙江大学', '浙江', '杭州', '华东', '985/211/双一流', 1, 1, 1, 'https://www.zju.edu.cn', 'https://grs.zju.edu.cn', '基础信息来自学校官网，招生字段以当年官方目录为准。'),
  ('上海交通大学', '上海', '上海', '华东', '985/211/双一流', 1, 1, 1, 'https://www.sjtu.edu.cn', 'https://yzb.sjtu.edu.cn', '官方研招入口已于 2026-07-10 在线核验。'),
  ('华中科技大学', '湖北', '武汉', '华中', '985/211/双一流', 1, 1, 1, 'https://www.hust.edu.cn', 'https://gszs.hust.edu.cn', '2026 年复试录取工作规定已于 2026-07-10 在线核验。'),
  ('电子科技大学', '四川', '成都', '西南', '985/211/双一流', 1, 1, 1, 'https://www.uestc.edu.cn', 'https://yz.uestc.edu.cn', '基础信息来自学校官网，招生字段以当年官方目录为准。'),
  ('北京邮电大学', '北京', '北京', '华北', '211/双一流', 0, 1, 1, 'https://www.bupt.edu.cn', 'https://yzb.bupt.edu.cn', '官方入口存在访问校验，具体公告需在浏览器中核验。'),
  ('西安电子科技大学', '陕西', '西安', '西北', '211/双一流', 0, 1, 1, 'https://www.xidian.edu.cn', 'https://gr.xidian.edu.cn', '2026 年招生简章与专业目录页面已于 2026-07-10 在线核验。'),
  ('深圳大学', '广东', '深圳', '华南', '普通院校', 0, 0, 0, 'https://www.szu.edu.cn', 'https://yz.szu.edu.cn', '基础信息来自学校官网，招生字段以当年官方目录为准。')
ON DUPLICATE KEY UPDATE
  province = VALUES(province), city = VALUES(city), region = VALUES(region), school_level = VALUES(school_level),
  is_985 = VALUES(is_985), is_211 = VALUES(is_211), is_double_first_class = VALUES(is_double_first_class),
  official_site = VALUES(official_site), graduate_site = VALUES(graduate_site), remark = VALUES(remark);

INSERT INTO college (school_id, name, official_site, remark)
SELECT id, '计算机学院', 'https://cs.pku.edu.cn', '学院名称和官网入口。' FROM school s WHERE s.name = '北京大学'
  AND NOT EXISTS (SELECT 1 FROM college c WHERE c.school_id = s.id AND c.name = '计算机学院');
INSERT INTO college (school_id, name, official_site, remark)
SELECT id, '计算机学院', 'https://cs.nju.edu.cn', '学院名称来自南京大学2026年官方硕士招生目录（院系代码033）。' FROM school s WHERE s.name = '南京大学'
  AND NOT EXISTS (SELECT 1 FROM college c WHERE c.school_id = s.id AND c.name = '计算机学院');
INSERT INTO college (school_id, name, official_site, remark)
SELECT id, '计算机学院', 'https://computer.hdu.edu.cn', '学院名称和官网入口。' FROM school s WHERE s.name = '杭州电子科技大学'
  AND NOT EXISTS (SELECT 1 FROM college c WHERE c.school_id = s.id AND c.name = '计算机学院');
INSERT INTO college (school_id, name, official_site, remark)
SELECT id, '计算机科学与技术学院', 'http://www.cs.zju.edu.cn', '学院名称和官网入口。' FROM school s WHERE s.name = '浙江大学'
  AND NOT EXISTS (SELECT 1 FROM college c WHERE c.school_id = s.id AND c.name = '计算机科学与技术学院');
INSERT INTO college (school_id, name, official_site, remark)
SELECT id, '计算机学院', NULL, '学院名称来自上海交通大学2026年官方硕士招生专业目录（院系代码033）。' FROM school s WHERE s.name = '上海交通大学'
  AND NOT EXISTS (SELECT 1 FROM college c WHERE c.school_id = s.id AND c.name = '计算机学院');
INSERT INTO college (school_id, name, official_site, remark)
SELECT id, '计算机科学与技术学院', 'https://cs.hust.edu.cn', '学院名称和官网入口。' FROM school s WHERE s.name = '华中科技大学'
  AND NOT EXISTS (SELECT 1 FROM college c WHERE c.school_id = s.id AND c.name = '计算机科学与技术学院');
INSERT INTO college (school_id, name, official_site, remark)
SELECT id, '计算机科学与工程学院', 'https://www.scse.uestc.edu.cn', '学院名称和官网入口。' FROM school s WHERE s.name = '电子科技大学'
  AND NOT EXISTS (SELECT 1 FROM college c WHERE c.school_id = s.id AND c.name = '计算机科学与工程学院');
INSERT INTO college (school_id, name, official_site, remark)
SELECT id, '计算机学院', 'https://scs.bupt.edu.cn', '学院名称和官网入口。' FROM school s WHERE s.name = '北京邮电大学'
  AND NOT EXISTS (SELECT 1 FROM college c WHERE c.school_id = s.id AND c.name = '计算机学院');
INSERT INTO college (school_id, name, official_site, remark)
SELECT id, '计算机科学与技术学院', 'https://cs.xidian.edu.cn', '学院名称和官网入口。' FROM school s WHERE s.name = '西安电子科技大学'
  AND NOT EXISTS (SELECT 1 FROM college c WHERE c.school_id = s.id AND c.name = '计算机科学与技术学院');
INSERT INTO college (school_id, name, official_site, remark)
SELECT id, '计算机与软件学院', 'https://csse.szu.edu.cn', '学院名称和官网入口。' FROM school s WHERE s.name = '深圳大学'
  AND NOT EXISTS (SELECT 1 FROM college c WHERE c.school_id = s.id AND c.name = '计算机与软件学院');

INSERT INTO major (school_id, college_id, name, major_code, degree_type, research_direction, study_mode, remark)
SELECT s.id, c.id, '计算机科学与技术', '081200', '学硕', NULL, '全日制', '专业方向和招生状态以当年专业目录为准。'
FROM school s JOIN college c ON c.school_id = s.id AND c.name = '计算机学院' WHERE s.name = '北京大学'
  AND NOT EXISTS (SELECT 1 FROM major m WHERE m.school_id = s.id AND m.college_id = c.id AND m.major_code = '081200' AND m.study_mode = '全日制');
INSERT INTO major (school_id, college_id, name, major_code, degree_type, research_direction, study_mode, remark)
SELECT s.id, c.id, '计算机科学与技术', '081200', '学硕', '新型程序设计与软件方法学；机器学习与智能化信息处理；分布计算与系统安全技术；媒体计算与内容处理技术；理论计算机科学', '全日制', '专业、方向和拟招人数来自南京大学2026年官方硕士招生目录。'
FROM school s JOIN college c ON c.school_id = s.id AND c.name = '计算机学院' WHERE s.name = '南京大学'
  AND NOT EXISTS (SELECT 1 FROM major m WHERE m.school_id = s.id AND m.college_id = c.id AND m.major_code = '081200' AND m.study_mode = '全日制');
INSERT INTO major (school_id, college_id, name, major_code, degree_type, research_direction, study_mode, remark)
SELECT s.id, c.id, '计算机科学与技术', '081200', '学硕', NULL, '全日制', '专业方向和招生状态以当年专业目录为准。'
FROM school s JOIN college c ON c.school_id = s.id AND c.name = '计算机学院' WHERE s.name = '杭州电子科技大学'
  AND NOT EXISTS (SELECT 1 FROM major m WHERE m.school_id = s.id AND m.college_id = c.id AND m.major_code = '081200' AND m.study_mode = '全日制');
INSERT INTO major (school_id, college_id, name, major_code, degree_type, research_direction, study_mode, remark)
SELECT s.id, c.id, '计算机科学与技术', '081200', '学硕', NULL, '全日制', '专业方向和招生状态以当年专业目录为准。'
FROM school s JOIN college c ON c.school_id = s.id AND c.name = '计算机科学与技术学院' WHERE s.name = '浙江大学'
  AND NOT EXISTS (SELECT 1 FROM major m WHERE m.school_id = s.id AND m.college_id = c.id AND m.major_code = '081200' AND m.study_mode = '全日制');
INSERT INTO major (school_id, college_id, name, major_code, degree_type, research_direction, study_mode, remark)
SELECT s.id, c.id, '计算机科学与技术', '081200', '学硕', '计算机科学与技术方向；软件工程方向', '全日制', '专业与方向来自上海交通大学2026年官方硕士招生专业目录。'
FROM school s JOIN college c ON c.school_id = s.id AND c.name = '计算机学院' WHERE s.name = '上海交通大学'
  AND NOT EXISTS (SELECT 1 FROM major m WHERE m.school_id = s.id AND m.college_id = c.id AND m.major_code = '081200' AND m.study_mode = '全日制');
INSERT INTO major (school_id, college_id, name, major_code, degree_type, research_direction, study_mode, remark)
SELECT s.id, c.id, '计算机科学与技术', '081200', '学硕', NULL, '全日制', '专业方向和招生状态以当年专业目录为准。'
FROM school s JOIN college c ON c.school_id = s.id AND c.name = '计算机科学与技术学院' WHERE s.name = '华中科技大学'
  AND NOT EXISTS (SELECT 1 FROM major m WHERE m.school_id = s.id AND m.college_id = c.id AND m.major_code = '081200' AND m.study_mode = '全日制');
INSERT INTO major (school_id, college_id, name, major_code, degree_type, research_direction, study_mode, remark)
SELECT s.id, c.id, '计算机科学与技术', '081200', '学硕', NULL, '全日制', '专业方向和招生状态以当年专业目录为准。'
FROM school s JOIN college c ON c.school_id = s.id AND c.name = '计算机科学与工程学院' WHERE s.name = '电子科技大学'
  AND NOT EXISTS (SELECT 1 FROM major m WHERE m.school_id = s.id AND m.college_id = c.id AND m.major_code = '081200' AND m.study_mode = '全日制');
INSERT INTO major (school_id, college_id, name, major_code, degree_type, research_direction, study_mode, remark)
SELECT s.id, c.id, '计算机科学与技术', '081200', '学硕', NULL, '全日制', '专业方向和招生状态以当年专业目录为准。'
FROM school s JOIN college c ON c.school_id = s.id AND c.name = '计算机学院' WHERE s.name = '北京邮电大学'
  AND NOT EXISTS (SELECT 1 FROM major m WHERE m.school_id = s.id AND m.college_id = c.id AND m.major_code = '081200' AND m.study_mode = '全日制');
INSERT INTO major (school_id, college_id, name, major_code, degree_type, research_direction, study_mode, remark)
SELECT s.id, c.id, '计算机科学与技术', '081200', '学硕', NULL, '全日制', '专业方向和招生状态以当年专业目录为准。'
FROM school s JOIN college c ON c.school_id = s.id AND c.name = '计算机科学与技术学院' WHERE s.name = '西安电子科技大学'
  AND NOT EXISTS (SELECT 1 FROM major m WHERE m.school_id = s.id AND m.college_id = c.id AND m.major_code = '081200' AND m.study_mode = '全日制');
INSERT INTO major (school_id, college_id, name, major_code, degree_type, research_direction, study_mode, remark)
SELECT s.id, c.id, '计算机科学与技术', '081200', '学硕', NULL, '全日制', '专业方向和招生状态以当年专业目录为准。'
FROM school s JOIN college c ON c.school_id = s.id AND c.name = '计算机与软件学院' WHERE s.name = '深圳大学'
  AND NOT EXISTS (SELECT 1 FROM major m WHERE m.school_id = s.id AND m.college_id = c.id AND m.major_code = '081200' AND m.study_mode = '全日制');

-- Permanent official entry points for the ten focus schools.
INSERT INTO document_source (title, source_type, source_url, school_id, college_id, year, is_official, audit_status, remark)
SELECT CONCAT(s.name, '研究生招生官方入口'), '官方研招入口', s.graduate_site, s.id, c.id, NULL, 1, 'PUBLISHED', '常设官方入口。'
FROM school s JOIN college c ON c.school_id = s.id
WHERE s.name IN ('北京大学','南京大学','杭州电子科技大学','浙江大学','上海交通大学','华中科技大学','电子科技大学','北京邮电大学','西安电子科技大学','深圳大学')
  AND c.id = (SELECT MIN(c2.id) FROM college c2 WHERE c2.school_id = s.id)
  AND NOT EXISTS (SELECT 1 FROM document_source ds WHERE ds.school_id = s.id AND ds.source_type = '官方研招入口' AND ds.source_url = s.graduate_site);

INSERT INTO document_source (title, source_type, source_url, publish_date, school_id, college_id, year, is_official, audit_status, remark)
SELECT '南京大学2026年硕士招生学院复试细则及复试名单汇总', '复试细则汇总', 'https://yzb.nju.edu.cn/9e/88/c47863a827016/page.htm', '2026-03-19', s.id, c.id, 2026, 1, 'PUBLISHED', '2026-07-10 在线核验；院系细则以汇总页最新链接为准。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机学院' WHERE s.name='南京大学'
  AND NOT EXISTS (SELECT 1 FROM document_source WHERE source_url='https://yzb.nju.edu.cn/9e/88/c47863a827016/page.htm');
INSERT INTO document_source (title, source_type, source_url, school_id, college_id, year, is_official, audit_status, remark)
SELECT '上海交通大学2026年硕士招生官方通知入口', '硕士招生通知', 'https://yzb.sjtu.edu.cn/', s.id, c.id, 2026, 1, 'PUBLISHED', '2026-07-10 在线核验，首页可见 2026 硕士招生通知。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机学院' WHERE s.name='上海交通大学'
  AND NOT EXISTS (SELECT 1 FROM document_source WHERE title='上海交通大学2026年硕士招生官方通知入口');
INSERT INTO document_source (title, source_type, source_url, publish_date, school_id, college_id, year, is_official, audit_status, remark)
SELECT '华中科技大学2026年硕士研究生招生复试录取工作规定', '复试录取规定', 'https://gszs.hust.edu.cn/info/1089/4074.htm', '2026-03-18', s.id, c.id, 2026, 1, 'PUBLISHED', '2026-07-10 在线核验正文。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机科学与技术学院' WHERE s.name='华中科技大学'
  AND NOT EXISTS (SELECT 1 FROM document_source WHERE source_url='https://gszs.hust.edu.cn/info/1089/4074.htm');
INSERT INTO document_source (title, source_type, source_url, publish_date, school_id, college_id, year, is_official, audit_status, remark)
SELECT '西安电子科技大学2026年硕士研究生招生简章及招生专业目录', '招生简章与专业目录', 'https://gr.xidian.edu.cn/info/1072/17294.htm', '2025-09-30', s.id, c.id, 2026, 1, 'PUBLISHED', '2026-07-10 在线核验页面及附件入口。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机科学与技术学院' WHERE s.name='西安电子科技大学'
  AND NOT EXISTS (SELECT 1 FROM document_source WHERE source_url='https://gr.xidian.edu.cn/info/1072/17294.htm');
INSERT INTO document_source (title, source_type, source_url, publish_date, school_id, college_id, year, is_official, audit_status, remark)
SELECT '上海交通大学2026年硕士研究生招生专业目录', '招生专业目录', 'https://yzb.sjtu.edu.cn/post/3309', '2025-09-30', s.id, c.id, 2026, 1, 'PUBLISHED', '2026-07-15在线核验官方正文；学院总计划不等同于081200专业计划。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机学院' WHERE s.name='上海交通大学'
  AND NOT EXISTS (SELECT 1 FROM document_source WHERE source_url='https://yzb.sjtu.edu.cn/post/3309');
INSERT INTO document_source (title, source_type, source_url, publish_date, school_id, college_id, year, is_official, audit_status, remark)
SELECT '南京大学2026年硕士研究生招生目录', '招生专业目录', 'https://yzb.nju.edu.cn/19/b1/c47862a793009/page.htm', '2025-10-09', s.id, c.id, 2026, 1, 'PUBLISHED', '2026-07-15在线核验页面及官方PDF文本层；081200拟招66人，最终人数可能调整。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机学院' WHERE s.name='南京大学'
  AND NOT EXISTS (SELECT 1 FROM document_source WHERE source_url='https://yzb.nju.edu.cn/19/b1/c47862a793009/page.htm');
INSERT INTO document_source (title, source_type, source_url, publish_date, school_id, college_id, year, is_official, audit_status, remark)
SELECT '浙江大学计算机科学与技术学院2026年硕士研究生招生考试复试录取方案', '复试录取方案', 'http://www.cs.zju.edu.cn/csen/2026/0318/c27010a3141869/page.htm', '2026-03-18', s.id, c.id, 2026, 1, 'PUBLISHED', '2026-07-15在线核验学院官方正文；计划9人为统考复试阶段口径，不等同全年目录总计划。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机科学与技术学院' WHERE s.name='浙江大学'
  AND NOT EXISTS (SELECT 1 FROM document_source WHERE source_url='http://www.cs.zju.edu.cn/csen/2026/0318/c27010a3141869/page.htm');

-- Published evidence documents. These texts preserve the source scope and explicitly reject unsupported inference.
INSERT INTO source_document (title, document_type, source_url, school_id, college_id, major_id, year, audit_status, source_reliability, raw_text, remark)
SELECT '南京大学2026年硕士招生官方入口核验', '官方招生快照', 'https://yzb.nju.edu.cn/main.htm', s.id, c.id, NULL, 2026, 'PUBLISHED', 'OFFICIAL', '南京大学研究生招生网于 2026-07-10 在线核验可访问。硕士最新通知栏目可见复试细则、拟录取调档和历年报考录取统计入口。具体专业数据需打开官方页面核验。', '只记录页面可直接核验的事实，不推断具体专业分数。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机学院' WHERE s.name='南京大学'
  AND NOT EXISTS (SELECT 1 FROM source_document WHERE title='南京大学2026年硕士招生官方入口核验');
INSERT INTO source_document (title, document_type, source_url, school_id, college_id, major_id, year, audit_status, source_reliability, raw_text, remark)
SELECT '上海交通大学2026年硕士招生官方入口核验', '官方招生快照', 'https://yzb.sjtu.edu.cn/', s.id, c.id, NULL, 2026, 'PUBLISHED', 'OFFICIAL', '上海交通大学研究生招生网于 2026-07-10 在线核验可访问。网站提供招生简章、复试信息、录取信息和历史数据入口，具体专业数据以栏目内公告为准。', '只记录页面可直接核验的事实。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机学院' WHERE s.name='上海交通大学'
  AND NOT EXISTS (SELECT 1 FROM source_document WHERE title='上海交通大学2026年硕士招生官方入口核验');
INSERT INTO source_document (title, document_type, source_url, school_id, college_id, major_id, year, audit_status, source_reliability, raw_text, remark)
SELECT '华中科技大学2026年硕士研究生复试录取工作规定核验', '复试录取规定', 'https://gszs.hust.edu.cn/info/1089/4074.htm', s.id, c.id, m.id, 2026, 'PUBLISHED', 'OFFICIAL', '华中科技大学2026年硕士复试规定：差额比例一般不低于120%，采用线下现场复试，录取总成绩按初试60%、复试40%折算，复试成绩低于60分者不予录取；具体要求以院系细则为准。', '学校级规则，院系可能有补充要求。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机科学与技术学院' JOIN major m ON m.college_id=c.id AND m.major_code='081200' WHERE s.name='华中科技大学'
  AND NOT EXISTS (SELECT 1 FROM source_document WHERE title='华中科技大学2026年硕士研究生复试录取工作规定核验');
INSERT INTO source_document (title, document_type, source_url, school_id, college_id, major_id, year, audit_status, source_reliability, raw_text, remark)
SELECT '西安电子科技大学2026年硕士招生简章与专业目录核验', '招生简章与专业目录', 'https://gr.xidian.edu.cn/info/1072/17294.htm', s.id, c.id, m.id, 2026, 'PUBLISHED', 'OFFICIAL', '西安电子科技大学2026年官方专业目录：计算机科学与技术学院081200专业初试科目为101思想政治理论、201英语一、301数学一、408计算机学科专业基础，目录拟招生70人。该人数不含推免、仅供参考，实际人数将动态调整。', '结构化字段来自官方 PDF 文本层，保留目录中的计划口径限制。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机科学与技术学院' JOIN major m ON m.college_id=c.id AND m.major_code='081200' WHERE s.name='西安电子科技大学'
  AND NOT EXISTS (SELECT 1 FROM source_document WHERE title='西安电子科技大学2026年硕士招生简章与专业目录核验');
INSERT INTO source_document (title, document_type, source_url, school_id, college_id, major_id, year, audit_status, source_reliability, raw_text, remark)
SELECT '北京邮电大学研招网2027年初试科目调整通知入口核验', '考试科目调整入口', 'https://yzb.bupt.edu.cn/', s.id, c.id, m.id, 2027, 'PUBLISHED', 'OFFICIAL', '北京邮电大学研究生招生网可见部分学院调整2027年硕士初试科目的通知入口。当前快照未取得计算机科学与技术专业的通知正文和具体科目，不能据此判断是否采用408。', '官方入口快照；具体调整内容需通过通知正文再次核验。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机学院' JOIN major m ON m.college_id=c.id AND m.major_code='081200' WHERE s.name='北京邮电大学'
  AND NOT EXISTS (SELECT 1 FROM source_document WHERE title='北京邮电大学研招网2027年初试科目调整通知入口核验');
INSERT INTO source_document (title, document_type, source_url, school_id, college_id, major_id, year, audit_status, source_reliability, raw_text, remark)
SELECT '上海交通大学2026年硕士研究生招生专业目录核验', '招生专业目录', 'https://yzb.sjtu.edu.cn/post/3309', s.id, c.id, m.id, 2026, 'PUBLISHED', 'OFFICIAL', '上海交通大学2026年官方目录：033计算机学院总计划覆盖多个专业；081200计算机科学与技术为全日制学硕，初试科目为101思想政治理论、201英语一、301数学一、408计算机学科专业基础。学院总计划不能推断为081200专业计划。', '2026-07-15在线核验官方正文；学院总计划不写入专业级招生计划。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机学院' JOIN major m ON m.college_id=c.id AND m.major_code='081200' WHERE s.name='上海交通大学'
  AND NOT EXISTS (SELECT 1 FROM source_document WHERE title='上海交通大学2026年硕士研究生招生专业目录核验');
INSERT INTO source_document (title, document_type, source_url, school_id, college_id, major_id, year, audit_status, source_reliability, raw_text, remark)
SELECT '南京大学2026年硕士研究生招生目录核验', '招生专业目录', 'https://yzb.nju.edu.cn/19/b1/c47862a793009/page.htm', s.id, c.id, m.id, 2026, 'PUBLISHED', 'OFFICIAL', '南京大学2026年官方目录：033计算机学院081200计算机科学与技术为全日制学硕，专业拟招66人，初试科目为101思想政治理论、201英语一、301数学一、408计算机学科专业基础。推免人数另行公布，最终招生人数可能调整。', '2026-07-15解析官方PDF文本层；统考和推免拆分保持为空。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机学院' JOIN major m ON m.college_id=c.id AND m.major_code='081200' WHERE s.name='南京大学'
  AND NOT EXISTS (SELECT 1 FROM source_document WHERE title='南京大学2026年硕士研究生招生目录核验');
INSERT INTO source_document (title, document_type, source_url, school_id, college_id, major_id, year, audit_status, source_reliability, raw_text, remark)
SELECT '浙江大学计算机科学与技术学院2026年硕士复试录取方案核验', '复试录取方案', 'http://www.cs.zju.edu.cn/csen/2026/0318/c27010a3141869/page.htm', s.id, c.id, m.id, 2026, 'PUBLISHED', 'OFFICIAL', '浙江大学计算机学院2026年复试方案：081200复试线总分382，单科50、50、75、75，按1:1.3差额复试，统考复试阶段计划9人。综合成绩中初试占65%、复试占35%；9人不是全年目录总计划。', '2026-07-15在线核验学院官方正文；只写入统考复试阶段口径。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机科学与技术学院' JOIN major m ON m.college_id=c.id AND m.major_code='081200' WHERE s.name='浙江大学'
  AND NOT EXISTS (SELECT 1 FROM source_document WHERE title='浙江大学计算机科学与技术学院2026年硕士复试录取方案核验');

INSERT INTO document_chunk (document_id, school_id, college_id, major_id, year, document_type, chunk_index, content, audit_status)
SELECT d.id, d.school_id, d.college_id, d.major_id, d.year, d.document_type, 0, d.raw_text, 'PUBLISHED'
FROM source_document d
WHERE d.title IN (
  '南京大学2026年硕士招生官方入口核验','上海交通大学2026年硕士招生官方入口核验','华中科技大学2026年硕士研究生复试录取工作规定核验',
  '西安电子科技大学2026年硕士招生简章与专业目录核验','北京邮电大学研招网2027年初试科目调整通知入口核验',
  '上海交通大学2026年硕士研究生招生专业目录核验','南京大学2026年硕士研究生招生目录核验','浙江大学计算机科学与技术学院2026年硕士复试录取方案核验'
) AND NOT EXISTS (SELECT 1 FROM document_chunk dc WHERE dc.document_id=d.id AND dc.chunk_index=0);

INSERT INTO admission_plan (school_id, college_id, major_id, year, total_quota, recommended_quota, unified_quota, has_adjustment, source_id, remark)
SELECT s.id,c.id,m.id,2026,70,NULL,70,0,ds.id,'官方专业目录拟招生人数，不含推免；根据 2025 年人数拟定，仅供参考，实际计划动态调整。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机科学与技术学院' JOIN major m ON m.college_id=c.id AND m.major_code='081200'
JOIN document_source ds ON ds.source_url='https://gr.xidian.edu.cn/info/1072/17294.htm' WHERE s.name='西安电子科技大学'
  AND NOT EXISTS (SELECT 1 FROM admission_plan ap WHERE ap.major_id=m.id AND ap.year=2026);
INSERT INTO admission_plan (school_id, college_id, major_id, year, total_quota, recommended_quota, unified_quota, has_adjustment, source_id, remark)
SELECT s.id,c.id,m.id,2026,66,NULL,NULL,NULL,ds.id,'官方目录081200专业拟招人数；推免人数另行公布，统考名额未知，最终人数可能调整。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机学院' JOIN major m ON m.college_id=c.id AND m.major_code='081200'
JOIN document_source ds ON ds.source_url='https://yzb.nju.edu.cn/19/b1/c47862a793009/page.htm' WHERE s.name='南京大学'
  AND NOT EXISTS (SELECT 1 FROM admission_plan ap WHERE ap.major_id=m.id AND ap.year=2026);
INSERT INTO admission_plan (school_id, college_id, major_id, year, total_quota, recommended_quota, unified_quota, has_adjustment, source_id, remark)
SELECT s.id,c.id,m.id,2026,NULL,NULL,9,NULL,ds.id,'学院复试录取方案中的081200统考复试阶段招生人数；不等同全年目录总计划。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机科学与技术学院' JOIN major m ON m.college_id=c.id AND m.major_code='081200'
JOIN document_source ds ON ds.source_url='http://www.cs.zju.edu.cn/csen/2026/0318/c27010a3141869/page.htm' WHERE s.name='浙江大学'
  AND NOT EXISTS (SELECT 1 FROM admission_plan ap WHERE ap.major_id=m.id AND ap.year=2026);

INSERT INTO exam_subject (school_id, college_id, major_id, year, politics, foreign_language, math_subject, professional_subject, is_408, source_id)
SELECT s.id,c.id,m.id,2026,'101 思想政治理论','201 英语（一）','301 数学（一）','408 计算机学科专业基础',1,ds.id
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机科学与技术学院' JOIN major m ON m.college_id=c.id AND m.major_code='081200'
JOIN document_source ds ON ds.source_url='https://gr.xidian.edu.cn/info/1072/17294.htm' WHERE s.name='西安电子科技大学'
  AND NOT EXISTS (SELECT 1 FROM exam_subject es WHERE es.major_id=m.id AND es.year=2026 AND es.professional_subject LIKE '408%');
INSERT INTO exam_subject (school_id, college_id, major_id, year, politics, foreign_language, math_subject, professional_subject, is_408, source_id)
SELECT s.id,c.id,m.id,2026,'101 思想政治理论','201 英语（一）','301 数学（一）','408 计算机学科专业基础',1,ds.id
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机学院' JOIN major m ON m.college_id=c.id AND m.major_code='081200'
JOIN document_source ds ON ds.source_url='https://yzb.sjtu.edu.cn/post/3309' WHERE s.name='上海交通大学'
  AND NOT EXISTS (SELECT 1 FROM exam_subject es WHERE es.major_id=m.id AND es.year=2026 AND es.professional_subject LIKE '408%');
INSERT INTO exam_subject (school_id, college_id, major_id, year, politics, foreign_language, math_subject, professional_subject, is_408, source_id)
SELECT s.id,c.id,m.id,2026,'101 思想政治理论','201 英语（一）','301 数学（一）','408 计算机学科专业基础',1,ds.id
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机学院' JOIN major m ON m.college_id=c.id AND m.major_code='081200'
JOIN document_source ds ON ds.source_url='https://yzb.nju.edu.cn/19/b1/c47862a793009/page.htm' WHERE s.name='南京大学'
  AND NOT EXISTS (SELECT 1 FROM exam_subject es WHERE es.major_id=m.id AND es.year=2026 AND es.professional_subject LIKE '408%');

INSERT INTO score_line (school_id, college_id, major_id, year, total_score, politics_score, foreign_language_score, math_score, professional_score, source_id, remark)
SELECT s.id,c.id,m.id,2026,382,50,50,75,75,ds.id,'计算机学院081200复试线；专项计划执行学校基本要求。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机科学与技术学院' JOIN major m ON m.college_id=c.id AND m.major_code='081200'
JOIN document_source ds ON ds.source_url='http://www.cs.zju.edu.cn/csen/2026/0318/c27010a3141869/page.htm' WHERE s.name='浙江大学'
  AND NOT EXISTS (SELECT 1 FROM score_line sl WHERE sl.major_id=m.id AND sl.year=2026);

INSERT INTO retest_rule (school_id, college_id, major_id, year, retest_time, retest_method, retest_ratio, initial_score_weight, retest_score_weight, qualification_line, materials, source_id, remark)
SELECT s.id,c.id,m.id,2026,'2026-03-21 至 2026-03-22','线下现场复试；具体形式由院系细则确定',1.20,60,40,'复试成绩低于 60 分者不予录取；思想品德考核不合格者不予录取。','资格审核材料和院系补充材料以学校研招网及院系复试细则为准。',ds.id,'学校级官方规定；1.20 表示一般不低于 120%。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机科学与技术学院' JOIN major m ON m.college_id=c.id AND m.major_code='081200'
JOIN document_source ds ON ds.source_url='https://gszs.hust.edu.cn/info/1089/4074.htm' WHERE s.name='华中科技大学'
  AND NOT EXISTS (SELECT 1 FROM retest_rule rr WHERE rr.major_id=m.id AND rr.year=2026);
INSERT INTO retest_rule (school_id, college_id, major_id, year, retest_time, retest_method, retest_ratio, initial_score_weight, retest_score_weight, qualification_line, materials, source_id, remark)
SELECT s.id,c.id,m.id,2026,'3月21日资格审查；3月22日上机考试；3月23日面试','现场复试；计算机类采用上机考试加面试',1.30,65,35,'复试成绩=机考35%+面试65%，低于60分不合格；思想品德不合格者不予录取。','身份证、准考证、学籍或学历证明、成绩单及科研成果等。',ds.id,'综合成绩中初试占65%、复试占35%；1.30表示按1:1.3确定复试线。'
FROM school s JOIN college c ON c.school_id=s.id AND c.name='计算机科学与技术学院' JOIN major m ON m.college_id=c.id AND m.major_code='081200'
JOIN document_source ds ON ds.source_url='http://www.cs.zju.edu.cn/csen/2026/0318/c27010a3141869/page.htm' WHERE s.name='浙江大学'
  AND NOT EXISTS (SELECT 1 FROM retest_rule rr WHERE rr.major_id=m.id AND rr.year=2026);
