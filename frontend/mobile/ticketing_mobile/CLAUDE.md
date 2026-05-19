# Ticketing Mobile - Flutter Development Guidelines

## Project Overview

A Flutter mobile application for event discovery and ticket purchasing in Zambia/Africa.
- **Target Platforms**: Android, iOS, Web
- **Backend**: GraphQL Federation (Apollo Router) with Keycloak authentication
- **State Management**: Riverpod with code generation
- **GraphQL**: Ferry with type generation

---

## Architecture Principles

### 1. Feature-First Architecture

```
lib/
├── core/                 # Shared infrastructure
├── shared/               # Reusable widgets & utilities
├── features/             # Feature modules (self-contained)
│   └── {feature}/
│       ├── data/         # Models, repositories
│       ├── providers/    # Riverpod providers
│       └── presentation/ # Screens, widgets
└── generated/            # Auto-generated code (DO NOT EDIT)
```

### 2. Dependency Rule

- Features CAN import from `core/` and `shared/`
- Features MUST NOT import from other features
- Cross-feature communication via Riverpod providers only

---

## OWASP Mobile Top 10 (2024) Compliance

### M1: Improper Credential Usage

```dart
// NEVER hardcode secrets
// BAD
const apiKey = "sk-1234567890";

// GOOD - Use environment variables
@Envied(path: '.env')
abstract class Env {
  @EnviedField(varName: 'API_KEY', obfuscate: true)
  static String apiKey = _Env.apiKey;
}
```

### M2: Inadequate Supply Chain Security

- Pin exact package versions in `pubspec.yaml`
- Run `flutter pub audit` regularly
- Use `dependabot` or `renovate` for updates

### M3: Insecure Authentication/Authorization

```dart
// ALWAYS use secure token storage
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class SecureTokenStorage {
  // Android: EncryptedSharedPreferences (AES-256)
  // iOS: Keychain Services
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

  static Future<void> saveToken(String token) async {
    await _storage.write(key: 'access_token', value: token);
  }

  static Future<String?> getToken() async {
    return await _storage.read(key: 'access_token');
  }

  static Future<void> clearAll() async {
    await _storage.deleteAll();
  }
}
```

### M4: Insufficient Input/Output Validation

```dart
// ALWAYS validate user input
extension StringValidation on String {
  bool get isValidEmail => RegExp(r'^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$').hasMatch(this);
  bool get isValidPhone => RegExp(r'^\+?[0-9]{10,15}$').hasMatch(this);

  String get sanitized => replaceAll(RegExp(r'[<>"\']'), '');
}

// ALWAYS sanitize data before display
Text(userInput.sanitized)
```

### M5: Insecure Communication

```dart
// ENFORCE certificate pinning for production
class CertificatePinning {
  static final expectedFingerprints = [
    'sha256/AAAA...', // Primary certificate
    'sha256/BBBB...', // Backup certificate
  ];

  static SecurityContext get context {
    final context = SecurityContext.defaultContext;
    // Add trusted certificates
    return context;
  }
}

// NEVER allow HTTP in production
// android/app/src/main/AndroidManifest.xml:
// android:usesCleartextTraffic="false"
```

### M6: Inadequate Privacy Controls

```dart
// MINIMIZE data collection
// Only request necessary permissions

// Clear sensitive data on logout
Future<void> logout() async {
  await SecureTokenStorage.clearAll();
  await Hive.deleteFromDisk(); // Clear local cache
  // Don't persist sensitive user data
}
```

### M7: Insufficient Binary Protections

```yaml
# Enable obfuscation for release builds
# android/app/build.gradle
buildTypes {
  release {
    minifyEnabled true
    shrinkResources true
    proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
  }
}

# Build command with obfuscation
# flutter build apk --obfuscate --split-debug-info=build/debug-info
```

### M8: Security Misconfiguration

```xml
<!-- android/app/src/main/AndroidManifest.xml -->
<application
    android:allowBackup="false"
    android:debuggable="false"
    android:usesCleartextTraffic="false"
    android:networkSecurityConfig="@xml/network_security_config">
```

### M9: Insecure Data Storage

```dart
// NEVER store sensitive data in SharedPreferences
// Use flutter_secure_storage for:
// - Tokens
// - User credentials
// - Payment information

// For non-sensitive cached data, use Hive with encryption
final encryptionKey = await SecureTokenStorage.getOrCreateEncryptionKey();
await Hive.initFlutter();
await Hive.openBox('cache',
  encryptionCipher: HiveAesCipher(encryptionKey),
);
```

### M10: Insufficient Cryptography

```dart
// Use platform-provided crypto
// NEVER implement custom encryption

// For hashing (non-reversible)
import 'package:crypto/crypto.dart';
final hash = sha256.convert(utf8.encode(data));

// For encryption, use flutter_secure_storage
// which uses platform KeyStore/Keychain
```

---

## Memory Optimization Guidelines

### 1. Image Memory Management

```dart
// ALWAYS use cacheWidth/cacheHeight for large images
Image.network(
  imageUrl,
  cacheWidth: 400, // Decode at display size, not original
  cacheHeight: 300,
  fit: BoxFit.cover,
)

// Use CachedNetworkImage for automatic caching
CachedNetworkImage(
  imageUrl: url,
  memCacheWidth: 400,
  maxWidthDiskCache: 800,
  placeholder: (_, __) => const ShimmerPlaceholder(),
  errorWidget: (_, __, ___) => const Icon(Icons.error),
)

// Clear image cache when memory pressure
imageCache.clear();
imageCache.clearLiveImages();
```

### 2. ListView Optimization

```dart
// ALWAYS use ListView.builder for long lists
ListView.builder(
  itemCount: items.length,
  // Add extent for better performance
  itemExtent: 80.0, // Fixed height items
  // Or use prototypeItem for dynamic sizing
  prototypeItem: const EventCard(),
  itemBuilder: (context, index) {
    return EventCard(event: items[index]);
  },
)

// For very long lists, use ListView.separated with keys
ListView.separated(
  itemCount: items.length,
  addAutomaticKeepAlives: false, // Disable for memory savings
  addRepaintBoundaries: true,
  itemBuilder: (context, index) {
    return EventCard(
      key: ValueKey(items[index].id), // Stable keys
      event: items[index],
    );
  },
  separatorBuilder: (_, __) => const SizedBox(height: 8),
)
```

### 3. Dispose Resources Properly

```dart
class _MyWidgetState extends State<MyWidget> {
  late final AnimationController _controller;
  late final ScrollController _scrollController;
  StreamSubscription? _subscription;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this);
    _scrollController = ScrollController();
    _subscription = someStream.listen(_handleData);
  }

  @override
  void dispose() {
    _controller.dispose();
    _scrollController.dispose();
    _subscription?.cancel();
    super.dispose();
  }
}
```

### 4. Riverpod Auto-Dispose

```dart
// PREFER autoDispose for transient data
@riverpod
Future<Event> eventDetail(Ref ref, String eventId) async {
  // Auto-disposed when no longer watched
  final client = ref.watch(graphqlClientProvider);

  // Cancel request on dispose
  final cancelToken = CancelToken();
  ref.onDispose(cancelToken.cancel);

  return client.fetchEvent(eventId, cancelToken: cancelToken);
}

// Use keepAlive for global/persistent data
@Riverpod(keepAlive: true)
AuthState authState(Ref ref) {
  // Persists for app lifetime
  return AuthState.initial();
}
```

---

## Network Request Optimization

### 1. Request Deduplication

```dart
// Ferry handles deduplication automatically via request ID
// For custom requests, implement deduplication:

class RequestDeduplicator {
  final _pendingRequests = <String, Future<dynamic>>{};

  Future<T> dedupe<T>(String key, Future<T> Function() request) async {
    if (_pendingRequests.containsKey(key)) {
      return _pendingRequests[key] as Future<T>;
    }

    final future = request();
    _pendingRequests[key] = future;

    try {
      return await future;
    } finally {
      _pendingRequests.remove(key);
    }
  }
}
```

### 2. Request Cancellation

```dart
@riverpod
Future<List<Event>> searchEvents(Ref ref, String query) async {
  // Debounce search
  await Future.delayed(const Duration(milliseconds: 300));

  // Check if still active after debounce
  if (!ref.exists) return [];

  // Create cancellable request
  final cancelToken = CancelToken();
  ref.onDispose(() => cancelToken.cancel('Provider disposed'));

  return ref.watch(eventsRepositoryProvider).search(
    query,
    cancelToken: cancelToken,
  );
}
```

### 3. Caching Strategy

```dart
// GraphQL Cache Configuration
final cache = Cache(
  store: HiveStore(), // Persistent cache
  possibleTypes: possibleTypesMap,
  typePolicies: {
    'Event': TypePolicy(
      keyFields: {'id': true},
      fields: {
        'ticketTiers': FieldPolicy(
          merge: (existing, incoming, _) => incoming, // Replace
        ),
      },
    ),
  },
);

// Fetch policies
enum FetchPolicy {
  cacheFirst,      // Return cache, fetch in background
  cacheAndNetwork, // Return cache immediately, then network
  networkOnly,     // Skip cache
  cacheOnly,       // Never fetch
  noCache,         // Fetch but don't cache
}
```

### 4. Offline Support

```dart
// Queue mutations when offline
class OfflineMutationQueue {
  final _queue = <OfflineMutation>[];

  void enqueue(OfflineMutation mutation) {
    _queue.add(mutation);
    _persistQueue();
  }

  Future<void> processQueue() async {
    final connectivity = await Connectivity().checkConnectivity();
    if (connectivity == ConnectivityResult.none) return;

    for (final mutation in List.from(_queue)) {
      try {
        await mutation.execute();
        _queue.remove(mutation);
      } catch (e) {
        // Keep in queue for retry
      }
    }
    _persistQueue();
  }
}
```

---

## Responsive Design & Rotation

### 1. Breakpoint System

```dart
enum ScreenSize { compact, medium, expanded }

extension ScreenSizeExtension on BuildContext {
  ScreenSize get screenSize {
    final width = MediaQuery.sizeOf(this).width;
    if (width < 600) return ScreenSize.compact;
    if (width < 840) return ScreenSize.medium;
    return ScreenSize.expanded;
  }

  bool get isLandscape =>
    MediaQuery.orientationOf(this) == Orientation.landscape;

  EdgeInsets get screenPadding =>
    MediaQuery.paddingOf(this); // Safe area
}
```

### 2. Adaptive Layouts

```dart
class AdaptiveScaffold extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth >= 840) {
          return _buildExpandedLayout();
        } else if (constraints.maxWidth >= 600) {
          return _buildMediumLayout();
        }
        return _buildCompactLayout();
      },
    );
  }
}
```

### 3. Rotation Handling

```dart
// Preserve state during rotation
class _EventDetailState extends State<EventDetail> {
  // Use AutomaticKeepAliveClientMixin for tabs
  @override
  bool get wantKeepAlive => true;

  @override
  Widget build(BuildContext context) {
    super.build(context); // Required for keep alive

    final isLandscape = context.isLandscape;

    return isLandscape
      ? _buildLandscapeLayout()
      : _buildPortraitLayout();
  }
}

// Prevent rotation on specific screens (e.g., QR code)
class TicketQRScreen extends StatefulWidget {
  @override
  State<TicketQRScreen> createState() => _TicketQRScreenState();
}

class _TicketQRScreenState extends State<TicketQRScreen> {
  @override
  void initState() {
    super.initState();
    // Lock to portrait for QR scanning
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
    ]);
  }

  @override
  void dispose() {
    // Restore rotation
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    super.dispose();
  }
}
```

---

## Platform-Specific Guidelines

### Android

```yaml
# android/app/build.gradle
android {
  compileSdkVersion 34

  defaultConfig {
    minSdkVersion 24  # Android 7.0+
    targetSdkVersion 34
    multiDexEnabled true
  }

  buildTypes {
    release {
      minifyEnabled true
      shrinkResources true
    }
  }
}
```

**Required Permissions:**
```xml
<!-- android/app/src/main/AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" /> <!-- QR scanning -->

<!-- AppAuth redirect -->
<activity android:name="net.openid.appauth.RedirectUriReceiverActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="com.ticketing.mobile" />
    </intent-filter>
</activity>
```

### iOS

```xml
<!-- ios/Runner/Info.plist -->
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>com.ticketing.mobile</string>
        </array>
    </dict>
</array>

<!-- Camera permission for QR -->
<key>NSCameraUsageDescription</key>
<string>Camera access is required to scan ticket QR codes</string>

<!-- App Transport Security -->
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <false/>
</dict>
```

### Web

```dart
// Use conditional imports for web-specific code
import 'stub.dart'
    if (dart.library.html) 'web.dart'
    if (dart.library.io) 'mobile.dart';

// Web-specific storage (no flutter_secure_storage)
class WebTokenStorage implements TokenStorage {
  @override
  Future<void> save(String token) async {
    // Use sessionStorage, not localStorage for tokens
    html.window.sessionStorage['token'] = token;
  }
}
```

---

## GraphQL Code Generation

### Configuration

```yaml
# build.yaml
targets:
  $default:
    builders:
      ferry_generator|graphql_builder:
        enabled: true
        options:
          schema: ticketing_mobile|lib/graphql/schema.graphql
          # Only generate for mobile-tagged operations
          output_dir: lib/generated/graphql/

      ferry_generator|serializer_builder:
        enabled: true
        options:
          schema: ticketing_mobile|lib/graphql/schema.graphql
```

### Operations Structure

```
lib/graphql/
├── schema.graphql          # Downloaded from backend
├── fragments/
│   ├── event_card.graphql  # Reusable fragments
│   └── ticket.graphql
├── queries/
│   ├── events.graphql      # Query operations
│   └── tickets.graphql
└── mutations/
    ├── purchase.graphql    # Mutation operations
    └── auth.graphql
```

### Download Schema

```bash
# Download schema from Apollo Router
rover supergraph introspect http://localhost:4000/graphql > lib/graphql/schema.graphql

# Or use get-graphql-schema
npx get-graphql-schema http://localhost:4000/graphql > lib/graphql/schema.graphql
```

### Generate Types

```bash
# Run code generation
dart run build_runner build --delete-conflicting-outputs

# Watch mode for development
dart run build_runner watch --delete-conflicting-outputs
```

---

## Testing Guidelines

### Unit Tests

```dart
// test/features/events/providers/events_provider_test.dart
void main() {
  late ProviderContainer container;
  late MockEventsRepository mockRepo;

  setUp(() {
    mockRepo = MockEventsRepository();
    container = ProviderContainer(
      overrides: [
        eventsRepositoryProvider.overrideWithValue(mockRepo),
      ],
    );
  });

  tearDown(() {
    container.dispose();
  });

  test('fetches featured events', () async {
    when(mockRepo.getFeatured()).thenAnswer(
      (_) async => [Event.mock()],
    );

    final events = await container.read(featuredEventsProvider.future);

    expect(events, hasLength(1));
    verify(mockRepo.getFeatured()).called(1);
  });
}
```

### Widget Tests

```dart
// test/features/home/presentation/home_screen_test.dart
void main() {
  testWidgets('displays featured events', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          featuredEventsProvider.overrideWith(
            (_) async => [Event.mock()],
          ),
        ],
        child: const MaterialApp(home: HomeScreen()),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.byType(EventCard), findsOneWidget);
  });
}
```

### Integration Tests

```dart
// integration_test/app_test.dart
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('complete purchase flow', (tester) async {
    app.main();
    await tester.pumpAndSettle();

    // Navigate to event
    await tester.tap(find.byType(EventCard).first);
    await tester.pumpAndSettle();

    // Tap buy
    await tester.tap(find.text('Buy Tickets'));
    await tester.pumpAndSettle();

    // Verify auth prompt appears
    expect(find.text('Login to continue'), findsOneWidget);
  });
}
```

---

## Pre-Commit Checklist

- [ ] No hardcoded secrets or API keys
- [ ] All sensitive data uses `flutter_secure_storage`
- [ ] Images use `cacheWidth`/`cacheHeight`
- [ ] Lists use `ListView.builder` with stable keys
- [ ] All controllers/subscriptions disposed
- [ ] Riverpod providers use `autoDispose` where appropriate
- [ ] Network requests are cancellable
- [ ] Input validation on all user inputs
- [ ] Works in both portrait and landscape
- [ ] Tested on Android, iOS, and Web
- [ ] No `print()` statements (use `logger`)
- [ ] Ran `flutter analyze` with no errors
- [ ] Types generated from GraphQL (no manual types)

---

## Build Commands

```bash
# Development
flutter run -d chrome --web-renderer html
flutter run -d ios
flutter run -d android

# Generate code
dart run build_runner build --delete-conflicting-outputs

# Analyze
flutter analyze
dart fix --apply

# Test
flutter test
flutter test --coverage

# Release builds
flutter build apk --release --obfuscate --split-debug-info=build/debug-info
flutter build ios --release
flutter build web --release --web-renderer canvaskit
```

---

## Environment Configuration

```dart
// lib/core/config/env.dart
@Envied(path: '.env')
abstract class Env {
  @EnviedField(varName: 'GRAPHQL_ENDPOINT')
  static const String graphqlEndpoint = _Env.graphqlEndpoint;

  @EnviedField(varName: 'KEYCLOAK_URL')
  static const String keycloakUrl = _Env.keycloakUrl;

  @EnviedField(varName: 'KEYCLOAK_REALM')
  static const String keycloakRealm = _Env.keycloakRealm;

  @EnviedField(varName: 'KEYCLOAK_CLIENT_ID')
  static const String keycloakClientId = _Env.keycloakClientId;
}
```

```bash
# .env (DO NOT COMMIT)
GRAPHQL_ENDPOINT=http://localhost:4000/graphql
KEYCLOAK_URL=http://localhost:8084
KEYCLOAK_REALM=event-ticketing
KEYCLOAK_CLIENT_ID=event-ticketing-mobile
```

---

## Contact & Resources

- **Backend GraphQL**: `http://localhost:4000/graphql`
- **Keycloak Admin**: `http://localhost:8084/admin`
- **Design System**: `docs/APP_DESIGN.md`
- **Navigation Diagrams**: `docs/NAVIGATION_DIAGRAM.md`
