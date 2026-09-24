package com.califorge.msorden.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion RabbitMQ del productor: exchange compartido calisat.exchange
 * y RabbitTemplate con serializacion JSON. La routing key se pasa en cada
 * convertAndSend (orden.confirmada / orden.cancelada).
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "calisat.exchange";
    public static final String ROUTING_KEY_ORDEN_CONFIRMADA = "orden.confirmada";
    public static final String ROUTING_KEY_ORDEN_CANCELADA = "orden.cancelada";

    @Bean
    public DirectExchange calisatExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        rabbitTemplate.setExchange(EXCHANGE);
        return rabbitTemplate;
    }
}
