package com.cjh.agentservice.tools.paiban;

import com.cjh.agentservice.business.BusinessApiException;
import com.cjh.agentservice.business.BusinessClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能排班系统 · 写操作工具集。
 * 仅在用户于界面确认（allowWrites=true）时挂载；所有调用透传调用者 JWT，权限由业务系统强制。
 * 破坏性操作（排班生成/清空、删除节假日、覆盖空闲时间）在描述中明确提示需先与用户确认。
 */
public class PaibanWriteTools {

    private final BusinessClient client;
    private final String token;
    private final ObjectMapper mapper;

    public PaibanWriteTools(BusinessClient client, String token, ObjectMapper mapper) {
        this.client = client;
        this.token = token;
        this.mapper = mapper;
    }

    // ==================== 请假 ====================

    @Tool(name = "approveLeave", description = "审批请假申请（仅管理员）：status 传 approved 或 rejected；驳回必须填写 remark 理由。"
            + "只能审批 pending 状态的申请；通过后系统会删除该申请人请假首日的排班。调用前请与用户确认。")
    public String approveLeave(
            @ToolParam(description = "请假申请 ID（可先用 leaveList/pendingLeaves 查询）") long leaveId,
            @ToolParam(description = "审批结果：approved 或 rejected") String status,
            @ToolParam(required = false, description = "审批意见（驳回时必填）") String remark) {
        String normalized = normalizeApproval(status);
        if ("rejected".equals(normalized) && (remark == null || remark.isBlank())) {
            throw new BusinessApiException(400, "驳回请假必须填写理由");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", leaveId);
        body.put("status", normalized);
        if (remark != null && !remark.isBlank()) {
            body.put("remark", remark);
        }
        return json(result("PUT /api/leaves/approve", client.put(token, "/api/leaves/approve", body)));
    }

    @Tool(name = "submitLeave", description = "提交值班请假申请（当前登录人）：leaveType 传 sick(病假)/personal(事假)/annual(年假)/compensatory(调休)；"
            + "startDate 必填（yyyy-MM-dd），endDate 缺省等于 startDate；提交前请与用户核对日期与事由。")
    public String submitLeave(
            @ToolParam(description = "开始日期 yyyy-MM-dd") String startDate,
            @ToolParam(required = false, description = "结束日期 yyyy-MM-dd，默认同开始日期") String endDate,
            @ToolParam(description = "请假类型：sick/personal/annual/compensatory") String leaveType,
            @ToolParam(required = false, description = "值班类型：dormitory(宿舍) 或 office(办公室)") String dutyType,
            @ToolParam(required = false, description = "请假事由") String reason) {
        if (startDate == null || startDate.isBlank()) {
            throw new BusinessApiException(400, "请假日期不能为空");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startDate", startDate);
        body.put("endDate", endDate == null || endDate.isBlank() ? startDate : endDate);
        body.put("leaveType", normalizeLeaveType(leaveType));
        if (dutyType != null && !dutyType.isBlank()) {
            body.put("dutyType", dutyType);
        }
        if (reason != null && !reason.isBlank()) {
            body.put("reason", reason);
        }
        return json(result("POST /api/leaves", client.post(token, "/api/leaves", body)));
    }

    @Tool(name = "cancelLeave", description = "撤销请假申请：普通用户只能撤销本人 pending 的申请，管理员可删除任意申请。调用前请与用户确认。")
    public String cancelLeave(@ToolParam(description = "请假申请 ID") long leaveId) {
        return json(result("DELETE /api/leaves/" + leaveId, client.delete(token, "/api/leaves/" + leaveId)));
    }

    // ==================== 换班 ====================

    @Tool(name = "submitSwapRequest", description = "提交换班申请（当前登录人）：必须已存在该人当天对应时段的排班；"
            + "timeSlot 建议与排班一致（如 巡班/坐班），targetDay 传目标日期 yyyy-MM-dd，targetSlot 传目标时段。")
    public String submitSwapRequest(
            @ToolParam(description = "类型：dormitory 或 office") String type,
            @ToolParam(description = "教学周次（数字）") int weekNumber,
            @ToolParam(description = "原值班日期 yyyy-MM-dd") String dutyDate,
            @ToolParam(description = "目标日期 yyyy-MM-dd") String targetDay,
            @ToolParam(description = "目标时段，如 巡班") String targetSlot,
            @ToolParam(required = false, description = "原时段（建议填写，与排班一致）") String timeSlot,
            @ToolParam(required = false, description = "地点名称") String locationName,
            @ToolParam(required = false, description = "换班事由") String reason) {
        if (type == null || dutyDate == null || targetDay == null || targetSlot == null) {
            throw new BusinessApiException(400, "请填写完整的换班信息（目标时段必填）");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("weekNumber", weekNumber);
        body.put("dutyDate", dutyDate);
        body.put("targetDay", targetDay);
        body.put("targetSlot", targetSlot);
        if (timeSlot != null && !timeSlot.isBlank()) {
            body.put("timeSlot", timeSlot);
        }
        if (locationName != null && !locationName.isBlank()) {
            body.put("locationName", locationName);
        }
        if (reason != null && !reason.isBlank()) {
            body.put("reason", reason);
        }
        return json(result("POST /api/swap-requests", client.post(token, "/api/swap-requests", body)));
    }

    @Tool(name = "approveSwapRequest", description = "审批换班申请（仅管理员）：action 传 approved 或 rejected。调用前请与用户确认。")
    public String approveSwapRequest(
            @ToolParam(description = "换班申请 ID") long swapRequestId,
            @ToolParam(description = "审批结果：approved 或 rejected") String action) {
        String normalized = normalizeApproval(action);
        return json(result("POST /api/swap-requests/" + swapRequestId + "/approve",
                client.post(token, "/api/swap-requests/" + swapRequestId + "/approve?action=" + normalized, null)));
    }

    @Tool(name = "completeSwapRequest", description = "把已批准的换班标记为已完成（done，仅管理员）。调用前请与用户确认。")
    public String completeSwapRequest(@ToolParam(description = "换班申请 ID") long swapRequestId) {
        return json(result("POST /api/swap-requests/" + swapRequestId + "/done",
                client.post(token, "/api/swap-requests/" + swapRequestId + "/done", null)));
    }

    @Tool(name = "cancelSwapRequest", description = "撤销换班申请：普通用户仅能撤销本人 pending 的申请，管理员可删除任意申请。调用前请与用户确认。")
    public String cancelSwapRequest(@ToolParam(description = "换班申请 ID") long swapRequestId) {
        return json(result("DELETE /api/swap-requests/" + swapRequestId,
                client.delete(token, "/api/swap-requests/" + swapRequestId)));
    }

    // ==================== 多排 ====================

    @Tool(name = "submitMultiDuty", description = "提交多排申请：weekStart/weekEnd 传教学周次（数字）；管理员可用 userId 代客提交。")
    public String submitMultiDuty(
            @ToolParam(description = "开始周次（数字）") int weekStart,
            @ToolParam(description = "结束周次（数字）") int weekEnd,
            @ToolParam(required = false, description = "类型：dormitory 或 office") String type,
            @ToolParam(required = false, description = "说明") String note,
            @ToolParam(required = false, description = "代客申请的用户 ID（仅管理员）") Long userId) {
        if (weekEnd < weekStart) {
            throw new BusinessApiException(400, "结束周次不能早于开始周次");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("weekStart", weekStart);
        body.put("weekEnd", weekEnd);
        if (type != null && !type.isBlank()) {
            body.put("type", type);
        }
        if (note != null && !note.isBlank()) {
            body.put("note", note);
        }
        if (userId != null) {
            body.put("userId", userId);
        }
        return json(result("POST /api/multi-duty", client.post(token, "/api/multi-duty", body)));
    }

    @Tool(name = "approveMultiDuty", description = "审批多排申请（仅管理员）：action 传 approved 或 rejected。调用前请与用户确认。")
    public String approveMultiDuty(
            @ToolParam(description = "多排申请 ID") long multiDutyId,
            @ToolParam(description = "审批结果：approved 或 rejected") String action) {
        String normalized = normalizeApproval(action);
        return json(result("POST /api/multi-duty/" + multiDutyId + "/approve",
                client.post(token, "/api/multi-duty/" + multiDutyId + "/approve?action=" + normalized, null)));
    }

    @Tool(name = "cancelMultiDuty", description = "撤销多排申请：普通用户仅能撤销本人 pending 的申请，管理员可删除任意申请。调用前请与用户确认。")
    public String cancelMultiDuty(@ToolParam(description = "多排申请 ID") long multiDutyId) {
        return json(result("DELETE /api/multi-duty/" + multiDutyId,
                client.delete(token, "/api/multi-duty/" + multiDutyId)));
    }

    // ==================== 排班 ====================

    @Tool(name = "generateDormitorySchedule", description = "生成宿舍排班（仅管理员，破坏性：会先删除所选楼栋在日期区间内的旧排班再重新生成）。"
            + "dormitoryIds 传宿舍楼 ID（逗号分隔，缺省=全部启用楼栋）。调用前必须与用户确认日期范围。")
    public String generateDormitorySchedule(
            @ToolParam(description = "开始日期 yyyy-MM-dd") String startDate,
            @ToolParam(description = "结束日期 yyyy-MM-dd") String endDate,
            @ToolParam(required = false, description = "宿舍楼 ID，逗号分隔（可先用 buildingList 查询），缺省=全部") String dormitoryIds) {
        requireRange(startDate, endDate);
        Map<String, Object> body = new LinkedHashMap<>();
        List<Long> ids = parseIds(dormitoryIds);
        if (!ids.isEmpty()) {
            body.put("dormitoryIds", ids);
        }
        body.put("startDate", startDate);
        body.put("endDate", endDate);
        return json(result("POST /api/schedules/auto/dormitory", client.post(token, "/api/schedules/auto/dormitory", body)));
    }

    @Tool(name = "generateOfficeSchedule", description = "生成办公室排班（仅管理员，破坏性：会先删除所选办公室在日期区间内的旧排班再重新生成）。"
            + "officeIds 传办公室 ID（逗号分隔，缺省=全部）。调用前必须与用户确认日期范围。")
    public String generateOfficeSchedule(
            @ToolParam(description = "开始日期 yyyy-MM-dd") String startDate,
            @ToolParam(description = "结束日期 yyyy-MM-dd") String endDate,
            @ToolParam(required = false, description = "办公室 ID，逗号分隔，缺省=全部") String officeIds) {
        requireRange(startDate, endDate);
        Map<String, Object> body = new LinkedHashMap<>();
        List<Long> ids = parseIds(officeIds);
        if (!ids.isEmpty()) {
            body.put("officeIds", ids);
        }
        body.put("startDate", startDate);
        body.put("endDate", endDate);
        return json(result("POST /api/schedules/auto/office", client.post(token, "/api/schedules/auto/office", body)));
    }

    @Tool(name = "clearSchedules", description = "批量清空排班（仅管理员，破坏性）：按类型+地点+日期区间删除排班记录。"
            + "type 传 dormitory 或 office；locationId 传楼栋/办公室 ID。调用前必须与用户确认。")
    public String clearSchedules(
            @ToolParam(description = "类型：dormitory 或 office") String type,
            @ToolParam(description = "地点 ID（楼栋或办公室）") long locationId,
            @ToolParam(required = false, description = "开始日期 yyyy-MM-dd") String startDate,
            @ToolParam(required = false, description = "结束日期 yyyy-MM-dd") String endDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("locationId", locationId);
        if (startDate != null && !startDate.isBlank()) {
            body.put("startDate", startDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            body.put("endDate", endDate);
        }
        return json(result("DELETE /api/schedules/batch", client.delete(token, "/api/schedules/batch", body)));
    }

    @Tool(name = "swapSchedules", description = "手工调班（仅管理员）：交换两条已有排班的人员（同类排班才能互换），系统会做性别/岗位权限/空闲时间校验。"
            + "scheduleIdA/scheduleIdB 可用 mySchedule/locationSchedule 查询获得。调用前请与用户确认。")
    public String swapSchedules(
            @ToolParam(description = "排班记录 A 的 ID") long scheduleIdA,
            @ToolParam(description = "排班记录 B 的 ID") long scheduleIdB,
            @ToolParam(required = false, description = "教学周次（可选，缺省按日期推算）") Integer weekNumber) {
        if (scheduleIdA == scheduleIdB) {
            throw new BusinessApiException(400, "不能选同一个班次");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scheduleIdA", scheduleIdA);
        body.put("scheduleIdB", scheduleIdB);
        if (weekNumber != null) {
            body.put("weekNumber", weekNumber);
        }
        return json(result("POST /api/schedules/swap", client.post(token, "/api/schedules/swap", body)));
    }

    @Tool(name = "deleteSchedule", description = "删除单条排班记录（仅管理员）。调用前请与用户确认。")
    public String deleteSchedule(@ToolParam(description = "排班记录 ID") long scheduleId) {
        return json(result("DELETE /api/schedules/" + scheduleId, client.delete(token, "/api/schedules/" + scheduleId)));
    }

    // ==================== 纪律 ====================

    @Tool(name = "handleDisciplineAction", description = "处理纪律处理单（仅管理员）：把待处理的处理单标记为已处理。调用前请与用户确认。")
    public String handleDisciplineAction(@ToolParam(description = "纪律处理单 ID（可先用 disciplineRecords 或 /api/discipline/actions 查询）") long actionId) {
        return json(result("POST /api/discipline/actions/" + actionId + "/handle",
                client.post(token, "/api/discipline/actions/" + actionId + "/handle", null)));
    }

    // ==================== 消息 ====================

    @Tool(name = "sendMessage", description = "发布系统消息/通知（仅管理员）：title 必填；type 传 duty/approval/system/urgent；"
            + "receivers 传接收人 ID（逗号分隔，留空=全体启用用户）。调用前请与用户确认内容。")
    public String sendMessage(
            @ToolParam(description = "消息标题") String title,
            @ToolParam(required = false, description = "消息内容") String content,
            @ToolParam(required = false, description = "类型：duty/approval/system/urgent，默认 duty") String type,
            @ToolParam(required = false, description = "接收人 ID，逗号分隔；留空=全体") String receivers) {
        if (title == null || title.isBlank()) {
            throw new BusinessApiException(400, "消息标题不能为空");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        if (content != null && !content.isBlank()) {
            body.put("content", content);
        }
        body.put("type", type == null || type.isBlank() ? "duty" : type);
        if (receivers != null && !receivers.isBlank()) {
            body.put("receivers", receivers);
        }
        body.put("status", 1);
        return json(result("POST /api/messages", client.post(token, "/api/messages", body)));
    }

    @Tool(name = "updateMessage", description = "修改已发布消息（仅管理员，部分更新）：只传需要修改的字段。调用前请与用户确认。")
    public String updateMessage(
            @ToolParam(description = "消息 ID") long messageId,
            @ToolParam(required = false, description = "新标题") String title,
            @ToolParam(required = false, description = "新内容") String content,
            @ToolParam(required = false, description = "新类型：duty/approval/system/urgent") String type,
            @ToolParam(required = false, description = "状态：0 草稿 / 1 发布") Integer status) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (title != null && !title.isBlank()) {
            body.put("title", title);
        }
        if (content != null && !content.isBlank()) {
            body.put("content", content);
        }
        if (type != null && !type.isBlank()) {
            body.put("type", type);
        }
        if (status != null) {
            body.put("status", status);
        }
        if (body.isEmpty()) {
            throw new BusinessApiException(400, "请至少提供一个要修改的字段");
        }
        return json(result("PUT /api/messages/" + messageId, client.put(token, "/api/messages/" + messageId, body)));
    }

    @Tool(name = "deleteMessage", description = "删除消息：管理员删除全局消息；普通用户删除发给自己的消息。调用前请与用户确认。")
    public String deleteMessage(@ToolParam(description = "消息 ID") long messageId) {
        return json(result("DELETE /api/messages/" + messageId, client.delete(token, "/api/messages/" + messageId)));
    }

    @Tool(name = "markAllMessagesRead", description = "把当前登录人的所有消息标记为已读。")
    public String markAllMessagesRead() {
        return json(result("POST /api/messages/read-all", client.post(token, "/api/messages/read-all", null)));
    }

    // ==================== 节假日 / 空闲时间 ====================

    @Tool(name = "deleteHoliday", description = "删除节假日配置（仅管理员，注意：删除不会恢复此前因放假被删除的排班）。调用前请与用户确认。")
    public String deleteHoliday(@ToolParam(description = "节假日 ID（可先用 holidayList 查询）") long holidayId) {
        return json(result("DELETE /api/holidays/" + holidayId, client.delete(token, "/api/holidays/" + holidayId)));
    }

    @Tool(name = "saveMyAvailability", description = "保存当前登录人的空闲时间（覆盖式：会先清空原有空闲时间再写入）。"
            + "slots 格式：`单双周:星期:时段ID` 多项用逗号分隔，例如 `odd:1:1,both:5:2`；"
            + "单双周取 odd/even/both，星期取 1-7，时段 ID 可先用 myAvailability 或查询时段列表获得。调用前请与用户确认。")
    public String saveMyAvailability(@ToolParam(description = "空闲时间列表，如 odd:1:1,both:5:2") String slots) {
        List<Map<String, Object>> payload = parseSlots(slots);
        return json(result("POST /api/availabilities/batch", client.post(token, "/api/availabilities/batch", payload)));
    }

    @Tool(name = "deleteAvailability", description = "删除一条空闲时间记录（管理员可删任意，普通用户仅本人）。调用前请与用户确认。")
    public String deleteAvailability(@ToolParam(description = "空闲时间记录 ID") long availabilityId) {
        return json(result("DELETE /api/availabilities/" + availabilityId,
                client.delete(token, "/api/availabilities/" + availabilityId)));
    }

    // ==================== 用户 ====================

    @Tool(name = "createUser", description = "创建用户（仅管理员）：username 必填且唯一；role 传 admin/user，status 传 0(禁用)/1(启用)，gender 传 男/女。"
            + "调用前请与用户确认账号信息。")
    public String createUser(
            @ToolParam(description = "登录账号（唯一）") String username,
            @ToolParam(required = false, description = "密码（明文，服务端加密存储；留空则需后续设置）") String password,
            @ToolParam(required = false, description = "真实姓名") String realName,
            @ToolParam(required = false, description = "角色：admin 或 user") String role,
            @ToolParam(required = false, description = "值班岗位：巡班/坐班/敲灯") String dutyRole,
            @ToolParam(required = false, description = "班级") String className,
            @ToolParam(required = false, description = "部门") String department,
            @ToolParam(required = false, description = "性别：男/女") String gender,
            @ToolParam(required = false, description = "状态：0 禁用 / 1 启用") Integer status) {
        if (username == null || username.isBlank()) {
            throw new BusinessApiException(400, "用户名不能为空");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        putIfPresent(body, "password", password);
        putIfPresent(body, "realName", realName);
        putIfPresent(body, "role", role);
        putIfPresent(body, "dutyRole", dutyRole);
        putIfPresent(body, "className", className);
        putIfPresent(body, "department", department);
        putIfPresent(body, "gender", gender);
        if (status != null) {
            body.put("status", status);
        }
        return json(result("POST /api/users", client.post(token, "/api/users", body)));
    }

    @Tool(name = "updateUser", description = "修改用户信息（仅管理员，部分更新）：只传需要修改的字段；password 传明文则重置密码。调用前请与用户确认。")
    public String updateUser(
            @ToolParam(description = "用户 ID") long userId,
            @ToolParam(required = false, description = "新密码（明文）") String password,
            @ToolParam(required = false, description = "真实姓名") String realName,
            @ToolParam(required = false, description = "角色：admin 或 user") String role,
            @ToolParam(required = false, description = "值班岗位：巡班/坐班/敲灯") String dutyRole,
            @ToolParam(required = false, description = "状态：0 禁用 / 1 启用") Integer status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", userId);
        putIfPresent(body, "password", password);
        putIfPresent(body, "realName", realName);
        putIfPresent(body, "role", role);
        putIfPresent(body, "dutyRole", dutyRole);
        if (status != null) {
            body.put("status", status);
        }
        return json(result("PUT /api/users", client.put(token, "/api/users", body)));
    }

    @Tool(name = "deleteUser", description = "删除用户（仅管理员，硬删除且无级联清理，排班/请假等记录仍会引用该 ID）。调用前必须与用户确认。")
    public String deleteUser(@ToolParam(description = "用户 ID") long userId) {
        return json(result("DELETE /api/users/" + userId, client.delete(token, "/api/users/" + userId)));
    }

    @Tool(name = "batchDeleteUsers", description = "批量删除用户（仅管理员，硬删除）。userIds 传逗号分隔的用户 ID。调用前必须与用户确认。")
    public String batchDeleteUsers(@ToolParam(description = "用户 ID 列表，逗号分隔，如 12,13") String userIds) {
        List<Long> ids = parseIds(userIds);
        if (ids.isEmpty()) {
            throw new BusinessApiException(400, "请提供要删除的用户 ID");
        }
        return json(result("POST /api/users/batch-delete", client.post(token, "/api/users/batch-delete", ids)));
    }

    // ==================== 排班补充 / 纪律 / 消息补充 ====================

    @Tool(name = "createSchedule", description = "手工新增单条排班（仅管理员）：userId 为值班人 ID，dutyDate 为日期 yyyy-MM-dd，timeSlot 为时段（如 巡班）。调用前请与用户确认。")
    public String createSchedule(
            @ToolParam(description = "类型：dormitory 或 office") String type,
            @ToolParam(description = "地点 ID（楼栋或办公室）") long locationId,
            @ToolParam(description = "值班人用户 ID") long userId,
            @ToolParam(description = "值班日期 yyyy-MM-dd") String dutyDate,
            @ToolParam(description = "时段，如 巡班/坐班/敲灯") String timeSlot,
            @ToolParam(required = false, description = "地点名称") String locationName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("locationId", locationId);
        body.put("userId", userId);
        body.put("dutyDate", dutyDate);
        body.put("timeSlot", timeSlot);
        putIfPresent(body, "locationName", locationName);
        return json(result("POST /api/schedules", client.post(token, "/api/schedules", body)));
    }

    @Tool(name = "addDisciplineRecord", description = "新增纪律记录（仅管理员）：recordType 传 late(迟到) 或 absent(缺岗)。调用前请与用户确认。")
    public String addDisciplineRecord(
            @ToolParam(description = "用户 ID") long userId,
            @ToolParam(description = "类型：late 或 absent") String recordType,
            @ToolParam(required = false, description = "值班日期 yyyy-MM-dd") String dutyDate,
            @ToolParam(required = false, description = "备注") String note) {
        if (!"late".equals(recordType) && !"absent".equals(recordType)) {
            throw new BusinessApiException(400, "无效记录类型：" + recordType + "（只能是 late 或 absent）");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("recordType", recordType);
        putIfPresent(body, "dutyDate", dutyDate);
        putIfPresent(body, "note", note);
        return json(result("POST /api/discipline", client.post(token, "/api/discipline", body)));
    }

    @Tool(name = "deleteDisciplineRecord", description = "删除纪律记录（仅管理员）。调用前请与用户确认。")
    public String deleteDisciplineRecord(@ToolParam(description = "纪律记录 ID") long recordId) {
        return json(result("DELETE /api/discipline/" + recordId, client.delete(token, "/api/discipline/" + recordId)));
    }

    @Tool(name = "markMessageRead", description = "把指定消息标记为已读（当前登录人）。")
    public String markMessageRead(@ToolParam(description = "消息 ID") long messageId) {
        return json(result("POST /api/messages/" + messageId + "/read",
                client.post(token, "/api/messages/" + messageId + "/read", null)));
    }

    @Tool(name = "saveMessageDraft", description = "保存消息草稿（仅管理员，按人 upsert 单条草稿）。")
    public String saveMessageDraft(
            @ToolParam(required = false, description = "草稿标题") String title,
            @ToolParam(required = false, description = "草稿内容") String content,
            @ToolParam(required = false, description = "类型：duty/approval/system/urgent") String type) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, "title", title);
        putIfPresent(body, "content", content);
        putIfPresent(body, "type", type);
        return json(result("POST /api/messages/draft", client.post(token, "/api/messages/draft", body)));
    }

    @Tool(name = "deleteMessageDraft", description = "删除本人消息草稿（仅管理员）。")
    public String deleteMessageDraft() {
        return json(result("DELETE /api/messages/draft", client.delete(token, "/api/messages/draft")));
    }

    // ==================== 基础数据与配置 ====================

    @Tool(name = "manageDormitory", description = "宿舍楼管理（仅管理员）：action 传 create/update/delete；update/delete 需 id；create 需 code 与 name。破坏性操作（delete 会级联删除该楼排班）需先与用户确认。")
    public String manageDormitory(
            @ToolParam(description = "操作：create / update / delete") String action,
            @ToolParam(required = false, description = "楼栋 ID（update/delete 必填）") Long id,
            @ToolParam(required = false, description = "楼栋编码（create 必填）") String code,
            @ToolParam(required = false, description = "楼栋名称（create 必填）") String name,
            @ToolParam(required = false, description = "所属区域") String building,
            @ToolParam(required = false, description = "楼层数") Integer floor,
            @ToolParam(required = false, description = "性别：男/女") String gender,
            @ToolParam(required = false, description = "状态：0 停用 / 1 启用") Integer status) {
        Map<String, Object> body = new LinkedHashMap<>();
        String normalized = normalizeAction(action);
        if (!"create".equals(normalized)) {
            if (id == null) {
                throw new BusinessApiException(400, "update/delete 需要提供 id");
            }
            body.put("id", id);
        } else {
            if (code == null || code.isBlank() || name == null || name.isBlank()) {
                throw new BusinessApiException(400, "create 需要提供 code 与 name");
            }
            body.put("code", code);
            body.put("name", name);
        }
        putIfPresent(body, "building", building);
        if (floor != null) {
            body.put("floor", floor);
        }
        putIfPresent(body, "gender", gender);
        if (status != null) {
            body.put("status", status);
        }
        if ("delete".equals(normalized)) {
            return json(result("DELETE /api/dormitories/" + id, client.delete(token, "/api/dormitories/" + id)));
        }
        if ("create".equals(normalized)) {
            return json(result("POST /api/dormitories", client.post(token, "/api/dormitories", body)));
        }
        return json(result("PUT /api/dormitories", client.put(token, "/api/dormitories", body)));
    }

    @Tool(name = "manageOffice", description = "办公室管理（仅管理员）：action 传 create/update/delete；update/delete 需 id；create 需 code 与 name。delete 会级联删除该办公室排班，需先与用户确认。")
    public String manageOffice(
            @ToolParam(description = "操作：create / update / delete") String action,
            @ToolParam(required = false, description = "办公室 ID（update/delete 必填）") Long id,
            @ToolParam(required = false, description = "编码（create 必填）") String code,
            @ToolParam(required = false, description = "名称（create 必填）") String name,
            @ToolParam(required = false, description = "所属楼宇") String building,
            @ToolParam(required = false, description = "楼层（字符串）") String floor,
            @ToolParam(required = false, description = "状态：0 停用 / 1 启用") Integer status) {
        Map<String, Object> body = new LinkedHashMap<>();
        String normalized = normalizeAction(action);
        if (!"create".equals(normalized)) {
            if (id == null) {
                throw new BusinessApiException(400, "update/delete 需要提供 id");
            }
            body.put("id", id);
        } else {
            if (code == null || code.isBlank() || name == null || name.isBlank()) {
                throw new BusinessApiException(400, "create 需要提供 code 与 name");
            }
            body.put("code", code);
            body.put("name", name);
        }
        putIfPresent(body, "building", building);
        putIfPresent(body, "floor", floor);
        if (status != null) {
            body.put("status", status);
        }
        if ("delete".equals(normalized)) {
            return json(result("DELETE /api/offices/" + id, client.delete(token, "/api/offices/" + id)));
        }
        if ("create".equals(normalized)) {
            return json(result("POST /api/offices", client.post(token, "/api/offices", body)));
        }
        return json(result("PUT /api/offices", client.put(token, "/api/offices", body)));
    }

    @Tool(name = "manageTimeSlot", description = "时间段管理（仅管理员）：action 传 create/update/delete；update/delete 需 id；create 需 label。调用前请与用户确认。")
    public String manageTimeSlot(
            @ToolParam(description = "操作：create / update / delete") String action,
            @ToolParam(required = false, description = "时段 ID（update/delete 必填）") Long id,
            @ToolParam(required = false, description = "时段名称（create 必填），如 巡班(19:00-21:00)") String label,
            @ToolParam(required = false, description = "开始时间 HH:mm") String startTime,
            @ToolParam(required = false, description = "结束时间 HH:mm") String endTime,
            @ToolParam(required = false, description = "时段分类，如 上午/下午/晚") String period,
            @ToolParam(required = false, description = "排序号") Integer sortOrder,
            @ToolParam(required = false, description = "状态：0 停用 / 1 启用") Integer status) {
        Map<String, Object> body = new LinkedHashMap<>();
        String normalized = normalizeAction(action);
        if (!"create".equals(normalized)) {
            if (id == null) {
                throw new BusinessApiException(400, "update/delete 需要提供 id");
            }
            body.put("id", id);
        } else if (label == null || label.isBlank()) {
            throw new BusinessApiException(400, "create 需要提供 label");
        }
        putIfPresent(body, "label", label);
        putIfPresent(body, "startTime", startTime);
        putIfPresent(body, "endTime", endTime);
        putIfPresent(body, "period", period);
        if (sortOrder != null) {
            body.put("sortOrder", sortOrder);
        }
        if (status != null) {
            body.put("status", status);
        }
        if ("delete".equals(normalized)) {
            return json(result("DELETE /api/time-slots/" + id, client.delete(token, "/api/time-slots/" + id)));
        }
        if ("create".equals(normalized)) {
            return json(result("POST /api/time-slots", client.post(token, "/api/time-slots", body)));
        }
        return json(result("PUT /api/time-slots", client.put(token, "/api/time-slots", body)));
    }

    @Tool(name = "saveSemester", description = "保存学期配置（仅管理员，upsert）：semesterId 为空则新增、有值则更新；startDate 为学期开始日期 yyyy-MM-dd，totalWeeks 默认 18。调用前请与用户确认。")
    public String saveSemester(
            @ToolParam(required = false, description = "学期 ID（更新时传）") Long semesterId,
            @ToolParam(required = false, description = "学期名称") String name,
            @ToolParam(description = "开始日期 yyyy-MM-dd") String startDate,
            @ToolParam(required = false, description = "总周数，默认 18") Integer totalWeeks) {
        if (startDate == null || startDate.isBlank()) {
            throw new BusinessApiException(400, "请提供学期开始日期");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (semesterId != null) {
            body.put("id", semesterId);
        }
        putIfPresent(body, "name", name);
        body.put("startDate", startDate);
        body.put("totalWeeks", totalWeeks == null ? 18 : totalWeeks);
        return json(result("POST /api/semester", client.post(token, "/api/semester", body)));
    }

    @Tool(name = "updateOfficeSlotCapacity", description = "设置办公室每节课人数上限（仅管理员，1~20）。调用前请与用户确认。")
    public String updateOfficeSlotCapacity(@ToolParam(description = "人数上限 1~20") int capacity) {
        if (capacity < 1 || capacity > 20) {
            throw new BusinessApiException(400, "每节课人数必须为 1~20 的整数");
        }
        return json(result("PUT /api/office-schedule-config",
                client.put(token, "/api/office-schedule-config", Map.of("slotCapacity", capacity))));
    }

    @Tool(name = "saveDormIdentityPermission", description = "保存宿舍岗位身份权限矩阵（仅管理员，覆盖式）："
            + "matrix 格式 `身份:巡班:坐班:敲灯`（0/1），多项逗号分隔，例如 `巡班:1:1:1,坐班:1:0:0,敲灯:1:1:1`。调用前请与用户确认。")
    public String saveDormIdentityPermission(@ToolParam(description = "权限矩阵，如 巡班:1:1:1,坐班:1:0:0") String matrix) {
        List<Map<String, Object>> payload = parseIdentityMatrix(matrix);
        return json(result("PUT /api/dorm-identity-permission",
                client.put(token, "/api/dorm-identity-permission", payload)));
    }

    @Tool(name = "manageDutyAdjustment", description = "调休配置管理（仅管理员）：action 传 create/delete；create 需 startDate、endDate，"
            + "makeups 格式 `补课日=被替换日` 多项逗号分隔（如 `2026-09-27=2026-10-05`）；delete 需 id。调用前请与用户确认。")
    public String manageDutyAdjustment(
            @ToolParam(description = "操作：create / delete") String action,
            @ToolParam(required = false, description = "调休配置 ID（delete 必填）") Long id,
            @ToolParam(required = false, description = "开始日期 yyyy-MM-dd（create 必填）") String startDate,
            @ToolParam(required = false, description = "结束日期 yyyy-MM-dd（create 必填）") String endDate,
            @ToolParam(required = false, description = "备注") String note,
            @ToolParam(required = false, description = "补课映射，如 2026-09-27=2026-10-05") String makeups) {
        if ("delete".equals(normalizeAction(action))) {
            if (id == null) {
                throw new BusinessApiException(400, "delete 需要提供 id");
            }
            return json(result("DELETE /api/duty-adjustments/" + id,
                    client.delete(token, "/api/duty-adjustments/" + id)));
        }
        if (startDate == null || startDate.isBlank() || endDate == null || endDate.isBlank()) {
            throw new BusinessApiException(400, "create 需要提供 startDate 与 endDate");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startDate", startDate);
        body.put("endDate", endDate);
        putIfPresent(body, "note", note);
        body.put("makeups", parseMakeups(makeups));
        return json(result("POST /api/duty-adjustments", client.post(token, "/api/duty-adjustments", body)));
    }

    // ==================== 内部实现 ====================

    private String normalizeAction(String action) {
        if (action == null) {
            throw new BusinessApiException(400, "请指定操作：create/update/delete");
        }
        String value = action.trim().toLowerCase();
        return switch (value) {
            case "create", "新增", "添加" -> "create";
            case "update", "修改", "更新" -> "update";
            case "delete", "删除" -> "delete";
            default -> throw new BusinessApiException(400, "无效操作：" + action + "（create/update/delete）");
        };
    }

    private List<Map<String, Object>> parseIdentityMatrix(String matrix) {
        if (matrix == null || matrix.isBlank()) {
            throw new BusinessApiException(400, "请提供权限矩阵，如 巡班:1:1:1,坐班:1:0:0");
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        for (String item : matrix.split(",")) {
            String[] parts = item.trim().split(":");
            if (parts.length != 4) {
                throw new BusinessApiException(400, "权限矩阵格式不正确：" + item + "（应为 身份:巡班:坐班:敲灯）");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("identity", parts[0].trim());
            row.put("allowPatrol", parseFlag(parts[1]));
            row.put("allowDuty", parseFlag(parts[2]));
            row.put("allowKnock", parseFlag(parts[3]));
            payload.add(row);
        }
        return payload;
    }

    private int parseFlag(String value) {
        String v = value.trim();
        if (!"0".equals(v) && !"1".equals(v)) {
            throw new BusinessApiException(400, "权限取值只能是 0 或 1：" + value);
        }
        return Integer.parseInt(v);
    }

    private List<Map<String, Object>> parseMakeups(String makeups) {
        List<Map<String, Object>> payload = new ArrayList<>();
        if (makeups == null || makeups.isBlank()) {
            return payload;
        }
        for (String item : makeups.split(",")) {
            String[] parts = item.trim().split("=");
            if (parts.length != 2) {
                throw new BusinessApiException(400, "补课映射格式不正确：" + item + "（应为 补课日=被替换日）");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("makeupDate", parts[0].trim());
            row.put("replacedDate", parts[1].trim());
            payload.add(row);
        }
        return payload;
    }

    private void requireRange(String startDate, String endDate) {
        if (startDate == null || startDate.isBlank() || endDate == null || endDate.isBlank()) {
            throw new BusinessApiException(400, "请提供开始与结束日期（yyyy-MM-dd）");
        }
    }

    private String normalizeApproval(String status) {
        if (status == null) {
            throw new BusinessApiException(400, "请指定审批结果：approved 或 rejected");
        }
        String value = status.trim().toLowerCase();
        if (value.startsWith("approve") || value.equals("通过") || value.equals("批准")) {
            return "approved";
        }
        if (value.startsWith("reject") || value.equals("驳回") || value.equals("拒绝")) {
            return "rejected";
        }
        throw new BusinessApiException(400, "无效的审批结果：" + status + "（只能是 approved 或 rejected）");
    }

    private String normalizeLeaveType(String leaveType) {
        if (leaveType == null || leaveType.isBlank()) {
            throw new BusinessApiException(400, "请指定请假类型：sick/personal/annual/compensatory");
        }
        return switch (leaveType.trim()) {
            case "病假" -> "sick";
            case "事假" -> "personal";
            case "年假" -> "annual";
            case "调休", "补休" -> "compensatory";
            default -> leaveType.trim().toLowerCase();
        };
    }

    private List<Long> parseIds(String csv) {
        List<Long> ids = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return ids;
        }
        for (String part : csv.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                try {
                    ids.add(Long.parseLong(value));
                } catch (NumberFormatException e) {
                    throw new BusinessApiException(400, "ID 格式不正确：" + value);
                }
            }
        }
        return ids;
    }

    private List<Map<String, Object>> parseSlots(String slots) {
        if (slots == null || slots.isBlank()) {
            throw new BusinessApiException(400, "请提供空闲时间（空字符串表示清空全部，如需清空请传 clear）");
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        for (String item : slots.split(",")) {
            String[] parts = item.trim().split(":");
            if (parts.length != 3) {
                throw new BusinessApiException(400, "空闲时间格式不正确：" + item + "（应为 单双周:星期:时段ID）");
            }
            String parity = parts[0].trim().toLowerCase();
            if (!parity.equals("odd") && !parity.equals("even") && !parity.equals("both")) {
                throw new BusinessApiException(400, "单双周取值不正确：" + parts[0] + "（odd/even/both）");
            }
            int dayOfWeek;
            long timeSlotId;
            try {
                dayOfWeek = Integer.parseInt(parts[1].trim());
                timeSlotId = Long.parseLong(parts[2].trim());
            } catch (NumberFormatException e) {
                throw new BusinessApiException(400, "星期或时段 ID 格式不正确：" + item);
            }
            if (dayOfWeek < 1 || dayOfWeek > 7) {
                throw new BusinessApiException(400, "星期取值应为 1-7：" + item);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("weekParity", parity);
            row.put("dayOfWeek", dayOfWeek);
            row.put("timeSlotId", timeSlotId);
            payload.add(row);
        }
        return payload;
    }

    private void putIfPresent(Map<String, Object> body, String key, String value) {
        if (value != null && !value.isBlank()) {
            body.put(key, value);
        }
    }

    private Map<String, Object> result(String source, JsonNode data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", source);
        result.put("ok", true);
        result.put("data", data == null || data.isNull() ? null : mapper.convertValue(data, Object.class));
        return result;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessApiException(500, "结果序列化失败：" + e.getMessage());
        }
    }
}
