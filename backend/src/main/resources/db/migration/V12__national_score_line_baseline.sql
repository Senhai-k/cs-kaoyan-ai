ALTER TABLE school
  ADD COLUMN is_self_determined_score TINYINT NOT NULL DEFAULT 0 AFTER is_double_first_class;

CREATE TABLE national_score_line (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  year INT NOT NULL,
  category_code VARCHAR(20) NOT NULL,
  category_name VARCHAR(100) NOT NULL,
  candidate_type VARCHAR(10) NOT NULL,
  total_score INT NOT NULL,
  score_100 INT NOT NULL,
  score_over_100 INT NOT NULL,
  source_title VARCHAR(255) NOT NULL,
  source_url VARCHAR(500) NOT NULL,
  published_date VARCHAR(20),
  source_hash VARCHAR(64),
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_national_score_line (year, category_code, candidate_type),
  KEY idx_national_score_line_year (year)
);

UPDATE school
SET is_self_determined_score = CASE WHEN name IN (
  '北京大学', '清华大学', '中国人民大学', '北京师范大学', '北京航空航天大学', '北京理工大学', '中国农业大学',
  '南开大学', '天津大学', '大连理工大学', '东北大学', '吉林大学', '哈尔滨工业大学', '复旦大学', '同济大学',
  '上海交通大学', '南京大学', '东南大学', '浙江大学', '中国科学技术大学', '厦门大学', '山东大学', '武汉大学',
  '华中科技大学', '湖南大学', '中南大学', '中山大学', '华南理工大学', '四川大学', '重庆大学', '电子科技大学',
  '西安交通大学', '西北工业大学', '兰州大学'
) THEN 1 ELSE 0 END;

INSERT INTO national_score_line (year, category_code, category_name, candidate_type, total_score, score_100,
  score_over_100, source_title, source_url, published_date, source_hash, remark)
VALUES
  (2026, '07', '理学', 'A', 275, 35, 53, '2026年全国硕士研究生招生考试考生进入复试的初试成绩基本要求', 'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf', '2026-02-28', '71aa4754127c14e2a1a720e90de51e70cb8da96750f41eb38b70db1419b060d1', '教育部公布的国家线，不等同于招生单位或学院实际复试线。'),
  (2026, '07', '理学', 'B', 265, 32, 48, '2026年全国硕士研究生招生考试考生进入复试的初试成绩基本要求', 'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf', '2026-02-28', '71aa4754127c14e2a1a720e90de51e70cb8da96750f41eb38b70db1419b060d1', '教育部公布的国家线，不等同于招生单位或学院实际复试线。'),
  (2026, '08', '工学（非照顾专业）', 'A', 264, 35, 53, '2026年全国硕士研究生招生考试考生进入复试的初试成绩基本要求', 'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf', '2026-02-28', '71aa4754127c14e2a1a720e90de51e70cb8da96750f41eb38b70db1419b060d1', '适用于计算机类工学及电子信息相关专业的国家线基准，不等同于招生单位或学院实际复试线。'),
  (2026, '08', '工学（非照顾专业）', 'B', 254, 32, 48, '2026年全国硕士研究生招生考试考生进入复试的初试成绩基本要求', 'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf', '2026-02-28', '71aa4754127c14e2a1a720e90de51e70cb8da96750f41eb38b70db1419b060d1', '适用于计算机类工学及电子信息相关专业的国家线基准，不等同于招生单位或学院实际复试线。'),
  (2026, '14', '交叉学科', 'A', 266, 35, 53, '2026年全国硕士研究生招生考试考生进入复试的初试成绩基本要求', 'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf', '2026-02-28', '71aa4754127c14e2a1a720e90de51e70cb8da96750f41eb38b70db1419b060d1', '教育部公布的国家线，不等同于招生单位或学院实际复试线。'),
  (2026, '14', '交叉学科', 'B', 256, 32, 48, '2026年全国硕士研究生招生考试考生进入复试的初试成绩基本要求', 'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf', '2026-02-28', '71aa4754127c14e2a1a720e90de51e70cb8da96750f41eb38b70db1419b060d1', '教育部公布的国家线，不等同于招生单位或学院实际复试线。');

INSERT INTO document_source (title, source_type, source_url, publish_date, school_id, college_id, year,
  is_official, audit_status, remark)
VALUES ('2026年全国硕士研究生招生考试国家线', '国家线',
  'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf',
  '2026-02-28', NULL, NULL, 2026, 1, 'PUBLISHED',
  '教育部通过中国研究生招生信息网公布；PDF SHA-256: 71aa4754127c14e2a1a720e90de51e70cb8da96750f41eb38b70db1419b060d1。');

INSERT INTO source_document (title, document_type, source_url, school_id, college_id, major_id, year,
  audit_status, source_reliability, raw_text, remark)
VALUES ('2026年计算机类考研国家线官方数据', '国家线',
  'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf',
  NULL, NULL, NULL, 2026, 'PUBLISHED', 'OFFICIAL',
  '教育部公布2026年全国硕士研究生招生考试考生进入复试的初试成绩基本要求。理学[07]：A类总分275、满分100分单科35、满分大于100分单科53；B类总分265、单科32、48。工学[08]非照顾专业：A类总分264、单科35、53；B类总分254、单科32、48。交叉学科[14]：A类总分266、单科35、53；B类总分256、单科32、48。A类对应报考地处一区的招生单位，B类对应报考地处二区的招生单位。国家线不是招生单位、学院或专业实际复试线；自主划线院校实际要求以学校和学院公告为准。',
  'PDF文本层核验；只结构化计算机考研相关学科门类，其他门类仍以官方原表为准。');

INSERT INTO document_chunk (document_id, school_id, college_id, major_id, year, document_type,
  chunk_index, content, audit_status)
SELECT id, NULL, NULL, NULL, 2026, '国家线', 0,
  '2026年计算机类考研国家线：理学07 A类275（单科35、53），B类265（单科32、48）；工学08非照顾专业 A类264（单科35、53），B类254（单科32、48）；交叉学科14 A类266（单科35、53），B类256（单科32、48）。国家线不等同于学校、学院或专业复试线；自主划线院校以学校和学院公告为准。',
  'PUBLISHED'
FROM source_document
WHERE source_url = 'https://t3.chei.com.cn/news/getfile/2293449092-2293449091-3e40264ede94bd5323ab5e01040f5f29.pdf';
