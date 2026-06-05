package com.example.myproject.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String WAITLIST_EXCHANGE = "ticket.exchange";
    public static final String WAITLIST_QUEUE = "ticket.waitlist.queue";
    public static final String WAITLIST_ROUTING_KEY = "ticket.waitlist";
    public static final String WAITLIST_DLQ = "ticket.waitlist.dlq";
    public static final String WAITLIST_DLK = "ticket.waitlist.dlq";

    /**
     * 直连交换机：退票后发送"车次ID"消息，消费者异步处理候补转正
     */
    @Bean
    public DirectExchange waitlistExchange() {
        return new DirectExchange(WAITLIST_EXCHANGE);
    }

    /**
     * 候补处理队列：持久化 + 绑定死信队列（重试3次仍失败则进入DLQ）
     */
    @Bean
    public Queue waitlistQueue() {
        return QueueBuilder.durable(WAITLIST_QUEUE)
                .deadLetterExchange(WAITLIST_EXCHANGE)
                .deadLetterRoutingKey(WAITLIST_DLK)
                .build();
    }

    @Bean
    public Queue waitlistDlq() {
        return QueueBuilder.durable(WAITLIST_DLQ).build();
    }

    @Bean
    public Binding waitlistBinding() {
        return BindingBuilder.bind(waitlistQueue())
                .to(waitlistExchange())
                .with(WAITLIST_ROUTING_KEY);
    }

    @Bean
    public Binding waitlistDlqBinding() {
        return BindingBuilder.bind(waitlistDlq())
                .to(waitlistExchange())
                .with(WAITLIST_DLK);
    }
}
