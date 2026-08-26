package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.infrastructure.metrics.IncidentMetrics;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReportCreatedEventRetryRouterTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final IncidentMessagingRetryProperties properties = new IncidentMessagingRetryProperties();
    private final ReportCreatedEventRetryRouter router =
            new ReportCreatedEventRetryRouter(rabbitTemplate, properties, IncidentMetrics.noop());

    private final ReportCreatedEvent event = new ReportCreatedEvent(
            UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111"),
            java.time.Instant.parse("2026-08-25T15:00:00Z"),
            UUID.fromString("bbbbbbbb-2222-2222-2222-222222222222"),
            "LITTER",
            "LOW",
            "Cigarette butts on the playground",
            new ReportCreatedEvent.Location(48.2, 16.4, null));

    @BeforeEach
    void validateDefaults() {
        properties.validate();
        assertThat(properties.getMaxRetries()).isEqualTo(3);
    }

    private Message sentMessage(String expectedRoutingKey) {
        ArgumentCaptor<MessagePostProcessor> processor =
                ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
                eq(MessagingTopology.DEAD_LETTER_EXCHANGE),
                eq(expectedRoutingKey),
                eq(event),
                processor.capture());
        return processor.getValue().postProcessMessage(
                new Message(new byte[0], new org.springframework.amqp.core.MessageProperties()));
    }

    @Test
    void transientFailureBelowLimitRepublishesToNextRetryQueueWithIncrementedCounter() {
        router.retryOrDeadLetter(event, null,
                new IllegalStateException("mongodb unavailable"), null);

        Message message = sentMessage(MessagingTopology.retryQueue(1));
        assertThat((Integer) message.getMessageProperties()
                .getHeader(MessagingTopology.RETRY_COUNT_HEADER)).isEqualTo(1);
    }

    @Test
    void transientFailureMidChainIncrementsTheExistingRetryCount() {
        router.retryOrDeadLetter(event, 2, new IllegalStateException("timeout"), null);

        Message message = sentMessage(MessagingTopology.retryQueue(3));
        assertThat((Integer) message.getMessageProperties()
                .getHeader(MessagingTopology.RETRY_COUNT_HEADER)).isEqualTo(3);
    }

    @Test
    void transientFailureAtTheRetryLimitGoesToTheDlqInsteadOfAnotherRetry() {
        router.retryOrDeadLetter(event, 3, new IllegalStateException("still failing"), null);

        Message message = sentMessage(MessagingTopology.INCIDENT_REPORT_CREATED_DLQ);
        assertThat((Integer) message.getMessageProperties()
                .getHeader(MessagingTopology.RETRY_COUNT_HEADER)).isEqualTo(3);
    }

    @Test
    void poisonFailureIsDeadLetteredImmediatelyRegardlessOfRetryCount() {
        router.retryOrDeadLetter(event, 0, new EventTranslationException("unknown report type"), null);
        router.deadLetter(event, 2, new EventTranslationException("unknown priority"), null);

        sentMessage(MessagingTopology.INCIDENT_REPORT_CREATED_DLQ);
    }

    @Test
    void zeroRetriesConfiguredRoutesEveryTransientFailureStraightToTheDlq() {
        properties.setMaxRetries(0);
        properties.setDelays(List.of(Duration.ofSeconds(1)));
        properties.validate();

        router.retryOrDeadLetter(event, null, new IllegalStateException("down"), null);

        sentMessage(MessagingTopology.INCIDENT_REPORT_CREATED_DLQ);
    }

    @Test
    void republishedRetryCopyCarriesTheOriginalCorrelationIdHeader() {
        String correlationId = UUID.randomUUID().toString();

        router.retryOrDeadLetter(event, 0, new IllegalStateException("mongodb unavailable"),
                correlationId);

        Message message = sentMessage(MessagingTopology.retryQueue(1));
        Object correlationHeader = message.getMessageProperties()
                .getHeader(MessagingTopology.CORRELATION_ID_HEADER);
        assertThat(String.valueOf(correlationHeader)).isEqualTo(correlationId);
    }

    @Test
    void deadLetteredCopyCarriesTheOriginalCorrelationIdHeader() {
        String correlationId = UUID.randomUUID().toString();

        router.deadLetter(event, 0, new EventTranslationException("unknown report type"),
                correlationId);

        Message message = sentMessage(MessagingTopology.INCIDENT_REPORT_CREATED_DLQ);
        Object correlationHeader = message.getMessageProperties()
                .getHeader(MessagingTopology.CORRELATION_ID_HEADER);
        assertThat(String.valueOf(correlationHeader)).isEqualTo(correlationId);
    }

    @Test
    void republishedCopiesOmitTheCorrelationHeaderWhenNoneWasReceived() {
        router.retryOrDeadLetter(event, 0, new IllegalStateException("mongodb unavailable"), null);

        Message message = sentMessage(MessagingTopology.retryQueue(1));
        Object correlationHeader = message.getMessageProperties()
                .getHeader(MessagingTopology.CORRELATION_ID_HEADER);
        assertThat(correlationHeader).isNull();
    }
}

