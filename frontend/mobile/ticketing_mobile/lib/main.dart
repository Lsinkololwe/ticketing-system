import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive_flutter/hive_flutter.dart';

import 'core/auth/auth_service.dart';
import 'core/navigation/app_router.dart';
import 'core/network/graphql_client.dart';
import 'core/theme/app_theme.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize Hive for local storage
  await Hive.initFlutter();

  // Initialize Ferry GraphQL cache
  await initHiveForFerry();

  // Set preferred orientations
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
  ]);

  // Set system UI overlay style
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
      systemNavigationBarColor: Colors.white,
      systemNavigationBarIconBrightness: Brightness.dark,
    ),
  );

  runApp(
    const ProviderScope(
      child: TicketingApp(),
    ),
  );
}

/// Root application widget
class TicketingApp extends ConsumerWidget {
  const TicketingApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Watch router for navigation updates
    final router = ref.watch(routerProvider);

    // Initialize auth service on first build
    ref.listen(authServiceProvider, (previous, next) {
      // Auth state changes handled by router redirect
    });

    return MaterialApp.router(
      title: 'Ticketing',
      debugShowCheckedModeBanner: false,

      // Theme
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: ThemeMode.system,

      // Router
      routerConfig: router,

      // Localization (future)
      // localizationsDelegates: AppLocalizations.localizationsDelegates,
      // supportedLocales: AppLocalizations.supportedLocales,
    );
  }
}
