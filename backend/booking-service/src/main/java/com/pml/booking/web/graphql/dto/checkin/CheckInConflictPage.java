package com.pml.booking.web.graphql.dto.checkin;

import com.pml.booking.domain.model.CheckInConflict;

import java.util.List;

/**
 * A page of refused scans.
 *
 * @param totalElements the full count, so the client can show "12 conflicts"
 *                      without pulling all twelve
 */
public record CheckInConflictPage(
        List<CheckInConflict> content,
        int totalElements,
        int page,
        int size
) {}
