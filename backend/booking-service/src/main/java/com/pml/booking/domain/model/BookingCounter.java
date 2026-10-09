package com.pml.booking.domain.model;

import com.pml.booking.persistence.BookingCollections;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/** A monotonic counter, one per sequence (for example {@code booking-2026}). Moved only by {@code $inc}. */
@Document(collection = BookingCollections.BOOKING_COUNTERS)
@TypeAlias("booking_counters")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingCounter {

    @Id
    private String id;

    private long seq;
}
