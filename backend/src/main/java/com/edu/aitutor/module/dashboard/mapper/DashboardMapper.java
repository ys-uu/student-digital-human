package com.edu.aitutor.module.dashboard.mapper;

import com.edu.aitutor.module.dashboard.vo.DashboardOverviewVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据大屏聚合查询。所有口径均直接基于明细表或聚合统计表，避免前端二次汇总。
 */
@Mapper
public interface DashboardMapper {

    @Select("<script>" +
            "select count(distinct s.user_id) as active_students, " +
            "coalesce(sum(s.effective_minutes), 0) as study_minutes, " +
            "coalesce(sum(s.exercise_count), 0) as exercise_count, " +
            "coalesce(sum(s.correct_count), 0) as correct_count " +
            "from study_stat_daily s " +
            "where s.is_deleted = 0 and s.stat_date &gt;= #{start} " +
            "<if test='courseId != null'> and s.course_id = #{courseId} </if>" +
            "</script>")
    Map<String, Object> selectStudySummary(@Param("start") LocalDate start,
                                          @Param("courseId") Long courseId);

    @Select("<script>" +
            "select count(*) from chat_session c " +
            "where c.is_deleted = 0 and date(c.create_time) &gt;= #{start} " +
            "<if test='courseId != null'> and c.course_id = #{courseId} </if>" +
            "</script>")
    long countQaSessions(@Param("start") LocalDate start,
                         @Param("courseId") Long courseId);

    @Select("<script>" +
            "select coalesce(round(avg(km.mastery_score), 1), 0) from knowledge_mastery km " +
            "where km.is_deleted = 0 " +
            "<if test='courseId != null'> and km.course_id = #{courseId} </if>" +
            "</script>")
    double selectAverageMastery(@Param("courseId") Long courseId);

    @Select("select count(*) from homework_submission " +
            "where is_deleted = 0 and grade_status = 0 and submit_status in (2, 3)")
    long countPendingHomework();

    @Select("select count(*) as call_count, " +
            "coalesce(sum(total_tokens), 0) as total_tokens, " +
            "coalesce(round(avg(duration_ms)), 0) as avg_duration_ms, " +
            "coalesce(round(sum(status = 1) * 100 / count(*), 1), 0) as success_rate " +
            "from ai_usage_log where create_time >= #{start}")
    Map<String, Object> selectAiSummary(@Param("start") LocalDate start);

    @Select("<script>" +
            "select date_format(s.stat_date, '%m-%d') as date, " +
            "coalesce(sum(s.effective_minutes), 0) as study_minutes, " +
            "coalesce(sum(s.qa_count), 0) as qa_count, " +
            "coalesce(sum(s.exercise_count), 0) as exercise_count " +
            "from study_stat_daily s " +
            "where s.is_deleted = 0 and s.stat_date &gt;= #{start} " +
            "<if test='courseId != null'> and s.course_id = #{courseId} </if>" +
            "group by s.stat_date order by s.stat_date" +
            "</script>")
    List<DashboardOverviewVO.TrendPoint> selectLearningTrend(@Param("start") LocalDate start,
                                                             @Param("courseId") Long courseId);

    @Select("<script>" +
            "select t.name as name, count(*) as value from (" +
            "  select case when km.mastery_score &lt; 60 then '薄弱' " +
            "              when km.mastery_score &lt; 75 then '一般' " +
            "              when km.mastery_score &lt; 90 then '良好' else '优秀' end as name " +
            "  from knowledge_mastery km where km.is_deleted = 0 " +
            "  <if test='courseId != null'> and km.course_id = #{courseId} </if>" +
            ") t group by t.name" +
            "</script>")
    List<DashboardOverviewVO.NameValue> selectMasteryDistribution(@Param("courseId") Long courseId);

    @Select("select c.course_name as name, coalesce(sum(s.effective_minutes), 0) as value " +
            "from study_stat_daily s join course c on c.id = s.course_id " +
            "where s.is_deleted = 0 and c.is_deleted = 0 and s.stat_date >= #{start} " +
            "group by c.id, c.course_name order by value desc limit 8")
    List<DashboardOverviewVO.NameValue> selectCourseRanking(@Param("start") LocalDate start);

    @Select("<script>" +
            "select kp.kp_name as name, round(avg(km.mastery_score), 1) as value " +
            "from knowledge_mastery km join knowledge_point kp on kp.id = km.kp_id " +
            "where km.is_deleted = 0 and kp.is_deleted = 0 " +
            "<if test='courseId != null'> and km.course_id = #{courseId} </if>" +
            "group by kp.id, kp.kp_name order by value asc limit 10" +
            "</script>")
    List<DashboardOverviewVO.NameValue> selectWeakPoints(@Param("courseId") Long courseId);

    @Select("select ci.class_name as name, count(distinct cm.user_id) as students, " +
            "coalesce(round(avg(t.minutes)), 0) as avg_minutes, " +
            "coalesce(round(avg(m.mastery), 1), 0) as avg_mastery " +
            "from class_info ci " +
            "join class_member cm on cm.class_id = ci.id and cm.member_type = 1 " +
            "left join (select s.user_id, sum(s.effective_minutes) as minutes from study_stat_daily s " +
            "           where s.is_deleted = 0 and s.stat_date >= #{start} group by s.user_id) t on t.user_id = cm.user_id " +
            "left join (select km.user_id, avg(km.mastery_score) as mastery from knowledge_mastery km " +
            "           where km.is_deleted = 0 group by km.user_id) m on m.user_id = cm.user_id " +
            "where ci.is_deleted = 0 " +
            "group by ci.id, ci.class_name order by ci.id")
    List<DashboardOverviewVO.ClassCompare> selectClassCompare(@Param("start") LocalDate start);

    @Select("select * from (" +
            "  select u.real_name as student_name, ci.class_name as class_name, '连续 3 天未学习' as reason " +
            "  from sys_user u " +
            "  join class_member cm on cm.user_id = u.id and cm.member_type = 1 " +
            "  join class_info ci on ci.id = cm.class_id " +
            "  where u.is_deleted = 0 and u.role = 1 " +
            "    and not exists (select 1 from study_stat_daily s where s.user_id = u.id " +
            "                    and s.is_deleted = 0 and s.stat_date >= date_sub(curdate(), interval 3 day)) " +
            "  union all " +
            "  select u.real_name, ci.class_name, '平均掌握度低于 65 分' " +
            "  from sys_user u " +
            "  join class_member cm on cm.user_id = u.id and cm.member_type = 1 " +
            "  join class_info ci on ci.id = cm.class_id " +
            "  join (select user_id, avg(mastery_score) as avg_score from knowledge_mastery " +
            "        where is_deleted = 0 group by user_id) m on m.user_id = u.id " +
            "  where u.is_deleted = 0 and u.role = 1 and m.avg_score < 65 " +
            "  union all " +
            "  select u.real_name, ci.class_name, '近 7 天正确率低于 70%' " +
            "  from sys_user u " +
            "  join class_member cm on cm.user_id = u.id and cm.member_type = 1 " +
            "  join class_info ci on ci.id = cm.class_id " +
            "  join (select user_id, sum(correct_count) * 100 / nullif(sum(exercise_count), 0) as rate " +
            "        from study_stat_daily where is_deleted = 0 " +
            "          and stat_date >= date_sub(curdate(), interval 7 day) group by user_id) r on r.user_id = u.id " +
            "  where u.is_deleted = 0 and u.role = 1 and r.rate < 70 " +
            ") t limit 12")
    List<DashboardOverviewVO.WarningItem> selectWarnings();

    @Select("select scene_type as name, count(*) as value from ai_usage_log " +
            "where create_time >= #{start} group by scene_type order by value desc")
    List<DashboardOverviewVO.NameValue> selectAiSceneDistribution(@Param("start") LocalDate start);

    @Select("select * from (" +
            "  (select 'ai' as `type`, coalesce(u.real_name, '未知用户') as user_name, " +
            "          '发起一次 AI 答疑' as content, date_format(a.create_time, '%m-%d %H:%i') as time " +
            "   from ai_usage_log a left join sys_user u on u.id = a.user_id order by a.id desc limit 10) " +
            "  union all " +
            "  (select 'session', coalesce(u.real_name, '未知用户'), '开始一次答疑会话', " +
            "          date_format(s.create_time, '%m-%d %H:%i') " +
            "   from chat_session s left join sys_user u on u.id = s.user_id order by s.id desc limit 6) " +
            "  union all " +
            "  (select 'homework', coalesce(u.real_name, '未知用户'), concat('布置了作业《', h.title, '》'), " +
            "          date_format(h.publish_at, '%m-%d %H:%i') " +
            "   from homework h left join sys_user u on u.id = h.teacher_id " +
            "   where h.is_deleted = 0 order by h.id desc limit 6) " +
            ") t order by t.time desc limit 14")
    List<DashboardOverviewVO.LiveEvent> selectLiveEvents();

    /** 今日教师新发布的作业数，用于教师动作在大屏上的即时反馈 */
    @Select("select count(*) from homework " +
            "where is_deleted = 0 and status = 1 and publish_at >= #{start}")
    long countTodayHomework(@Param("start") LocalDate start);

    @Select("select count(*) from sys_user_role ur join sys_role r on r.id = ur.role_id " +
            "where ur.user_id = #{userId} and r.role_code = 'admin'")
    int countAdminRole(@Param("userId") Long userId);

    // ---------------- 以下仅用于演示数据模拟器 ----------------

    /** 补齐当天缺失的学习统计，保证模拟器有行可累加 */
    @Insert("insert ignore into study_stat_daily " +
            "(user_id, course_id, stat_date, effective_minutes, qa_count, homework_count, " +
            " exercise_count, correct_count, focus_score, review_finished, avg_answer_seconds) " +
            "select u.id, c.id, curdate(), 30, 0, 0, 15, 10, 70, 0, 60 " +
            "from sys_user u cross join course c " +
            "where u.user_no like 'S2024%' and u.is_deleted = 0 " +
            "  and c.course_code in ('CS101', 'EN201', 'MA301')")
    int ensureTodayStudyStat();

    /** 随机挑一名学生写一条 AI 调用日志 */
    @Insert("insert into ai_usage_log " +
            "(user_id, model_name, scene_type, prompt_tokens, completion_tokens, total_tokens, " +
            " duration_ms, status, create_time) " +
            "select u.id, 'demo-simulator', 1 + floor(rand() * 5), " +
            "       260 + floor(rand() * 300), 140 + floor(rand() * 200), " +
            "       400 + floor(rand() * 500), 700 + floor(rand() * 1500), 1, now() " +
            "from sys_user u " +
            "where u.user_no like 'S2024%' and u.is_deleted = 0 " +
            "order by rand() limit 1")
    int insertSimulatedAiLog();

    /** 随机挑一名学生开一次答疑会话 */
    @Insert("insert into chat_session " +
            "(session_no, user_id, course_id, scene_type, title, status, message_count, " +
            " last_message_at, create_time) " +
            "select concat('DEMO-SIM-', unix_timestamp(), '-', u.id), u.id, " +
            "       (select id from course where course_code = 'CS101' and is_deleted = 0 limit 1), " +
            "       1 + floor(rand() * 5), '实时模拟答疑', 0, 0, now(), now() " +
            "from sys_user u " +
            "where u.user_no like 'S2024%' and u.is_deleted = 0 " +
            "order by rand() limit 1")
    int insertSimulatedSession();

    /** 随机给当天某条学习统计累加学习行为 */
    @Update("update study_stat_daily " +
            "set effective_minutes = effective_minutes + 2, " +
            "    exercise_count = exercise_count + 1, " +
            "    correct_count = correct_count + if(rand() < 0.75, 1, 0), " +
            "    update_time = now() " +
            "where id = (select id from (" +
            "  select id from study_stat_daily " +
            "  where stat_date = curdate() and is_deleted = 0 order by rand() limit 1" +
            ") t)")
    int increaseTodayStudyStat();
}
