package com.pml.catalog.service;

import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.Location;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.repository.ReferenceDataRepository;
import com.pml.catalog.web.graphql.dto.EventLocationInput;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.regex.Pattern;

/**
 * Turns the venue an organizer typed into a venue under a city of the reference data.
 *
 * <p>Geography has one source: the {@code CITY}, {@code PROVINCE} and {@code COUNTRY} rows of the
 * reference data. The typed city must name an active {@code CITY} row, by name or code and ignoring
 * case, and sit in the typed country (and province, when one is given); anything else is refused
 * with {@code LOCATION_UNKNOWN} rather than stored as free text. The venue itself is shared across
 * organizations, so an existing venue with the same name in the same city is reused.
 */
@Component
@RequiredArgsConstructor
public class VenueResolver {

    private final ReferenceDataRepository referenceData;
    private final ReactiveMongoTemplate mongo;
    private final Clock clock;

    /** The city row a venue input names, with its province and country, or a refusal. */
    public record Place(ReferenceData city, ReferenceData province, ReferenceData country) {
    }

    public Mono<Location> resolve(EventLocationInput input, String organizationId, String actorId) {
        return place(input).flatMap(place -> existingVenue(input, place)
                .switchIfEmpty(Mono.defer(() -> mongo.insert(newVenue(input, place, organizationId, actorId)))));
    }

    public Mono<Place> place(EventLocationInput input) {
        String city = input.city().trim();
        return referenceData.findByTypeAndIsActiveTrueOrderByDisplayOrderAscNameAsc(ReferenceType.CITY)
                .filter(row -> row.getName().equalsIgnoreCase(city) || row.getCode().equalsIgnoreCase(city))
                .next()
                .flatMap(cityRow -> referenceData.findByTypeAndCode(ReferenceType.PROVINCE, cityRow.getParentCode())
                        .filter(ReferenceData::isActive)
                        .flatMap(province -> referenceData.findByTypeAndCode(ReferenceType.COUNTRY, province.getParentCode())
                                .filter(ReferenceData::isActive)
                                .map(country -> new Place(cityRow, province, country))))
                .filter(place -> names(place.country(), input.country())
                        && (input.province() == null || input.province().isBlank() || names(place.province(), input.province())))
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.LOCATION_UNKNOWN,
                        "No active city '" + city + "' in " + input.country() + " in the reference data")));
    }

    private static boolean names(ReferenceData row, String typed) {
        String value = typed.trim();
        return row.getName().equalsIgnoreCase(value) || row.getCode().equalsIgnoreCase(value);
    }

    private Mono<Location> existingVenue(EventLocationInput input, Place place) {
        // The typed name goes into the pattern quoted: it is matched as text, never as a regex.
        Query query = Query.query(Criteria.where("cityId").is(place.city().getCode())
                .and("name").regex("^" + Pattern.quote(input.name().trim()) + "$", "i")
                .and("isActive").is(true));
        return mongo.findOne(query, Location.class, CatalogCollections.LOCATIONS);
    }

    private Location newVenue(EventLocationInput input, Place place, String organizationId, String actorId) {
        return Location.builder()
                .name(input.name().trim())
                .address(input.address().trim())
                .cityId(place.city().getCode())
                .cityName(place.city().getName())
                .provinceName(place.province().getName())
                .country(place.country().getName())
                .postalCode(input.postalCode())
                .latitude(input.coordinates() != null ? input.coordinates().latitude() : coordinate(place.city(), "latitude"))
                .longitude(input.coordinates() != null ? input.coordinates().longitude() : coordinate(place.city(), "longitude"))
                .description(input.description())
                .organizationId(organizationId)
                .createdById(actorId)
                .createdAt(clock.instant())
                .isActive(true)
                .build();
    }

    private static Double coordinate(ReferenceData city, String key) {
        Object value = city.getMetadata() == null ? null : city.getMetadata().get(key);
        return value instanceof Number number ? number.doubleValue() : null;
    }

}
