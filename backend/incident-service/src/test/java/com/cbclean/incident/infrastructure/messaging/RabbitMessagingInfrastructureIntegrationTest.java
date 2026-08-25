package com.cbclean.incident.infrastructure.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
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

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    private boolean listenersWereRunning;

    @BeforeEach
    void pauseConsumers() {
        listenersWereRunning = listenerRegistry.isRunning();
        if (listenersWereRunning) {
            listenerRegistry.stop();
        }
    }

    @AfterEach
    void resumeConsumers() {
        if (listenersWereRunning) {
            listenerRegistry.start();
        }
    }

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
    void declaresDurableDeadLetterExchangeQueueAndRetryQueues() throws Exception {
        try (Connection connection = connectionFactory.createConnection();
             Channel channel = connection.createChannel(false)) {

            channel.exchangeDeclarePassive(MessagingTopology.DEAD_LETTER_EXCHANGE);
            channel.exchangeDeclarePassive(MessagingTopology.EVENTS_EXCHANGE);

            channel.queueDeclarePassive(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE);
            channel.queueDeclarePassive(MessagingTopology.INCIDENT_REPORT_CREATED_DLQ);
            for (int retryNumber = 1; retryNumber <= 3; retryNumber++) {
                channel.queueDeclarePassive(MessagingTopology.retryQueue(retryNumber));
            }

            byte[] payload = "durability-check".getBytes(StandardCharsets.UTF_8);
            AMQP.BasicProperties persistent = new AMQP.BasicProperties.Builder()
                    .deliveryMode(2)
                    .build();

            // DLQ binding on the DLX.
            channel.basicPublish(MessagingTopology.DEAD_LETTER_EXCHANGE,
                    MessagingTopology.INCIDENT_REPORT_CREATED_DLQ, persistent, payload);
            GetResponse dead = pollFor(() -> {
                try {
                    return channel.basicGet(MessagingTopology.INCIDENT_REPORT_CREATED_DLQ, true);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            assertThat(dead).as("DLX routes the DLQ routing key into the DLQ").isNotNull();

            // Retry chain: publishing onto the first retry queue's routing key
            // must resurface the message on the main queue after its TTL.
            channel.basicPublish(MessagingTopology.DEAD_LETTER_EXCHANGE,
                    MessagingTopology.retryQueue(1), persistent, payload);
            GetResponse retried = pollFor(() -> {
                try {
                    return channel.basicGet(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE, true);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            assertThat(retried)
                    .as("retry queue expiry dead-letters back to the main exchange")
                    .isNotNull();
            assertThat(new String(retried.getBody(), StandardCharsets.UTF_8))
                    .isEqualTo("durability-check");
        }
    }

    private GetResponse pollFor(java.util.function.Supplier<GetResponse> getter)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            GetResponse response = getter.get();
            if (response != null) {
                return response;
            }
            Thread.sleep(200);
        }
        return null;
    }

    @Test
    void declaresDurableExchangeAndQueueBoundWithReportCreatedRoutingKey() throws Exception {
        try (Connection connection = connectionFactory.createConnection();
             Channel channel = connection.createChannel(false)) {

            channel.exchangeDeclarePassive(MessagingTopology.EVENTS_EXCHANGE);

            AMQP.Queue.DeclareOk queue = channel.queueDeclarePassive(
                    MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE);
            assertThat(queue.getQueue()).isEqualTo(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE);

            byte[] payload = "topology-check".getBytes(StandardCharsets.UTF_8);
            AMQP.BasicProperties persistent = new AMQP.BasicProperties.Builder()
                    .deliveryMode(2)
                    .build();

            channel.basicPublish(MessagingTopology.EVENTS_EXCHANGE,
                    MessagingTopology.REPORT_CREATED_ROUTING_KEY, persistent, payload);
            GetResponse message = channel.basicGet(
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
