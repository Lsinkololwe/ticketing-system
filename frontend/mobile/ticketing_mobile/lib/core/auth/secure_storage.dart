import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Secure token storage using platform-specific secure storage
/// - Android: EncryptedSharedPreferences (AES-256)
/// - iOS: Keychain Services
class SecureTokenStorage {
  SecureTokenStorage._();

  static const _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(
      encryptedSharedPreferences: true,
      keyCipherAlgorithm: KeyCipherAlgorithm.RSA_ECB_OAEPwithSHA_256andMGF1Padding,
      storageCipherAlgorithm: StorageCipherAlgorithm.AES_GCM_NoPadding,
    ),
    iOptions: IOSOptions(
      accessibility: KeychainAccessibility.first_unlock_this_device,
    ),
  );

  // Storage keys
  static const _accessTokenKey = 'access_token';
  static const _refreshTokenKey = 'refresh_token';
  static const _idTokenKey = 'id_token';
  static const _tokenExpiryKey = 'token_expiry';

  /// Save access token
  static Future<void> saveAccessToken(String token) async {
    await _storage.write(key: _accessTokenKey, value: token);
  }

  /// Get access token
  static Future<String?> getAccessToken() async {
    return _storage.read(key: _accessTokenKey);
  }

  /// Save refresh token
  static Future<void> saveRefreshToken(String token) async {
    await _storage.write(key: _refreshTokenKey, value: token);
  }

  /// Get refresh token
  static Future<String?> getRefreshToken() async {
    return _storage.read(key: _refreshTokenKey);
  }

  /// Save ID token
  static Future<void> saveIdToken(String token) async {
    await _storage.write(key: _idTokenKey, value: token);
  }

  /// Get ID token
  static Future<String?> getIdToken() async {
    return _storage.read(key: _idTokenKey);
  }

  /// Save token expiry timestamp
  static Future<void> saveTokenExpiry(DateTime expiry) async {
    await _storage.write(
      key: _tokenExpiryKey,
      value: expiry.millisecondsSinceEpoch.toString(),
    );
  }

  /// Get token expiry timestamp
  static Future<DateTime?> getTokenExpiry() async {
    final value = await _storage.read(key: _tokenExpiryKey);
    if (value == null) return null;
    return DateTime.fromMillisecondsSinceEpoch(int.parse(value));
  }

  /// Check if token is expired (with 30 second buffer)
  static Future<bool> isTokenExpired() async {
    final expiry = await getTokenExpiry();
    if (expiry == null) return true;
    return DateTime.now().isAfter(expiry.subtract(const Duration(seconds: 30)));
  }

  /// Save all tokens at once
  static Future<void> saveTokens({
    required String accessToken,
    String? refreshToken,
    String? idToken,
    DateTime? expiry,
  }) async {
    await Future.wait([
      saveAccessToken(accessToken),
      if (refreshToken != null) saveRefreshToken(refreshToken),
      if (idToken != null) saveIdToken(idToken),
      if (expiry != null) saveTokenExpiry(expiry),
    ]);
  }

  /// Clear all tokens (on logout)
  static Future<void> clearAll() async {
    await _storage.deleteAll();
  }

  /// Check if user has tokens stored
  static Future<bool> hasTokens() async {
    final token = await getAccessToken();
    return token != null && token.isNotEmpty;
  }
}
