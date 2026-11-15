package com.edu.aitutor.module.teacher.service;

import com.edu.aitutor.common.BusinessException;
import com.edu.aitutor.common.ErrorCode;
import com.edu.aitutor.framework.security.SecurityUtils;
import com.edu.aitutor.module.dashboard.mq.DashboardMqConfig;
import com.edu.aitutor.module.teacher.mapper.TeacherMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 教师工作台：极简版教学动作入口。
 * 每次动作都会落入业务表，并通过 RabbitMQ 通知大屏即时刷新。
 */
@Service
public class TeacherWorkbenchService {

    private static final Logger log = LoggerFactory.getLogger(TeacherWorkbenchService.class);

    private static final DateTimeFormatter TITLE_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    @Resource
    private TeacherMapper teacherMapper;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 一键布置随堂练习。
     *
     * @return 作业标题与课程名，供前端即时反馈
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publishQuickHomework() {
        Long teacherId = SecurityUtils.currentUserId();
        Map<String, Object> course = teacherMapper.selectFirstCourse(teacherId);
        if (course == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前账号未绑定课程，无法布置作业");
        }
        Long courseId = ((Number) course.get("courseId")).longValue();
        String courseName = String.valueOf(course.get("courseName"));
        String title = "随堂练习 " + LocalDateTime.now().format(TITLE_FORMAT);

        teacherMapper.insertQuickHomework(courseId, teacherId, title, "教师端一键布置的随堂练习");

        // 事务提交后再通知大屏，避免消费者读到未提交的数据
        afterCommit(() -> sendRefreshEvent(title, courseName));

        Map<String, Object> result = new HashMap<>();
        result.put("title", title);
        result.put("courseName", courseName);
        return result;
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void sendRefreshEvent(String title, String courseName) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "homework.published");
            payload.put("title", title);
            payload.put("courseName", courseName);
            rabbitTemplate.convertAndSend(DashboardMqConfig.EXCHANGE, DashboardMqConfig.ROUTING_KEY,
                    objectMapper.writeValueAsString(payload));
            log.info("已发送大屏刷新事件：{}", title);
        } catch (Exception e) {
            // 消息发送失败不影响作业发布结果，大屏由定时推送兜底
            log.warn("大屏刷新事件发送失败，交由定时推送兜底：{}", e.getMessage());
        }
    }
}
