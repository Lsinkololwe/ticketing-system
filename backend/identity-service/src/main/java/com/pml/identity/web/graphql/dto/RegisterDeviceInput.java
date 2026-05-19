package com.pml.identity.web.graphql.dto;

import com.pml.identity.domain.enums.DevicePlatform;

/**
 * Input DTO for registering a user device for push notifications.
 *
 * @param deviceToken the push notification token (FCM for Android/Web, APNS for iOS)
 * @param platform the device platform
 * @param deviceName human-readable device name (e.g., "John's iPhone")
 */
public record RegisterDeviceInput(
    String deviceToken,
    DevicePlatform platform,
    String deviceName
) {}
