package com.pml.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pml.shared.testing.jwt.StubIssuer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * ET-PLT-007-R2 · a service outside local development or a test refuses to start with the
 * audience check off, rather than only logging the gap.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("A blank audience check is a boot failure outside local development or a test")
class AudienceRequirementTest {

    private static StubIssuer issuer;

    @BeforeAll
    static void start() {
        issuer = StubIssuer.start("myticketzm");
    }

    @AfterAll
    static void stop() {
        issuer.close();
    }

    @Test
    @DisplayName("the local profile is exempt")
    void localIsExempt() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThat(AudienceRequirement.outsideLocalOrTest(environment)).isFalse();
    }

    @Test
    @DisplayName("the test profile is exempt")
    void testIsExempt() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        assertThat(AudienceRequirement.outsideLocalOrTest(environment)).isFalse();
    }

    @Test
    @DisplayName("no profile at all is not exempt — a deployment that forgot to set one is the case this guards")
    void noProfileIsNotExempt() {
        assertThat(AudienceRequirement.outsideLocalOrTest(new MockEnvironment())).isTrue();
    }

    @Test
    @DisplayName("any other profile is not exempt")
    void otherProfileIsNotExempt() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");

        assertThat(AudienceRequirement.outsideLocalOrTest(environment)).isTrue();
    }

    @Test
    @DisplayName("requireAudience set and no audience configured: the service refuses to build its security chain")
    void refusesToStartWithoutAudience() {
        assertThatThrownBy(() -> PlatformResourceServer.jwt(issuer.issuer(), "", "myticketzm-catalog-service", "", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("myticketzm-catalog-service");
    }

    @Test
    @DisplayName("requireAudience set and an audience configured: no exception")
    void startsWhenAudienceIsConfigured() {
        assertThat(PlatformResourceServer.jwt(issuer.issuer(), "", "myticketzm-catalog-service",
                "myticketzm-api-gateway", true)).isNotNull();
    }

    @Test
    @DisplayName("requireAudience unset and no audience configured: no exception, same as before")
    void doesNotStartFailingWhenNotRequired() {
        assertThat(PlatformResourceServer.jwt(issuer.issuer(), "", "myticketzm-catalog-service", ""))
                .isNotNull();
    }
}
