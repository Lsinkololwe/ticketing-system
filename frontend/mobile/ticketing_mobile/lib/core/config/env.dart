import 'package:envied/envied.dart';

part 'env.g.dart';

/// Environment configuration loaded from .env file
/// Run: dart run build_runner build
///
/// Create a .env file in project root:
/// ```
/// GRAPHQL_ENDPOINT=http://localhost:4000/graphql
/// KEYCLOAK_URL=http://localhost:8084
/// KEYCLOAK_REALM=event-ticketing
/// KEYCLOAK_CLIENT_ID=event-ticketing-mobile
/// ```
@Envied(path: '.env', obfuscate: true)
abstract class Env {
  /// GraphQL endpoint (Apollo Router)
  @EnviedField(varName: 'GRAPHQL_ENDPOINT')
  static String graphqlEndpoint = _Env.graphqlEndpoint;

  /// Keycloak base URL
  @EnviedField(varName: 'KEYCLOAK_URL')
  static String keycloakUrl = _Env.keycloakUrl;

  /// Keycloak realm name
  @EnviedField(varName: 'KEYCLOAK_REALM')
  static String keycloakRealm = _Env.keycloakRealm;

  /// Keycloak client ID for mobile app
  @EnviedField(varName: 'KEYCLOAK_CLIENT_ID')
  static String keycloakClientId = _Env.keycloakClientId;

  /// Keycloak redirect scheme (for OAuth callback)
  @EnviedField(varName: 'KEYCLOAK_REDIRECT_SCHEME', defaultValue: 'com.ticketing.mobile')
  static String keycloakRedirectScheme = _Env.keycloakRedirectScheme;
}

/// Derived URLs from environment
class AppConfig {
  AppConfig._();

  /// GraphQL endpoint
  static String get graphqlEndpoint => Env.graphqlEndpoint;

  /// Keycloak discovery URL (OIDC well-known endpoint)
  static String get keycloakDiscoveryUrl =>
      '${Env.keycloakUrl}/realms/${Env.keycloakRealm}/.well-known/openid-configuration';

  /// Keycloak authorization endpoint
  static String get keycloakAuthorizationEndpoint =>
      '${Env.keycloakUrl}/realms/${Env.keycloakRealm}/protocol/openid-connect/auth';

  /// Keycloak token endpoint
  static String get keycloakTokenEndpoint =>
      '${Env.keycloakUrl}/realms/${Env.keycloakRealm}/protocol/openid-connect/token';

  /// Keycloak end session endpoint
  static String get keycloakEndSessionEndpoint =>
      '${Env.keycloakUrl}/realms/${Env.keycloakRealm}/protocol/openid-connect/logout';

  /// Keycloak userinfo endpoint
  static String get keycloakUserInfoEndpoint =>
      '${Env.keycloakUrl}/realms/${Env.keycloakRealm}/protocol/openid-connect/userinfo';

  /// OAuth redirect URI
  static String get redirectUri => '${Env.keycloakRedirectScheme}://callback';

  /// OAuth post-logout redirect URI
  static String get postLogoutRedirectUri => '${Env.keycloakRedirectScheme}://logout-callback';

  /// Keycloak client ID
  static String get clientId => Env.keycloakClientId;

  /// OAuth scopes
  static const List<String> scopes = [
    'openid',
    'profile',
    'email',
    'phone',
    'offline_access', // For refresh tokens
  ];

  /// Reservation timeout in minutes
  static const int reservationTimeoutMinutes = 10;

  /// Payment polling interval in seconds
  static const int paymentPollingIntervalSeconds = 3;

  /// Maximum payment polling attempts
  static const int maxPaymentPollingAttempts = 60; // 3 minutes
}
