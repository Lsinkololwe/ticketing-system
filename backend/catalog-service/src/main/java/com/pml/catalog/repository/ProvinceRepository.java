package com.pml.catalog.repository;

import com.pml.catalog.domain.model.Province;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
/**
 * Province Repository with cursor-based and admin pagination support.
 */
@Repository
public interface ProvinceRepository extends ReactiveMongoRepository<Province, String> {

    // ==========================================
    // Admin pagination (for admin dashboard tables)
    // ==========================================

    Flux<Province> findAllBy(Pageable pageable);

    // ==========================================
    // Flux-based Queries (for service layer)
    // ==========================================

}
