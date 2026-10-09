package com.pml.catalog.service.impl;

import com.pml.catalog.domain.model.Province;
import com.pml.catalog.repository.ProvinceRepository;
import com.pml.catalog.service.ProvinceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Province Service Implementation with cursor-based and admin pagination.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvinceServiceImpl implements ProvinceService {

    private final ProvinceRepository provinceRepository;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;

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
        province.setCreatedAt(clock.instant());
        province.setUpdatedAt(clock.instant());
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
                    existing.setUpdatedAt(clock.instant());
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

}
