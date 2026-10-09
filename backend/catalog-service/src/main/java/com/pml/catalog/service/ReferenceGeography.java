package com.pml.catalog.service;

import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.City;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.EventCategory;
import com.pml.catalog.domain.model.Province;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.repository.ReferenceDataRepository;
import com.pml.shared.constants.EventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * The public province, city and category lists, answered from the reference data.
 *
 * <p>There is one list of each. Event validation, venue resolution and the seed already treat the
 * {@code PROVINCE}, {@code CITY} and {@code EVENT_CATEGORY} rows as the source ({@link EventCategories},
 * {@link VenueResolver}), and an event stores a category or city by its <em>code</em>. The public
 * {@code categories}, {@code provinces}, {@code cities} and {@code citiesWithEvents} queries used to read
 * three separate collections that nothing seeded, so every dropdown they feed was empty on a fresh
 * database while the lists they should have shown sat one collection over. These return the same
 * answer from the reference rows, with {@code id} equal to the code so the id a client sends back is
 * the code an event is filed under.
 */
@Component
@RequiredArgsConstructor
public class ReferenceGeography {

    private final ReferenceDataRepository referenceData;
    private final ReactiveMongoTemplate mongo;

    public Flux<EventCategory> categories() {
        return referenceData.findByTypeAndIsActiveTrueOrderByDisplayOrderAscNameAsc(ReferenceType.EVENT_CATEGORY)
                .map(row -> EventCategory.builder()
                        .id(row.getCode()).code(row.getCode()).name(row.getName())
                        .description(row.getDescription()).displayOrder(row.getDisplayOrder())
                        .isActive(true).createdAt(row.getCreatedAt()).updatedAt(row.getUpdatedAt())
                        .build());
    }

    public Flux<Province> provinces() {
        return referenceData.findByTypeAndIsActiveTrueOrderByDisplayOrderAscNameAsc(ReferenceType.PROVINCE)
                .concatMap(row -> countryName(row.getParentCode()).map(country -> Province.builder()
                        .id(row.getCode()).code(row.getCode()).name(row.getName()).country(country)
                        .isActive(true).createdAt(row.getCreatedAt()).updatedAt(row.getUpdatedAt()).build()));
    }

    /** Active cities, optionally those of one province (named by its code). */
    public Flux<City> cities(String provinceCode) {
        return provinceNames().flatMapMany(names -> {
            Flux<ReferenceData> rows = provinceCode == null || provinceCode.isBlank()
                    ? referenceData.findByTypeAndIsActiveTrueOrderByDisplayOrderAscNameAsc(ReferenceType.CITY)
                    : referenceData.findByTypeAndParentCodeAndIsActiveTrueOrderByDisplayOrderAscNameAsc(
                            ReferenceType.CITY, provinceCode);
            return rows.map(row -> city(row, names, 0));
        });
    }

    /** Cities with at least one published event, with how many. */
    public Flux<City> citiesWithEvents() {
        Criteria published = Criteria.where("status").is(EventStatus.PUBLISHED.name()).and("published").is(true)
                .and("isActive").is(true).and("isDeleted").is(false).and("cityId").ne(null);
        Mono<Map<String, Integer>> counts = mongo.aggregate(
                        Aggregation.newAggregation(Aggregation.match(published),
                                Aggregation.group("cityId").count().as("n")),
                        CatalogCollections.EVENTS, org.bson.Document.class)
                .collectMap(doc -> doc.getString("_id"), doc -> ((Number) doc.get("n")).intValue());
        return Mono.zip(counts, provinceNames()).flatMapMany(both -> referenceData
                .findByTypeAndIsActiveTrueOrderByDisplayOrderAscNameAsc(ReferenceType.CITY)
                .filter(row -> both.getT1().containsKey(row.getCode()))
                .map(row -> city(row, both.getT2(), both.getT1().get(row.getCode()))));
    }

    public Mono<Long> cityCount(String provinceCode) {
        return referenceData.findByTypeAndParentCodeAndIsActiveTrueOrderByDisplayOrderAscNameAsc(
                ReferenceType.CITY, provinceCode).count();
    }

    private Mono<String> countryName(String countryCode) {
        if (countryCode == null) {
            return Mono.just("");
        }
        return referenceData.findByTypeAndCode(ReferenceType.COUNTRY, countryCode)
                .map(ReferenceData::getName).defaultIfEmpty(countryCode);
    }

    private Mono<Map<String, String>> provinceNames() {
        return referenceData.findByTypeOrderByDisplayOrderAscNameAsc(ReferenceType.PROVINCE)
                .collectMap(ReferenceData::getCode, ReferenceData::getName, HashMap::new);
    }

    private static City city(ReferenceData row, Map<String, String> provinces, int eventCount) {
        return City.builder().id(row.getCode()).name(row.getName()).provinceId(row.getParentCode())
                .provinceName(provinces.get(row.getParentCode())).country("Zambia")
                .eventCount(eventCount).isActive(true).createdAt(row.getCreatedAt()).updatedAt(row.getUpdatedAt())
                .build();
    }
}
