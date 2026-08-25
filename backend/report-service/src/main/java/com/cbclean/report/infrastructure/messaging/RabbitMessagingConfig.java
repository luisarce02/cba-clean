package com.cbclean.report.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Messaging infrastructure configuration.
 *
 * <p>Serializes published messages as JSON using Spring Boot's
 * auto-configured {@link ObjectMapper}, so the existing Jackson
 * configuration of the service applies to RabbitMQ payloads as well. The
 * converter also stamps the {@code __TypeId__} header, letting consumers
 * map the payload back to their local contract class.</p>
 */
@Configuration
public class RabbitMessagingConfig {

    @Bean
    public MessageConverter rabbitJsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
