package com.pml.catalog.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.catalog.domain.model.City;
import com.pml.catalog.domain.model.EventCategory;
import com.pml.catalog.domain.model.Province;
import com.pml.catalog.service.CityService;
import com.pml.catalog.service.EventService;
import com.pml.catalog.service.ProvinceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * Field Resolver for Reference Data types (City, Province, EventCategory).
 *
 * <h2>Business Context</h2>
 * Resolves computed fields that require additional queries for reference data entities.
 * These fields provide derived information used in UI displays and analytics.
 *
 * <h2>Resolved Fields</h2>
 * <ul>
 *   <li><b>EventCategory.eventCount</b> - Count of events in category</li>
 *   <li><b>EventCategory.code</b> - Normalized category code</li>
 *   <li><b>EventCategory.sortOrder</b> - Display order alias</li>
 *   <li><b>Province.cityCount</b> - Count of cities in province</li>
 *   <li><b>Province.formattedName</b> - Formatted display name</li>
 *   <li><b>City.code</b> - Normalized city code</li>
 *   <li><b>City.province</b> - Resolve province from provinceId</li>
 * </ul>
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ReferenceDataFieldResolver {

    private final EventService eventService;
    private final CityService cityService;
    private final ProvinceService provinceService;

    // ═══════════════════════════════════════════════════════════════════════════
    // EVENT CATEGORY FIELD RESOLVERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resolve EventCategory.eventCount - count of events in this category.
     * Delegates to EventService for proper layered architecture.
     */
    @DgsData(parentType = "EventCategory", field = "eventCount")
    public CompletableFuture<Integer> eventCount(DgsDataFetchingEnvironment dfe) {
        EventCategory category = dfe.getSource();
        log.debug("Resolving eventCount for category: {}", category.getId());
        return eventService.countByCategory(category.getId())
                .map(Long::intValue)
                .defaultIfEmpty(0)
                .toFuture();
    }

    /**
     * Resolve EventCategory.code - normalized category code
     */
    @DgsData(parentType = "EventCategory", field = "code")
    public String categoryCode(DgsDataFetchingEnvironment dfe) {
        EventCategory category = dfe.getSource();
        // Return stored code if available, otherwise generate from name
        if (category.getCode() != null) {
            return category.getCode();
        }
        return category.getName() != null
                ? category.getName().toUpperCase().replaceAll("[^A-Z0-9]", "_")
                : null;
    }

    /**
     * Resolve EventCategory.sortOrder - alias for displayOrder
     */
    @DgsData(parentType = "EventCategory", field = "sortOrder")
    public Integer sortOrder(DgsDataFetchingEnvironment dfe) {
        EventCategory category = dfe.getSource();
        return category.getDisplayOrder();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROVINCE FIELD RESOLVERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resolve Province.cityCount - count of cities in this province.
     * Delegates to CityService for proper layered architecture.
     */
    @DgsData(parentType = "Province", field = "cityCount")
    public CompletableFuture<Integer> cityCount(DgsDataFetchingEnvironment dfe) {
        Province province = dfe.getSource();
        log.debug("Resolving cityCount for province: {}", province.getId());
        return cityService.countByProvinceId(province.getId())
                .map(Long::intValue)
                .defaultIfEmpty(0)
                .toFuture();
    }

    /**
     * Resolve Province.formattedName - formatted display name
     */
    @DgsData(parentType = "Province", field = "formattedName")
    public String formattedName(DgsDataFetchingEnvironment dfe) {
        Province province = dfe.getSource();
        if (province.getName() == null) {
            return null;
        }
        // Format: "Province Name, Country"
        if (province.getCountry() != null) {
            return province.getName() + ", " + province.getCountry();
        }
        return province.getName();
    }

    /**
     * Resolve Province.createdBy - audit field
     */
    @DgsData(parentType = "Province", field = "createdBy")
    public String provinceCreatedBy(DgsDataFetchingEnvironment dfe) {
        return "system"; // Default value - extend with audit tracking
    }

    /**
     * Resolve Province.updatedBy - audit field
     */
    @DgsData(parentType = "Province", field = "updatedBy")
    public String provinceUpdatedBy(DgsDataFetchingEnvironment dfe) {
        return "system"; // Default value - extend with audit tracking
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CITY FIELD RESOLVERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resolve City.code - normalized city code
     */
    @DgsData(parentType = "City", field = "code")
    public String cityCode(DgsDataFetchingEnvironment dfe) {
        City city = dfe.getSource();
        // Generate code from name if not stored
        return city.getName() != null
                ? city.getName().toUpperCase().replaceAll("[^A-Z0-9]", "_")
                : null;
    }

    /**
     * Resolve City.province - resolve provinceId to Province
     */
    @DgsData(parentType = "City", field = "province")
    public CompletableFuture<Province> cityProvince(DgsDataFetchingEnvironment dfe) {
        City city = dfe.getSource();
        if (city.getProvinceId() == null) {
            return CompletableFuture.completedFuture(null);
        }
        return provinceService.findById(city.getProvinceId()).toFuture();
    }

    /**
     * Resolve City.createdBy - audit field
     */
    @DgsData(parentType = "City", field = "createdBy")
    public String cityCreatedBy(DgsDataFetchingEnvironment dfe) {
        return "system"; // Default value - extend with audit tracking
    }

    /**
     * Resolve City.updatedBy - audit field
     */
    @DgsData(parentType = "City", field = "updatedBy")
    public String cityUpdatedBy(DgsDataFetchingEnvironment dfe) {
        return "system"; // Default value - extend with audit tracking
    }
}
