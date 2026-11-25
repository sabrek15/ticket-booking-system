package com.booking.core.config.datasource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {

        if (RoutingContextHolder.isForcePrimary()) {
            log.info("⚡ [CQRS Routing] Read-After-Write Consistency Active! Routing read query to PRIMARY to eliminate replication lag.");
            return DataSourceType.PRIMARY;
        }

        boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();

        if (isReadOnly) {
            log.info("📖 [CQRS Routing] @Transactional(readOnly = true) -> Routing to REPLICA (Reader)");
            return DataSourceType.REPLICA;
        }

        log.info("✍️ [CQRS Routing] @Transactional(readOnly = false) -> Routing to PRIMARY (Writer)");
        return DataSourceType.PRIMARY;
    }
}
