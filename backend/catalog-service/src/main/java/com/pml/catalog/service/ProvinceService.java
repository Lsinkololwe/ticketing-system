package com.pml.catalog.service;

import com.pml.catalog.dto.CursorPaginationInput;
import com.pml.catalog.dto.PageableInput;
import com.pml.catalog.dto.PagedResult;
import com.pml.catalog.dto.ProvinceConnection;
import com.pml.catalog.domain.model.Province;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Province Service interface with cursor-based and admin pagination support.
 */
public interface ProvinceService {

    // ==========================================
    // Single Province Operations
    // ==========================================

    Mono<Province> findById(String id);

    Mono<Province> createProvince(Province province);

    Mono<Province> updateProvince(String id, Province province);

    Mono<Void> deleteProvince(String id);

    // ==========================================
    // Flux-based Queries (for pagination helper methods)
    // ==========================================

    Flux<Province> findAllProvinces();

    Flux<Province> findProvincesByCountry(String country);

    Flux<Province> searchProvinces(String query);

    // ==========================================
    // Cursor-based Pagination (for mobile infinite scroll)
    // ==========================================

    Mono<ProvinceConnection> findProvincesCursor(CursorPaginationInput pagination);

    Mono<ProvinceConnection> searchProvincesCursor(String query, CursorPaginationInput pagination);

    // ==========================================
    // Admin Pagination (for dashboard tables)
    // ==========================================

    Mono<PagedResult<Province>> findProvincesAdmin(PageableInput pageable);
}
