package com.pml.catalog.service.impl;

import com.pml.catalog.domain.model.City;
import com.pml.catalog.repository.CityRepository;
import com.pml.catalog.service.CityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * City Service Implementation with cursor-based and admin pagination.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CityServiceImpl implements CityService {

    private final CityRepository cityRepository;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;

    // ==========================================
    // Single City Operations
    // ==========================================

    @Override
    public Mono<City> findById(String id) {
        return cityRepository.findById(id);
    }

    @Override
    public Mono<City> createCity(City city) {
        log.info("Creating city: {}", city.getName());
        city.setCreatedAt(clock.instant());
        city.setUpdatedAt(clock.instant());
        city.setActive(true);
        return cityRepository.save(city)
                .doOnSuccess(c -> log.info("City created: {}", c.getId()));
    }

    @Override
    public Mono<City> updateCity(String id, City city) {
        return cityRepository.findById(id)
                .flatMap(existing -> {
                    existing.setName(city.getName());
                    existing.setProvinceId(city.getProvinceId());
                    existing.setProvinceName(city.getProvinceName());
                    existing.setUpdatedAt(clock.instant());
                    return cityRepository.save(existing);
                });
    }

    @Override
    public Mono<Void> deleteCity(String id) {
        return cityRepository.deleteById(id);
    }

    // ==========================================
    // Flux-based Queries (for pagination helper methods)
    // ==========================================

    @Override
    public Flux<City> findCitiesWithEvents() {
        return cityRepository.findCitiesWithEvents();
    }

    // ==========================================
    // Count Operations
    // ==========================================

    @Override
    public Mono<Long> countByProvinceId(String provinceId) {
        log.debug("Counting cities for province: {}", provinceId);
        return cityRepository.countByProvinceId(provinceId);
    }
}
