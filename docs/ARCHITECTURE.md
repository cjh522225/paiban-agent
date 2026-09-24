# 架构说明

## 总体架构

```mermaid
flowchart LR
  subgraph Desktop[桌面端]
    DSB[DeepSeekBall<br/>Electron + React + TS<br/>MCP Client]
  end

  subgraph AS1[agent-service · paiban 模式 :8090]
    CHAT1[Chat API / SSE]
    TOOLS1[19 排班工具 + searchPolicy + 节假日预览]
    RAG1[RAG 检索/评测]
    MCP1[MCP Server SSE]
    AUDIT1[审计]
  end

  subgraph AS2[agent-service · dorm 模式 :8091]
    CHAT2[Chat API]
    TOOLS2[8 宿舍工具 + 2 智能辅助 + searchPolicy]
    MCP2[MCP Server SSE]
  end

  PB[智能排班系统后端<br/>Spring Boot 3.2 + MySQL]
  DM[宿舍管理系统后端<br/>RuoYi + MySQL]
  DEEPSEEK[(DeepSeek API)]
  SF[(SiliconFlow<br/>BGE-M3 / Reranker)]

  DSB -->|MCP over SSE| MCP1
  DSB -->|MCP over SSE| MCP2
  CHAT1 --> DEEPSEEK
  CHAT2 --> DEEPSEEK
  RAG1 --> SF
  TOOLS1 -->|REST + 用户 JWT 透传| PB
  TOOLS2 -->|REST + JWT 透传| DM
```

## 一次对话的工具编排流程（paiban 模式）

```mermaid
sequenceDiagram
  participant U as 前端/桌面端
  participant A as agent-service
  participant D as DeepSeek
  participant P as 排班后端

  U->>A: POST /api/chat {message, allowWrites} + JWT
  A->>A: 按模式组装工具（JWT 绑定进工具实例 + 审计装饰）
  A->>D: ChatCompletion(messages, tools)
  D-->>A: tool_calls: currentWeek
  A->>P: GET /api/semester（透传 JWT）
  P-->>A: 学期配置
  A-->>D: tool result（紧凑 JSON）
  D-->>A: tool_calls: mySchedule
  A->>P: GET /api/schedules/my
  P-->>A: 排班列表
  A-->>D: tool result
  D-->>A: 最终回答（带数据来源）
  A-->>U: 答案 + 审计已记录
```

## RAG 流程

```mermaid
flowchart LR
  DOCS[制度文档 4 篇 Markdown] --> CHUNK[分块：按章节 + 目标 480 字/重叠 80]
  CHUNK --> EMB[Embedding<br/>SiliconFlow BGE-M3 或 本地哈希兜底]
  EMB --> STORE[SimpleVectorStore]
  Q[问题] --> SEARCH[向量检索 topK]
  STORE --> SEARCH
  SEARCH --> RERANK[可选：bge-reranker-v2-m3]
  RERANK --> CITED[带 source/section 的片段]
  CITED --> TOOL[searchPolicy 工具]
  EVAL[50 题评测集] --> REPORT[hit@1 / hit@3 / MRR]
```

## 结构化输出 + 人工确认（HITL）

```mermaid
sequenceDiagram
  participant M as 管理员
  participant A as agent-service
  participant L as LLM
  participant P as 排班后端

  M->>A: "国庆1-7放假、10.11补周三课"
  A->>L: 解析为 HolidayDraft（结构化）
  L-->>A: holidays / makeups / warnings
  A->>A: 日期与星期一致性校验 → draftToken（10 分钟）
  A-->>M: 预览 + 警告（未写库）
  M->>A: 确认落库（管理员 JWT）
  A->>P: POST /api/holidays
  P-->>A: 创建成功
  A-->>M: 落库结果
```

## MCP 工具暴露（只读）

```mermaid
flowchart TB
  MCPCLIENT[DeepSeekBall MCP Client] -->|SSE /sse| MCPSERVER[MCP Server]
  MCPSERVER --> PROV[McpToolProvider]
  PROV --> DYN[DynamicTokenToolCallback<br/>调用时解析服务账号 token]
  DYN --> P1[PaibanTools / DormTools]
  DYN --> RAGS[searchPolicy]
  DYN --> PREV[previewHolidayConfig（仅排班模式）]
  DYN --> AUD[审计装饰器]
```

说明：
- 发送到 MCP 的工具为**只读**；写操作（`applyHolidayConfig`）只在交互式对话且 `allowWrites=true` 时挂载
- 权限边界：agent-service 不自行判定数据权限，一律透传调用者 JWT，由业务系统按角色/范围强制
