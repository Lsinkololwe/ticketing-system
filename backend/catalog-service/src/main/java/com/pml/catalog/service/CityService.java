package com.pml.catalog.service;

import com.pml.catalog.domain.model.City;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * City Service interface with cursor-based and admin pagination support.
 */
public interface CityService {

    // ==========================================
    // Single City Operations
    // ==========================================

    Mono<City> findById(String id);

    Mono<City> createCity(City city);

    Mono<City> updateCity(String id, City city);

    Mono<Void> deleteCity(String id);

    // ==========================================
    // Flux-based Queries (for pagination helper methods)
    // ==========================================

    Flux<City> findCitiesWithEvents();

    // ==========================================
    // Count Operations
    // ==========================================

    /**
     * Count cities in a province.
     * Used by ReferenceDataFieldResolver for Province.cityCount field.
     *
     * @param provinceId The province ID
     * @return Count of cities in the province
     */
    Mono<Long> countByProvinceId(String provinceId);
}
