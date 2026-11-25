package com.edu.aitutor.module.privatechat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 私信权限判定 Mapper。
 * <p>
 * 师生关系口径（接口文档 18.1）：存在一门课程 c，使
 * <ul>
 *   <li>c.teacher_id = 教师</li>
 *   <li>c.is_deleted = 0 且 c.status = 1</li>
 *   <li>学生在 c.class_id 对应班级的 class_member 中（member_type = 1）</li>
 * </ul>
 * 三项同时满足才算「同老师 + 同课程」，因此同班但不同课、同课但不同老师均不构成私信资格。
 * <p>
 * 注意：判定完全实时计算，不在会话表冗余"关系类型"，
 * 以免管理员调整班级/课程后权限与数据不一致。
 */
@Mapper
public interface PrivatePermissionMapper {

    /**
     * 判断 teacherId 是否为 studentId 的任课教师（同老师 + 同课程）。
     * <p>
     * 返回大于 0 表示成立。只匹配「教师任课且学生在班」这一核心条件，
     * 学生与教师谁在前由业务层按 sys_user.role 判断后传入。
     */
    @Select("select count(*) from course c " +
            "join class_member cm on cm.class_id = c.class_id " +
            "  and cm.user_id = #{studentId} " +
            "  and cm.member_type = 1 " +
            "  and cm.is_deleted = 0 " +
            "where c.teacher_id = #{teacherId} " +
            "  and c.is_deleted = 0 " +
            "  and c.status = 1")
    int countTeacherOfStudent(@Param("teacherId") Long teacherId,
                              @Param("studentId") Long studentId);

    /**
     * 判断两个学生是否构成"同学"。
     * <p>
     * 口径：存在一门课程 c，两名学生都在 c.class_id 对应班级中（member_type = 1）。
     * 用 distinct c.id 计数，避免同一门课因 join 产生重复行造成误判。
     */
    @Select("select count(distinct c.id) from course c " +
            "join class_member ca on ca.class_id = c.class_id " +
            "  and ca.user_id = #{userA} " +
            "  and ca.member_type = 1 " +
            "  and ca.is_deleted = 0 " +
            "join class_member cb on cb.class_id = c.class_id " +
            "  and cb.user_id = #{userB} " +
            "  and cb.member_type = 1 " +
            "  and cb.is_deleted = 0 " +
            "where c.is_deleted = 0 " +
            "  and c.status = 1")
    int countSharedCourseBetweenStudents(@Param("userA") Long userA,
                                         @Param("userB") Long userB);

    /**
     * 可私信联系人：当前用户为【学生】时的可见范围。
     * <p>
     * 包含两类：同学（同课程的学生）+ 我的老师（我所选课程的 teacher_id）。
     * relation 字段直接给前端做分组展示。
     * <p>
     * 注意：本方法**不使用** &lt;script&gt; 标签，SQL 以普通字符串下发，
     * 因此这里必须写原生 {@code <>}，不能写 XML 转义形式的 &amp;lt;&amp;gt;——
     * 后者会被数据库当作列名解析，报 "Unknown column 'lt'"。
     */
    @Select("select u.id as user_id, u.real_name, u.user_no, u.role, u.avatar_url, " +
            "       '同学' as relation " +
            "from sys_user u " +
            "where u.is_deleted = 0 and u.status = 1 and u.role = 1 and u.id <> #{userId} " +
            "  and exists ( " +
            "    select 1 from course c " +
            "    join class_member ca on ca.class_id = c.class_id " +
            "      and ca.user_id = #{userId} and ca.member_type = 1 and ca.is_deleted = 0 " +
            "    join class_member cb on cb.class_id = c.class_id " +
            "      and cb.user_id = u.id and cb.member_type = 1 and cb.is_deleted = 0 " +
            "    where c.is_deleted = 0 and c.status = 1 " +
            "  ) " +
            "union " +
            "select u.id, u.real_name, u.user_no, u.role, u.avatar_url, '我的老师' " +
            "from sys_user u " +
            "where u.is_deleted = 0 and u.status = 1 and u.role = 2 " +
            "  and exists ( " +
            "    select 1 from course c " +
            "    join class_member ca on ca.class_id = c.class_id " +
            "      and ca.user_id = #{userId} and ca.member_type = 1 and ca.is_deleted = 0 " +
            "    where c.teacher_id = u.id and c.is_deleted = 0 and c.status = 1 " +
            "  ) " +
            "order by relation, user_id")
    List<Map<String, Object>> selectContactsForStudent(@Param("userId") Long userId);

    /**
     * 可私信联系人：当前用户为【教师】时的可见范围。
     * <p>
     * 只包含"自己课程的学生"：即我所授课程的 class_id 对应班级中的学生。
     * 教师看不到其他教师的学生，也看不到非自己任课班级的学生。
     */
    @Select("select distinct u.id as user_id, u.real_name, u.user_no, u.role, u.avatar_url, " +
            "       '我的学生' as relation " +
            "from sys_user u " +
            "join class_member cm on cm.user_id = u.id " +
            "  and cm.member_type = 1 " +
            "  and cm.is_deleted = 0 " +
            "join course c on c.class_id = cm.class_id " +
            "  and c.teacher_id = #{userId} " +
            "  and c.is_deleted = 0 " +
            "  and c.status = 1 " +
            "where u.is_deleted = 0 and u.status = 1 " +
            "order by u.id")
    List<Map<String, Object>> selectContactsForTeacher(@Param("userId") Long userId);
}