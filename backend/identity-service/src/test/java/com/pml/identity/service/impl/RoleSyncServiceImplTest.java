package com.pml.identity.service.impl;

import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.domain.model.User;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.repository.AuditLogRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.shared.constants.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RoleSyncServiceImpl
 *
 * Tests cover:
 * - Successful role grant/revoke
 * - Idempotency (duplicate operations)
 * - Error handling (user not found, Keycloak errors)
 * - Audit logging
 * - Circuit breaker fallback
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoleSyncService Unit Tests")
class RoleSyncServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakService keycloakService;

    @Mock
    private AuditLogRepository auditLogRepository;

    private RoleSyncServiceImpl roleSyncService;

    @BeforeEach
    void setUp() {
        roleSyncService = new RoleSyncServiceImpl(userRepository, keycloakService, auditLogRepository);
    }

    @Test
    @DisplayName("Should grant ORGANIZER role successfully")
    void shouldGrantOrganizerRoleSuccessfully() {
        // Given
        String userId = "user-123";
        String adminId = "admin-456";
        String organizationId = "org-789";

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(EnumSet.of(UserType.CUSTOMER))
                .build();

        User updatedUser = user.toBuilder()
                .roles(EnumSet.of(UserType.CUSTOMER, UserType.ORGANIZER))
                .build();

        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(updatedUser));
        when(keycloakService.addRoleToUser(userId, "ORGANIZER")).thenReturn(Mono.empty());
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(Mono.just(new AuditLog()));

        // When & Then
        StepVerifier.create(roleSyncService.grantOrganizerRole(userId, adminId, organizationId))
                .verifyComplete();

        // Verify user was saved with new role
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRoles()).contains(UserType.ORGANIZER);

        // Verify Keycloak was called
        verify(keycloakService).addRoleToUser(userId, "ORGANIZER");

        // Verify audit log was created
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, atLeastOnce()).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertThat(audit.getAction()).isEqualTo(AuditLog.AuditAction.ROLE_GRANT);
        assertThat(audit.getUserId()).isEqualTo(userId);
        assertThat(audit.getPerformedBy()).isEqualTo(adminId);
    }

    @Test
    @DisplayName("Should be idempotent when user already has ORGANIZER role")
    void shouldBeIdempotentWhenRoleExists() {
        // Given
        String userId = "user-123";
        String adminId = "admin-456";
        String organizationId = "org-789";

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(EnumSet.of(UserType.CUSTOMER, UserType.ORGANIZER)) // Already has role
                .build();

        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(Mono.just(new AuditLog()));

        // When & Then
        StepVerifier.create(roleSyncService.grantOrganizerRole(userId, adminId, organizationId))
                .verifyComplete();

        // Verify MongoDB save was NOT called (idempotent)
        verify(userRepository, never()).save(any(User.class));

        // Verify Keycloak was NOT called (idempotent)
        verify(keycloakService, never()).addRoleToUser(any(), any());

        // Verify audit log was still created
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should handle user not found error")
    void shouldHandleUserNotFound() {
        // Given
        String userId = "nonexistent-user";
        String adminId = "admin-456";
        String organizationId = "org-789";

        when(userRepository.findById(userId)).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(roleSyncService.grantOrganizerRole(userId, adminId, organizationId))
                .expectErrorMatches(error ->
                        error instanceof IllegalArgumentException &&
                        error.getMessage().contains("User not found")
                )
                .verify();

        // Verify no side effects
        verify(userRepository, never()).save(any());
        verify(keycloakService, never()).addRoleToUser(any(), any());
    }

    @Test
    @DisplayName("Should handle Keycloak failure gracefully")
    void shouldHandleKeycloakFailureGracefully() {
        // Given
        String userId = "user-123";
        String adminId = "admin-456";
        String organizationId = "org-789";

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(EnumSet.of(UserType.CUSTOMER))
                .build();

        User updatedUser = user.toBuilder()
                .roles(EnumSet.of(UserType.CUSTOMER, UserType.ORGANIZER))
                .build();

        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(updatedUser));
        when(keycloakService.addRoleToUser(userId, "ORGANIZER"))
                .thenReturn(Mono.error(new RuntimeException("Keycloak connection timeout")));
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(Mono.just(new AuditLog()));

        // When & Then
        StepVerifier.create(roleSyncService.grantOrganizerRole(userId, adminId, organizationId))
                .verifyComplete(); // Should complete despite Keycloak failure

        // Verify user was saved in MongoDB (partial success)
        verify(userRepository).save(any(User.class));

        // Verify failure audit log was created
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, atLeastOnce()).save(auditCaptor.capture());

        // Find the failure audit log
        boolean hasFailureLog = auditCaptor.getAllValues().stream()
                .anyMatch(audit ->
                        audit.getAction() == AuditLog.AuditAction.KEYCLOAK_SYNC_FAILURE &&
                        audit.getStatus() == AuditLog.AuditStatus.FAILURE
                );
        assertThat(hasFailureLog).isTrue();
    }

    @Test
    @DisplayName("Should revoke ORGANIZER role successfully")
    void shouldRevokeOrganizerRoleSuccessfully() {
        // Given
        String userId = "user-123";
        String adminId = "admin-456";
        String organizationId = "org-789";

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(EnumSet.of(UserType.CUSTOMER, UserType.ORGANIZER))
                .build();

        User updatedUser = user.toBuilder()
                .roles(EnumSet.of(UserType.CUSTOMER))
                .build();

        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(updatedUser));
        when(keycloakService.removeRoleFromUser(userId, "ORGANIZER")).thenReturn(Mono.empty());
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(Mono.just(new AuditLog()));

        // When & Then
        StepVerifier.create(roleSyncService.revokeOrganizerRole(userId, adminId, organizationId))
                .verifyComplete();

        // Verify user was saved without ORGANIZER role
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRoles()).doesNotContain(UserType.ORGANIZER);
        assertThat(savedUser.getRoles()).contains(UserType.CUSTOMER); // CUSTOMER role remains

        // Verify Keycloak was called
        verify(keycloakService).removeRoleFromUser(userId, "ORGANIZER");

        // Verify audit log
        verify(auditLogRepository, atLeastOnce()).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should be idempotent when revoking non-existent role")
    void shouldBeIdempotentWhenRevokingNonExistentRole() {
        // Given
        String userId = "user-123";
        String adminId = "admin-456";
        String organizationId = "org-789";

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(EnumSet.of(UserType.CUSTOMER)) // Does not have ORGANIZER role
                .build();

        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(Mono.just(new AuditLog()));

        // When & Then
        StepVerifier.create(roleSyncService.revokeOrganizerRole(userId, adminId, organizationId))
                .verifyComplete();

        // Verify no state changes (idempotent)
        verify(userRepository, never()).save(any(User.class));
        verify(keycloakService, never()).removeRoleFromUser(any(), any());

        // Verify audit log was created
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should sync all user roles to Keycloak")
    void shouldSyncAllUserRolesToKeycloak() {
        // Given
        String userId = "user-123";
        String adminId = "admin-456";

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(EnumSet.of(UserType.CUSTOMER, UserType.ORGANIZER, UserType.SCANNER))
                .build();

        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(keycloakService.syncUserRoles(eq(userId), any())).thenReturn(Mono.empty());
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(Mono.just(new AuditLog()));

        // When & Then
        StepVerifier.create(roleSyncService.syncUserRoles(userId, adminId))
                .verifyComplete();

        // Verify Keycloak sync was called with correct roles
        ArgumentCaptor<EnumSet<UserType>> rolesCaptor = ArgumentCaptor.forClass(EnumSet.class);
        verify(keycloakService).syncUserRoles(eq(userId), rolesCaptor.capture());
        EnumSet<UserType> syncedRoles = rolesCaptor.getValue();
        assertThat(syncedRoles).containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.ORGANIZER, UserType.SCANNER);

        // Verify success audit log
        verify(auditLogRepository, atLeastOnce()).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should check Keycloak health status")
    void shouldCheckKeycloakHealthStatus() {
        // Given - Keycloak is healthy
        when(keycloakService.countUsers()).thenReturn(Mono.just(100));

        // When & Then
        StepVerifier.create(roleSyncService.isKeycloakHealthy())
                .expectNext(true)
                .verifyComplete();

        // Given - Keycloak is unhealthy
        when(keycloakService.countUsers()).thenReturn(Mono.error(new RuntimeException("Connection refused")));

        // When & Then
        StepVerifier.create(roleSyncService.isKeycloakHealthy())
                .expectNext(false)
                .verifyComplete();
    }
}
