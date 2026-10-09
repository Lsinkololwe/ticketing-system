package com.pml.catalog.domain.model;

import com.pml.catalog.persistence.CatalogCollections;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Location Model
 *
 * Represents a simple address/location for events.
 * The platform does not own/manage venues - this is just location info.
 */
@Document(collection = CatalogCollections.LOCATIONS)
@TypeAlias("locations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Location {

    @Id
    private String id;

    private String name;

    private String address;

    /** The reference-data {@code CITY} code of the venue; {@link #cityName} is that row's name, copied for display. */
    private String cityId;

    private String cityName;

    private String provinceName;

    private String country;

    private String postalCode;

    private Double latitude;

    private Double longitude;

    private String description;

    /** The organization that created the venue; venues are shared, attribution is kept. */
    private String organizationId;

    private String createdById;

    private java.time.Instant createdAt;

    @Builder.Default
    private boolean isActive = true;

    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder();
        if (address != null && !address.isEmpty()) {
            sb.append(address);
        }
        if (cityName != null && !cityName.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(cityName);
        }
        if (provinceName != null && !provinceName.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(provinceName);
        }
        if (country != null && !country.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(country);
        }
        return sb.toString();
    }

    public boolean hasValidCoordinates() {
        return latitude != null && longitude != null &&
                latitude >= -90 && latitude <= 90 &&
                longitude >= -180 && longitude <= 180;
    }
}
