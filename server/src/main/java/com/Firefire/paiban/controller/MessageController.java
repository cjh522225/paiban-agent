package com.Firefire.paiban.controller;

import com.Firefire.paiban.common.Result;
import com.Firefire.paiban.entity.Message;
import com.Firefire.paiban.entity.MessageDraft;
import com.Firefire.paiban.entity.MessageRead;
import com.Firefire.paiban.entity.User;
import com.Firefire.paiban.service.MessageService;
import com.Firefire.paiban.service.MessageDraftService;
import com.Firefire.paiban.service.MessageReadService;
import com.Firefire.paiban.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final MessageDraftService messageDraftService;
    private final MessageReadService messageReadService;
    private final UserService userService;

    @Value("${upload.dir:uploads}")
    private String uploadDir;

    @GetMapping
    public Result<List<Message>> list(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");

        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        // 普通用户只能看到已发布且发给自己的消息（receivers为空=全体人员）
        if ("user".equals(role)) {
            wrapper.eq(Message::getStatus, 1);
            wrapper.and(w -> w.isNull(Message::getReceivers)
                .or().eq(Message::getReceivers, "")
                .or().apply("FIND_IN_SET({0}, receivers) > 0", userId));
            // 排除该用户个人已删除的消息（is_deleted=1）
            List<MessageRead> delRecs = messageReadService.list(
                new LambdaQueryWrapper<MessageRead>()
                    .eq(MessageRead::getUserId, userId)
                    .eq(MessageRead::getIsDeleted, 1));
            if (!delRecs.isEmpty()) {
                List<Long> delIds = delRecs.stream().map(MessageRead::getMessageId).toList();
                wrapper.notIn(Message::getId, delIds);
            }
        }
        wrapper.orderByDesc(Message::getCreateTime);
        List<Message> messages = messageService.list(wrapper);

        // 统计：管理员返回 viewCount/readCount/targetCount；用户返回 isRead
        enrichStats(messages, role, userId);
        return Result.success(messages);
    }

    // 给消息列表补充统计信息
    private void enrichStats(List<Message> messages, String role, Long userId) {
        if (messages == null || messages.isEmpty()) return;
        List<Long> ids = messages.stream().map(Message::getId).toList();

        // 一次性查所有相关 message_read
        List<MessageRead> reads = messageReadService.list(
            new LambdaQueryWrapper<MessageRead>().in(MessageRead::getMessageId, ids));

        // 按消息ID分组：messageId -> 该消息的所有已查看记录
        Map<Long, List<MessageRead>> byMsg = new HashMap<>();
        for (MessageRead r : reads) {
            byMsg.computeIfAbsent(r.getMessageId(), k -> new ArrayList<>()).add(r);
        }

        boolean isAdmin = "admin".equals(role);
        for (Message m : messages) {
            List<MessageRead> msgReads = byMsg.getOrDefault(m.getId(), Collections.emptyList());
            if (isAdmin) {
                m.setViewCount(msgReads.size());
                m.setReadCount((int) msgReads.stream().filter(r -> r.getIsRead() != null && r.getIsRead() == 1).count());
                m.setTargetCount(calcTargetCount(m));
            } else {
                // 用户端：是否已读
                MessageRead mine = msgReads.stream().filter(r -> r.getUserId().equals(userId)).findFirst().orElse(null);
                m.setIsRead(mine != null && mine.getIsRead() != null && mine.getIsRead() == 1 ? 1 : 0);
            }
        }
    }

    // 通知人数：全体=所有启用用户；自定义=receivers逗号分隔个数
    private int calcTargetCount(Message m) {
        String receivers = m.getReceivers();
        if (receivers == null || receivers.isBlank()) {
            // 全体人员 = 所有启用用户数（含 admin 和 user）
            return Math.toIntExact(userService.count(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 1)));
        }
        String[] ids = receivers.split(",");
        return ids.length;
    }

    // ========== 附件上传 ==========

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }
        // 校验格式
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf(".") + 1).toLowerCase();
        }
        if (!Set.of("jpg", "png", "pdf").contains(ext)) {
            return Result.error("仅支持 jpg/png/pdf 格式");
        }
        // 校验大小（≤5MB）
        if (file.getSize() > 5 * 1024 * 1024) {
            return Result.error("单个文件不能超过 5MB");
        }
        try {
            // 上传目录：upload.dir（相对路径基于工作目录解析）
            File base = new File(uploadDir);
            if (!base.isAbsolute()) {
                base = new File(System.getProperty("user.dir"), uploadDir);
            }
            File dirFile = new File(base, "msg");
            if (!dirFile.exists() && !dirFile.mkdirs()) {
                return Result.error("创建上传目录失败");
            }
            // 时间戳 + 随机数 + 扩展名，防文件名冲突
            String filename = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
            File dest = new File(dirFile, filename);
            file.transferTo(dest);
            return Result.success("/uploads/msg/" + filename);
        } catch (IOException e) {
            return Result.error("文件保存失败: " + e.getMessage());
        }
    }

    // ========== 草稿（独立 message_draft 表） ==========

    @GetMapping("/draft")
    public Result<MessageDraft> getDraft(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        MessageDraft draft = messageDraftService.getOne(
            new LambdaQueryWrapper<MessageDraft>().eq(MessageDraft::getSenderId, userId)
        );
        return Result.success(draft);
    }

    @PostMapping("/draft")
    public Result<Void> saveDraft(@RequestBody MessageDraft draft, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        draft.setSenderId(userId);
        messageDraftService.saveOrUpdate(draft,
            new LambdaQueryWrapper<MessageDraft>().eq(MessageDraft::getSenderId, userId)
        );
        return Result.success();
    }

    @DeleteMapping("/draft")
    public Result<Void> deleteDraft(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        messageDraftService.remove(
            new LambdaQueryWrapper<MessageDraft>().eq(MessageDraft::getSenderId, userId)
        );
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<Message> getById(@PathVariable Long id) {
        return Result.success(messageService.getById(id));
    }

    @PostMapping
    public Result<Void> save(@RequestBody Message message, HttpServletRequest request) {
        Long senderId = (Long) request.getAttribute("userId");
        message.setSenderId(senderId);
        // 发送人显示真实姓名，账号不能当姓名显示；查不到才退回登录账号
        User sender = userService.getById(senderId);
        message.setSenderName(sender != null && sender.getRealName() != null && !sender.getRealName().isBlank()
            ? sender.getRealName() : (String) request.getAttribute("username"));
        if (message.getStatus() == null) {
            message.setStatus(1);
        }
        messageService.save(message);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Message message) {
        // 编辑消息：通知对象（receivers）不可修改，强制保留原值
        Message old = messageService.getById(id);
        if (old == null) {
            return Result.error("消息不存在");
        }
        message.setId(id);
        message.setReceivers(old.getReceivers());
        messageService.updateById(message);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if ("user".equals(role)) {
            // 普通用户：个人删除——只校验该消息是否发给此用户，是则打删除标记（不影响其他用户）
            Long userId = (Long) request.getAttribute("userId");
            Message msg = messageService.getById(id);
            if (msg == null) return Result.success();
            // 校验可见性：receivers 为空=全体，否则须含该 userId
            boolean visible = msg.getReceivers() == null || msg.getReceivers().isBlank()
                || Arrays.stream(msg.getReceivers().split(",")).anyMatch(s -> s.trim().equals(String.valueOf(userId)));
            if (!visible) return Result.error("只能删除发给自己的消息");
            // 标记该用户已删除该消息（upsert message_read）
            markUserDeleted(msg.getId(), userId);
            return Result.success();
        }
        // 管理员：撤销发送，删除全局消息（保持现状）
        messageService.removeById(id);
        // 连带删除该消息的查看/已读记录
        messageReadService.remove(new LambdaQueryWrapper<MessageRead>().eq(MessageRead::getMessageId, id));
        return Result.success();
    }

    // 个人删除：在 message_read 中为该用户打 is_deleted=1 标记（无记录则插入一条）
    private void markUserDeleted(Long messageId, Long userId) {
        MessageRead existing = messageReadService.getOne(
            new LambdaQueryWrapper<MessageRead>()
                .eq(MessageRead::getMessageId, messageId)
                .eq(MessageRead::getUserId, userId)
        );
        if (existing != null) {
            existing.setIsDeleted(1);
            messageReadService.updateById(existing);
        } else {
            MessageRead mr = new MessageRead();
            mr.setMessageId(messageId);
            mr.setUserId(userId);
            mr.setIsDeleted(1);
            messageReadService.save(mr);
        }
    }

    // ========== 已查看 / 已读 ==========

    // 用户登录后标记所有未查看的新消息为已查看
    @PostMapping("/mark-viewed")
    public Result<Void> markViewed(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        // 该用户可见的已发布消息
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getStatus, 1);
        wrapper.and(w -> w.isNull(Message::getReceivers)
            .or().eq(Message::getReceivers, "")
            .or().apply("FIND_IN_SET({0}, receivers) > 0", userId));
        List<Message> visible = messageService.list(wrapper);

        // 该用户已有的查看记录
        Set<Long> seenIds = messageReadService.list(
            new LambdaQueryWrapper<MessageRead>().eq(MessageRead::getUserId, userId))
            .stream().map(MessageRead::getMessageId).collect(java.util.stream.Collectors.toSet());

        for (Message m : visible) {
            if (!seenIds.contains(m.getId())) {
                MessageRead mr = new MessageRead();
                mr.setMessageId(m.getId());
                mr.setUserId(userId);
                mr.setIsRead(0);
                messageReadService.save(mr);
            }
        }
        return Result.success();
    }

    // 单条标记已读（幂等）
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        MessageRead existing = messageReadService.getOne(
            new LambdaQueryWrapper<MessageRead>()
                .eq(MessageRead::getMessageId, id)
                .eq(MessageRead::getUserId, userId)
        );
        if (existing == null) {
            MessageRead mr = new MessageRead();
            mr.setMessageId(id);
            mr.setUserId(userId);
            mr.setIsRead(1);
            messageReadService.save(mr);
        } else if (existing.getIsRead() == null || existing.getIsRead() != 1) {
            existing.setIsRead(1);
            messageReadService.updateById(existing);
        }
        return Result.success();
    }

    // 全部已读
    @PostMapping("/read-all")
    public Result<Void> readAll(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<MessageRead> records = messageReadService.list(
            new LambdaQueryWrapper<MessageRead>().eq(MessageRead::getUserId, userId));
        for (MessageRead mr : records) {
            if (mr.getIsRead() == null || mr.getIsRead() != 1) {
                mr.setIsRead(1);
                messageReadService.updateById(mr);
            }
        }
        return Result.success();
    }
}
