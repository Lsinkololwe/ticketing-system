package com.pml.identity.migration;

import com.pml.shared.migration.MigrationLedger;
import com.pml.shared.migration.MigrationRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Identity's data migrations, in dependency order.
 *
 * <h2>What was wrong before this existed</h2>
 * identity-service had one migration and no runner, so
 * {@link OrganizationStatusSemanticMigrationService} had never run anywhere. The
 * consequence was visible in the live database and nowhere else: organizations
 * written before {@code statusSemantic} existed carry no semantic at all, and the
 * admin review queue selects <em>by semantic</em>. A pending application is
 * therefore absent from the queue that exists to show it — which reads as
 * "nothing to review" rather than as a fault, and is exactly the failure the
 * migration's own javadoc warned about while nothing called it.
 *
 * <p>Its integration test passed throughout, because it invoked the migration
 * directly. Same shape as booking's seven dead migrations and catalog's
 * unwired reference-data bootstrapper.
 *
 * @see MigrationRunner
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityMigrationRunner extends MigrationRunner {

    /** Separate from booking's, so each service's history reads on its own. */
    static final String LEDGER_COLLECTION = "identity_migrations";

    private final ReactiveMongoTemplate mongoTemplate;

    /** The platform clock (ET-PLT-001 R3), handed to the ledger so its audit rows are testable. */
    private final Clock clock;

    private final OrganizationStatusSemanticMigrationService organizationSemantics;

    @Value("${identity.migrations.enabled:true}")
    private boolean enabled;

    @Value("${identity.migrations.fail-fast:true}")
    private boolean failFast;

    @Override
    protected MigrationLedger ledger() {
        return new MigrationLedger(mongoTemplate, LEDGER_COLLECTION, clock);
    }

    @Override
    protected boolean isEnabled() {
        return enabled;
    }

    @Override
    protected boolean isFailFast() {
        return failFast;
    }

    @Override
    protected String serviceName() {
        return "identity-service";
    }

    @Override
    protected Map<String, Supplier<Mono<?>>> steps() {
        Map<String, Supplier<Mono<?>>> steps = new LinkedHashMap<>();

        // Stamps statusSemantic onto organizations written before the field
        // existed. It resolves each status against catalog's reference data, so
        // it needs those rows to exist — which they now do, since catalog seeds
        // them at startup rather than never.
        steps.put("organization-status-semantic", organizationSemantics::migrate);

        return steps;
    }
}
