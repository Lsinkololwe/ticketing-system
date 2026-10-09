package com.pml.catalog.security;

import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.service.ReferenceDataService;
import com.pml.catalog.service.ReferenceMetadataValidator;
import com.pml.catalog.web.graphql.query.ReferenceDataQueryResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ET-PLT-014-R11 · a signed-out visitor reads exactly the public reference types; a signed-in caller reads
 * all of them. The gateway admits the root field; this is the per-type decision, exercised through the real
 * method-security proxy on the real resolver.
 */
@Tag("L1")
@Tag("ET-PLT-014")
@DisplayName("ET-PLT-014-R11 · anonymous reference reads are limited to the public types")
class ReferenceAccessTest {

    private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    private static final Authentication ORGANIZER = new TestingAuthenticationToken("org-user", "n/a", "ROLE_ORGANIZER");

    private static AnnotationConfigApplicationContext context;
    private static ReferenceDataQueryResolver resolver;

    @Configuration
    @EnableReactiveMethodSecurity
    static class Wiring {
        @Bean
        ReferenceAccess referenceAccess() {
            return new ReferenceAccess();
        }

        @Bean
        ReferenceDataService service() {
            ReferenceDataService service = Mockito.mock(ReferenceDataService.class);
            Mockito.when(service.findByType(Mockito.any(), Mockito.anyBoolean())).thenAnswer(call -> Flux.just(
                    ReferenceData.builder().type(call.getArgument(0)).code("X").name("X").build()));
            Mockito.when(service.findByParent(Mockito.any(), Mockito.anyString())).thenReturn(Flux.empty());
            return service;
        }

        @Bean
        ReferenceDataQueryResolver resolver(ReferenceDataService service) {
            return new ReferenceDataQueryResolver(service, new ReferenceMetadataValidator());
        }
    }

    @BeforeAll
    static void start() {
        context = new AnnotationConfigApplicationContext(Wiring.class);
        resolver = context.getBean(ReferenceDataQueryResolver.class);
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    private static List<ReferenceData> read(ReferenceType type, Authentication who) {
        return resolver.referenceData(type, true)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(who)).collectList().block();
    }

    @Test
    @DisplayName("a visitor reads every public type, with the same answer a signed-in caller gets")
    void anonymousReadsPublicTypes() {
        for (ReferenceType type : ReferenceAccess.PUBLIC_TYPES) {
            assertThat(read(type, ANONYMOUS)).as(type.name()).hasSize(1);
            assertThat(read(type, ORGANIZER)).as(type.name()).hasSize(1);
        }
    }

    @Test
    @DisplayName("a visitor is refused every other type, and a signed-in caller is not")
    void anonymousRefusedElsewhere() {
        for (ReferenceType type : ReferenceType.values()) {
            if (ReferenceAccess.PUBLIC_TYPES.contains(type)) {
                continue;
            }
            assertThatThrownBy(() -> read(type, ANONYMOUS)).as(type.name()).isInstanceOf(AccessDeniedException.class);
            assertThat(read(type, ORGANIZER)).as(type.name()).hasSize(1);
        }
    }

    @Test
    @DisplayName("the lists the buyer needs are public and the ones that expose business detail are not")
    void theBoundary() {
        assertThat(ReferenceAccess.PUBLIC_TYPES).contains(ReferenceType.COUNTRY, ReferenceType.MOBILE_MONEY_OPERATOR,
                ReferenceType.EVENT_CATEGORY, ReferenceType.PROVINCE, ReferenceType.CITY, ReferenceType.AGE_RESTRICTION,
                ReferenceType.REFUND_REASON, ReferenceType.NOTIFICATION_CHANNEL);
        assertThat(ReferenceAccess.PUBLIC_TYPES).doesNotContain(ReferenceType.BANK, ReferenceType.KYB_DOCUMENT_TYPE,
                ReferenceType.BUSINESS_TYPE, ReferenceType.ORGANIZER_TYPE, ReferenceType.CANCELLATION_REASON,
                ReferenceType.TAX_RATE, ReferenceType.ORGANIZATION_ROLE, ReferenceType.PAYOUT_STATUS);
    }

    @Test
    @DisplayName("the child-row query carries the same gate")
    void parentQuery() {
        assertThatThrownBy(() -> resolver.referenceDataByParent(ReferenceType.BANK, "X")
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(ANONYMOUS)).collectList().block())
                .isInstanceOf(AccessDeniedException.class);
        assertThat(resolver.referenceDataByParent(ReferenceType.MUSIC_GENRE, "MUSIC")
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(ANONYMOUS)).collectList().block()).isEmpty();
        assertThat(Arrays.stream(ReferenceType.values()).count()).isGreaterThan(ReferenceAccess.PUBLIC_TYPES.size());
    }
}
