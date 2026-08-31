package com.cbclean.report.application.report.list;

import java.util.List;

/**
 * Generic paginated result wrapper used by the application layer.
 * Decoupled from Spring to keep the use-case signatures framework-agnostic.
 */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
