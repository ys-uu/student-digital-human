package com.edu.aitutor.module.teacher.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 教师工作台数据访问。
 */
@Mapper
public interface TeacherMapper {

    /** 取该教师任教的第一门课程（演示场景下一名教师只带一门课） */
    @Select("select id as courseId, course_name as courseName from course " +
            "where teacher_id = #{teacherId} and is_deleted = 0 order by id limit 1")
    Map<String, Object> selectFirstCourse(@Param("teacherId") Long teacherId);

    /** 一键布置随堂练习：状态直接置为已发布 */
    @Insert("insert into homework (course_id, teacher_id, title, description, homework_type, " +
            "publish_at, deadline, estimated_minutes, allow_late, status) " +
            "values (#{courseId}, #{teacherId}, #{title}, #{description}, 1, now(), " +
            "date_add(now(), interval 1 day), 20, 1, 1)")
    int insertQuickHomework(@Param("courseId") Long courseId,
                            @Param("teacherId") Long teacherId,
                            @Param("title") String title,
                            @Param("description") String description);
}
