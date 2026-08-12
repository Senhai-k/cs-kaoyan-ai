# 贡献指南

## 开发准备

环境要求与启动方式见 [README](README.md)。不要提交 `deploy/.env`、模型文件、虚拟环境、运行数据库、备份、Cookie、日志或构建产物。

## 变更流程

1. 从 `main` 创建短期分支。
2. 保持变更聚焦，并同步更新相关测试和文档。
3. 数据变更必须保留官方来源、审核状态和 SHA-256 完整性信息；缺失字段保持为空，不使用推断值补齐。
4. 提交 Pull Request，并说明验证结果和数据边界。

## 本地验证

```powershell
cd backend
mvn.cmd test

cd ..\agent-service
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m app.quality_gate

cd ..\frontend
npm.cmd run preflight
```

涉及采集、导入或部署时，还应运行对应脚本的测试或预检。界面变更需要检查桌面和移动视口，并附截图。

## 数据与安全

- 只提交有权公开或处理的数据。
- 禁止提交考生姓名、考号、联系方式或可逆的候选人标识。
- 禁止在 Issue、PR、日志或截图中暴露密码、Token、Cookie、私钥、本机个人路径或内部地址。
- 外部网页内容先进入草稿与人工审核流程，不直接发布到用户侧或 RAG 索引。
