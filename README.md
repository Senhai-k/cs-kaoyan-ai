# 计算机考研助手

面向计算机考研择校与资料核验的全栈项目。系统提供院校筛选、推荐、候选备注、年度趋势、对比、证据详情、RAG 问答，以及资料采集、审核、发布、回滚和质量评估。

## 当前能力

- React 工作台覆盖用户端和管理端，支持桌面、平板和移动端。
- Spring Boot 管理院校、招生数据、资料、权限、版本和发布流程。
- LangGraph Agent 使用 `StateGraph`、`ToolNode`、Checkpointer、HITL、`Send` 并行节点和 Specialist subgraph。
- RAG 链路包含文档解析、切片、BGE Embedding、Qdrant + BM25、多路融合、特征重排和有依据生成。
- 重排基准在同一 30 案例、同一语料上对比关闭重排、特征重排和 CrossEncoder，并保存模型与数据指纹。
- 数据遵循“缺失优于虚构”，用户结论可追溯到官方 URL、资料原文和检索片段。
- H2 用于开发，MySQL + Flyway V1-V15 用于生产结构迁移。
- Prometheus、Alertmanager、OpenTelemetry、备份恢复和故障演练已纳入工程流程。

当前运行基线包含 397 所学校档案，其中 395 所有 2026 年 408 目录记录；共导入 1830 条研招网目录记录、1876 份已发布文档和 1900 个 Agent 索引切片。`database/catalog-408-2026.json` 已完成项目定义的 17 个计算机核心专业代码全部分页，文件标记为 `complete=true`；该结论不扩展到其他学科或历史年份。

院校详情会按学院、专业代码、学位类型、学习方式和四门初试科目展示全部已核验 408 组合，并链接对应研招网证据；同一专业的多组研究方向在重复导入时合并去重。

目录导入会将唯一且明确标注为“专业：N（不含推免）”的人数结构化为统考计划；方向级、院系级、学科级和冲突数字仍只保留原文，不参与覆盖率或推荐计算。目录中带明确“复试内容/科目/方式”标签的原文可结构化为不含时间、比例和权重推断的复试信息。当前招生计划覆盖 355 所院校，90 条复试规则覆盖 20 所院校，真实字段平均覆盖率为 69%。

2026 国家线按理学、工学非照顾专业和交叉学科分别保存 A/B 类总分与单科线，并映射 31 个省级地区和 34 所自主划线院校。34 所自主划线院校均有官方文章、表格图片与哈希证据，其中 33 所已结构化工学学硕学校基本线，武汉大学计算机相关行尚未公布并保持空值。页面和 Agent 将国家线、学校基本线、学院或专业复试线严格分层。

拟录取名单使用独立的匿名导入链路：本机脚本将名单内唯一编号加盐哈希，输出不含姓名、考号和联系方式；管理端先预览专业映射与分数覆盖，再建立草稿，只有管理员可发布。普通计划且专业唯一匹配时才生成聚合结果，专项计划、映射歧义和已有人工结果不会自动合并或覆盖。当前框架已验收，尚未导入真实拟录取名单。

## 项目结构

```text
frontend/          React + TypeScript 用户端和管理端
backend/           Spring Boot API、H2 schema、Flyway 迁移
agent-service/     LangGraph、RAG、评估、任务与 trace
database/          408 数据批次、导入模板和辅助 SQL
deploy/            Compose、Nginx、Prometheus、Alertmanager
scripts/           启动、迁移、备份、采集和故障演练
docs/              架构、路线图、验证与数据质量
```

## 文档

- [项目架构](docs/architecture.md)：模块、Agent、RAG、数据模型、API 和部署设计。
- [项目路线图](docs/roadmap.md)：里程碑状态、已交付主线和后续顺序。
- [验证与数据质量](docs/verification.md)：测试证据、数据边界、验证命令和已知限制。
- [项目经历](docs/项目经历.md)：用于简历和招聘平台的项目描述。

## 快速启动

环境要求：Java 17、Maven、Node.js、Python 3.11。PowerShell 下使用 `npm.cmd` 和 `mvn.cmd`。

首次准备 Agent：

```powershell
cd agent-service
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
git clone https://www.modelscope.cn/AI-ModelScope/bge-small-zh-v1.5.git .\models\bge-small-zh-v1.5
git clone https://www.modelscope.cn/AI-ModelScope/bge-reranker-base.git .\models\bge-reranker-base
```

从仓库根目录启动全部开发服务：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1 -Rebuild
```

本地模型冷启动较慢时，可通过 `-AgentReadyTimeoutSeconds 300` 延长 Agent 就绪等待时间。

访问：`http://127.0.0.1:5173/`

| 服务 | 地址 |
| --- | --- |
| Frontend | `http://127.0.0.1:5173` |
| Backend | `http://127.0.0.1:18888/api/health` |
| Agent | `http://127.0.0.1:18889/api/health` |

启动脚本会幂等导入 408 目录和 34 所自主划线院校批次，并在服务就绪后同步 Agent 索引。

管理端“重排基准”会运行 `off / feature / cross-encoder` 三组真实实验。规划器 A/B 要求配置模型、API Key 和兼容端点。按 Token 计费时使用 `METERED` 并填写真实费率；订阅制或供应商不公开单价时使用 `UNMETERED`，只记录真实 Token，不虚构金额：

```powershell
$env:AGENT_OPENAI_API_KEY="..."
$env:AGENT_OPENAI_BASE_URL="https://你的兼容端点/v1"
$env:AGENT_OPENAI_MODEL="模型名称"
$env:AGENT_PLANNER_PRICING_MODE="METERED" # 或 UNMETERED
$env:AGENT_PLANNER_INPUT_COST_PER_MILLION_USD="输入费率"
$env:AGENT_PLANNER_OUTPUT_COST_PER_MILLION_USD="输出费率"
```

Compose 环境在 `deploy/.env` 填入以上配置后，用一条命令重建 Agent、提交异步实验并检查 `quality-gate-v3`：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-planner-ab.ps1 -RestartAgent
```

报告同时记录两种规划器的 Exact Match、目标召回、延迟、Token、费用状态、失败案例和被安全规则拦截的模型原始提议。最近真实 LLM A/B 为 10/10 Exact Match、10/10 目标召回、4,740 Token、0 失败、0 越界提议，回放门禁通过。

## 管理端

开发启动脚本会为本次运行生成临时管理密码并输出到本机终端。直接启动后端或正式部署必须通过 `ADMIN_USERNAME`、`ADMIN_PASSWORD` 等环境变量显式配置凭据，并使用随机 `AGENT_INTERNAL_TOKEN`；仓库不提供固定默认密码。

角色：

- `ADMIN`：全部管理、删除、发布、回滚和 Agent 运维权限。
- `DATA_EDITOR`：读取、新增和修改，不能执行危险操作。
- `AUDITOR`：只读审核。

密码以 BCrypt 保存；浏览器持有随机 token，数据库只保存 token 的 SHA-256。管理接口需携带 `Authorization: Bearer <token>`。

## 常用验证

```powershell
# Spring
cd backend
mvn.cmd test

# Agent
cd agent-service
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m app.quality_gate

# Frontend
cd frontend
npm.cmd run preflight
npm.cmd run test:e2e
```

数据库与故障演练：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\mysql-migration-drill.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\backup-restore-h2.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\failure-drill.ps1
```

## MySQL

```powershell
mysql -u root -p < database/create-database.sql
cd backend
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="你的密码"
mvn.cmd spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

Flyway 自动执行 V1-V15；V10 使用自然键导入已核验的重点院校官方证据，V11 将聚合研究方向字段扩展为 `TEXT`，V12 增加国家线基准与自主划线标记，V13 增加独立学校基本线表，V14 增加拟录取名单草稿和匿名候选人表，V15 允许复试规则按学校、学院或专业层级保存。正式环境禁止依赖默认空密码，也不要手工跳过迁移版本。

官网定时监测默认关闭。只有管理员登记并启用的精确官方文章 URL 才会被轮询；任务使用数据库租约避免多实例重复执行，采集结果只进入草稿和变化复核流程，不自动发布或重建索引。启用后台扫描时配置：

管理端可从院校已登记的研招入口发现同站候选链接。发现器限制公共 HTTP(S)、标准端口、同一主机、三次重定向、1 MB 响应和 300 个链接，并按目标年份与资料类型排序；候选必须人工采用，采用后仍为待采集状态，不会自动抓取或发布。

```powershell
$env:WEB_CAPTURE_MONITOR_ENABLED="true"
$env:WEB_CAPTURE_MONITOR_INITIAL_DELAY_MS="60000"
$env:WEB_CAPTURE_MONITOR_SCAN_DELAY_MS="300000"
$env:WEB_CAPTURE_MONITOR_MAX_PER_RUN="2"
```

## 生产部署

生成不回显、不会提交到 Git 的随机部署密钥，再执行完整预检：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\new-deploy-env.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\deployment-preflight.ps1 -EnvFile deploy/.env
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\deployment-preflight.ps1 -EnvFile deploy/.env -Build -Start
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\alertmanager-webhook-drill.ps1
```

Compose 编排 MySQL、Backend、Agent、索引同步、Nginx Frontend、Blackbox、Prometheus 和 Alertmanager。`index-sync` 会幂等导入 408 目录与学校基本线批次后同步知识索引；启动预检会等待它以 `0` 退出，并检查其余 7 个服务就绪。正式环境将 `ALERT_WEBHOOK_URL` 指向组织通知系统，再复跑 webhook 演练。

## 数据更新

结构化数据录入前必须先保存官方来源和资料原文。文本 PDF、文本文件和已登记精确官方文章可生成待审核草稿；扫描版 PDF 只有在显式启用 Tesseract 且安装 `chi_sim` 后才进入 OCR。

匿名 408 目录采集：

```powershell
node .\scripts\catalog\chsi-408-collector.mjs --page-limit=1 --output=database\catalog-408-2026.json
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\import-catalog-408.ps1
```

自主划线院校官方来源与人工核验批次：

```powershell
node .\scripts\catalog\chsi-self-score-line-collector.mjs --year=2026
node .\scripts\catalog\build-self-score-line-review.mjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\import-self-score-lines.ps1
```

拟录取名单匿名批次：

```powershell
$env:ADMISSION_IMPORT_SALT="至少16位、仅本次导入使用的随机值"
node .\scripts\admissions\prepare-admission-result-batch.mjs `
  --input .\database\admission-result-import-template.json `
  --source D:\Data\学校官方拟录取名单.pdf `
  --output D:\Data\学校-2026-拟录取匿名批次.json
Remove-Item Env:ADMISSION_IMPORT_SALT
```

生成后的匿名批次在管理端“拟录取名单导入”中预览并建立草稿。原始名单和临时编号文件不得提交到仓库。

全国补全只能使用用户本人合法登录会话。项目不读取浏览器隐私、不绕过登录、验证码或限流。完整流程会先生成候选批次，重算 SHA-256 并检查记录、学校和第四科，事务导入成功后才替换正式文件，最后同步 Agent 索引：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\complete-catalog-408.ps1 `
  -PromptForCookie
```

执行后终端会要求隐藏粘贴浏览器请求头中的 Cookie 值，内容不会显示、写入文件或保留在环境变量中。也可以改用 `-CookieFile "$env:TEMP\chsi.cookies.txt" -DeleteCookieAfterSuccess`。采集器参数可通过 `node .\scripts\catalog\chsi-408-collector.mjs --help` 查看；完整采集未提供登录会话时会在发起网络请求前立即失败。
