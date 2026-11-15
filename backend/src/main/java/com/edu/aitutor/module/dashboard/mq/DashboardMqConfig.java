package com.edu.aitutor.module.dashboard.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据大屏消息拓扑。
 * <p>
 * 业务动作（如教师布置作业）写库成功后投递一条刷新事件，
 * 大屏消费者收到后失效 Redis 缓存并立即触发 WebSocket 推送。
 */
@Configuration
public class DashboardMqConfig {

    /** 业务事件交换机 */
    public static final String EXCHANGE = "aitutor.dashboard.exchange";

    /** 大屏刷新队列 */
    public static final String QUEUE = "dashboard.refresh.queue";

    /** 路由键 */
    public static final String ROUTING_KEY = "dashboard.refresh";

    @Bean
    public TopicExchange dashboardExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue dashboardRefreshQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding dashboardRefreshBinding(Queue dashboardRefreshQueue, TopicExchange dashboardExchange) {
        return BindingBuilder.bind(dashboardRefreshQueue).to(dashboardExchange).with(ROUTING_KEY);
    }
}
