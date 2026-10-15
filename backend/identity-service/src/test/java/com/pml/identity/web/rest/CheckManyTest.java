package com.pml.identity.web.rest;

import com.pml.identity.service.AuthorizationService;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
import com.pml.shared.dto.authorization.BatchAuthorizationRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("L1")
@Tag("ET-PLT-013")
@DisplayName("check-many answers each question in its own slot, in order, and refuses what it cannot decide")
class CheckManyTest {

    private static AuthorizationRequest ask(String permission) {
        return AuthorizationRequest.builder().userId("u").organizationId("org").requiredPermission(permission).build();
    }

    @Test
    @DisplayName("answers keep the order asked, and an entry that fails is a refusal in its slot, not a failed batch")
    void orderAndPartialFailure() {
        AuthorizationService service = mock(AuthorizationService.class);
        when(service.checkAuthorization(any())).thenAnswer(call -> {
            AuthorizationRequest request = call.getArgument(0);
            return switch (request.getRequiredPermission()) {
                case "event:view" -> Mono.just(AuthorizationResult.authorizedAsMember("org", "OWNER"));
                case "event:edit" -> Mono.error(new IllegalStateException("store down"));
                default -> Mono.just(AuthorizationResult.deniedNotMember());
            };
        });
        InternalAuthorizationController controller = new InternalAuthorizationController(service, new SimpleMeterRegistry());

        var response = controller.checkMany(new BatchAuthorizationRequest(
                List.of(ask("event:view"), ask("event:edit"), ask("event:delete")))).block();

        assertThat(response.results()).hasSize(3);
        assertThat(response.results().get(0).isAuthorized()).isTrue();
        assertThat(response.results().get(1).isAuthorized()).isFalse();
        assertThat(response.results().get(2).isAuthorized()).isFalse();
    }
}
