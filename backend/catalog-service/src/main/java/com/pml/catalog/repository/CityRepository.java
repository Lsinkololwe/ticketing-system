package com.pml.catalog.repository;

import com.pml.catalog.domain.model.City;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * City Repository with cursor-based and admin pagination support.
 */
@Repository
public interface CityRepository extends ReactiveMongoRepository<City, String> {

    // ==========================================
    // Admin pagination (for admin dashboard tables)
    // ==========================================

    Flux<City> findAllBy(Pageable pageable);

    // Count queries
    Mono<Long> countByProvinceId(String provinceId);

    // ==========================================
    // Flux-based Queries (for service layer)
    // ==========================================

    @Query("{ 'isActive': true, 'eventCount': { '$gt': 0 } }")
    Flux<City> findCitiesWithEvents();
}
