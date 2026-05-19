package com.pml.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * Spring Modulith Event Publication Configuration
 *
 * This configuration sets up the JDBC transaction manager required by Spring Modulith's
 * Event Publication Registry. The registry persists events to PostgreSQL to provide
 * guaranteed delivery semantics.
 *
 * HYBRID ARCHITECTURE:
 * --------------------
 * - Business Data: Reactive MongoDB (uses ReactiveTransactionManager internally)
 * - Event Publication: PostgreSQL JDBC (uses PlatformTransactionManager)
 *
 * WHY THIS WORKS:
 * ---------------
 * Spring Modulith's @ApplicationModuleListener operates synchronously within the
 * event publication flow, even in a WebFlux application. The event is:
 * 1. Persisted to PostgreSQL (blocking JDBC) BEFORE the listener is invoked
 * 2. Listener processes the event (can use reactive code internally)
 * 3. Event marked as completed in PostgreSQL after successful processing
 *
 * If the listener fails or the app crashes, the event remains in PostgreSQL
 * and will be retried on restart.
 *
 * TRANSACTION MANAGER SELECTION:
 * ------------------------------
 * The DataSourceTransactionManager is marked as @Primary because:
 * 1. Spring Modulith's JDBC repository requires a PlatformTransactionManager
 * 2. Reactive MongoDB doesn't use PlatformTransactionManager (uses ReactiveTransactionManager)
 * 3. There's no conflict since they manage different data sources
 *
 * @see org.springframework.modulith.events.jdbc.JdbcEventPublicationConfiguration
 */
@Configuration
@EnableTransactionManagement
public class ModulithEventConfig {

    /**
     * Creates the JDBC transaction manager for Spring Modulith event publication.
     *
     * This transaction manager is used exclusively by Spring Modulith's
     * JdbcEventPublicationRepository to persist and complete event publications.
     *
     * @param dataSource The PostgreSQL DataSource configured in application.yml
     * @return PlatformTransactionManager for JDBC operations
     */
    @Bean("jdbcTransactionManager")
    @Primary
    public PlatformTransactionManager jdbcTransactionManager(DataSource dataSource) {
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        txManager.setDefaultTimeout(30); // 30 second timeout
        return txManager;
    }
}
