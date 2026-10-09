package com.pml.catalog.service;

import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.web.graphql.dto.CreateTicketTierInput;
import com.pml.shared.error.FieldViolation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The one place a ticket tier is built from input, whether it arrives with a new event or on its own.
 *
 * <p>Defaults: the sales window ends when the event starts, and an order is capped at
 * {@code catalog.tier.default-max-per-order}. A tier is priced in the event's currency.
 */
@Component
public class TicketTierFactory {

    private final int defaultMaxPerOrder;

    public TicketTierFactory(@Value("${catalog.tier.default-max-per-order:10}") int defaultMaxPerOrder) {
        this.defaultMaxPerOrder = defaultMaxPerOrder;
    }

    /** Every reason {@code input} cannot become a tier of {@code event}, each at {@code path}. */
    public List<FieldViolation> check(Event event, CreateTicketTierInput input, String path) {
        List<FieldViolation> violations = new ArrayList<>();
        if (input.currency() != null && !input.currency().equalsIgnoreCase(event.getCurrency())) {
            violations.add(new FieldViolation(path + ".currency", "must be " + event.getCurrency()));
        }
        Instant salesEnd = salesEnd(event, input);
        if (input.salesStartAt() != null && salesEnd != null && !input.salesStartAt().isBefore(salesEnd)) {
            violations.add(new FieldViolation(path + ".salesStartAt", "must be before the sales end"));
        }
        if (salesEnd != null && event.getEndDateTime() != null && salesEnd.isAfter(event.getEndDateTime())) {
            violations.add(new FieldViolation(path + ".salesEndAt", "must not be after the event ends"));
        }
        if (input.earlyBirdPrice() != null) {
            if (input.earlyBirdEndsAt() == null) {
                violations.add(new FieldViolation(path + ".earlyBirdEndsAt", "is required with an early-bird price"));
            }
            if (input.price() != null && input.earlyBirdPrice().compareTo(input.price()) >= 0) {
                violations.add(new FieldViolation(path + ".earlyBirdPrice", "must be below the price"));
            }
        }
        int maxPerOrder = maxPerOrder(input);
        if (input.minPerOrder() != null && input.minPerOrder() > maxPerOrder) {
            violations.add(new FieldViolation(path + ".minPerOrder", "must not exceed maxPerOrder"));
        }
        return violations;
    }

    public TicketTier build(Event event, CreateTicketTierInput input, int sortOrder, Instant now) {
        return TicketTier.builder()
                .eventId(event.getId())
                .organizationId(event.getOrganizationId())
                .currency(event.getCurrency())
                .code(input.code())
                .name(input.name())
                .description(input.description())
                .price(input.price())
                .quantity(input.quantity())
                .availableQuantity(input.quantity())
                .soldQuantity(0)
                .maxPerOrder(maxPerOrder(input))
                .minPerOrder(input.minPerOrder())
                .benefits(input.benefits())
                .sortOrder(sortOrder)
                .isActive(true)
                .salesStartAt(input.salesStartAt())
                .salesEndAt(salesEnd(event, input))
                .earlyBirdPrice(input.earlyBirdPrice())
                .earlyBirdEndsAt(input.earlyBirdEndsAt())
                .isHidden(Boolean.TRUE.equals(input.isHidden()))
                .accessCode(input.accessCode())
                .category(input.category() != null ? input.category() : com.pml.shared.constants.TicketCategory.GENERAL)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static Instant salesEnd(Event event, CreateTicketTierInput input) {
        return input.salesEndAt() != null ? input.salesEndAt() : event.getEventDateTime();
    }

    private int maxPerOrder(CreateTicketTierInput input) {
        return input.maxPerOrder() != null ? input.maxPerOrder() : defaultMaxPerOrder;
    }
}
