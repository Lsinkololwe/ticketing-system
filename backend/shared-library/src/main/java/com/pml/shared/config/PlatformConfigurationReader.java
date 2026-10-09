package com.pml.shared.config;

import com.pml.shared.config.model.PlatformPaymentDefaults;
import com.pml.shared.config.model.PlatformRulesSection;
import com.pml.shared.config.model.PlatformRulesView;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

/**
 * Reads the platform settings for the services that do not own them.
 *
 * <p>The platform keeps one settings table, {@code catalog_platform_configuration}. Catalog is its
 * only writer; every other service reads it here, directly and read-only, the same arrangement as
 * the reference data. Asking catalog over the graph instead would put catalog in the path of
 * creating an organization, and a settings read is not worth that dependency.
 *
 * <p>The collection name is a literal because shared-library may not import from a service.
 */
public class PlatformConfigurationReader {

    static final String COLLECTION = "catalog_platform_configuration";
    static final String DOCUMENT_ID = "platform-config";

    private final Supplier<ReactiveMongoTemplate> mongoTemplate;

    public PlatformConfigurationReader(ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider) {
        this.mongoTemplate = mongoTemplateProvider::getObject;
    }

    /** Direct construction, for tests that build a template themselves. */
    public PlatformConfigurationReader(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = () -> mongoTemplate;
    }

    /**
     * The payment, payout and commission defaults a new organization starts from; empty when the
     * settings document or its payment section is absent. No default is invented here: these are
     * financial values and their only source is the settings table.
     */
    public Mono<PlatformPaymentDefaults> paymentDefaults() {
        ReactiveMongoTemplate template = mongoTemplate.get();
        return template.findOne(Query.query(Criteria.where("_id").is(DOCUMENT_ID)), Document.class, COLLECTION)
                .mapNotNull(settings -> settings.get("payment", Document.class))
                .map(payment -> template.getConverter().read(PlatformPaymentDefaults.class, payment));
    }

    /**
     * The rules organizers and buyers obey, read from the same document; empty until catalog has
     * seeded the {@code rules} section.
     */
    public Mono<PlatformRulesView> rules() {
        ReactiveMongoTemplate template = mongoTemplate.get();
        return template.findOne(Query.query(Criteria.where("_id").is(DOCUMENT_ID)), Document.class, COLLECTION)
                .filter(settings -> settings.get("rules", Document.class) != null)
                .map(settings -> {
                    PlatformRulesSection rules = template.getConverter()
                            .read(PlatformRulesSection.class, settings.get("rules", Document.class));
                    Document payment = settings.get("payment", Document.class);
                    return PlatformRulesView.builder()
                            .rules(rules)
                            .commissionRate(payment == null ? null : number(payment.get("commissionRate")))
                            .minimumPayout(payment == null ? null : money(payment.get("minimumPayoutAmount")))
                            .approvalSlaHours(integer(settings.get("approvalSlaHours")))
                            .approvalWarningThresholdHours(integer(settings.get("approvalWarningThresholdHours")))
                            .autoEscalationEnabled(Boolean.TRUE.equals(settings.get("autoEscalationEnabled")))
                            .escalationDelayHours(integer(settings.get("escalationDelayHours")))
                            .requireCommentsOnRejection(Boolean.TRUE.equals(settings.get("requireCommentsOnRejection")))
                            .requireCommentsOnChangesRequested(
                                    Boolean.TRUE.equals(settings.get("requireCommentsOnChangesRequested")))
                            .allowSelfApproval(Boolean.TRUE.equals(settings.get("allowSelfApproval")))
                            .updatedAt(settings.get("updatedAt") instanceof java.util.Date d ? d.toInstant() : null)
                            .updatedBy(settings.getString("updatedBy"))
                            .build();
                });
    }

    private static int integer(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static Double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    private static java.math.BigDecimal money(Object value) {
        if (value instanceof org.bson.types.Decimal128 d) {
            return d.bigDecimalValue();
        }
        return value instanceof Number n ? new java.math.BigDecimal(n.toString()) : null;
    }
}
