package com.cbclean.report.infrastructure.transaction;

import com.cbclean.report.application.port.UnitOfWork;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Spring transaction adapter for the {@link UnitOfWork} application port.
 *
 * <p>Runs the wrapped work in a PostgreSQL transaction via
 * {@link TransactionTemplate}. Persistence adapters called inside the unit of
 * work (e.g. the report repository and the outbox store) join this existing
 * transaction with default {@code PROPAGATION_REQUIRED} semantics - which is
 * what makes "save report + append outbox entry" atomic.</p>
 */
@Component
public class SpringUnitOfWork implements UnitOfWork {

    private final TransactionTemplate transactions;

    public SpringUnitOfWork(PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
