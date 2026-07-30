package com.pml.identity.domain.model;

import com.pml.shared.config.model.PlatformPaymentDefaults;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Read-only view of the shared {@code platform_configuration} document.
 *
 * <p>The document is owned and written by catalog-service; identity-service only needs the
 * payment/payout/commission defaults from it at organization creation. Mapping the same
 * {@code @TypeAlias} against the same collection lets both services share the single
 * configuration document instead of each keeping a private configuration collection.</p>
 *
 * <p>Only the fields identity cares about are declared here — the approval-workflow fields
 * owned by catalog are simply ignored on read.</p>
 */
@Document(collection = "platform_configuration")
@TypeAlias("platformConfiguration")
@Data
@NoArgsConstructor
public class PlatformConfigurationView {

    /** Fixed singleton identifier of the platform configuration document. */
    public static final String DEFAULT_ID = "platform-config";

    @Id
    private String id;

    /** Platform-wide payment/payout/commission defaults. */
    private PlatformPaymentDefaults payment;
}
