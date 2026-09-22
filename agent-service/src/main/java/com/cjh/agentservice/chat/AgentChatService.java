package com.cjh.agentservice.chat;

import com.cjh.agentservice.chat.store.ConversationStore;
import com.cjh.agentservice.chat.store.FileChatMemoryRepository;
import com.cjh.agentservice.config.AgentProperties;
import com.cjh.agentservice.tools.ToolRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AgentChatService {

    private static final int MEMORY_WINDOW = 40;

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;
    private final ChatMemory chatMemory;
    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final ConversationStore conversationStore;
    private final ConversationOwnerResolver ownerResolver;
    private final AgentProperties agentProperties;

    public AgentChatService(ChatClient chatClient, ToolRegistry toolRegistry, ConversationStore conversationStore,
                            ConversationOwnerResolver ownerResolver, AgentProperties agentProperties) {
        this.chatClient = chatClient;
        this.toolRegistry = toolRegistry;
        this.conversationStore = conversationStore;
        this.ownerResolver = ownerResolver;
        this.agentProperties = agentProperties;
        this.chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new FileChatMemoryRepository(conversationStore))
                .maxMessages(MEMORY_WINDOW)
                .build();
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    public ChatReply chat(String authorizationHeader, String message, boolean allowWrites, String conversationId) {
        String id = prepare(authorizationHeader, conversationId);
        String content = chatClient.prompt()
                .system(systemPrompt(allowWrites))
                .user(message)
                .toolCallbacks(toolRegistry.toolsFor(authorizationHeader, allowWrites))
                .advisors(memoryAdvisor)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, id))
                .call()
                .content();
        return new ChatReply(content);
    }

    public Flux<String> stream(String authorizationHeader, String message, boolean allowWrites, String conversationId) {
        String id = prepare(authorizationHeader, conversationId);
        List<ToolCallback> tools = toolRegistry.toolsFor(authorizationHeader, allowWrites);
        return chatClient.prompt()
                .system(systemPrompt(allowWrites))
                .user(message)
                .toolCallbacks(tools)
                .advisors(memoryAdvisor)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, id))
                .stream()
                .content();
    }

    public void reset(String conversationId) {
        chatMemory.clear(normalize(conversationId));
    }

    private String prepare(String authorizationHeader, String conversationId) {
        String id = normalize(conversationId);
        conversationStore.ensure(id, ownerResolver.resolve(authorizationHeader));
        return id;
    }

    private String normalize(String conversationId) {
        return conversationId == null || conversationId.isBlank()
                ? ChatMemory.DEFAULT_CONVERSATION_ID
                : conversationId;
    }

    private String systemPrompt(boolean allowWrites) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String writeRule;
        if (agentProperties.isDormMode()) {
            writeRule = "7. 本模式仅提供只读查询工具，不能执行任何写操作；如需修改数据，请引导用户在宿舍系统界面操作。";
        } else if (allowWrites) {
            writeRule = """
                7. 写操作已由用户在界面确认，可调用写工具：请假提交/审批/撤销、换班提交/审批/完成/撤销、多排提交/审批/撤销、
                   排班生成/清空/调班/删除、纪律处理、消息发布/修改/删除、空闲时间保存/删除、用户创建/修改/删除、节假日落库
                   （节假日落库必须使用 previewHolidayConfig 生成的 draftToken）。
                   对于破坏性操作（排班生成/清空、删除用户/消息/节假日、覆盖空闲时间、批量删除），必须先向用户复述影响并得到明确确认后再调用。
                """;
        } else {
            writeRule = "7. 当前为只读模式：只能查询与预览（previewHolidayConfig），不能执行任何写操作；如需写入，请提示用户在界面打开写操作确认后再试。";
        }
        String domain = agentProperties.isDormMode()
                ? "你是宿舍管理系统的 AI 助手，面向高校宿舍管理场景（楼栋/床位/住宿/违规/辅导员等）。"
                : "你是智能排班系统的 AI 助手，面向高校党群组织的值班管理场景。";
        String dateRule = agentProperties.isDormMode()
                ? "2. 涉及统计口径或时间范围时，先确认用户意图（例如「最近」指最近多少条），不要自行编造数据。"
                : "2. 涉及日期与周次的问题，必须先调用 currentWeek 获取当前教学周与日期范围，不要自行推算。";
        String dataRule = agentProperties.isDormMode()
                ? "1. 涉及任何业务数据（宿舍总览、空床位、学生住宿、房间占用、辅导员范围、违规记录、楼栋信息、学生检索）都必须调用工具获取，严禁编造。"
                : "1. 涉及任何业务数据（排班、统计、请假、空闲时间、换班、多排、纪律、消息、用户）都必须调用工具获取，严禁编造。";
        String policyRule = agentProperties.isDormMode()
                ? "3. 涉及管理规定、违纪处理等制度问题时，必须调用 searchPolicy 检索制度原文，回答时引用出处（文档名与章节）。"
                : "3. 请假、换班、排班规则等制度问题，必须调用 searchPolicy 检索制度原文，回答时引用出处（文档名与章节）。";
        return """
                %s
                今天是 %s。

                规则：
                %s
                %s
                %s
                4. 回答时说明数据来源（如"根据宿舍总览接口 /api/dashboard/data"），先给结论，再列明细。
                5. 工具返回 {"error": ...} 时，如实告知用户原因（例如无权限、未登录、参数不合法），不要猜测数据。
                6. 权限由系统控制：普通用户只能查询自己权限范围内的数据，若工具提示无权限，请如实说明。
                %s
                8. 结合上下文理解用户的追问；不确定指代时先澄清。
                9. 只用简体中文回答（不要输出英文句子或英文前言），简洁、准确；禁止任何开场白（如 "I'll…" "Let me…"），直接给结论。
                10. 严禁谎报操作结果：任何写操作必须以工具返回为准；未实际调用工具或工具返回 error 时，不得声称已执行成功。
                11. 表格必须使用规范 Markdown 管道语法（每行形如 `| 列1 | 列2 |`，表头、分隔行、数据行各自一行），禁止用制表符(Tab)或空格对齐来排版表格，也不要把整张表写在一行里。
                """.formatted(domain, today, dataRule, dateRule, policyRule, writeRule);
    }
}
