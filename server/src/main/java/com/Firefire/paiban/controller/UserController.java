package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.dto.ImportProgress;
import com.Firefire.paiban.entity.*;
import com.Firefire.paiban.mapper.DutyScheduleMapper;
import com.Firefire.paiban.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final DutyScheduleService dutyScheduleService;
    private final DormitoryService dormitoryService;
    private final PasswordEncoder passwordEncoder;
    private final LeaveRequestService leaveRequestService;
    private final MultiDutyRequestService multiDutyRequestService;
    private final UserAvailabilityService userAvailabilityService;
    private final DutyScheduleMapper dutyScheduleMapper;
    private final DormIdentityPermissionService dormIdentityPermissionService;

    /** 导入任务进度存储（内存态） */
    private static final Map<String, ImportProgress> IMPORT_TASKS = new ConcurrentHashMap<>();
    /** 导入后台线程池：避免大数据量导入阻塞请求线程、也避免前端请求超时 */
    private static final ExecutorService IMPORT_EXECUTOR = Executors.newFixedThreadPool(2);
    /** 查重/插入每批大小（内存只跟文件行数挂钩，不加载整张用户表） */
    private static final int IMPORT_CHUNK = 500;
    /** 失败行明细最多保留条数 */
    private static final int MAX_ERRORS = 100;
    /** 已完成任务保留时长（毫秒） */
    private static final long TASK_KEEP_MS = 10 * 60 * 1000L;

    @GetMapping
    public Result<List<User>> list(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like(User::getUsername, keyword)
                .or().like(User::getRealName, keyword)
                .or().like(User::getClassName, keyword)
                .or().like(User::getDepartment, keyword)
            );
        }
        wrapper.orderByAsc(User::getId);
        return Result.success(userService.list(wrapper));
    }

    @GetMapping("/page")
    public Result<Page<User>> page(@RequestParam(defaultValue = "1") Integer current,
                                    @RequestParam(defaultValue = "50") Integer size,
                                    @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like(User::getUsername, keyword)
                .or().like(User::getRealName, keyword)
                .or().like(User::getClassName, keyword)
                .or().like(User::getDepartment, keyword)
            );
        }
        wrapper.orderByAsc(User::getId);
        Page<User> page = new Page<>(current, size);
        return Result.success(userService.page(page, wrapper));
    }

    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    @PostMapping
    public Result<Void> save(@RequestBody User user) {
        userService.save(user);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody User user) {
        userService.updateById(user);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.removeById(id);
        return Result.success();
    }

    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.success();
        }
        userService.removeByIds(ids);
        return Result.success();
    }

    /**
     * 导入用户：改为"提交后台任务"模式，立即返回任务ID，避免大数据量导入时前端请求超时。
     * 前端凭任务ID轮询进度接口获取实时进度与最终结果。
     */
    @PostMapping("/import/excel")
    public Result<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("请选择要导入的文件");
        }
        cleanupOldTasks();

        // 关键：MultipartFile 的临时文件在请求结束后会被 Spring 清理，
        // 后台任务必须读磁盘上的副本，否则解析时找不到文件
        File tmpFile;
        try {
            tmpFile = File.createTempFile("paiban_import_", ".xlsx");
            file.transferTo(tmpFile);
        } catch (IOException e) {
            return Result.error("文件接收失败: " + e.getMessage());
        }

        String taskId = UUID.randomUUID().toString().substring(0, 12);
        ImportProgress progress = new ImportProgress();
        progress.setTaskId(taskId);
        progress.setStatus("running");
        progress.setStartTime(System.currentTimeMillis());
        IMPORT_TASKS.put(taskId, progress);

        IMPORT_EXECUTOR.submit(() -> doImport(taskId, tmpFile));

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        return Result.success(result);
    }

    /** 查询导入进度（任务不存在时返回 null，避免前端轮询报错刷屏） */
    @GetMapping("/import/progress/{taskId}")
    public Result<ImportProgress> importProgress(@PathVariable String taskId) {
        return Result.success(IMPORT_TASKS.get(taskId));
    }

    /** 后台执行导入：解析 → 分批查重 → 分批批量插入，全程更新进度 */
    private void doImport(String taskId, File tmpFile) {
        ImportProgress progress = IMPORT_TASKS.get(taskId);
        int success = 0, skip = 0, fail = 0;
        List<String> errors = new ArrayList<>();
        List<User> validUsers = new ArrayList<>();

        // 宿舍楼编码 → ID 映射（一次查出，内存只跟宿舍楼数量挂钩）
        Map<String, Long> codeToDormId = dormitoryService.list()
            .stream().collect(Collectors.toMap(Dormitory::getCode, Dormitory::getId));

        // ① 解析文件
        try (InputStream in = new FileInputStream(tmpFile); Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            progress.setTotal(lastRow);
            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                try {
                    if (row == null) { continue; }
                    String username = getCellStr(row, 0);
                    String realName = getCellStr(row, 1);
                    if (username == null || username.isBlank() || realName == null || realName.isBlank()) {
                        skip++;
                        continue;
                    }
                    User user = new User();
                    user.setUsername(username);
                    user.setPassword("123456");
                    user.setRealName(realName);
                    user.setRole("user");
                    user.setStatus(1);
                    user.setGender(getCellStr(row, 2));
                    user.setDepartment(getCellStr(row, 3));
                    user.setDutyRole(getCellStr(row, 4));
                    user.setPhone(getCellStr(row, 5));
                    String dormCode = getCellStr(row, 6);
                    if (dormCode != null) user.setDormitoryId(codeToDormId.get(dormCode.toUpperCase()));
                    user.setClassName(getCellStr(row, 7));
                    validUsers.add(user);
                } catch (Exception e) {
                    fail++;
                    addError(errors, "第" + (i + 1) + "行: " + e.getMessage());
                } finally {
                    progress.setProcessed(progress.getProcessed() + 1);
                    progress.setSkip(skip);
                    progress.setFail(fail);
                }
            }
        } catch (Exception e) {
            progress.setStatus("error");
            progress.setMessage("文件解析失败: " + e.getMessage());
            progress.setEndTime(System.currentTimeMillis());
            return;
        } finally {
            // 解析完即删除临时文件，防止磁盘残留
            tmpFile.delete();
        }

        // ② 分批 IN 查重：只查出文件中已存在的账号，内存只跟文件行数挂钩
        if (!validUsers.isEmpty()) {
            Set<String> existing = new HashSet<>();
            List<String> names = validUsers.stream().map(User::getUsername).collect(Collectors.toList());
            for (int from = 0; from < names.size(); from += IMPORT_CHUNK) {
                List<String> chunk = names.subList(from, Math.min(from + IMPORT_CHUNK, names.size()));
                userService.list(new LambdaQueryWrapper<User>()
                        .in(User::getUsername, chunk)
                        .select(User::getUsername))
                    .forEach(u -> existing.add(u.getUsername()));
            }
            List<User> toInsert = validUsers.stream()
                .filter(u -> !existing.contains(u.getUsername()))
                .collect(Collectors.toList());
            skip += validUsers.size() - toInsert.size();
            progress.setSkip(skip);

            // ③ 分批批量插入；整批失败时回退逐条，保证单行出错不影响其余行
            // 注意：saveBatch 不走 UserServiceImpl.save() 的密码加密，需先统一加密
            String encodedPwd = passwordEncoder.encode("123456");
            for (User u : toInsert) u.setPassword(encodedPwd);
            for (int from = 0; from < toInsert.size(); from += IMPORT_CHUNK) {
                List<User> chunk = toInsert.subList(from, Math.min(from + IMPORT_CHUNK, toInsert.size()));
                try {
                    userService.saveBatch(chunk);
                    success += chunk.size();
                    progress.setSuccess(success);
                } catch (Exception e) {
                    for (User u : chunk) {
                        try {
                            userService.save(u);
                            success++;
                            progress.setSuccess(success);
                        } catch (Exception e2) {
                            fail++;
                            addError(errors, "账号 " + u.getUsername() + ": " + e2.getMessage());
                            progress.setFail(fail);
                        }
                    }
                }
            }
        }

        progress.setSuccess(success);
        progress.setSkip(skip);
        progress.setFail(fail);
        progress.setErrors(errors);
        progress.setStatus("done");
        progress.setEndTime(System.currentTimeMillis());
    }

    /** 失败明细限量，避免大数据量错误时撑爆内存/响应体 */
    private void addError(List<String> errors, String msg) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(msg);
        }
    }

    /** 清理过期任务，防止内存持续增长 */
    private void cleanupOldTasks() {
        long now = System.currentTimeMillis();
        IMPORT_TASKS.entrySet().removeIf(e -> e.getValue().getEndTime() > 0
            && now - e.getValue().getEndTime() > TASK_KEEP_MS);
    }

    private String getCellStr(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        String v;
        switch (cell.getCellType()) {
            case STRING:  v = cell.getStringCellValue(); break;
            case NUMERIC: v = new DecimalFormat("0").format(cell.getNumericCellValue()); break;
            case BOOLEAN: v = String.valueOf(cell.getBooleanCellValue()); break;
            case FORMULA: v = cell.getCellFormula(); break;
            default:      v = "";
        }
        String t = (v == null) ? "" : v.trim();
        return t.isEmpty() ? null : t;
    }

    @GetMapping("/staff")
    public Result<List<User>> getStaffList() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getStatus, 1);
        wrapper.select(User::getId, User::getUsername, User::getRealName, User::getDepartment, User::getGender, User::getDormitoryId, User::getDutyRole, User::getClassName);
        return Result.success(userService.list(wrapper));
    }

    @GetMapping("/staff-with-counts")
    public Result<List<Map<String, Object>>> getStaffWithCounts(@RequestParam String type) {
        List<User> staff = getStaffList().getData();
        // 值班次数：数据库端聚合（巡班按天去重），避免整表拉入内存
        Map<Long, Map<String, Long>> counts = new HashMap<>();
        for (Map<String, Object> row : dutyScheduleMapper.countByType(type)) {
            Long uid = ((Number) row.get("user_id")).longValue();
            String slot = (String) row.get("time_slot");
            long cnt = ((Number) row.get("cnt")).longValue();
            counts.computeIfAbsent(uid, k -> new HashMap<>()).merge(slot, cnt, Long::sum);
        }
        // 已批准请假的日期区间（供前端手动添加时过滤"当天请假"的人员）
        Map<Long, List<Map<String, Object>>> leaveByUser = new HashMap<>();
        for (LeaveRequest l : leaveRequestService.list(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getStatus, "approved"))) {
            if (l.getStartDate() == null) continue;
            Map<String, Object> range = new HashMap<>();
            range.put("start", l.getStartDate().toString());
            range.put("end", (l.getEndDate() != null ? l.getEndDate() : l.getStartDate()).toString());
            leaveByUser.computeIfAbsent(l.getUserId(), k -> new ArrayList<>()).add(range);
        }
        // 多排申请已通过（供前端优先显示+打标签）
        Map<Long, List<Map<String, Object>>> multiByUser = new HashMap<>();
        for (MultiDutyRequest md : multiDutyRequestService.list(new LambdaQueryWrapper<MultiDutyRequest>()
                .eq(MultiDutyRequest::getType, type)
                .eq(MultiDutyRequest::getStatus, "approved"))) {
            Map<String, Object> m = new HashMap<>();
            m.put("weekStart", md.getWeekStart());
            m.put("weekEnd", md.getWeekEnd());
            multiByUser.computeIfAbsent(md.getUserId(), k -> new ArrayList<>()).add(m);
        }
        // 空闲时段（供办公室手动添加下拉框判断"该时段有没有空"；宿舍排班不用）
        Map<Long, List<Map<String, Object>>> availByUser = new HashMap<>();
        if ("office".equals(type)) {
            for (UserAvailability ua : userAvailabilityService.list()) {
                Map<String, Object> a = new HashMap<>();
                a.put("dayOfWeek", ua.getDayOfWeek());
                a.put("timeSlotId", ua.getTimeSlotId());
                a.put("weekParity", ua.getWeekParity());
                availByUser.computeIfAbsent(ua.getUserId(), k -> new ArrayList<>()).add(a);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        // 宿舍排班：附上"该值班身份可顶岗位集"(身份权限)，供前端手动添加下拉过滤
        Map<String, java.util.Set<String>> allowedByIdentity =
                "dormitory".equals(type) ? dormIdentityPermissionService.allowedByIdentity() : java.util.Collections.emptyMap();
        for (User user : staff) {
            Map<String, Long> userCounts = counts.getOrDefault(user.getId(), Collections.emptyMap());
            long total = userCounts.values().stream().mapToLong(Long::longValue).sum();
            Map<String, Object> item = new HashMap<>();
            item.put("id", user.getId());
            item.put("realName", user.getRealName());
            item.put("username", user.getUsername());
            item.put("gender", user.getGender());
            item.put("dutyRole", user.getDutyRole());
            item.put("totalCount", total);
            item.put("detailCounts", userCounts);
            item.put("leaveRanges", leaveByUser.getOrDefault(user.getId(), Collections.emptyList()));
            item.put("multiDuty", multiByUser.getOrDefault(user.getId(), Collections.emptyList()));
            item.put("availability", availByUser.getOrDefault(user.getId(), Collections.emptyList()));
            item.put("allowedSlots", allowedByIdentity.getOrDefault(user.getDutyRole(), Collections.emptySet()));
            result.add(item);
        }
        result.sort(Comparator.comparingLong(m -> (Long) m.get("totalCount")));
        return Result.success(result);
    }

    @GetMapping("/staff-for-role")
    public Result<List<Map<String, Object>>> getStaffForRole(@RequestParam String roleType) {
        List<User> staff = getStaffList().getData();
        List<User> eligible;
        switch (roleType) {
            case "巡班":
                eligible = staff.stream().filter(u -> "巡班".equals(u.getDutyRole())).collect(Collectors.toList());
                break;
            case "坐班":
                eligible = staff.stream().filter(u -> "巡班".equals(u.getDutyRole()) || "坐班".equals(u.getDutyRole())).collect(Collectors.toList());
                break;
            default:
                eligible = staff;
                break;
        }
        return Result.success(eligible.stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("realName", u.getRealName());
            m.put("gender", u.getGender());
            m.put("dutyRole", u.getDutyRole());
            return m;
        }).collect(Collectors.toList()));
    }
}
