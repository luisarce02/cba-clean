package com.cbclean.incident.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Inclusive date range for filtering incidents by {@code createdAt}.
 * Both boundaries are nullable: {@code null} from means "no lower bound";
 * {@code null} to means "no upper bound".
 */
public record DateRange(Instant from, Instant to) {

    public DateRange {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
    }

    public static DateRange of(Instant from, Instant to) {
        return new DateRange(
                from == null ? null : Objects.requireNonNull(from),
                to == null ? null : Objects.requireNonNull(to));
    }
}
