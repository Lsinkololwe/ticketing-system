package com.pml.catalog.service.impl;

import com.pml.catalog.dto.*;
import com.pml.catalog.domain.model.City;
import com.pml.catalog.repository.CityRepository;
import com.pml.catalog.service.CityService;
import com.pml.catalog.util.CursorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * City Service Implementation with cursor-based and admin pagination.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CityServiceImpl implements CityService {

    private final CityRepository cityRepository;

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
        city.setCreatedAt(LocalDateTime.now());
        city.setUpdatedAt(LocalDateTime.now());
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
                    existing.setUpdatedAt(LocalDateTime.now());
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
    public Flux<City> findAllCities() {
        return cityRepository.findByIsActiveTrue();
    }

    @Override
    public Flux<City> findCitiesByProvince(String provinceId) {
        return cityRepository.findByProvinceIdAndIsActiveTrue(provinceId);
    }

    @Override
    public Flux<City> findCitiesByCountry(String country) {
        return cityRepository.findByCountryAndIsActiveTrue(country);
    }

    @Override
    public Flux<City> searchCities(String query) {
        return cityRepository.findByNameContainingIgnoreCaseAndIsActiveTrue(query);
    }

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

    // ==========================================
    // Cursor-based Pagination (for mobile infinite scroll)
    // ==========================================

    @Override
    public Mono<CityConnection> findCitiesCursor(CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<City> citiesFlux;
        if (afterId != null) {
            citiesFlux = cityRepository.findCitiesAfterCursor(afterId, pageable);
        } else {
            citiesFlux = cityRepository.findByIsActiveTrueOrderByNameAsc(pageable);
        }

        return buildConnection(citiesFlux, limit, afterId != null);
    }

    @Override
    public Mono<CityConnection> findCitiesByProvinceCursor(String provinceId, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<City> citiesFlux;
        if (afterId != null) {
            citiesFlux = cityRepository.findByProvinceAfterCursor(provinceId, afterId, pageable);
        } else {
            citiesFlux = cityRepository.findByProvinceFirstPage(provinceId, pageable);
        }

        return buildConnection(citiesFlux, limit, afterId != null);
    }

    @Override
    public Mono<CityConnection> searchCitiesCursor(String query, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<City> citiesFlux;
        if (afterId != null) {
            citiesFlux = cityRepository.searchCitiesAfterCursor(query, afterId, pageable);
        } else {
            citiesFlux = cityRepository.searchCitiesFirstPage(query, pageable);
        }

        return buildConnection(citiesFlux, limit, afterId != null);
    }

    // ==========================================
    // Admin Pagination (for dashboard tables)
    // ==========================================

    @Override
    public Mono<PagedResult<City>> findCitiesAdmin(PageableInput pageable) {
        Pageable springPageable = pageable.toPageable();

        return Mono.zip(
                cityRepository.findAllBy(springPageable).collectList(),
                cityRepository.count()
        ).map(tuple -> PagedResult.of(
                tuple.getT1(),
                springPageable.getPageNumber(),
                springPageable.getPageSize(),
                tuple.getT2()
        ));
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private Mono<CityConnection> buildConnection(Flux<City> citiesFlux, int limit, boolean hasPreviousPage) {
        return citiesFlux.collectList().map(cities -> {
            boolean hasNextPage = cities.size() > limit;

            List<City> pageCities = hasNextPage
                    ? cities.subList(0, limit)
                    : cities;

            List<CityEdge> edges = pageCities.stream()
                    .map(CityEdge::from)
                    .toList();

            PageInfo pageInfo = PageInfo.builder()
                    .hasNextPage(hasNextPage)
                    .hasPreviousPage(hasPreviousPage)
                    .startCursor(edges.isEmpty() ? null : edges.get(0).getCursor())
                    .endCursor(edges.isEmpty() ? null : edges.get(edges.size() - 1).getCursor())
                    .build();

            return CityConnection.builder()
                    .edges(edges)
                    .pageInfo(pageInfo)
                    .build();
        });
    }
}
