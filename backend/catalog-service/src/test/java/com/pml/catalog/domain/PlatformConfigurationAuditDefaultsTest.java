package com.pml.catalog.domain;

import com.pml.catalog.domain.model.PlatformConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code PlatformConfiguration.updatedBy} and {@code updatedAt} are non-null in the schema. The
 * singleton is created with documented defaults before any administrator has saved it, so those
 * fields must still read as values, or the whole platform-configuration page fails on a fresh database.
 */
@Tag("L1")
@Tag("ET-ADM-002")
@DisplayName("ET-ADM-002 · a platform configuration nobody has saved still has an editor and a timestamp")
class PlatformConfigurationAuditDefaultsTest {

    @Test
    void defaultsHaveSystemActorAndTimestamp() {
        PlatformConfiguration config = PlatformConfiguration.createDefault();
        assertThat(config.getUpdatedBy()).isEqualTo("system");
        assertThat(config.getUpdatedAt()).isNotNull();
        assertThat(config.getApprovalSlaHours()).isEqualTo(48);
    }

    @Test
    void documentWrittenWithoutAuditFieldsStillReads() {
        PlatformConfiguration legacy = new PlatformConfiguration();
        assertThat(legacy.getUpdatedBy()).isNotBlank();
        assertThat(legacy.getUpdatedAt()).isNotNull();
    }
}
