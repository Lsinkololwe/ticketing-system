package com.pml.booking.security;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.enums.ValidationMethod;
import com.pml.booking.domain.model.CheckIn;
import com.pml.booking.domain.model.CheckInConflict;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.repository.CheckInConflictRepository;
import com.pml.booking.repository.CheckInRepository;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.service.impl.CheckInServiceImpl;
import com.pml.booking.web.graphql.dto.checkin.ValidateTicketInput;
import com.pml.booking.web.graphql.mutation.CheckInMutationResolver;
import com.pml.booking.web.graphql.query.CheckInQueryResolver;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.dto.EventSummaryDto;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who may work an event's gate, against a MongoDB replica set with the real check-in service and
 * repositories; only catalog (who owns the event) and identity (who holds {@code ticket:scan})
 * are replaced. A refused caller records nothing: no admission, no conflict.
 */
@Tag("L2")
@Tag("ET-TKT-003")
@DisplayName("Scanning, gate reads and conflict review need ticket:scan on the event")
class GateScanAccessTest {

    private static final Instant NOW = Instant.parse("2026-09-18T18:00:00Z");
    private static final String EVENT = "event-gate";
    private static final String OTHER_EVENT = "event-other-org";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TicketRepository tickets;
    private static CheckInRepository checkIns;
    private static CheckInConflictRepository conflicts;

    private IdentityServiceClient identity;
    private CheckInMutationResolver scans;
    private CheckInQueryResolver reads;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_gate_access"));
        ReactiveMongoRepositoryFactory factory = new ReactiveMongoRepositoryFactory(template);
        tickets = factory.getRepository(TicketRepository.class);
        checkIns = factory.getRepository(CheckInRepository.class);
        conflicts = factory.getRepository(CheckInConflictRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), Ticket.class).block();
        template.remove(new Query(), CheckIn.class).block();
        template.remove(new Query(), CheckInConflict.class).block();
        tickets.save(ticket("ticket-1", "TKT-0001", EVENT, "organizer-a")).block();
        tickets.save(ticket("ticket-2", "TKT-0002", EVENT, "organizer-a")).block();
        tickets.save(ticket("ticket-9", "TKT-0009", OTHER_EVENT, "organizer-b")).block();

        CatalogServiceClient catalog = mock(CatalogServiceClient.class);
        when(catalog.getEventById(EVENT)).thenReturn(Mono.just(event(EVENT, "org-a", "organizer-a")));
        when(catalog.getEventById(OTHER_EVENT)).thenReturn(Mono.just(event(OTHER_EVENT, "org-b", "organizer-b")));
        when(catalog.getEventById("event-missing")).thenReturn(Mono.error(
                WebClientResponseException.create(HttpStatus.NOT_FOUND.value(), "Not Found", HttpHeaders.EMPTY, new byte[0], null)));

        identity = mock(IdentityServiceClient.class);
        when(identity.checkAuthorization(any())).thenReturn(Mono.just(AuthorizationResult.deniedNotMember()));
        allow("user-steward", EVENT);
        when(identity.checkAuthorization(argThat(request -> request != null && "user-viewer".equals(request.getUserId()))))
                .thenReturn(Mono.just(AuthorizationResult.deniedInsufficientPermissions("ticket:scan", "VIEWER")));

        EventGateAccess gates = new EventGateAccess(catalog, identity);
        CheckInServiceImpl service = new CheckInServiceImpl(template, TestClock.frozenAt(NOW), tickets, checkIns, conflicts);
        scans = new CheckInMutationResolver(service, gates);
        reads = new CheckInQueryResolver(service, tickets, gates);
    }

    @Test
    @DisplayName("Gate staff admit a ticket; the admission belongs to the event's organizer and names the steward")
    void stewardAdmits() {
        var result = as("user-steward", scans.validateTicket(scan(EVENT, "TKT-0001", "scan-1"))).block();

        assertThat(result.outcome().name()).isEqualTo("ADMITTED");
        CheckIn admission = checkIns.findByTicketId("ticket-1").block();
        assertThat(admission.getOrganizerId()).isEqualTo("organizer-a");
        assertThat(admission.getScannedBy()).isEqualTo("user-steward");
    }

    @Test
    @DisplayName("Someone outside the organization is refused as if the event did not exist, and nothing is recorded")
    void outsiderIsRefusedAndNothingRecorded() {
        assertRefused(() -> as("user-outsider", scans.validateTicket(scan(EVENT, "TKT-0001", "scan-2"))).block(),
                ErrorCode.EVENT_UNKNOWN);

        assertThat(checkIns.count().block()).isZero();
        assertThat(conflicts.count().block()).isZero();
    }

    @Test
    @DisplayName("A member without ticket:scan on the event is told so, and nothing is recorded")
    void viewerIsRefusedAsNotPermitted() {
        assertRefused(() -> as("user-viewer", scans.validateTicket(scan(EVENT, "TKT-0001", "scan-3"))).block(),
                ErrorCode.ACTOR_NOT_PERMITTED);

        assertThat(checkIns.count().block()).isZero();
    }

    @Test
    @DisplayName("Holding the gate of one event does not open another organization's gate")
    void oneGateDoesNotOpenAnother() {
        assertRefused(() -> as("user-steward", scans.validateTicket(scan(OTHER_EVENT, "TKT-0009", "scan-4"))).block(),
                ErrorCode.EVENT_UNKNOWN);

        assertThat(tickets.findById("ticket-9").block().getStatus()).isEqualTo(TicketStatus.ISSUED);
    }

    @Test
    @DisplayName("An offline batch spanning a refused event records none of its scans")
    void batchWithARefusedEventRecordsNothing() {
        List<ValidateTicketInput> batch = List.of(
                scan(EVENT, "TKT-0001", "scan-5"), scan(EVENT, "TKT-0002", "scan-6"), scan(OTHER_EVENT, "TKT-0009", "scan-7"));

        assertRefused(() -> as("user-steward", scans.uploadScans(batch)).block(), ErrorCode.EVENT_UNKNOWN);

        assertThat(checkIns.count().block()).isZero();
        assertThat(conflicts.count().block()).isZero();
    }

    @Test
    @DisplayName("An unknown event is refused like someone else's")
    void unknownEventIsRefused() {
        assertRefused(() -> as("user-steward", scans.validateTicket(scan("event-missing", "TKT-0001", "scan-8"))).block(),
                ErrorCode.EVENT_UNKNOWN);
    }

    @Test
    @DisplayName("A platform administrator scans without an organization check; finance staff do not")
    void platformRoles() {
        var admitted = as("user-admin", Set.of("ROLE_ADMIN"), scans.validateTicket(scan(EVENT, "TKT-0002", "scan-9"))).block();
        assertThat(admitted.outcome().name()).isEqualTo("ADMITTED");
        verify(identity, never()).checkAuthorization(argThat(request -> request != null && "user-admin".equals(request.getUserId())));

        assertRefused(() -> as("user-finance", Set.of("ROLE_FINANCE"), scans.validateTicket(scan(EVENT, "TKT-0001", "scan-10"))).block(),
                ErrorCode.EVENT_UNKNOWN);
    }

    @Test
    @DisplayName("Gate reads serve the steward the event's figures and refuse an outsider")
    void gateReads() {
        as("user-steward", scans.validateTicket(scan(EVENT, "TKT-0001", "scan-11"))).block();

        var summary = as("user-steward", reads.checkInSummary(EVENT)).block();
        assertThat(summary).isNotNull();
        assertThat(as("user-steward", reads.recentCheckIns(EVENT, 10)).collectList().block()).hasSize(1);
        assertRefused(() -> as("user-outsider", reads.checkInSummary(EVENT)).block(), ErrorCode.EVENT_UNKNOWN);
        assertRefused(() -> as("user-outsider", reads.recentCheckIns(EVENT, 10)).collectList().block(), ErrorCode.EVENT_UNKNOWN);
    }

    @Test
    @DisplayName("A scan conflict is reviewed by gate staff of its event, and refused to anyone else")
    void conflictReview() {
        as("user-steward", scans.validateTicket(scan(EVENT, "TKT-0001", "scan-12"))).block();
        as("user-steward", scans.validateTicket(scan(EVENT, "TKT-0001", "scan-13"))).block();
        CheckInConflict duplicate = conflicts.findAll().blockFirst();
        assertThat(duplicate).as("the second scan of one ticket is a conflict").isNotNull();

        assertRefused(() -> as("user-outsider", scans.reviewConflict(duplicate.getId(), "checked the ID")).block(),
                ErrorCode.EVENT_UNKNOWN);
        CheckInConflict reviewed = as("user-steward", scans.reviewConflict(duplicate.getId(), "checked the ID")).block();
        assertThat(reviewed.getReviewedBy()).isEqualTo("user-steward");
    }

    private void allow(String userId, String eventId) {
        when(identity.checkAuthorization(argThat((AuthorizationRequest request) -> request != null
                && userId.equals(request.getUserId()) && eventId.equals(request.getEventId())
                && "ticket:scan".equals(request.getRequiredPermission()))))
                .thenReturn(Mono.just(AuthorizationResult.authorizedByEventGrant(eventId, "CHECK_IN")));
    }

    private static ValidateTicketInput scan(String eventId, String code, String scanId) {
        return new ValidateTicketInput(eventId, code, ValidationMethod.QR_ONLINE, scanId, "device-1", NOW, null);
    }

    private static Ticket ticket(String id, String number, String eventId, String organizerId) {
        return Ticket.builder().id(id).ticketNumber(number).eventId(eventId).organizerId(organizerId)
                .status(TicketStatus.ISSUED).build();
    }

    private static EventSummaryDto event(String id, String organizationId, String organizerId) {
        EventSummaryDto event = new EventSummaryDto();
        event.setId(id);
        event.setOrganizationId(organizationId);
        event.setOrganizerId(organizerId);
        return event;
    }

    private static <T> Mono<T> as(String userId, Mono<T> call) {
        return as(userId, Set.of("ROLE_ORGANIZER"), call);
    }

    private static <T> Mono<T> as(String userId, Set<String> roles, Mono<T> call) {
        return call.contextWrite(ReactiveSecurityContextHolder.withAuthentication(token(userId, roles)));
    }

    private static <T> reactor.core.publisher.Flux<T> as(String userId, reactor.core.publisher.Flux<T> call) {
        return call.contextWrite(ReactiveSecurityContextHolder.withAuthentication(token(userId, Set.of("ROLE_ORGANIZER"))));
    }

    private static JwtAuthenticationToken token(String userId, Set<String> roles) {
        List<GrantedAuthority> authorities = roles.stream().map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
        return new JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "none").subject(userId).build(), authorities);
    }

    private static void assertRefused(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainRefusal.class,
                refused -> assertThat(refused.errorCode()).isEqualTo(code));
    }
}
