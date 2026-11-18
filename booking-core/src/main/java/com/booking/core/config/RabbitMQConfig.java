package com.booking.core.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String BOOKING_EXCHANGE = "booking.exchange";
    public static final String BOOKING_ROUTING_KEY = "booking.confirmed";
    public static final String BOOKING_QUEUE = "booking.queue";

    public static final String BOOKING_DLX = "booking.dlx";
    public static final String BOOKING_DLQ_ROUTING_KEY = "booking.deadletter";
    public static final String BOOKING_DLQ = "booking.dlq";

    @Bean
    public DirectExchange bookingExchange() {
        return new DirectExchange(BOOKING_EXCHANGE, true, false);
    }

    @Bean
    public Queue bookingQueue() {
        return QueueBuilder.durable(BOOKING_QUEUE)
                .deadLetterExchange(BOOKING_DLX)
                .deadLetterRoutingKey(BOOKING_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding bookingBinding(
            @Qualifier("bookingQueue") Queue bookingQueue,
            @Qualifier("bookingExchange") DirectExchange bookingExchange
    ) {
        return BindingBuilder.bind(bookingQueue).to(bookingExchange).with(BOOKING_ROUTING_KEY);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(BOOKING_DLX, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(BOOKING_DLQ).build();
    }

    @Bean
    public Binding deadLetterBinding(
            @Qualifier("bookingQueue") Queue deadLetterBinding,
            @Qualifier("bookingExchange") DirectExchange deadLetterExchange
    ) {
        return BindingBuilder.bind(deadLetterBinding).to(deadLetterExchange).with(BOOKING_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
