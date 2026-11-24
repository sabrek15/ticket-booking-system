package com.booking.core.config.datasource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();

        if (isReadOnly) {
            log.info("📖 [CQRS Routing] @Transactional(readOnly = true) -> Routing to REPLICA (Reader)");
            return DataSourceType.REPLICA;
        }

        log.info("✍️ [CQRS Routing] @Transactional(readOnly = false) -> Routing to PRIMARY (Writer)");
        return DataSourceType.PRIMARY;
    }
}
