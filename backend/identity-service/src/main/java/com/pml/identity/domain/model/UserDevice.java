package com.pml.identity.domain.model;

import com.pml.identity.domain.enums.DevicePlatform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Entity representing a user's registered device for push notifications.
 * Tracks device tokens for mobile (FCM/APNS) and web push notifications.
 */
@Document(collection = "user_devices")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserDevice {

    /**
     * Unique identifier for the device registration
     */
    @Id
    private String id;

    /**
     * ID of the user who owns this device
     */
    @Indexed
    private String userId;

    /**
     * Push notification token (FCM token for Android/Web, APNS token for iOS)
     */
    @Indexed
    private String deviceToken;

    /**
     * Platform of the device
     */
    private DevicePlatform platform;

    /**
     * Human-readable device name (e.g., "John's iPhone", "Chrome on MacBook")
     */
    private String deviceName;

    /**
     * Whether the device is currently active
     */
    @Builder.Default
    private boolean isActive = true;

    /**
     * Timestamp when device was last active
     */
    private LocalDateTime lastActiveAt;

    /**
     * Timestamp when device was registered
     */
    @CreatedDate
    private LocalDateTime createdAt;
}
