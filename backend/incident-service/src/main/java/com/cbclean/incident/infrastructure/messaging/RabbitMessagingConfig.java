package com.cbclean.incident.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Messaging infrastructure configuration.
 *
 * <p>Deserializes inbound messages as JSON using Spring Boot's auto-configured
 * {@link ObjectMapper}. The type mapper uses {@code INFERRED} precedence so the
 * payload is always bound to the listener method's parameter type (the local
 * {@code ReportCreatedEvent} contract), ignoring the {@code __TypeId__} header
 * stamped by the Report Service, which names a class that does not exist in
 * this service.</p>
 */
@Configuration
public class RabbitMessagingConfig {

    @Bean
    public MessageConverter rabbitJsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(DefaultJackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
