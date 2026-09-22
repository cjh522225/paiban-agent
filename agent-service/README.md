# agent-service

独立 Agent 服务：工具编排 / RAG / MCP Server / 审计 / 评测。

- 技术栈：Spring Boot 3.5.3 + Spring AI 1.0.0（DeepSeek）+ MCP Server (WebMVC) + Java 21
- 模式：`demo`（合成数据，可独立运行与展示）/ `paiban`（对接智能排班系统）/ `dorm`（对接宿舍管理系统）
- 工具层通过业务系统 REST API + 用户 JWT 透传，数据权限由业务系统强制，模型无法绕过

## 本地运行

```powershell
# 加载密钥（勿提交）
. D:\AgentProjects\scripts\env.local.ps1
mvn spring-boot:run
```

- 健康检查：`GET http://localhost:8090/actuator/health`
- 对话：`POST http://localhost:8090/api/chat` `{"message":"你好"}`
- 流式：`POST http://localhost:8090/api/chat/stream`（SSE）
- MCP（SSE）：`http://localhost:8090/sse`

## 构建与测试

```powershell
mvn -B test
mvn -B -DskipTests package
```
