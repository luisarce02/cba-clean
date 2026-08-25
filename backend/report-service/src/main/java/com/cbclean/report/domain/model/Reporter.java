package com.cbclean.report.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record Reporter(String name, String email, String phone) {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^\\+?[0-9 ]{6,20}$");

    public Reporter {
        name = normalizeOptional(name, "Reporter name", 100);
        email = normalizeOptional(email, "Reporter email", 200);
        phone = normalizeOptional(phone, "Reporter phone", 25);

        if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidReportException("Reporter email is not a valid email address: " + email);
        }
        if (phone != null && !PHONE_PATTERN.matcher(phone).matches()) {
            throw new InvalidReportException("Reporter phone is not a valid phone number: " + phone);
        }
        if (email == null && phone == null && name != null) {
            throw new InvalidReportException("A reporter without contact details must be anonymous");
        }
    }

    public static Reporter anonymous() {
        return new Reporter(null, null, null);
    }

    public boolean isAnonymous() {
        return email == null && phone == null;
    }

    private static String normalizeOptional(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new InvalidReportException(field + " must not exceed " + maxLength + " characters");
        }
        return trimmed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reporter other)) return false;
        return Objects.equals(name, other.name)
                && Objects.equals(email, other.email)
                && Objects.equals(phone, other.phone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email, phone);
    }
}
