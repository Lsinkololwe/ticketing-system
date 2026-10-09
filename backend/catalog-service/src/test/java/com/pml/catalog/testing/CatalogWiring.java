package com.pml.catalog.testing;

import com.pml.catalog.repository.ReferenceDataRepository;
import com.pml.catalog.repository.TicketTierRepository;
import com.pml.catalog.service.EventTierMirror;
import com.pml.catalog.service.TicketTierFactory;
import com.pml.catalog.service.VenueResolver;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;

import java.time.Clock;

/** The real collaborators event and tier writes need, over one template, for tests that build services by hand. */
public final class CatalogWiring {

    private CatalogWiring() {
    }

    /** A template that writes as the application does: money as Decimal128, not as a string. */
    public static ReactiveMongoTemplate platformTemplate(com.mongodb.reactivestreams.client.MongoClient client, String database) {
        org.springframework.data.mongodb.core.convert.MongoCustomConversions conversions =
                new com.pml.shared.persistence.MoneyConversions().mongoCustomConversions();
        org.springframework.data.mongodb.core.mapping.MongoMappingContext context =
                new org.springframework.data.mongodb.core.mapping.MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        org.springframework.data.mongodb.core.convert.MappingMongoConverter converter =
                new org.springframework.data.mongodb.core.convert.MappingMongoConverter(
                        org.springframework.data.mongodb.core.convert.NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        return new ReactiveMongoTemplate(
                new org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory(client, database), converter);
    }

    public static TicketTierRepository tiers(ReactiveMongoTemplate template) {
        return new ReactiveMongoRepositoryFactory(template).getRepository(TicketTierRepository.class);
    }

    public static VenueResolver venues(ReactiveMongoTemplate template, Clock clock) {
        return new VenueResolver(new ReactiveMongoRepositoryFactory(template).getRepository(ReferenceDataRepository.class),
                template, clock);
    }

    public static TicketTierFactory tierFactory() {
        return new TicketTierFactory(10);
    }

    public static com.pml.catalog.service.EventCategories categories(ReactiveMongoTemplate template) {
        return new com.pml.catalog.service.EventCategories(
                new ReactiveMongoRepositoryFactory(template).getRepository(ReferenceDataRepository.class));
    }

    public static EventTierMirror mirror(ReactiveMongoTemplate template, Clock clock) {
        return new EventTierMirror(tiers(template), template, clock);
    }
}
