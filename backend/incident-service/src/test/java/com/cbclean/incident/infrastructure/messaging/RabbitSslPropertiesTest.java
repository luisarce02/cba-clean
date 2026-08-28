package com.cbclean.incident.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitSslPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(RabbitAutoConfiguration.class));

    @Test
    void sslDisabledByDefault() {
        runner.run(ctx -> {
            RabbitProperties props = ctx.getBean(RabbitProperties.class);
            assertThat(props.getSsl().determineEnabled()).isFalse();
        });
    }

    @Test
    void sslCanBeEnabledViaProperty() {
        runner.withPropertyValues(
                "spring.rabbitmq.ssl.enabled=true",
                "spring.rabbitmq.host=toucan.lmq.cloudamqp.com",
                "spring.rabbitmq.port=8883"
        ).run(ctx -> {
            RabbitProperties props = ctx.getBean(RabbitProperties.class);
            assertThat(props.getSsl().determineEnabled()).isTrue();
            assertThat(props.getSsl().getEnabled()).isTrue();
            assertThat(props.getHost()).isEqualTo("toucan.lmq.cloudamqp.com");
            assertThat(props.getPort()).isEqualTo(8883);
        });
    }

    @Test
    void virtualHostDefaultsToSlash() {
        runner.run(ctx -> {
            RabbitProperties props = ctx.getBean(RabbitProperties.class);
            assertThat(props.getVirtualHost()).isNull();
            assertThat(props.determineVirtualHost()).isIn(null, "/");
        });
    }

    @Test
    void virtualHostCanBeOverriddenToCloudAmqpVhost() {
        runner.withPropertyValues("spring.rabbitmq.virtual-host=ddyttzxu").run(ctx -> {
            RabbitProperties props = ctx.getBean(RabbitProperties.class);
            assertThat(props.determineVirtualHost()).isEqualTo("ddyttzxu");
            assertThat(props.getVirtualHost()).isEqualTo("ddyttzxu");
        });
    }
}
