package com.pml.catalog.service.impl;

import com.pml.catalog.dto.*;
import com.pml.catalog.domain.model.Province;
import com.pml.catalog.repository.ProvinceRepository;
import com.pml.catalog.service.ProvinceService;
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
 * Province Service Implementation with cursor-based and admin pagination.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvinceServiceImpl implements ProvinceService {

    private final ProvinceRepository provinceRepository;

    // ==========================================
    // Single Province Operations
    // ==========================================

    @Override
    public Mono<Province> findById(String id) {
        return provinceRepository.findById(id);
    }

    @Override
    public Mono<Province> createProvince(Province province) {
        log.info("Creating province: {}", province.getName());
        province.setCreatedAt(LocalDateTime.now());
        province.setUpdatedAt(LocalDateTime.now());
        province.setActive(true);
        return provinceRepository.save(province)
                .doOnSuccess(p -> log.info("Province created: {}", p.getId()));
    }

    @Override
    public Mono<Province> updateProvince(String id, Province province) {
        return provinceRepository.findById(id)
                .flatMap(existing -> {
                    existing.setName(province.getName());
                    existing.setCode(province.getCode());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return provinceRepository.save(existing);
                });
    }

    @Override
    public Mono<Void> deleteProvince(String id) {
        return provinceRepository.deleteById(id);
    }

    // ==========================================
    // Flux-based Queries (for pagination helper methods)
    // ==========================================

    @Override
    public Flux<Province> findAllProvinces() {
        return provinceRepository.findByIsActiveTrue();
    }

    @Override
    public Flux<Province> findProvincesByCountry(String country) {
        return provinceRepository.findByCountryAndIsActiveTrue(country);
    }

    @Override
    public Flux<Province> searchProvinces(String query) {
        return provinceRepository.findByNameContainingIgnoreCaseAndIsActiveTrue(query);
    }

    // ==========================================
    // Cursor-based Pagination (for mobile infinite scroll)
    // ==========================================

    @Override
    public Mono<ProvinceConnection> findProvincesCursor(CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<Province> provincesFlux;
        if (afterId != null) {
            provincesFlux = provinceRepository.findProvincesAfterCursor(afterId, pageable);
        } else {
            provincesFlux = provinceRepository.findByIsActiveTrueOrderByNameAsc(pageable);
        }

        return buildConnection(provincesFlux, limit, afterId != null);
    }

    @Override
    public Mono<ProvinceConnection> searchProvincesCursor(String query, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<Province> provincesFlux;
        if (afterId != null) {
            provincesFlux = provinceRepository.searchProvincesAfterCursor(query, afterId, pageable);
        } else {
            provincesFlux = provinceRepository.searchProvincesFirstPage(query, pageable);
        }

        return buildConnection(provincesFlux, limit, afterId != null);
    }

    // ==========================================
    // Admin Pagination (for dashboard tables)
    // ==========================================

    @Override
    public Mono<PagedResult<Province>> findProvincesAdmin(PageableInput pageable) {
        Pageable springPageable = pageable.toPageable();

        return Mono.zip(
                provinceRepository.findAllBy(springPageable).collectList(),
                provinceRepository.count()
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

    private Mono<ProvinceConnection> buildConnection(Flux<Province> provincesFlux, int limit, boolean hasPreviousPage) {
        return provincesFlux.collectList().map(provinces -> {
            boolean hasNextPage = provinces.size() > limit;

            List<Province> pageProvinces = hasNextPage
                    ? provinces.subList(0, limit)
                    : provinces;

            List<ProvinceEdge> edges = pageProvinces.stream()
                    .map(ProvinceEdge::from)
                    .toList();

            PageInfo pageInfo = PageInfo.builder()
                    .hasNextPage(hasNextPage)
                    .hasPreviousPage(hasPreviousPage)
                    .startCursor(edges.isEmpty() ? null : edges.get(0).getCursor())
                    .endCursor(edges.isEmpty() ? null : edges.get(edges.size() - 1).getCursor())
                    .build();

            return ProvinceConnection.builder()
                    .edges(edges)
                    .pageInfo(pageInfo)
                    .build();
        });
    }
}
