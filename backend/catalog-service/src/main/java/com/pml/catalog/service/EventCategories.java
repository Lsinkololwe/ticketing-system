package com.pml.catalog.service;

import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.repository.ReferenceDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * An event's category is an {@code EVENT_CATEGORY} row of the reference data, named by its code.
 * There is no second list: an administrator adds, renames or deactivates a category there.
 */
@Component
@RequiredArgsConstructor
public class EventCategories {

    private final ReferenceDataRepository referenceData;

    /** Whether {@code code} names a category an organizer may file a new or edited event under. */
    public Mono<Boolean> isSelectable(String code) {
        return referenceData.findByTypeAndCode(ReferenceType.EVENT_CATEGORY, code)
                .map(ReferenceData::isActive)
                .defaultIfEmpty(false);
    }
}
