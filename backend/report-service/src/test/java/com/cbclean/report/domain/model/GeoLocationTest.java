package com.cbclean.report.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoLocationTest {

    @Test
    void acceptsValidCoordinates() {
        GeoLocation location = new GeoLocation(-90, 180, "  Hauptplatz 1  ");

        assertThat(location.latitude()).isEqualTo(-90.0);
        assertThat(location.longitude()).isEqualTo(180.0);
        assertThat(location.address()).isEqualTo("Hauptplatz 1");
    }

    @Test
    void rejectsOutOfRangeCoordinates() {
        assertThatThrownBy(() -> new GeoLocation(90.01, 0, null))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("Latitude");

        assertThatThrownBy(() -> new GeoLocation(0, -180.01, null))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("Longitude");
    }

    @Test
    void blankAddressBecomesNull() {
        assertThat(GeoLocation.of(0, 0).address()).isNull();
        assertThat(new GeoLocation(0, 0, "   ").address()).isNull();
    }

    @Test
    void valueEqualityIgnoresNothing() {
        assertThat(GeoLocation.of(1.5, 2.5)).isEqualTo(GeoLocation.of(1.5, 2.5));
        assertThat(GeoLocation.of(1.5, 2.5)).isNotEqualTo(GeoLocation.of(1.5, 2.6));
        Set<GeoLocation> set = new java.util.HashSet<>();
        assertThat(set.add(GeoLocation.of(1.5, 2.5))).isTrue();
        assertThat(set.add(GeoLocation.of(1.5, 2.5))).isFalse();
        assertThat(set).hasSize(1);
    }
}
