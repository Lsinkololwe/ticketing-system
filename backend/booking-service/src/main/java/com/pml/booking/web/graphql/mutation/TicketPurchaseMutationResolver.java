package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.web.graphql.dto.PurchaseTicketMutationResponse;
import com.pml.booking.web.graphql.dto.TicketPurchaseInput;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.service.CommissionService;
import com.pml.booking.service.PaymentService;
import com.pml.booking.service.TicketService;
import com.pml.shared.constants.TicketCategory;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GraphQL Mutation Resolver for Ticket Purchase Operations
 *
 * Business Intent: Handles the critical ticket purchase flow including:
 * - Event and ticket category validation
 * - Ticket creation with commission calculation
 * - Payment intent creation and initiation
 *
 * This is the primary entry point for customers purchasing tickets.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class TicketPurchaseMutationResolver {

    private final TicketService ticketService;
    private final PaymentService paymentService;
    private final CommissionService commissionService;
    private final CatalogServiceClient catalogServiceClient;

    /**
     * Purchase a ticket for an event.
     *
     * <p>Security: Buyer ID is extracted from JWT - NEVER from client input.</p>
     *
     * <h2>Flow:</h2>
     * <ol>
     *   <li>Extract buyer ID from authenticated JWT</li>
     *   <li>Validate event exists and is available</li>
     *   <li>Get ticket category details</li>
     *   <li>Create ticket in PENDING_PAYMENT status</li>
     *   <li>Create and initiate payment intent</li>
     *   <li>Return ticket with payment URL</li>
     * </ol>
     *
     * <h2>OWASP Compliance</h2>
     * <ul>
     *   <li>A01:2021 - Broken Access Control: buyerId extracted from JWT, not client input</li>
     * </ul>
     *
     * @param input Ticket purchase input containing event, category, buyer details
     * @return PurchaseTicketMutationResponse with ticket data or error
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<PurchaseTicketMutationResponse> purchaseTicket(
            @InputArgument TicketPurchaseInput input
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(buyerId -> log.info("Processing ticket purchase for event: {} by buyer: {}",
                        input.eventId(), buyerId))
                .flatMap(buyerId -> {
                    // Validate required fields
                    if (input.eventId() == null || input.eventId().isBlank()) {
                        return Mono.just(createErrorResponse("Event ID is required"));
                    }
                    if (input.ticketCategoryCode() == null || input.ticketCategoryCode().isBlank()) {
                        return Mono.just(createErrorResponse("Ticket category code is required"));
                    }
                    if (input.buyerName() == null || input.buyerName().isBlank()) {
                        return Mono.just(createErrorResponse("Buyer name is required"));
                    }
                    if (input.buyerEmail() == null || input.buyerEmail().isBlank()) {
                        return Mono.just(createErrorResponse("Buyer email is required"));
                    }
                    if (input.buyerPhone() == null || input.buyerPhone().isBlank()) {
                        return Mono.just(createErrorResponse("Buyer phone is required for mobile money payment"));
                    }

                    return catalogServiceClient.isEventAvailable(input.eventId())
                            .flatMap(isAvailable -> {
                                if (!isAvailable) {
                                    return Mono.just(createErrorResponse("Event is not available for ticket purchases"));
                                }
                                return fetchEventAndCreateTicket(input, buyerId);
                            });
                })
                .onErrorResume(SecurityException.class, e -> {
                    log.warn("Authentication required for ticket purchase");
                    return Mono.just(createErrorResponse("Authentication required to purchase tickets"));
                })
                .onErrorResume(e -> {
                    log.error("Ticket purchase failed: {}", e.getMessage(), e);
                    return Mono.just(createErrorResponse("Failed to purchase ticket: " + e.getMessage()));
                });
    }

    private Mono<PurchaseTicketMutationResponse> fetchEventAndCreateTicket(
            TicketPurchaseInput input,
            String buyerId
    ) {
        return catalogServiceClient.getEventById(input.eventId())
                .flatMap(event -> catalogServiceClient.getTicketCategory(input.eventId(), input.ticketCategoryCode())
                        .flatMap(category -> {
                            // Calculate commission
                            BigDecimal ticketPrice = category.getPrice() != null ? category.getPrice() : input.amount();
                            if (ticketPrice == null) {
                                return Mono.just(createErrorResponse("Ticket price not available"));
                            }

                            BigDecimal commissionAmount = commissionService.calculateCommission(ticketPrice);
                            BigDecimal netAmount = commissionService.calculateNetAmount(ticketPrice);
                            BigDecimal commissionRate = commissionService.getCommissionRate();

                            // Build ticket
                            Ticket ticket = Ticket.builder()
                                    .ticketNumber(Ticket.generateTicketNumber())
                                    .eventId(input.eventId())
                                    .eventTitle(event.getTitle())
                                    .eventDate(event.getStartDate() != null ? event.getStartDate().toString() : null)
                                    .eventLocationName(event.getLocationName())
                                    .eventLocationAddress(event.getCityName())
                                    .buyerId(buyerId)
                                    .buyerName(input.buyerName())
                                    .buyerEmail(input.buyerEmail())
                                    .buyerPhone(input.buyerPhone())
                                    .ticketCategory(mapTicketCategory(input.ticketCategoryCode()))
                                    .ticketCategoryCode(input.ticketCategoryCode())
                                    .ticketCategoryName(category.getName())
                                    .price(ticketPrice)
                                    .currency(input.currency() != null ? input.currency() : "ZMW")
                                    .status(TicketStatus.PENDING_PAYMENT)
                                    .quantity(input.quantity())
                                    .correlationId(input.correlationId() != null ? input.correlationId() : generateCorrelationId())
                                    .paymentReference(input.paymentReference())
                                    .commissionRate(commissionRate)
                                    .commissionAmount(commissionAmount)
                                    .netAmount(netAmount)
                                    .qrCode(generateQRCode())
                                    .barcode(generateBarcode())
                                    .metadata(input.metadata())
                                    .isActive(true)
                                    .build();

                            return ticketService.createTicket(ticket)
                                    .flatMap(savedTicket -> createPaymentAndReturnResponse(savedTicket, input));
                        }))
                .onErrorResume(e -> {
                    log.error("Failed to fetch event or category: {}", e.getMessage());
                    return Mono.just(createErrorResponse("Failed to fetch event details: " + e.getMessage()));
                });
    }

    private Mono<PurchaseTicketMutationResponse> createPaymentAndReturnResponse(
            Ticket ticket,
            TicketPurchaseInput input
    ) {
        return paymentService.createPaymentIntent(
                        ticket.getId(),
                        ticket.getEventId(),
                        ticket.getBuyerId(),
                        ticket.getPrice(),
                        ticket.getCurrency(),
                        input.buyerPhone()
                )
                .flatMap(paymentIntent -> paymentService.initiatePayment(paymentIntent.getId()))
                .map(paymentIntent -> {
                    Map<String, Object> metadata = Map.of(
                            "paymentIntentId", paymentIntent.getId(),
                            "paymentStatus", paymentIntent.getStatus().name(),
                            "transactionRef", paymentIntent.getTransactionRef()
                    );

                    return new PurchaseTicketMutationResponse(
                            true,
                            "Ticket purchase initiated. Please complete payment.",
                            ticket,
                            List.of(),
                            metadata
                    );
                })
                .onErrorResume(e -> {
                    log.error("Payment initiation failed for ticket: {}", ticket.getTicketNumber(), e);
                    // Ticket is created but payment failed - return ticket with error info
                    return Mono.just(new PurchaseTicketMutationResponse(
                            false,
                            "Ticket created but payment initiation failed: " + e.getMessage(),
                            ticket,
                            List.of(e.getMessage()),
                            Map.of("ticketId", ticket.getId(), "ticketNumber", ticket.getTicketNumber())
                    ));
                });
    }

    private PurchaseTicketMutationResponse createErrorResponse(String message) {
        return new PurchaseTicketMutationResponse(
                false,
                message,
                null,
                List.of(message),
                null
        );
    }

    private TicketCategory mapTicketCategory(String categoryCode) {
        if (categoryCode == null) {
            return TicketCategory.GENERAL;
        }
        return switch (categoryCode.toUpperCase()) {
            case "VIP" -> TicketCategory.VIP;
            case "VVIP" -> TicketCategory.VVIP;
            case "PREMIUM" -> TicketCategory.PREMIUM;
            case "EARLY_BIRD" -> TicketCategory.EARLY_BIRD;
            case "PRE_SALE" -> TicketCategory.PRE_SALE;
            case "STUDENT" -> TicketCategory.STUDENT;
            case "SENIOR" -> TicketCategory.SENIOR;
            case "GROUP" -> TicketCategory.GROUP;
            case "CORPORATE" -> TicketCategory.CORPORATE;
            case "SPONSOR" -> TicketCategory.SPONSOR;
            case "FREE" -> TicketCategory.FREE;
            default -> TicketCategory.GENERAL;
        };
    }

    private String generateCorrelationId() {
        return "COR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private String generateQRCode() {
        return UUID.randomUUID().toString();
    }

    private String generateBarcode() {
        return String.valueOf(System.currentTimeMillis()) + String.format("%04d", (int) (Math.random() * 10000));
    }
}
