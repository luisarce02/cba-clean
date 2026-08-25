package com.cbclean.report.domain.model;

import java.util.Set;

public enum ReportStatus {
    NEW,
    ACKNOWLEDGED,
    IN_PROGRESS,
    RESOLVED,
    CANCELLED;

    public boolean canTransitionTo(ReportStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isClosed() {
        return this == RESOLVED || this == CANCELLED;
    }

    private Set<ReportStatus> allowedTransitions() {
        return switch (this) {
            case NEW -> Set.of(ACKNOWLEDGED, IN_PROGRESS, CANCELLED);
            case ACKNOWLEDGED -> Set.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> Set.of(RESOLVED, CANCELLED);
            case RESOLVED, CANCELLED -> Set.of();
        };
    }
}
