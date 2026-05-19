package com.pml.identity.service.impl;

import com.pml.identity.web.graphql.dto.SendNotificationInput;
import com.pml.identity.domain.model.Notification;
import com.pml.identity.domain.enums.NotificationChannel;
import com.pml.identity.domain.model.NotificationPreferences;
import com.pml.identity.domain.enums.NotificationStatus;
import com.pml.identity.repository.NotificationPreferencesRepository;
import com.pml.identity.repository.NotificationRepository;
import com.pml.identity.repository.UserDeviceRepository;
import com.pml.identity.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of NotificationService.
 * Manages notification creation, multi-channel delivery, and lifecycle tracking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferencesRepository preferencesRepository;
    private final UserDeviceRepository deviceRepository;

    @Override
    public Mono<Notification> createNotification(SendNotificationInput input) {
        log.debug("Creating notification for user {} of type {}", input.userId(), input.type());

        Notification notification = Notification.builder()
            .userId(input.userId())
            .type(input.type())
            .title(input.title())
            .body(input.body())
            .data(input.data())
            .channels(input.channels())
            .status(NotificationStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .build();

        return notificationRepository.save(notification)
            .flatMap(this::sendToChannels);
    }

    @Override
    public Mono<Integer> sendBulkNotification(List<String> userIds, SendNotificationInput input) {
        log.debug("Sending bulk notification to {} users", userIds.size());

        return Flux.fromIterable(userIds)
            .flatMap(userId -> {
                SendNotificationInput userInput = new SendNotificationInput(
                    userId,
                    input.type(),
                    input.title(),
                    input.body(),
                    input.data(),
                    input.channels()
                );
                return createNotification(userInput);
            })
            .count()
            .map(Long::intValue);
    }

    @Override
    public Flux<Notification> findByUserId(String userId, int limit, int offset, boolean unreadOnly) {
        log.debug("Finding notifications for user {}, limit={}, offset={}, unreadOnly={}",
            userId, limit, offset, unreadOnly);

        PageRequest pageRequest = PageRequest.of(offset / limit, limit);

        if (unreadOnly) {
            return notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageRequest);
        } else {
            return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);
        }
    }

    @Override
    public Flux<Notification> findByUserId(String userId) {
        log.debug("Finding all notifications for user {}", userId);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public Mono<Long> countUnread(String userId) {
        log.debug("Counting unread notifications for user {}", userId);
        return notificationRepository.countUnreadByUserId(userId);
    }

    @Override
    public Mono<Notification> findById(String id) {
        log.debug("Finding notification by id {}", id);
        return notificationRepository.findById(id);
    }

    @Override
    public Mono<Notification> markAsRead(String notificationId) {
        log.debug("Marking notification {} as read", notificationId);

        return notificationRepository.findById(notificationId)
            .flatMap(notification -> {
                if (notification.getReadAt() == null) {
                    notification.setReadAt(LocalDateTime.now());
                    notification.setStatus(NotificationStatus.READ);
                    return notificationRepository.save(notification);
                }
                return Mono.just(notification);
            });
    }

    @Override
    public Mono<Integer> markAllAsRead(String userId) {
        log.debug("Marking all notifications as read for user {}", userId);

        return notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(
            userId, PageRequest.of(0, 1000))
            .flatMap(notification -> {
                notification.setReadAt(LocalDateTime.now());
                notification.setStatus(NotificationStatus.READ);
                return notificationRepository.save(notification);
            })
            .count()
            .map(Long::intValue);
    }

    @Override
    public Mono<Boolean> deleteNotification(String notificationId) {
        log.debug("Deleting notification {}", notificationId);

        return notificationRepository.deleteById(notificationId)
            .thenReturn(true)
            .onErrorReturn(false);
    }

    /**
     * Send notification to all configured channels based on user preferences.
     *
     * @param notification the notification to send
     * @return Mono containing the updated notification
     */
    private Mono<Notification> sendToChannels(Notification notification) {
        return preferencesRepository.findByUserId(notification.getUserId())
            .defaultIfEmpty(NotificationPreferences.defaultPreferences(notification.getUserId()))
            .flatMap(prefs -> {
                List<Mono<Void>> sends = new ArrayList<>();

                for (NotificationChannel channel : notification.getChannels()) {
                    if (isChannelEnabled(prefs, channel)) {
                        sends.add(sendToChannel(notification, channel));
                    }
                }

                return Flux.merge(sends)
                    .then(Mono.just(notification.toBuilder()
                        .status(NotificationStatus.SENT)
                        .sentAt(LocalDateTime.now())
                        .build()))
                    .flatMap(notificationRepository::save);
            })
            .onErrorResume(error -> {
                log.error("Error sending notification {}: {}", notification.getId(), error.getMessage());
                notification.setStatus(NotificationStatus.FAILED);
                return notificationRepository.save(notification);
            });
    }

    /**
     * Check if a notification channel is enabled in user preferences.
     *
     * @param prefs the user preferences
     * @param channel the channel to check
     * @return true if channel is enabled
     */
    private boolean isChannelEnabled(NotificationPreferences prefs, NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> prefs.isEmailEnabled();
            case SMS -> prefs.isSmsEnabled();
            case WHATSAPP -> prefs.isWhatsappEnabled();
            case PUSH -> prefs.isPushEnabled();
            case IN_APP -> true; // Always enabled (stored in DB)
        };
    }

    /**
     * Send notification to a specific channel.
     *
     * @param notification the notification to send
     * @param channel the channel to use
     * @return Mono signaling completion
     */
    private Mono<Void> sendToChannel(Notification notification, NotificationChannel channel) {
        return switch (channel) {
            case PUSH -> sendPushNotification(notification);
            case SMS -> sendSmsNotification(notification);
            case WHATSAPP -> sendWhatsAppNotification(notification);
            case EMAIL -> sendEmailNotification(notification);
            case IN_APP -> Mono.empty(); // Already saved to DB
        };
    }

    /**
     * Send push notification to user's devices.
     * TODO: Integrate with FCM/APNS for actual delivery.
     *
     * @param notification the notification to send
     * @return Mono signaling completion
     */
    private Mono<Void> sendPushNotification(Notification notification) {
        log.debug("Sending push notification {} to user {}", notification.getId(), notification.getUserId());

        return deviceRepository.findByUserIdAndIsActive(notification.getUserId(), true)
            .flatMap(device -> {
                // TODO: Integrate with FCM (Android/Web) or APNS (iOS)
                log.info("Would send push to device {} ({}): {}",
                    device.getDeviceToken(), device.getPlatform(), notification.getTitle());
                return Mono.empty();
            })
            .then();
    }

    /**
     * Send SMS notification.
     * TODO: Integrate with SMS provider.
     *
     * @param notification the notification to send
     * @return Mono signaling completion
     */
    private Mono<Void> sendSmsNotification(Notification notification) {
        log.debug("Sending SMS notification {} to user {}", notification.getId(), notification.getUserId());

        // TODO: Integrate with SMS provider (Africa's Talking, Twilio, etc.)
        log.info("Would send SMS to user {}: {}", notification.getUserId(), notification.getBody());
        return Mono.empty();
    }

    /**
     * Send WhatsApp notification.
     * TODO: Integrate with WhatsApp Business API.
     *
     * @param notification the notification to send
     * @return Mono signaling completion
     */
    private Mono<Void> sendWhatsAppNotification(Notification notification) {
        log.debug("Sending WhatsApp notification {} to user {}", notification.getId(), notification.getUserId());

        // TODO: Integrate with WhatsApp Business API
        log.info("Would send WhatsApp to user {}: {}", notification.getUserId(), notification.getBody());
        return Mono.empty();
    }

    /**
     * Send email notification.
     * TODO: Integrate with email provider.
     *
     * @param notification the notification to send
     * @return Mono signaling completion
     */
    private Mono<Void> sendEmailNotification(Notification notification) {
        log.debug("Sending email notification {} to user {}", notification.getId(), notification.getUserId());

        // TODO: Integrate with email provider (SendGrid, AWS SES, etc.)
        log.info("Would send email to user {}: Subject={}, Body={}",
            notification.getUserId(), notification.getTitle(), notification.getBody());
        return Mono.empty();
    }
}
