package com.pml.identity.migration;

import com.pml.shared.security.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The table that turns stored upper-case permission names into catalogue codes. Pure values. */
@Tag("L1")
@Tag("ET-ORG-003")
@DisplayName("Old permission names translate to catalogue codes, and nothing else survives")
class LegacyPermissionNamesTest {

    @Test
    @DisplayName("Codes are kept, old names translated, unknown values dropped, and the result sorted and distinct")
    void translate() {
        assertThat(PermissionModelMigrationService.translate(Arrays.asList(
                "MEMBER_INVITE", "team:invite", "EVENT_MANAGE_ACCESS", "NOTIFICATION_SEND", "event:fly", 42, null)))
                .containsExactly("event_access:grant", "team:invite");
    }

    @Test
    @DisplayName("Both generations of old names that meant the same thing land on the same code")
    void synonymsConverge() {
        assertThat(PermissionModelMigrationService.translate(List.of("ORG_SETTINGS", "ORG_MANAGE_SETTINGS", "ORG_EDIT")))
                .containsExactly("organization:edit");
        assertThat(PermissionModelMigrationService.translate(List.of("FIN_VIEW_REVENUE", "FIN_VIEW_TRANSACTIONS", "FINANCIAL_VIEW")))
                .containsExactly("financial:view");
    }

    @Test
    @DisplayName("No old name translates to a platform permission, so a stored custom grant cannot become one")
    void noOldNameBecomesAPlatformPermission() {
        assertThat(PermissionModelMigrationService.LEGACY_NAMES.values())
                .noneMatch(permission -> permission.scope() == Permission.Scope.PLATFORM);
    }

    @Test
    @DisplayName("The collections dropped are only the retired permission ones")
    void onlyRetiredCollectionsAreDropped() {
        assertThat(PermissionModelMigrationService.RETIRED_COLLECTIONS)
                .allMatch(name -> name.contains("permission"));
    }
}
