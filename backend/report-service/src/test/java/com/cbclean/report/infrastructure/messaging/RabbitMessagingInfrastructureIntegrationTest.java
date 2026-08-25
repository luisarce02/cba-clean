package com.cbclean.report.infrastructure.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RabbitMessagingInfrastructureIntegrationTest {

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @Autowired
    private ConnectionFactory connectionFactory;

    @Test
    void establishesConnectionToRabbitMq() {
        try (Connection connection = connectionFactory.createConnection();
             Channel channel = connection.createChannel(false)) {
            assertThat(channel.isOpen()).isTrue();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to establish RabbitMQ connection", e);
        }
    }

    @Test
    void declaresDurableExchangeAndQueueBoundWithReportCreatedRoutingKey() throws Exception {
        try (Connection connection = connectionFactory.createConnection();
             Channel channel = connection.createChannel(false)) {

            channel.exchangeDeclarePassive(MessagingTopology.EVENTS_EXCHANGE);

            AMQP.Queue.DeclareOk queue = channel.queueDeclare(
                    MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE, true, false, false, null);
            assertThat(queue.getQueue()).isEqualTo(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE);

            byte[] payload = "topology-check".getBytes(StandardCharsets.UTF_8);
            AMQP.BasicProperties persistent = new AMQP.BasicProperties.Builder()
                    .deliveryMode(2)
                    .build();

            channel.basicPublish(MessagingTopology.EVENTS_EXCHANGE,
                    MessagingTopology.REPORT_CREATED_ROUTING_KEY, persistent, payload);
            com.rabbitmq.client.GetResponse message = channel.basicGet(
                    MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE, true);
            assertThat(message).isNotNull();
            assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                    .isEqualTo("topology-check");

            channel.basicPublish(MessagingTopology.EVENTS_EXCHANGE,
                    "report.unknown-routing-key", persistent, payload);
            assertThat(channel.basicGet(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE, true))
                    .isNull();
        }
    }
}
