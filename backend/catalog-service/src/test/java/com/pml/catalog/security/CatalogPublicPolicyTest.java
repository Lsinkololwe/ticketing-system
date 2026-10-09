package com.pml.catalog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.shared.security.publicop.PublicOperationPolicy;
import com.pml.shared.security.publicop.PublicOperationRules;
import com.pml.shared.security.publicop.PublicOperationRules.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** ET-CAT-004-R13 · the catalog's allowlist applied to the queries the buyer app really sends. */
@Tag("L1")
@Tag("ET-CAT-004")
@DisplayName("ET-CAT-004-R13 · the buyer's anonymous reads are admitted and everything else is not")
class CatalogPublicPolicyTest {


    private static final ObjectMapper JSON = new ObjectMapper();
    private static final PublicOperationRules RULES = new PublicOperationRules(
            PublicOperationPolicy.of("catalog", PublicDiscoveryConfig.PUBLIC_ROOT_FIELDS)
                    .withEntities(PublicDiscoveryConfig.PUBLIC_ENTITY_FIELDS), JSON);

    private static Verdict judge(String query, String variables) {
        String body = "{\"query\":" + JSON.valueToTree(query) + (variables == null ? "" : ",\"variables\":" + variables) + "}";
        return RULES.judge(body.getBytes(StandardCharsets.UTF_8));
    }

    private static final String CARD = "fragment EventCardFields on Event { id title description status featured eventDateTime endDateTime cityName "
            + "locationName bannerImageUrl galleryImages organizerName soldTickets totalCapacity availableTickets minTicketPrice maxTicketPrice "
            + "currency category { id name } }";

    @Test
    @DisplayName("the buyer's discovery, trending, event page and filter queries are admitted")
    void buyerQueriesAreAdmitted() {
        assertThat(judge("query DiscoverEvents($filter: EventDiscoveryFilterInput!, $pagination: CursorPaginationInput, $sort: EventDiscoverySort) { "
                + "discoverEvents(filter: $filter, pagination: $pagination, sort: $sort) { edges { node { ...EventCardFields soldOut } } "
                + "pageInfo { totalElements hasNext endCursor } } } " + CARD, "{\"filter\":{}}")).isEqualTo(Verdict.ALLOWED);
        assertThat(judge("query BuyerTrendingEvents($first: Int) { trendingEvents(first: $first) { ...EventCardFields soldOut } } " + CARD, null))
                .isEqualTo(Verdict.ALLOWED);
        assertThat(judge("query EventPage($id: ID!) { event(id: $id) { ...EventCardFields ticketTiers { id name price quantity soldQuantity isActive isHidden } "
                + "organization { id publishedEventCount } accessibility { wheelchairAccessible } faqs { question answer } } } " + CARD, null))
                .isEqualTo(Verdict.ALLOWED);
        assertThat(judge("query F { categories { id name code } provinces { id name } cities { id name province } citiesWithEvents { id name } }", null))
                .isEqualTo(Verdict.ALLOWED);
        assertThat(judge("query($representations:[_Any!]!){_entities(representations:$representations){...on Organization{publishedEventCount}}}",
                "{\"representations\":[{\"__typename\":\"Organization\",\"id\":\"o1\"}]}")).isEqualTo(Verdict.ALLOWED);
    }

    @Test
    @DisplayName("everything else stays behind a token: personal, organizer, admin, mutations, introspection, mixed")
    void everythingElseIsRefused() {
        for (String query : new String[] {
                "{ recommendedEvents(first: 3) { reason } }",
                "{ myEvents { totalElements } }",
                "{ events { content { id } } }",
                "{ eventStats { total } }",
                "{ platformConfiguration { id } }",
                "{ searchEvents(query: \"abc\") { edges { node { id } } } }",
                "{ trendingEvents { id } me { id } }",
                "mutation { unlockTierWithAccessCode(eventId: \"1\", accessCode: \"x\") { id } }",
                "{ __schema { types { name } } }",
                "{ _service { sdl } }",
                "{ _entities(representations: []) { __typename } }" }) {
            assertThat(judge(query, null)).as(query).isNotEqualTo(Verdict.ALLOWED);
        }
        assertThat(judge("query($r:[_Any!]!){_entities(representations:$r){...on Event{organizerEmail}}}",
                "{\"representations\":[{\"__typename\":\"Event\",\"id\":\"e1\"}]}")).isNotEqualTo(Verdict.ALLOWED);
        assertThat(judge("query($r:[_Any!]!){_entities(representations:$r){...on Organization{publishedEventCount}}}",
                "{\"representations\":[{\"__typename\":\"User\",\"id\":\"u1\"}]}")).isNotEqualTo(Verdict.ALLOWED);
    }
}
