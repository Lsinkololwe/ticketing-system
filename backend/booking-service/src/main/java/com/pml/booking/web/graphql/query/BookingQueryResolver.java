package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.Booking;
import com.pml.booking.service.BookingReads;
import com.pml.booking.service.Pages;
import com.pml.booking.web.graphql.dto.BookingFilterInput;
import com.pml.booking.web.graphql.dto.BookingOffsetPage;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * Bookings: the purchase as a buyer, an organizer and an administrator each need to see it.
 *
 * <p>Authorization lives in {@link BookingReads}, which decides per booking (and per organization
 * for lists) from the token and from identity; the annotations here only require a signed-in caller.
 */
@DgsComponent
@RequiredArgsConstructor
public class BookingQueryResolver {

    private final BookingReads bookings;

    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<Booking> booking(@InputArgument String id) {
        return bookings.byId(id);
    }

    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<Booking> bookingByNumber(@InputArgument String bookingNumber) {
        return bookings.byNumber(bookingNumber);
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN', 'FINANCE', 'SUPER_ADMIN')")
    public Mono<BookingOffsetPage> bookingsByOrganizer(@InputArgument String organizationId,
                                                       @InputArgument BookingFilterInput filter,
                                                       @InputArgument OffsetPaginationInput pagination) {
        return bookings.byOrganizer(organizationId, filter, pagination).map(BookingQueryResolver::page);
    }

    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<BookingOffsetPage> bookingsByBuyer(@InputArgument String buyerId,
                                                   @InputArgument BookingFilterInput filter,
                                                   @InputArgument OffsetPaginationInput pagination) {
        return bookings.byBuyer(buyerId, filter, pagination).map(BookingQueryResolver::page);
    }

    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<BookingOffsetPage> myBookings(@InputArgument BookingFilterInput filter,
                                              @InputArgument OffsetPaginationInput pagination) {
        return bookings.mine(filter, pagination).map(BookingQueryResolver::page);
    }

    private static BookingOffsetPage page(Pages.Slice<Booking> slice) {
        return new BookingOffsetPage(slice.data(), slice.pagination());
    }
}
