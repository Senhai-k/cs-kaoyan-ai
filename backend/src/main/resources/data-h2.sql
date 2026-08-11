MERGE INTO school (id, name, province, city, region, school_level, is_985, is_211, is_double_first_class, official_site, graduate_site, remark) KEY(id)
VALUES
  (1, '北京大学', '北京', '北京', '华北', '985/211/双一流', 1, 1, 1, 'https://www.pku.edu.cn', 'https://grs.pku.edu.cn', '基础信息来自学校官网，招生字段以当年官方目录为准。'),
  (2, '南京大学', '江苏', '南京', '华东', '985/211/双一流', 1, 1, 1, 'https://www.nju.edu.cn', 'https://yzb.nju.edu.cn', '官方研招入口已于 2026-07-10 在线核验。'),
  (3, '杭州电子科技大学', '浙江', '杭州', '华东', '普通院校', 0, 0, 0, 'https://www.hdu.edu.cn', 'https://grs.hdu.edu.cn', '基础信息来自学校官网，招生字段以当年官方目录为准。'),
  (4, '浙江大学', '浙江', '杭州', '华东', '985/211/双一流', 1, 1, 1, 'https://www.zju.edu.cn', 'https://grs.zju.edu.cn', '基础信息来自学校官网，招生字段以当年官方目录为准。'),
  (5, '上海交通大学', '上海', '上海', '华东', '985/211/双一流', 1, 1, 1, 'https://www.sjtu.edu.cn', 'https://yzb.sjtu.edu.cn', '官方研招入口已于 2026-07-10 在线核验。'),
  (6, '华中科技大学', '湖北', '武汉', '华中', '985/211/双一流', 1, 1, 1, 'https://www.hust.edu.cn', 'https://gszs.hust.edu.cn', '2026 年复试录取工作规定已于 2026-07-10 在线核验。'),
  (7, '电子科技大学', '四川', '成都', '西南', '985/211/双一流', 1, 1, 1, 'https://www.uestc.edu.cn', 'https://yz.uestc.edu.cn', '基础信息来自学校官网，招生字段以当年官方目录为准。'),
  (8, '北京邮电大学', '北京', '北京', '华北', '211/双一流', 0, 1, 1, 'https://www.bupt.edu.cn', 'https://yzb.bupt.edu.cn', '官方入口存在访问校验，具体公告需在浏览器中核验。'),
  (9, '西安电子科技大学', '陕西', '西安', '西北', '211/双一流', 0, 1, 1, 'https://www.xidian.edu.cn', 'https://gr.xidian.edu.cn', '2026 年招生简章与专业目录页面已于 2026-07-10 在线核验。'),
  (10, '深圳大学', '广东', '深圳', '华南', '普通院校', 0, 0, 0, 'https://www.szu.edu.cn', 'https://yz.szu.edu.cn', '基础信息来自学校官网，招生字段以当年官方目录为准。');

MERGE INTO college (id, school_id, name, official_site, remark) KEY(id)
VALUES
  (1, 1, '计算机学院', 'https://cs.pku.edu.cn', '学院名称和官网入口。'),
  (2, 2, '计算机学院', 'https://cs.nju.edu.cn', '学院名称来自南京大学2026年官方硕士招生目录（院系代码033）。'),
  (3, 3, '计算机学院', 'https://computer.hdu.edu.cn', '学院名称和官网入口。'),
  (4, 4, '计算机科学与技术学院', 'http://www.cs.zju.edu.cn', '学院名称和官网入口。'),
  (5, 5, '计算机学院', NULL, '学院名称来自上海交通大学2026年官方硕士招生专业目录（院系代码033）。'),
  (6, 6, '计算机科学与技术学院', 'https://cs.hust.edu.cn', '学院名称和官网入口。'),
  (7, 7, '计算机科学与工程学院', 'https://www.scse.uestc.edu.cn', '学院名称和官网入口。'),
  (8, 8, '计算机学院', 'https://scs.bupt.edu.cn', '学院名称和官网入口。'),
  (9, 9, '计算机科学与技术学院', 'https://cs.xidian.edu.cn', '学院名称和官网入口。'),
  (10, 10, '计算机与软件学院', 'https://csse.szu.edu.cn', '学院名称和官网入口。');

MERGE INTO major (id, school_id, college_id, name, major_code, degree_type, research_direction, study_mode, remark) KEY(id)
VALUES
  (1, 1, 1, '计算机科学与技术', '081200', '学硕', NULL, '全日制', '专业方向和招生状态以当年专业目录为准。'),
  (2, 2, 2, '计算机科学与技术', '081200', '学硕', '新型程序设计与软件方法学；机器学习与智能化信息处理；分布计算与系统安全技术；媒体计算与内容处理技术；理论计算机科学', '全日制', '专业、方向和拟招人数来自南京大学2026年官方硕士招生目录。'),
  (3, 3, 3, '计算机科学与技术', '081200', '学硕', NULL, '全日制', '专业方向和招生状态以当年专业目录为准。'),
  (4, 4, 4, '计算机科学与技术', '081200', '学硕', NULL, '全日制', '专业方向和招生状态以当年专业目录为准。'),
  (5, 5, 5, '计算机科学与技术', '081200', '学硕', '计算机科学与技术方向；软件工程方向', '全日制', '专业与方向来自上海交通大学2026年官方硕士招生专业目录。'),
  (6, 6, 6, '计算机科学与技术', '081200', '学硕', NULL, '全日制', '专业方向和招生状态以当年专业目录为准。'),
  (7, 7, 7, '计算机科学与技术', '081200', '学硕', NULL, '全日制', '专业方向和招生状态以当年专业目录为准。'),
  (8, 8, 8, '计算机科学与技术', '081200', '学硕', NULL, '全日制', '专业方向和招生状态以当年专业目录为准。'),
  (9, 9, 9, '计算机科学与技术', '081200', '学硕', NULL, '全日制', '专业方向和招生状态以当年专业目录为准。'),
  (10, 10, 10, '计算机科学与技术', '081200', '学硕', NULL, '全日制', '专业方向和招生状态以当年专业目录为准。');

MERGE INTO document_source (id, title, source_type, source_url, publish_date, school_id, college_id, year, is_official, audit_status, remark) KEY(id)
VALUES
  (1, '北京大学研究生招生官方入口', '官方研招入口', 'https://grs.pku.edu.cn', NULL, 1, 1, NULL, 1, 'PUBLISHED', '常设官方入口。'),
  (2, '南京大学研究生招生官方入口', '官方研招入口', 'https://yzb.nju.edu.cn', NULL, 2, 2, NULL, 1, 'PUBLISHED', '2026-07-10 在线核验可访问。'),
  (3, '杭州电子科技大学研究生招生官方入口', '官方研招入口', 'https://grs.hdu.edu.cn', NULL, 3, 3, NULL, 1, 'PUBLISHED', '常设官方入口。'),
  (4, '浙江大学研究生招生官方入口', '官方研招入口', 'https://grs.zju.edu.cn', NULL, 4, 4, NULL, 1, 'PUBLISHED', '常设官方入口。'),
  (5, '上海交通大学研究生招生官方入口', '官方研招入口', 'https://yzb.sjtu.edu.cn', NULL, 5, 5, NULL, 1, 'PUBLISHED', '2026-07-10 在线核验可访问。'),
  (6, '华中科技大学研究生招生信息网', '官方研招入口', 'https://gszs.hust.edu.cn', NULL, 6, 6, NULL, 1, 'PUBLISHED', '2026-07-10 在线核验可访问。'),
  (7, '电子科技大学研究生招生官方入口', '官方研招入口', 'https://yz.uestc.edu.cn', NULL, 7, 7, NULL, 1, 'PUBLISHED', '常设官方入口。'),
  (8, '北京邮电大学研究生招生官方入口', '官方研招入口', 'https://yzb.bupt.edu.cn', NULL, 8, 8, NULL, 1, 'PUBLISHED', '站点存在访问校验，建议浏览器打开。'),
  (9, '西安电子科技大学研究生院官方入口', '官方研招入口', 'https://gr.xidian.edu.cn', NULL, 9, 9, NULL, 1, 'PUBLISHED', '2026-07-10 在线核验可访问。'),
  (10, '深圳大学研究生招生官方入口', '官方研招入口', 'https://yz.szu.edu.cn', NULL, 10, 10, NULL, 1, 'PUBLISHED', '常设官方入口。'),
  (11, '南京大学2026年硕士招生学院复试细则及复试名单汇总', '复试细则汇总', 'https://yzb.nju.edu.cn/9e/88/c47863a827016/page.htm', '2026-03-19', 2, 2, 2026, 1, 'PUBLISHED', '2026-07-10 在线核验；院系细则以汇总页最新链接为准。'),
  (12, '上海交通大学2026年硕士招生官方通知入口', '硕士招生通知', 'https://yzb.sjtu.edu.cn/', NULL, 5, 5, 2026, 1, 'PUBLISHED', '2026-07-10 在线核验，首页可见 2026 硕士招生通知。'),
  (13, '华中科技大学2026年硕士研究生招生复试录取工作规定', '复试录取规定', 'https://gszs.hust.edu.cn/info/1089/4074.htm', '2026-03-18', 6, 6, 2026, 1, 'PUBLISHED', '2026-07-10 在线核验正文。'),
  (14, '西安电子科技大学2026年硕士研究生招生简章及招生专业目录', '招生简章与专业目录', 'https://gr.xidian.edu.cn/info/1072/17294.htm', '2025-09-30', 9, 9, 2026, 1, 'PUBLISHED', '2026-07-10 在线核验页面及附件入口。');

MERGE INTO document_source (title, source_type, source_url, publish_date, school_id, college_id, year, is_official, audit_status, remark) KEY(source_url)
VALUES
  ('上海交通大学2026年硕士研究生招生专业目录', '招生专业目录', 'https://yzb.sjtu.edu.cn/post/3309', '2025-09-30', 5, 5, 2026, 1, 'PUBLISHED', '2026-07-15在线核验官方正文；学院总计划不等同于081200专业计划。'),
  ('南京大学2026年硕士研究生招生目录', '招生专业目录', 'https://yzb.nju.edu.cn/19/b1/c47862a793009/page.htm', '2025-10-09', 2, 2, 2026, 1, 'PUBLISHED', '2026-07-15在线核验页面及官方PDF文本层；081200拟招66人，最终人数可能调整。'),
  ('浙江大学计算机科学与技术学院2026年硕士研究生招生考试复试录取方案', '复试录取方案', 'http://www.cs.zju.edu.cn/csen/2026/0318/c27010a3141869/page.htm', '2026-03-18', 4, 4, 2026, 1, 'PUBLISHED', '2026-07-15在线核验学院官方正文；计划9人为统考复试阶段口径，不等同全年目录总计划。');

MERGE INTO source_document (id, title, document_type, source_url, school_id, college_id, major_id, year, audit_status, source_reliability, raw_text, remark) KEY(id)
VALUES
  (1, '南京大学2026年硕士招生官方入口核验', '官方招生快照', 'https://yzb.nju.edu.cn/main.htm', 2, 2, NULL, 2026, 'PUBLISHED', 'OFFICIAL', '南京大学研究生招生网于 2026-07-10 在线核验可访问。硕士最新通知栏目可见：2026-05-15 研究生党团组织关系转接及户口迁移说明、2026-05-08 拟录取硕士研究生调档和思政考核通知、2026-04-08 少数民族骨干计划调剂公告、2026-03-19 硕士招生学院复试细则及复试名单汇总。网站同时提供复试基本分数线和往年报考录取统计入口。', '只记录页面可直接核验的事实，不推断具体专业分数。'),
  (2, '上海交通大学2026年硕士招生官方入口核验', '官方招生快照', 'https://yzb.sjtu.edu.cn/', 5, 5, NULL, 2026, 'PUBLISHED', 'OFFICIAL', '上海交通大学研究生招生网于 2026-07-10 在线核验可访问。硕士招生栏目可见 2026-07-09 研究生新生报到须知、2026-07-01 致新生、2026-06-30 硕士研究生录取通知书发放事项说明。网站提供招生简章、复试信息、录取信息和历史数据入口。', '只记录页面可直接核验的事实。'),
  (3, '华中科技大学2026年硕士研究生复试录取工作规定核验', '复试录取规定', 'https://gszs.hust.edu.cn/info/1089/4074.htm', 6, 6, 6, 2026, 'PUBLISHED', 'OFFICIAL', '华中科技大学研究生招生信息网于 2026-03-18 发布 2026 年硕士研究生招生复试录取工作规定。合格生源充足的院系差额比例一般不低于 120%；复试采用线下现场方式；学校集中复试时间为 3 月 21 日至 22 日；录取总成绩按初试成绩 60%、复试成绩 40% 折算；复试成绩低于 60 分者不予录取；具体内容和材料要求以各院系复试工作细则为准。页面于 2026-07-10 在线核验。', '学校级规则，院系可能有补充要求。'),
  (4, '西安电子科技大学2026年硕士招生简章与专业目录核验', '招生简章与专业目录', 'https://gr.xidian.edu.cn/info/1072/17294.htm', 9, 9, 9, 2026, 'PUBLISHED', 'OFFICIAL', '西安电子科技大学研究生院于 2025-09-30 发布 2026 年硕士研究生招生简章及招生专业目录页面。2026-07-10 使用系统 PDF 文本层解析官方专业目录附件，计算机科学与技术学院 081200 计算机科学与技术专业初试科目为 101 思想政治理论、201 英语一、301 数学一、408 计算机学科专业基础，目录拟招生人数为 70。目录首页明确说明拟招生人数不含推免、根据 2025 年招生人数拟定、仅供参考，实际人数将动态调整。', '结构化字段来自官方 PDF 文本层，保留目录中的计划口径限制。'),
  (5, '北京邮电大学研招网2027年初试科目调整通知入口核验', '考试科目调整入口', 'https://yzb.bupt.edu.cn/', 8, 8, 8, 2027, 'PUBLISHED', 'OFFICIAL', '北京邮电大学研究生招生网首页于 2026-07-09 采集时可见 2026-06-09 发布的部分学院调整 2027 年硕士研究生招生考试初试科目通知入口。当前快照只能确认调整通知存在，未取得计算机科学与技术专业的通知正文和具体科目，不据此写入考试科目字段。', '官方入口快照；具体调整内容需通过通知正文再次核验，当前不推断是否改考 408。');

MERGE INTO source_document (title, document_type, source_url, school_id, college_id, major_id, year, audit_status, source_reliability, raw_text, remark) KEY(source_url)
VALUES
  ('上海交通大学2026年硕士研究生招生专业目录核验', '招生专业目录', 'https://yzb.sjtu.edu.cn/post/3309', 5, 5, 5, 2026, 'PUBLISHED', 'OFFICIAL', '上海交通大学研究生招生网于2025-09-30发布《上海交通大学2026年硕士研究生招生专业目录》。目录列明：033计算机学院总招生计划357名，其中全日制学术学位94名、全日制专业学位263名，全日制推免生招生计划174名。081200计算机科学与技术为学术学位，包含计算机科学与技术方向和软件工程方向，均为全日制，初试科目为101思想政治理论、201英语（一）、301数学（一）、408计算机学科专业基础。085400电子信息的计算机与大数据技术、软件工程、网络空间安全、人工智能等相关方向同样采用408。学院总计划覆盖多个专业，不能据此推断081200专业级招生人数。', '2026-07-15在线核验官方正文；学院总计划不写入专业级招生计划。'),
  ('南京大学2026年硕士研究生招生目录核验', '招生专业目录', 'https://yzb.nju.edu.cn/19/b1/c47862a793009/page.htm', 2, 2, 2, 2026, 'PUBLISHED', 'OFFICIAL', '南京大学研究生招生网于2025-10-09发布《南京大学2026年硕士研究生招生目录》。官方PDF第42页列明：033计算机学院全日制学术学位拟招86人、全日制专业学位拟招150人；其中081200计算机科学与技术为全日制学术学位，专业行拟招66人，包含新型程序设计与软件方法学、机器学习与智能化信息处理、分布计算与系统安全技术、媒体计算与内容处理技术、理论计算机科学五个方向，初试科目为101思想政治理论、201英语（一）、301数学（一）、408计算机学科专业基础。目录说明各专业推免人数另行公布，最终招生人数可能根据教育部下达计划增加或减少。', '2026-07-15解析官方PDF文本层；66为081200专业拟招人数，推免和统考拆分保持为空。'),
  ('浙江大学计算机科学与技术学院2026年硕士复试录取方案核验', '复试录取方案', 'http://www.cs.zju.edu.cn/csen/2026/0318/c27010a3141869/page.htm', 4, 4, 4, 2026, 'PUBLISHED', 'OFFICIAL', '浙江大学计算机科学与技术学院于2026-03-18发布硕士研究生招生考试复试录取方案。081200计算机科学与技术复试线为总分382，政治50、外语50、业务课一75、业务课二75；学院按1:1.3差额比例确定复试线。复试阶段招生人数为9人。计算机类采用现场上机考试加面试，复试成绩为机考35%加面试65%，综合成绩为初试折算成绩65%加复试成绩35%；复试成绩低于60分、思想品德考核不合格或缺少规定复试环节者不予录取。', '2026-07-15在线核验学院官方正文；9人为统考复试阶段计划，仅写入统考名额，不写全年总计划。');

MERGE INTO document_chunk (id, document_id, school_id, college_id, major_id, year, document_type, chunk_index, content, audit_status) KEY(id)
VALUES
  (1, 1, 2, 2, NULL, 2026, '官方招生快照', 0, '南京大学 2026 硕士招生官方信息：3 月 19 日发布学院复试细则及复试名单汇总，4 月 8 日发布专项调剂公告，5 月 8 日发布拟录取硕士调档和思政考核通知。官方研招网提供复试基本分数线和往年报考录取统计入口。具体专业数据需打开官方页面核验。', 'PUBLISHED'),
  (2, 2, 5, 5, NULL, 2026, '官方招生快照', 0, '上海交通大学 2026 硕士招生官方信息：研究生招生网提供招生简章、复试信息、录取信息和历史数据入口；6 月 30 日发布硕士研究生录取通知书发放事项说明。具体专业分数、计划和录取结果需以栏目内公告为准。', 'PUBLISHED'),
  (3, 3, 6, 6, 6, 2026, '复试录取规定', 0, '华中科技大学 2026 硕士复试规则：合格生源充足的院系差额比例一般不低于 120%，采用线下现场复试，集中复试时间为 3 月 21 日至 22 日，录取总成绩按初试 60% 和复试 40% 折算，复试成绩低于 60 分不予录取。具体要求以院系细则为准。', 'PUBLISHED'),
  (4, 4, 9, 9, 9, 2026, '招生简章与专业目录', 0, '西安电子科技大学 2026 官方专业目录：计算机科学与技术学院 081200 计算机科学与技术，初试科目为 101 思想政治理论、201 英语一、301 数学一、408 计算机学科专业基础，目录拟招生人数 70。该人数不含推免，是根据 2025 年招生人数拟定的参考计划，实际招生人数将根据上级计划和报考情况动态调整。', 'PUBLISHED'),
  (5, 5, 8, 8, 8, 2027, '考试科目调整入口', 0, '北京邮电大学研究生招生网在 2026-06-09 发布了部分学院调整 2027 年硕士研究生招生考试初试科目的通知入口。当前已核验资料不包含计算机科学与技术专业的具体调整内容，不能据此判断是否采用 408，必须打开官方通知正文确认。', 'PUBLISHED');

MERGE INTO document_chunk (document_id, school_id, college_id, major_id, year, document_type, chunk_index, content, audit_status) KEY(document_id, chunk_index)
VALUES
  ((SELECT id FROM source_document WHERE source_url = 'https://yzb.sjtu.edu.cn/post/3309'), 5, 5, 5, 2026, '招生专业目录', 1, '上海交通大学2026年官方硕士招生专业目录：033计算机学院总招生计划357名，其中学硕94名、专硕263名、推免174名。081200计算机科学与技术包含计算机科学与技术方向和软件工程方向，均为全日制，初试科目为101思想政治理论、201英语（一）、301数学（一）、408计算机学科专业基础。学院总计划覆盖多个专业，不能据此推断081200专业级招生人数。', 'PUBLISHED'),
  ((SELECT id FROM source_document WHERE source_url = 'https://yzb.nju.edu.cn/19/b1/c47862a793009/page.htm'), 2, 2, 2, 2026, '招生专业目录', 1, '南京大学2026年官方硕士招生目录：033计算机学院081200计算机科学与技术为全日制学术学位，包含新型程序设计与软件方法学、机器学习与智能化信息处理、分布计算与系统安全技术、媒体计算与内容处理技术、理论计算机科学五个方向，初试科目为101思想政治理论、201英语（一）、301数学（一）、408计算机学科专业基础，专业拟招66人。推免人数另行公布，最终招生人数可能调整。', 'PUBLISHED'),
  ((SELECT id FROM source_document WHERE source_url = 'http://www.cs.zju.edu.cn/csen/2026/0318/c27010a3141869/page.htm'), 4, 4, 4, 2026, '复试录取方案', 1, '浙江大学计算机学院2026年硕士复试录取方案：081200复试线为总分382，单科50、50、75、75，按1:1.3差额复试，统考复试阶段招生计划9人。计算机类采用现场上机考试加面试，复试成绩为机考35%加面试65%，综合成绩中初试占65%、复试占35%；复试成绩低于60分或思想品德考核不合格者不予录取。9人不是全年目录总计划。', 'PUBLISHED');

MERGE INTO admission_plan (school_id, college_id, major_id, year, total_quota, recommended_quota, unified_quota, has_adjustment, source_id, remark) KEY(school_id, major_id, year)
VALUES
  (9, 9, 9, 2026, 70, NULL, 70, 0, 14, '官方专业目录拟招生人数，不含推免；根据 2025 年人数拟定，仅供参考，实际计划动态调整。'),
  (2, 2, 2, 2026, 66, NULL, NULL, NULL, (SELECT id FROM document_source WHERE source_url = 'https://yzb.nju.edu.cn/19/b1/c47862a793009/page.htm'), '官方目录081200专业拟招人数；推免人数另行公布，统考名额未知，最终人数可能调整。'),
  (4, 4, 4, 2026, NULL, NULL, 9, NULL, (SELECT id FROM document_source WHERE source_url = 'http://www.cs.zju.edu.cn/csen/2026/0318/c27010a3141869/page.htm'), '学院复试录取方案中的081200统考复试阶段招生人数；不等同于包含推免的全年目录总计划。');

MERGE INTO exam_subject (school_id, college_id, major_id, year, politics, foreign_language, math_subject, professional_subject, is_408, source_id) KEY(school_id, major_id, year)
VALUES
  (9, 9, 9, 2026, '101 思想政治理论', '201 英语（一）', '301 数学（一）', '408 计算机学科专业基础', 1, 14),
  (5, 5, 5, 2026, '101 思想政治理论', '201 英语（一）', '301 数学（一）', '408 计算机学科专业基础', 1, (SELECT id FROM document_source WHERE source_url = 'https://yzb.sjtu.edu.cn/post/3309')),
  (2, 2, 2, 2026, '101 思想政治理论', '201 英语（一）', '301 数学（一）', '408 计算机学科专业基础', 1, (SELECT id FROM document_source WHERE source_url = 'https://yzb.nju.edu.cn/19/b1/c47862a793009/page.htm'));

MERGE INTO score_line (school_id, college_id, major_id, year, total_score, politics_score, foreign_language_score, math_score, professional_score, source_id, remark) KEY(school_id, major_id, year)
VALUES
  (4, 4, 4, 2026, 382, 50, 50, 75, 75, (SELECT id FROM document_source WHERE source_url = 'http://www.cs.zju.edu.cn/csen/2026/0318/c27010a3141869/page.htm'), '计算机学院081200复试线；专项计划执行学校基本要求。');

MERGE INTO retest_rule (school_id, college_id, major_id, year, retest_time, retest_method, retest_ratio, initial_score_weight, retest_score_weight, qualification_line, materials, source_id, remark) KEY(school_id, major_id, year)
VALUES
  (6, 6, 6, 2026, '2026-03-21 至 2026-03-22', '线下现场复试；具体形式由院系细则确定', 1.20, 60, 40, '复试成绩低于 60 分者不予录取；思想品德考核不合格者不予录取。', '资格审核材料和院系补充材料以学校研招网及院系复试细则为准。', 13, '学校级官方规定，2026-07-10 在线核验；1.20 表示一般不低于 120%。'),
  (4, 4, 4, 2026, '3月21日资格审查；3月22日上机考试；3月23日面试', '现场复试；计算机类采用上机考试加面试', 1.30, 65, 35, '复试成绩=机考35%+面试65%，低于60分不合格；思想品德不合格或缺少规定复试环节者不予录取。', '身份证、准考证、学籍或学历证明、成绩单及科研成果等；以学院方案完整清单为准。', (SELECT id FROM document_source WHERE source_url = 'http://www.cs.zju.edu.cn/csen/2026/0318/c27010a3141869/page.htm'), '综合成绩=（初试总分/5）×65%+复试成绩×35%；1.30表示按1:1.3确定复试线。');

UPDATE school
SET is_self_determined_score = CASE WHEN name IN (
  '北京大学', '清华大学', '中国人民大学', '北京师范大学', '北京航空航天大学', '北京理工大学', '中国农业大学',
  '南开大学', '天津大学', '大连理工大学', '东北大学', '吉林大学', '哈尔滨工业大学', '复旦大学', '同济大学',
  '上海交通大学', '南京大学', '东南大学', '浙江大学', '中国科学技术大学', '厦门大学', '山东大学', '武汉大学',
  '华中科技大学', '湖南大学', '中南大学', '中山大学', '华南理工大学', '四川大学', '重庆大学', '电子科技大学',
  '西安交通大学', '西北工业大学', '兰州大学'
) THEN 1 ELSE 0 END;

MERGE INTO national_score_line (year, category_code, category_name, candidate_type, total_score, score_100,
  score_over_100, source_title, source_url, published_date, source_hash, remark)
KEY(year, category_code, candidate_type)
VALUES
  (2026, '07', '理学', 'A', 275, 35, 53, '2026年全国硕士研究生招生考试考生进入复试的初试成绩基本要求', 'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf', '2026-02-28', '71aa4754127c14e2a1a720e90de51e70cb8da96750f41eb38b70db1419b060d1', '教育部公布的国家线，不等同于招生单位或学院实际复试线。'),
  (2026, '07', '理学', 'B', 265, 32, 48, '2026年全国硕士研究生招生考试考生进入复试的初试成绩基本要求', 'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf', '2026-02-28', '71aa4754127c14e2a1a720e90de51e70cb8da96750f41eb38b70db1419b060d1', '教育部公布的国家线，不等同于招生单位或学院实际复试线。'),
  (2026, '08', '工学（非照顾专业）', 'A', 264, 35, 53, '2026年全国硕士研究生招生考试考生进入复试的初试成绩基本要求', 'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf', '2026-02-28', '71aa4754127c14e2a1a720e90de51e70cb8da96750f41eb38b70db1419b060d1', '适用于计算机类工学及电子信息相关专业的国家线基准，不等同于招生单位或学院实际复试线。'),
  (2026, '08', '工学（非照顾专业）', 'B', 254, 32, 48, '2026年全国硕士研究生招生考试考生进入复试的初试成绩基本要求', 'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf', '2026-02-28', '71aa4754127c14e2a1a720e90de51e70cb8da96750f41eb38b70db1419b060d1', '适用于计算机类工学及电子信息相关专业的国家线基准，不等同于招生单位或学院实际复试线。'),
  (2026, '14', '交叉学科', 'A', 266, 35, 53, '2026年全国硕士研究生招生考试考生进入复试的初试成绩基本要求', 'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf', '2026-02-28', '71aa4754127c14e2a1a720e90de51e70cb8da96750f41eb38b70db1419b060d1', '教育部公布的国家线，不等同于招生单位或学院实际复试线。'),
  (2026, '14', '交叉学科', 'B', 256, 32, 48, '2026年全国硕士研究生招生考试考生进入复试的初试成绩基本要求', 'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf', '2026-02-28', '71aa4754127c14e2a1a720e90de51e70cb8da96750f41eb38b70db1419b060d1', '教育部公布的国家线，不等同于招生单位或学院实际复试线。');

MERGE INTO document_source (title, source_type, source_url, publish_date, school_id, college_id, year,
  is_official, audit_status, remark) KEY(source_url)
VALUES ('2026年全国硕士研究生招生考试国家线', '国家线',
  'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf',
  '2026-02-28', NULL, NULL, 2026, 1, 'PUBLISHED',
  '教育部通过中国研究生招生信息网公布；PDF SHA-256: 71aa4754127c14e2a1a720e90de51e70cb8da96750f41eb38b70db1419b060d1。');

MERGE INTO source_document (title, document_type, source_url, school_id, college_id, major_id, year,
  audit_status, source_reliability, raw_text, remark) KEY(source_url)
VALUES ('2026年计算机类考研国家线官方数据', '国家线',
  'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf',
  NULL, NULL, NULL, 2026, 'PUBLISHED', 'OFFICIAL',
  '教育部公布2026年全国硕士研究生招生考试考生进入复试的初试成绩基本要求。理学[07]：A类总分275、满分100分单科35、满分大于100分单科53；B类总分265、单科32、48。工学[08]非照顾专业：A类总分264、单科35、53；B类总分254、单科32、48。交叉学科[14]：A类总分266、单科35、53；B类总分256、单科32、48。A类对应报考地处一区的招生单位，B类对应报考地处二区的招生单位。国家线不是招生单位、学院或专业实际复试线；自主划线院校实际要求以学校和学院公告为准。',
  'PDF文本层核验；只结构化计算机考研相关学科门类，其他门类仍以官方原表为准。');

MERGE INTO document_chunk (document_id, school_id, college_id, major_id, year, document_type,
  chunk_index, content, audit_status) KEY(document_id, chunk_index)
VALUES ((SELECT id FROM source_document WHERE source_url = 'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf'),
  NULL, NULL, NULL, 2026, '国家线', 0,
  '2026年计算机类考研国家线：理学07 A类275（单科35、53），B类265（单科32、48）；工学08非照顾专业 A类264（单科35、53），B类254（单科32、48）；交叉学科14 A类266（单科35、53），B类256（单科32、48）。国家线不等同于学校、学院或专业复试线；自主划线院校以学校和学院公告为准。',
  'PUBLISHED');
