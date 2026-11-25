package com.edu.aitutor.module.privatechat.service;

import com.edu.aitutor.common.BusinessException;
import com.edu.aitutor.common.ErrorCode;
import com.edu.aitutor.module.privatechat.mapper.PrivatePermissionMapper;
import com.edu.aitutor.module.user.entity.User;
import com.edu.aitutor.module.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 私信权限判定。
 * <p>
 * 规则（接口文档 18.1）：
 * <table border="1">
 *   <tr><th>发送方</th><th>接收方</th><th>结果</th></tr>
 *   <tr><td>学生</td><td>学生</td><td>允许（同课程同学）</td></tr>
 *   <tr><td>学生/教师</td><td>自己的老师/自己的学生</td><td>允许（同老师 + 同课程）</td></tr>
 *   <tr><td>教师</td><td>教师</td><td>拒绝 1016</td></tr>
 *   <tr><td>管理员</td><td>任何人</td><td>拒绝 1017（走系统通知）</td></tr>
 *   <tr><td>任何人</td><td>无课程关系者</td><td>拒绝 1015</td></tr>
 *   <tr><td>任何人</td><td>自己</td><td>拒绝 1001</td></tr>
 * </table>
 * <p>
 * 权限完全实时计算，不缓存、不在会话表冗余关系类型：
 * 管理员调整班级或课程后，下一次发消息立即按新关系生效。
 */
@Service
public class PrivatePermissionService {

    private static final Logger log = LoggerFactory.getLogger(PrivatePermissionService.class);

    /** 角色常量，与 sys_user.role 一致 */
    private static final int ROLE_STUDENT = 1;
    private static final int ROLE_TEACHER = 2;
    private static final int ROLE_ADMIN = 3;

    private final UserMapper userMapper;
    /**
     * 注意：字段名不能叫 permissionMapper。
     * RBAC 模块的 RbacServiceImpl 用 @Resource 注入同名字段（PermissionMapper），
     * 两个 Mapper 的默认 Bean 名均参与按名解析，字段名重复会导致按类型注入时匹配到错误的代理对象，
     * 报 "The bean 'permissionMapper' could not be injected because it is a JDK dynamic proxy"。
     * 改用 chatPermissionMapper 彻底避开歧义。
     */
    private final PrivatePermissionMapper chatPermissionMapper;

    public PrivatePermissionService(UserMapper userMapper,
                                    PrivatePermissionMapper chatPermissionMapper) {
        this.userMapper = userMapper;
        this.chatPermissionMapper = chatPermissionMapper;
    }

    /**
     * 校验收发双方是否允许私信，不满足时直接抛 BusinessException。
     * <p>
     * 校验顺序有意如此：先拦最确定的拒绝项（自己、管理员、教师互发），
     * 再走较贵的课程关系查询，避免无效 SQL。
     */
    public void assertCanSend(Long fromId, Long toId) {
        if (fromId == null || toId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "收发双方不能为空");
        }
        if (fromId.equals(toId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能给自己发私信");
        }

        User from = requireUser(fromId);
        User to = requireUser(toId);

        int fromRole = normalizeRole(from.getRole());
        int toRole = normalizeRole(to.getRole());

        // 管理员不参与私信，统一走系统通知
        if (fromRole == ROLE_ADMIN || toRole == ROLE_ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_CHAT_DENIED, "管理员不参与私信，请使用系统通知");
        }

        // 教师之间不开放
        if (fromRole == ROLE_TEACHER && toRole == ROLE_TEACHER) {
            throw new BusinessException(ErrorCode.TEACHER_TO_TEACHER_DENIED, "教师之间暂不开放私信");
        }

        // 学生 ↔ 学生：需为同学（存在共同课程）
        if (fromRole == ROLE_STUDENT && toRole == ROLE_STUDENT) {
            if (chatPermissionMapper.countSharedCourseBetweenStudents(fromId, toId) <= 0) {
                throw new BusinessException(ErrorCode.PRIVATE_FORBIDDEN, "只能与同一课程中的同学私信");
            }
            return;
        }

        // 师生关系：判断教师是否为学生的任课教师（同老师 + 同课程）
        Long teacherId = fromRole == ROLE_TEACHER ? fromId : toId;
        Long studentId = fromRole == ROLE_TEACHER ? toId : fromId;
        if (chatPermissionMapper.countTeacherOfStudent(teacherId, studentId) <= 0) {
            throw new BusinessException(ErrorCode.PRIVATE_FORBIDDEN,
                    "只能与同一课程中的老师或学生私信");
        }
    }

    /**
     * 判断两个用户是否允许私信，不抛异常。
     * 供联系人列表过滤等场景复用，行为与 assertCanSend 保持一致。
     */
    public boolean canSend(Long fromId, Long toId) {
        try {
            assertCanSend(fromId, toId);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    /**
     * 按主键取用户，不存在或已删除时抛业务异常。
     */
    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在：" + userId);
        }
        return user;
    }

    /**
     * 角色归一：库里 role 为 tinyint，1 学生 / 2 教师 / 3 管理员。
     * 空值按学生处理，避免历史脏数据导致私信不可用。
     */
    private int normalizeRole(Integer role) {
        if (role == null) {
            log.debug("用户角色为空，按学生处理");
            return ROLE_STUDENT;
        }
        return role;
    }
}