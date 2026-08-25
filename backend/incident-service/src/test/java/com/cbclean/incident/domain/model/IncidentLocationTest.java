package com.cbclean.incident.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentLocationTest {

    @Test
    void coordinatesOnlyLocationHasNoAddressOrZone() {
        IncidentLocation location = IncidentLocation.of(50.4501, 30.5234);

        assertThat(location.latitude()).isEqualTo(50.4501);
        assertThat(location.longitude()).isEqualTo(30.5234);
        assertThat(location.address()).isNull();
        assertThat(location.zone()).isNull();
    }

    @Test
    void valueObjectEqualityIsByAllComponents() {
        IncidentLocation location = new IncidentLocation(10.0, 20.0, "Main Street 1", "ZONE-A");

        assertThat(location).isEqualTo(new IncidentLocation(10.0, 20.0, "Main Street 1", "ZONE-A"));
        assertThat(location).isNotEqualTo(new IncidentLocation(10.0, 20.0, "Main Street 1", "ZONE-B"));
        assertThat(location).isNotEqualTo(new IncidentLocation(11.0, 20.0, "Main Street 1", "ZONE-A"));
    }

    @Test
    void blankAddressAndZoneAreNormalizedToNull() {
        IncidentLocation location = new IncidentLocation(10.0, 20.0, "   ", "");

        assertThat(location.address()).isNull();
        assertThat(location.zone()).isNull();
    }

    @Test
    void latitudeOutOfRangeIsRejected() {
        assertThatThrownBy(() -> IncidentLocation.of(90.01, 0.0))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("Latitude");
    }

    @Test
    void longitudeOutOfRangeIsRejected() {
        assertThatThrownBy(() -> IncidentLocation.of(0.0, -180.01))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("Longitude");
    }

    @Test
    void oversizedFieldsAreRejected() {
        String longAddress = "a".repeat(301);
        String longZone = "z".repeat(51);

        assertThatThrownBy(() -> new IncidentLocation(0.0, 0.0, longAddress, null))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("Address");

        assertThatThrownBy(() -> new IncidentLocation(0.0, 0.0, null, longZone))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("Zone");
    }
}
