import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../auth/auth_service.dart';
import '../../features/home/presentation/home_screen.dart';
import '../../features/explore/presentation/explore_screen.dart';
import '../../features/tickets/presentation/tickets_screen.dart';
import '../../features/profile/presentation/profile_screen.dart';
import '../../features/events/presentation/event_detail_screen.dart';
import '../../features/auth/presentation/auth_modal.dart';
import '../../features/purchase/presentation/purchase_flow.dart';
import '../../shared/widgets/main_scaffold.dart';

/// Route paths
class AppRoutes {
  AppRoutes._();

  // Bottom navigation tabs
  static const home = '/';
  static const explore = '/explore';
  static const tickets = '/tickets';
  static const profile = '/profile';

  // Event routes
  static const eventDetail = '/event/:id';
  static String eventDetailPath(String id) => '/event/$id';

  // Purchase flow
  static const purchase = '/event/:id/purchase';
  static String purchasePath(String id) => '/event/$id/purchase';

  // Ticket routes
  static const ticketDetail = '/ticket/:id';
  static String ticketDetailPath(String id) => '/ticket/$id';

  // Auth
  static const login = '/login';

  // Profile sub-routes
  static const editProfile = '/profile/edit';
  static const notificationSettings = '/profile/notifications';

  // Search
  static const search = '/search';
  static const searchResults = '/search/results';
  static const categoryEvents = '/category/:id';
  static String categoryEventsPath(String id) => '/category/$id';
}

/// Navigation key for programmatic navigation
final rootNavigatorKey = GlobalKey<NavigatorState>(debugLabel: 'root');
final shellNavigatorKey = GlobalKey<NavigatorState>(debugLabel: 'shell');

/// Router provider
final routerProvider = Provider<GoRouter>((ref) {
  final authState = ref.watch(authServiceProvider);

  return GoRouter(
    navigatorKey: rootNavigatorKey,
    initialLocation: AppRoutes.home,
    debugLogDiagnostics: true,

    // Redirect logic
    redirect: (context, state) {
      final isAuthenticated = authState.isAuthenticated;
      final isLoading = authState.status == AuthStatus.unknown;
      final currentPath = state.uri.path;

      // Don't redirect while loading auth state
      if (isLoading) return null;

      // Protected routes that require auth
      final protectedRoutes = [
        AppRoutes.tickets,
        AppRoutes.profile,
        '/ticket',
        '/profile/',
      ];

      final isProtectedRoute = protectedRoutes.any(
        (route) => currentPath.startsWith(route),
      );

      // Redirect to login if accessing protected route while not authenticated
      if (isProtectedRoute && !isAuthenticated) {
        // Save the intended destination
        return '${AppRoutes.login}?redirect=${Uri.encodeComponent(currentPath)}';
      }

      // If already logged in and trying to access login, redirect home
      if (currentPath == AppRoutes.login && isAuthenticated) {
        return AppRoutes.home;
      }

      return null;
    },

    routes: [
      // Main scaffold with bottom navigation
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) {
          return MainScaffold(navigationShell: navigationShell);
        },
        branches: [
          // Home tab
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.home,
                name: 'home',
                pageBuilder: (context, state) => const NoTransitionPage(
                  child: HomeScreen(),
                ),
                routes: [
                  // Event detail from home
                  GoRoute(
                    path: 'event/:id',
                    name: 'eventDetail',
                    builder: (context, state) {
                      final id = state.pathParameters['id']!;
                      return EventDetailScreen(eventId: id);
                    },
                    routes: [
                      // Purchase flow
                      GoRoute(
                        path: 'purchase',
                        name: 'purchase',
                        pageBuilder: (context, state) {
                          final id = state.pathParameters['id']!;
                          return ModalBottomSheetPage(
                            child: PurchaseFlow(eventId: id),
                          );
                        },
                      ),
                    ],
                  ),
                ],
              ),
            ],
          ),

          // Explore tab
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.explore,
                name: 'explore',
                pageBuilder: (context, state) => const NoTransitionPage(
                  child: ExploreScreen(),
                ),
                routes: [
                  // Search results
                  GoRoute(
                    path: 'search',
                    name: 'searchResults',
                    builder: (context, state) {
                      final query = state.uri.queryParameters['q'] ?? '';
                      return SearchResultsScreen(query: query);
                    },
                  ),
                  // Category events
                  GoRoute(
                    path: 'category/:id',
                    name: 'categoryEvents',
                    builder: (context, state) {
                      final id = state.pathParameters['id']!;
                      final name = state.uri.queryParameters['name'] ?? 'Category';
                      return CategoryEventsScreen(categoryId: id, categoryName: name);
                    },
                  ),
                ],
              ),
            ],
          ),

          // Tickets tab (requires auth)
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.tickets,
                name: 'tickets',
                pageBuilder: (context, state) => const NoTransitionPage(
                  child: TicketsScreen(),
                ),
                routes: [
                  // Ticket detail
                  GoRoute(
                    path: ':id',
                    name: 'ticketDetail',
                    builder: (context, state) {
                      final id = state.pathParameters['id']!;
                      return TicketDetailScreen(ticketId: id);
                    },
                    routes: [
                      // QR fullscreen
                      GoRoute(
                        path: 'qr',
                        name: 'ticketQr',
                        pageBuilder: (context, state) {
                          final id = state.pathParameters['id']!;
                          return MaterialPage(
                            fullscreenDialog: true,
                            child: TicketQrScreen(ticketId: id),
                          );
                        },
                      ),
                    ],
                  ),
                ],
              ),
            ],
          ),

          // Profile tab (requires auth)
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.profile,
                name: 'profile',
                pageBuilder: (context, state) => const NoTransitionPage(
                  child: ProfileScreen(),
                ),
                routes: [
                  GoRoute(
                    path: 'edit',
                    name: 'editProfile',
                    builder: (context, state) => const EditProfileScreen(),
                  ),
                  GoRoute(
                    path: 'notifications',
                    name: 'notificationSettings',
                    builder: (context, state) => const NotificationSettingsScreen(),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),

      // Auth modal (displayed over any screen)
      GoRoute(
        path: AppRoutes.login,
        name: 'login',
        pageBuilder: (context, state) {
          final redirect = state.uri.queryParameters['redirect'];
          return ModalBottomSheetPage(
            child: AuthModal(redirectPath: redirect),
          );
        },
      ),
    ],

    errorBuilder: (context, state) => ErrorScreen(error: state.error),
  );
});

/// Modal bottom sheet page for transitions
class ModalBottomSheetPage<T> extends Page<T> {
  final Widget child;

  const ModalBottomSheetPage({
    required this.child,
    super.key,
  });

  @override
  Route<T> createRoute(BuildContext context) {
    return ModalBottomSheetRoute<T>(
      settings: this,
      isScrollControlled: true,
      builder: (context) => child,
    );
  }
}

/// Placeholder screens (to be implemented)
class SearchResultsScreen extends StatelessWidget {
  final String query;
  const SearchResultsScreen({super.key, required this.query});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Search: $query')),
      body: const Center(child: Text('Search Results')),
    );
  }
}

class CategoryEventsScreen extends StatelessWidget {
  final String categoryId;
  final String categoryName;
  const CategoryEventsScreen({super.key, required this.categoryId, required this.categoryName});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(categoryName)),
      body: const Center(child: Text('Category Events')),
    );
  }
}

class TicketDetailScreen extends StatelessWidget {
  final String ticketId;
  const TicketDetailScreen({super.key, required this.ticketId});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Ticket')),
      body: const Center(child: Text('Ticket Detail')),
    );
  }
}

class TicketQrScreen extends StatelessWidget {
  final String ticketId;
  const TicketQrScreen({super.key, required this.ticketId});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('QR Code')),
      body: const Center(child: Text('QR Code Fullscreen')),
    );
  }
}

class EditProfileScreen extends StatelessWidget {
  const EditProfileScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Edit Profile')),
      body: const Center(child: Text('Edit Profile')),
    );
  }
}

class NotificationSettingsScreen extends StatelessWidget {
  const NotificationSettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Notifications')),
      body: const Center(child: Text('Notification Settings')),
    );
  }
}

class ErrorScreen extends StatelessWidget {
  final Exception? error;
  const ErrorScreen({super.key, this.error});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Error')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, size: 64),
            const SizedBox(height: 16),
            Text(error?.toString() ?? 'Page not found'),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: () => context.go(AppRoutes.home),
              child: const Text('Go Home'),
            ),
          ],
        ),
      ),
    );
  }
}
