package com.cbclean.incident.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentIdTest {

    @Test
    void newIdProducesUniqueNonNullIds() {
        IncidentId first = IncidentId.newId();
        IncidentId second = IncidentId.newId();

        assertThat(first.value()).isNotNull();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void fromStringParsesValidUuid() {
        UUID uuid = UUID.randomUUID();

        assertThat(IncidentId.fromString(uuid.toString()).value()).isEqualTo(uuid);
    }

    @Test
    void nullValueIsRejected() {
        assertThatThrownBy(() -> new IncidentId(null))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void blankStringIsRejected() {
        assertThatThrownBy(() -> IncidentId.fromString("   "))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void malformedStringIsRejected() {
        assertThatThrownBy(() -> IncidentId.fromString("not-a-uuid"))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("valid UUID");
    }

    @Test
    void valueObjectEqualityIsByContent() {
        UUID uuid = UUID.randomUUID();

        assertThat(new IncidentId(uuid)).isEqualTo(new IncidentId(uuid));
        assertThat(new IncidentId(uuid)).hasSameHashCodeAs(new IncidentId(uuid));
        assertThat(new IncidentId(uuid)).isNotEqualTo(new IncidentId(UUID.randomUUID()));
    }
}
