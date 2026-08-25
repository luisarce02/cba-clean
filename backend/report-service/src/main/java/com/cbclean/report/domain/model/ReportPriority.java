package com.cbclean.report.domain.model;

public enum ReportPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL;

    public boolean isHigherThan(ReportPriority other) {
        return compareTo(other) > 0;
    }
}
