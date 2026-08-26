package com.cbclean.report.application.port;

import java.util.function.Supplier;

/**
 * Application-owned port for a transactional unit of work.
 *
 * <p>Expresses the need to execute several persistence operations as a single
 * atomic step without committing the application layer to a concrete
 * transaction technology. The infrastructure adapter (Spring's
 * {@code TransactionTemplate} over the PostgreSQL transaction manager)
 * decides <em>how</em>; a pass-through implementation is available for tests
 * that do not exercise real transactions.</p>
 *
 * <p><strong>Failure semantics:</strong> if the wrapped work throws, no
 * operation performed inside the unit of work takes effect - either all of
 * them are committed or none.</p>
 */
public interface UnitOfWork {

    /**
     * Runs the given work inside one atomic unit (one database transaction).
     *
     * @param work the operations to execute together
     * @return the result produced by the work
     */
    <T> T execute(Supplier<T> work);

    /** Pass-through implementation for tests without a real transaction manager. */
    static UnitOfWork identity() {
        return Supplier::get;
    }
}
