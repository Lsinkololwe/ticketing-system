package com.pml.catalog.security;

import com.netflix.graphql.dgs.DgsMutation;
import com.pml.catalog.web.graphql.mutation.ApprovalWorkflowMutationResolver;
import com.pml.catalog.web.graphql.mutation.ReferenceDataMutationResolver;
import com.pml.shared.security.revocation.FailClosedOnRevocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Platform configuration changes refuse to run when the caller's token cannot be checked. */
@Tag("L1")
@Tag("ET-IDN-003")
@DisplayName("Catalog's platform-configuration mutations fail closed on revocation")
class SensitiveMutationsTest {

    @Test
    @DisplayName("reference data and the platform configuration are marked")
    void platformConfigurationIsMarked() {
        assertThat(ReferenceDataMutationResolver.class.isAnnotationPresent(FailClosedOnRevocation.class)).isTrue();
        assertThat(Arrays.stream(ApprovalWorkflowMutationResolver.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("updatePlatformConfiguration"))
                .filter(m -> m.isAnnotationPresent(DgsMutation.class)))
                .singleElement()
                .satisfies(m -> assertThat(m.isAnnotationPresent(FailClosedOnRevocation.class)).isTrue());
    }
}
