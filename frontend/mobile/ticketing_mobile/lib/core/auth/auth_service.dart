import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_appauth/flutter_appauth.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/env.dart';
import 'secure_storage.dart';

/// Authentication state
enum AuthStatus { unknown, authenticated, unauthenticated }

/// User info from JWT token
class AuthUser {
  final String id;
  final String? email;
  final String? fullName;
  final String? firstName;
  final String? lastName;
  final String? phoneNumber;
  final bool phoneVerified;
  final bool emailVerified;
  final List<String> roles;

  const AuthUser({
    required this.id,
    this.email,
    this.fullName,
    this.firstName,
    this.lastName,
    this.phoneNumber,
    this.phoneVerified = false,
    this.emailVerified = false,
    this.roles = const [],
  });

  bool get isOrganizer => roles.contains('ORGANIZER');
  bool get isAdmin => roles.contains('ADMIN') || roles.contains('SUPER_ADMIN');
  bool get isCustomer => roles.contains('CUSTOMER');

  factory AuthUser.fromJwt(Map<String, dynamic> claims) {
    // Extract roles from realm_access.roles
    final realmAccess = claims['realm_access'] as Map<String, dynamic>?;
    final roles = (realmAccess?['roles'] as List<dynamic>?)
            ?.map((e) => e.toString())
            .toList() ??
        [];

    return AuthUser(
      id: claims['sub'] as String,
      email: claims['email'] as String?,
      fullName: claims['name'] as String? ??
          '${claims['given_name'] ?? ''} ${claims['family_name'] ?? ''}'.trim(),
      firstName: claims['given_name'] as String?,
      lastName: claims['family_name'] as String?,
      phoneNumber: claims['phone_number'] as String?,
      phoneVerified: claims['phone_verified'] as bool? ?? false,
      emailVerified: claims['email_verified'] as bool? ?? false,
      roles: roles,
    );
  }
}

/// Authentication state with user
class AuthState {
  final AuthStatus status;
  final AuthUser? user;
  final String? accessToken;
  final String? error;

  const AuthState({
    this.status = AuthStatus.unknown,
    this.user,
    this.accessToken,
    this.error,
  });

  bool get isAuthenticated => status == AuthStatus.authenticated;
  bool get isLoading => status == AuthStatus.unknown;

  AuthState copyWith({
    AuthStatus? status,
    AuthUser? user,
    String? accessToken,
    String? error,
  }) {
    return AuthState(
      status: status ?? this.status,
      user: user ?? this.user,
      accessToken: accessToken ?? this.accessToken,
      error: error ?? this.error,
    );
  }
}

/// Auth service using AppAuth for Keycloak OIDC
class AuthService extends StateNotifier<AuthState> {
  final FlutterAppAuth _appAuth;

  AuthService({FlutterAppAuth? appAuth})
      : _appAuth = appAuth ?? const FlutterAppAuth(),
        super(const AuthState());

  /// Initialize and check for existing session
  Future<void> initialize() async {
    try {
      final hasTokens = await SecureTokenStorage.hasTokens();
      if (!hasTokens) {
        state = const AuthState(status: AuthStatus.unauthenticated);
        return;
      }

      // Check if token is expired
      final isExpired = await SecureTokenStorage.isTokenExpired();
      if (isExpired) {
        // Try to refresh
        final refreshed = await refreshToken();
        if (!refreshed) {
          await SecureTokenStorage.clearAll();
          state = const AuthState(status: AuthStatus.unauthenticated);
          return;
        }
      }

      // Parse user from token
      final accessToken = await SecureTokenStorage.getAccessToken();
      if (accessToken != null) {
        final user = _parseUserFromToken(accessToken);
        state = AuthState(
          status: AuthStatus.authenticated,
          user: user,
          accessToken: accessToken,
        );
      } else {
        state = const AuthState(status: AuthStatus.unauthenticated);
      }
    } catch (e) {
      debugPrint('Auth initialization error: $e');
      state = const AuthState(status: AuthStatus.unauthenticated);
    }
  }

  /// Login with Keycloak (opens browser)
  Future<bool> login() async {
    try {
      final result = await _appAuth.authorizeAndExchangeCode(
        AuthorizationTokenRequest(
          AppConfig.clientId,
          AppConfig.redirectUri,
          discoveryUrl: AppConfig.keycloakDiscoveryUrl,
          scopes: AppConfig.scopes,
          promptValues: ['login'],
        ),
      );

      if (result != null) {
        await _saveTokenResult(result);

        final user = _parseUserFromToken(result.accessToken!);
        state = AuthState(
          status: AuthStatus.authenticated,
          user: user,
          accessToken: result.accessToken,
        );
        return true;
      }

      return false;
    } catch (e) {
      debugPrint('Login error: $e');
      state = AuthState(
        status: AuthStatus.unauthenticated,
        error: e.toString(),
      );
      return false;
    }
  }

  /// Refresh access token using refresh token
  Future<bool> refreshToken() async {
    try {
      final refreshToken = await SecureTokenStorage.getRefreshToken();
      if (refreshToken == null) return false;

      final result = await _appAuth.token(
        TokenRequest(
          AppConfig.clientId,
          AppConfig.redirectUri,
          discoveryUrl: AppConfig.keycloakDiscoveryUrl,
          refreshToken: refreshToken,
          scopes: AppConfig.scopes,
        ),
      );

      if (result != null && result.accessToken != null) {
        await _saveTokenResult(result);

        final user = _parseUserFromToken(result.accessToken!);
        state = AuthState(
          status: AuthStatus.authenticated,
          user: user,
          accessToken: result.accessToken,
        );
        return true;
      }

      return false;
    } catch (e) {
      debugPrint('Token refresh error: $e');
      return false;
    }
  }

  /// Logout and clear tokens
  Future<void> logout() async {
    try {
      final idToken = await SecureTokenStorage.getIdToken();

      if (idToken != null) {
        // End session with Keycloak
        await _appAuth.endSession(
          EndSessionRequest(
            idTokenHint: idToken,
            postLogoutRedirectUrl: AppConfig.postLogoutRedirectUri,
            discoveryUrl: AppConfig.keycloakDiscoveryUrl,
          ),
        );
      }
    } catch (e) {
      debugPrint('Logout error: $e');
    } finally {
      await SecureTokenStorage.clearAll();
      state = const AuthState(status: AuthStatus.unauthenticated);
    }
  }

  /// Get valid access token (refresh if needed)
  Future<String?> getValidAccessToken() async {
    final isExpired = await SecureTokenStorage.isTokenExpired();

    if (isExpired) {
      final refreshed = await refreshToken();
      if (!refreshed) return null;
    }

    return SecureTokenStorage.getAccessToken();
  }

  Future<void> _saveTokenResult(TokenResponse result) async {
    await SecureTokenStorage.saveTokens(
      accessToken: result.accessToken!,
      refreshToken: result.refreshToken,
      idToken: result.idToken,
      expiry: result.accessTokenExpirationDateTime,
    );
  }

  AuthUser? _parseUserFromToken(String token) {
    try {
      // Decode JWT payload
      final parts = token.split('.');
      if (parts.length != 3) return null;

      final payload = parts[1];
      final normalized = base64Url.normalize(payload);
      final decoded = utf8.decode(base64Url.decode(normalized));
      final claims = json.decode(decoded) as Map<String, dynamic>;

      return AuthUser.fromJwt(claims);
    } catch (e) {
      debugPrint('Token parsing error: $e');
      return null;
    }
  }
}

/// Provider for auth service
final authServiceProvider = StateNotifierProvider<AuthService, AuthState>((ref) {
  final service = AuthService();
  // Initialize on creation
  service.initialize();
  return service;
});

/// Provider for current user (convenience)
final currentUserProvider = Provider<AuthUser?>((ref) {
  return ref.watch(authServiceProvider).user;
});

/// Provider for auth status
final isAuthenticatedProvider = Provider<bool>((ref) {
  return ref.watch(authServiceProvider).isAuthenticated;
});
