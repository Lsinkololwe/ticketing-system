package com.pml.catalog.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.infrastructure.client.IdentityServiceClient;
import com.pml.catalog.service.OrganizerProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import com.mongodb.client.result.UpdateResult;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * {@code Event.organizerName} is non-null in the schema. An event with no stored name must not fail
 * the list it appears in, and is repaired the first time it is read. Flat methods: F-055.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("Event.organizerName never answers null and repairs a legacy event once")
class EventOrganizerNameFieldTest {

    private IdentityServiceClient identity;
    private ReactiveMongoTemplate mongo;
    private EventContentFieldResolver resolver;

    @BeforeEach
    void setUp() {
        identity = Mockito.mock(IdentityServiceClient.class);
        mongo = Mockito.mock(ReactiveMongoTemplate.class);
        Mockito.when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Event.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));
        resolver = new EventContentFieldResolver(Mockito.mock(OrganizerProfileService.class), identity, mongo);
    }

    private String nameOf(Event event) {
        DgsDataFetchingEnvironment dfe = Mockito.mock(DgsDataFetchingEnvironment.class);
        Mockito.when(dfe.getSource()).thenReturn(event);
        return resolver.organizerName(dfe).block();
    }

    @Test
    @DisplayName("a stored name is returned as stored, with no lookup")
    void storedNameIsReturned() {
        assertThat(nameOf(Event.builder().id("e1").organizationId("org-1").organizerName("Showstop Live Events").build()))
                .isEqualTo("Showstop Live Events");
        Mockito.verifyNoInteractions(identity);
    }

    @Test
    @DisplayName("an event with no stored name asks identity and writes the answer back")
    void missingNameIsRepaired() {
        Mockito.when(identity.getOrganizationName("org-1")).thenReturn(Mono.just("Showstop Live Events"));

        assertThat(nameOf(Event.builder().id("e1").organizationId("org-1").build())).isEqualTo("Showstop Live Events");

        Mockito.verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(Event.class));
    }

    @Test
    @DisplayName("a blank stored name is treated as missing")
    void blankNameIsRepaired() {
        Mockito.when(identity.getOrganizationName("org-1")).thenReturn(Mono.just("Showstop"));
        assertThat(nameOf(Event.builder().id("e1").organizationId("org-1").organizerName("   ").build())).isEqualTo("Showstop");
    }

    @Test
    @DisplayName("an identity outage answers Unknown and writes nothing, so the next read tries again")
    void outageAnswersUnknownAndWritesNothing() {
        Mockito.when(identity.getOrganizationName("org-1")).thenReturn(Mono.error(new IllegalStateException("down")));

        assertThat(nameOf(Event.builder().id("e1").organizationId("org-1").build())).isEqualTo("Unknown");

        Mockito.verify(mongo, Mockito.never()).updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Event.class));
    }

    @Test
    @DisplayName("an unknown organization answers Unknown and writes nothing")
    void unknownOrganizationAnswersUnknown() {
        Mockito.when(identity.getOrganizationName("org-gone")).thenReturn(Mono.empty());
        assertThat(nameOf(Event.builder().id("e1").organizationId("org-gone").build())).isEqualTo("Unknown");
        Mockito.verify(mongo, Mockito.never()).updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Event.class));
    }

    @Test
    @DisplayName("an event with no organization answers Unknown without calling identity")
    void noOrganizationIsUnknown() {
        assertThat(nameOf(Event.builder().id("e1").build())).isEqualTo("Unknown");
        Mockito.verifyNoInteractions(identity);
    }

    @Test
    @DisplayName("an event carries no organizer contact details: identity owns them and the graph federates them")
    void contactDetailsAreNotStoredOnTheEvent() {
        assertThat(java.util.Arrays.stream(Event.class.getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("organizerEmail", "organizerPhone", "organizerBusinessEmail",
                        "organizerBusinessPhone", "organizerFirstName", "organizerLastName", "organizerCompanyName");
    }
}
