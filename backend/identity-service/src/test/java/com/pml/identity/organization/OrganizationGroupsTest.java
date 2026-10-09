package com.pml.identity.organization;

import com.pml.identity.domain.valueobject.OrganizationGroups;
import com.pml.identity.domain.valueobject.OrganizationRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R8 · every role mirrors into a group the organization's tree actually has")
class OrganizationGroupsTest {

    @Test
    @DisplayName("R8 · each role's group is a subgroup the tree creates, one per role")
    void everyRoleHasItsGroup() {
        for (OrganizationRole role : OrganizationRole.values()) {
            assertThat(OrganizationGroups.TREE)
                    .as("a write to a group the tree lacks is a silent no-op at Keycloak: " + role)
                    .contains(OrganizationGroups.of(role));
        }
        assertThat(OrganizationGroups.TREE).hasSize(OrganizationRole.values().length).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("the owners and admins constants agree with the role mapping")
    void constants() {
        assertThat(OrganizationGroups.of(OrganizationRole.OWNER)).isEqualTo(OrganizationGroups.OWNERS);
        assertThat(OrganizationGroups.of(OrganizationRole.ADMIN)).isEqualTo(OrganizationGroups.ADMINS);
    }
}
