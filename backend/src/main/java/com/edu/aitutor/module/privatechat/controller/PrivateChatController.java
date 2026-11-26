package com.edu.aitutor.module.privatechat.controller;

import com.edu.aitutor.common.PageResult;
import com.edu.aitutor.common.Result;
import com.edu.aitutor.framework.security.SecurityUtils;
import com.edu.aitutor.module.privatechat.dto.CreateConversationDTO;
import com.edu.aitutor.module.privatechat.dto.SendPrivateMessageDTO;
import com.edu.aitutor.module.privatechat.mapper.PrivatePermissionMapper;
import com.edu.aitutor.module.privatechat.service.OnlineUserService;
import com.edu.aitutor.module.privatechat.service.PrivateMessageService;
import com.edu.aitutor.module.privatechat.vo.ConversationVO;
import com.edu.aitutor.module.privatechat.vo.MessageVO;
import com.edu.aitutor.module.user.entity.User;
import com.edu.aitutor.module.user.mapper.UserMapper;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 私信 HTTP 兜底接口，对齐接口文档 18.5。
 * <p>
 * 这些接口不是主通道：私信主通道是 WebSocket /ws/private。
 * 本组接口用于 WebSocket 不可用（如企业代理阻断 Upgrade）时降级，
 * 以及首屏加载历史与会话列表。
 * 所有接口均需登录，身份一律取自 SecurityContext，不接受客户端传入的发送方。
 */
@RestController
@RequestMapping("/api/v1/private")
public class PrivateChatController {

    @Resource
    private PrivateMessageService messageService;

    /**
     * 字段名不可为 permissionMapper：RBAC 的 RbacServiceImpl 已使用该名字注入 PermissionMapper，
     * 同名字段会让 Spring 按名解析时命中错误的 Bean，改用 chatPermissionMapper 避免歧义。
     */
    @Resource
    private PrivatePermissionMapper chatPermissionMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private OnlineUserService onlineUserService;

    /** 18.5.1 会话列表 */
    @GetMapping("/conversations")
    public Result<PageResult<ConversationVO>> listConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        Long userId = SecurityUtils.currentUserId();
        return Result.success(messageService.listConversations(userId, page, pageSize));
    }

    /** 18.5.2 创建或复用会话 */
    @PostMapping("/conversations")
    public Result<Map<String, Object>> createConversation(@Valid @RequestBody CreateConversationDTO dto) {
        Long userId = SecurityUtils.currentUserId();
        return Result.success(messageService.createOrGetConversation(userId, dto.getPeerId()));
    }

    /**
     * 18.5.3 消息历史（seq 游标分页）。
     *
     * @param beforeSeq 取该 seq 之前更早的消息，为空时返回最新一页
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<List<MessageVO>> listMessages(
            @PathVariable Long conversationId,
            @RequestParam(name = "before_seq", required = false) Long beforeSeq,
            @RequestParam(defaultValue = "20") int limit) {
        Long userId = SecurityUtils.currentUserId();
        return Result.success(messageService.listMessages(userId, conversationId, beforeSeq, limit));
    }

    /**
     * 18.5.7 按 seq 区间补洞（PPT P11）。
     * <p>
     * 客户端检测到 seq 跳号时调用，返回 (after_seq, up_to_seq] 区间缺失消息。
     */
    @GetMapping("/conversations/{conversationId}/messages/range")
    public Result<List<MessageVO>> listSeqRange(
            @PathVariable Long conversationId,
            @RequestParam(name = "after_seq") long afterSeq,
            @RequestParam(name = "up_to_seq", required = false) Long upToSeq,
            @RequestParam(defaultValue = "200") int limit) {
        Long userId = SecurityUtils.currentUserId();
        return Result.success(messageService.listSeqRange(userId, conversationId, afterSeq, upToSeq, limit));
    }

    /** 18.5.4 发送消息（HTTP 兜底） */
    @PostMapping("/messages")
    public Result<MessageVO> sendMessage(@Valid @RequestBody SendPrivateMessageDTO dto) {
        Long userId = SecurityUtils.currentUserId();
        return Result.success(messageService.send(userId, dto));
    }

    /**
     * 18.5.5 标记已读（seq 游标）。
     *
     * @param lastReadSeq 已读到的 seq，不传则视为读到会话内最新一条
     */
    @PostMapping("/conversations/{conversationId}/read")
    public Result<Map<String, Object>> markRead(
            @PathVariable Long conversationId,
            @RequestParam(name = "last_read_seq", required = false) Long lastReadSeq) {
        Long userId = SecurityUtils.currentUserId();
        Long cursor = messageService.markRead(userId, conversationId, lastReadSeq);
        Map<String, Object> data = new HashMap<>();
        data.put("conversation_id", String.valueOf(conversationId));
        data.put("last_read_seq", String.valueOf(cursor));
        return Result.success(data);
    }

    /**
     * 18.5.6 可私信联系人。
     * <p>
     * 按角色分流：学生看「同学 + 我的老师」，教师看「我的学生」，管理员返回空（不参与私信）。
     * relation 字段直接给前端分组；online 用于展示在线状态。
     */
    @GetMapping("/contacts")
    public Result<List<Map<String, Object>>> contacts() {
        Long userId = SecurityUtils.currentUserId();
        User me = userMapper.selectById(userId);
        int role = me == null || me.getRole() == null ? 1 : me.getRole();

        List<Map<String, Object>> rows;
        if (role == 2) {
            rows = chatPermissionMapper.selectContactsForTeacher(userId);
        } else if (role == 1) {
            rows = chatPermissionMapper.selectContactsForStudent(userId);
        } else {
            // 管理员不参与私信：返回空列表而非报错，前端无需特判
            rows = new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("user_id", String.valueOf(row.get("user_id")));
            item.put("real_name", row.get("real_name"));
            item.put("user_no", row.get("user_no"));
            item.put("role", row.get("role"));
            item.put("avatar_url", row.get("avatar_url"));
            item.put("relation", row.get("relation"));
            Long peerId = Long.valueOf(String.valueOf(row.get("user_id")));
            item.put("online", onlineUserService.isOnline(peerId));
            result.add(item);
        }
        return Result.success(result);
    }
}