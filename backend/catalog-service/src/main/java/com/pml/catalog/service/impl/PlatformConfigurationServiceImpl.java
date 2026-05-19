package com.pml.catalog.service.impl;

import com.pml.catalog.domain.model.PlatformConfiguration;
import com.pml.catalog.repository.PlatformConfigurationRepository;
import com.pml.catalog.service.PlatformConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Implementation of PlatformConfigurationService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformConfigurationServiceImpl implements PlatformConfigurationService {

    private final PlatformConfigurationRepository configurationRepository;

    @Override
    public Mono<PlatformConfiguration> getConfiguration() {
        return configurationRepository.getConfiguration();
    }

    @Override
    public Mono<PlatformConfiguration> updateConfiguration(PlatformConfiguration config, String updatedBy) {
        return configurationRepository.getConfiguration()
                .flatMap(existing -> {
                    // Merge updates with existing configuration
                    if (config.getApprovalSlaHours() > 0) {
                        existing.setApprovalSlaHours(config.getApprovalSlaHours());
                    }
                    if (config.getApprovalWarningThresholdHours() >= 0) {
                        existing.setApprovalWarningThresholdHours(config.getApprovalWarningThresholdHours());
                    }

                    existing.setAutoEscalationEnabled(config.isAutoEscalationEnabled());

                    if (config.getEscalationDelayHours() >= 0) {
                        existing.setEscalationDelayHours(config.getEscalationDelayHours());
                    }
                    if (config.getEscalationRecipientRole() != null) {
                        existing.setEscalationRecipientRole(config.getEscalationRecipientRole());
                    }
                    if (config.getEscalationReminderIntervalHours() > 0) {
                        existing.setEscalationReminderIntervalHours(config.getEscalationReminderIntervalHours());
                    }
                    if (config.getMaxEscalationReminders() > 0) {
                        existing.setMaxEscalationReminders(config.getMaxEscalationReminders());
                    }

                    if (config.getOrganizerNotificationChannel() != null) {
                        existing.setOrganizerNotificationChannel(config.getOrganizerNotificationChannel());
                    }
                    if (config.getAdminNotificationChannel() != null) {
                        existing.setAdminNotificationChannel(config.getAdminNotificationChannel());
                    }
                    existing.setSendSlaWarningNotifications(config.isSendSlaWarningNotifications());
                    existing.setSendEscalationNotifications(config.isSendEscalationNotifications());

                    existing.setRequireCommentsOnRejection(config.isRequireCommentsOnRejection());
                    existing.setRequireCommentsOnChangesRequested(config.isRequireCommentsOnChangesRequested());
                    existing.setAllowSelfApproval(config.isAllowSelfApproval());

                    existing.setUpdatedAt(LocalDateTime.now());
                    existing.setUpdatedBy(updatedBy);

                    log.info("Updating platform configuration by admin: {}", updatedBy);
                    return configurationRepository.save(existing);
                })
                .doOnSuccess(cfg -> log.info("Platform configuration updated successfully"));
    }
}
