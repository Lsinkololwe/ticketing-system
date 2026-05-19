import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/widgets/event_card.dart';
import '../../../shared/widgets/category_chip.dart';
import '../../../shared/widgets/shimmer_loading.dart';

/// Home screen with featured events, categories, and trending
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      body: CustomScrollView(
        slivers: [
          // App Bar
          SliverAppBar(
            floating: true,
            title: Row(
              children: [
                Icon(Icons.location_on, size: 20, color: AppColors.primary),
                const SizedBox(width: 4),
                Text(
                  'Lusaka',
                  style: AppTypography.titleMedium,
                ),
                const Icon(Icons.keyboard_arrow_down, size: 20),
              ],
            ),
            actions: [
              IconButton(
                icon: const Icon(Icons.notifications_outlined),
                onPressed: () {
                  // TODO: Navigate to notifications
                },
              ),
            ],
          ),

          // Hero Carousel
          SliverToBoxAdapter(
            child: _FeaturedCarousel(),
          ),

          // Categories Section
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 24, 16, 8),
              child: Text(
                'Categories',
                style: AppTypography.titleLarge,
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: _CategoriesRow(),
          ),

          // Trending Events Section
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 24, 16, 8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Trending Now',
                    style: AppTypography.titleLarge,
                  ),
                  TextButton(
                    onPressed: () => context.go('/explore'),
                    child: const Text('See All'),
                  ),
                ],
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: _TrendingEventsList(),
          ),

          // Upcoming Events Section
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 24, 16, 8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Upcoming Events',
                    style: AppTypography.titleLarge,
                  ),
                  TextButton(
                    onPressed: () => context.go('/explore'),
                    child: const Text('See All'),
                  ),
                ],
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
            sliver: _UpcomingEventsList(),
          ),
        ],
      ),
    );
  }
}

/// Featured events carousel
class _FeaturedCarousel extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    // TODO: Replace with actual data from provider
    return SizedBox(
      height: 200,
      child: PageView.builder(
        itemCount: 3,
        controller: PageController(viewportFraction: 0.9),
        itemBuilder: (context, index) {
          return Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
            child: Container(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(16),
                gradient: AppColors.heroGradient,
              ),
              child: Stack(
                children: [
                  // Placeholder for featured event image
                  Positioned.fill(
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(16),
                      child: Container(
                        decoration: BoxDecoration(
                          gradient: AppColors.imageOverlayGradient,
                        ),
                      ),
                    ),
                  ),
                  // Event info
                  Positioned(
                    left: 16,
                    bottom: 16,
                    right: 16,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: AppColors.accent,
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            'FEATURED',
                            style: AppTypography.badge.copyWith(color: Colors.white),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Amazing Event ${index + 1}',
                          style: AppTypography.titleLarge.copyWith(color: Colors.white),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Sat, Jan 25 • Mulungushi Center',
                          style: AppTypography.bodySmall.copyWith(color: Colors.white70),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

/// Categories horizontal row
class _CategoriesRow extends StatelessWidget {
  // Mock categories
  static const _categories = [
    ('Music', Icons.music_note, AppColors.categoryMusic),
    ('Drama', Icons.theater_comedy, AppColors.categoryDrama),
    ('Sports', Icons.sports_soccer, AppColors.categorySports),
    ('Comedy', Icons.sentiment_very_satisfied, AppColors.categoryComedy),
    ('Festivals', Icons.celebration, AppColors.categoryFestival),
  ];

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 100,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12),
        itemCount: _categories.length,
        itemBuilder: (context, index) {
          final (name, icon, color) = _categories[index];
          return Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: CategoryChip(
              name: name,
              icon: icon,
              color: color,
              onTap: () {
                context.go('/explore/category/$index?name=$name');
              },
            ),
          );
        },
      ),
    );
  }
}

/// Trending events list
class _TrendingEventsList extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    // TODO: Replace with actual data from provider
    return SliverList(
      delegate: SliverChildBuilderDelegate(
        (context, index) {
          return Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: EventCard(
              eventId: 'event_$index',
              title: 'Trending Event ${index + 1}',
              imageUrl: null,
              date: DateTime.now().add(Duration(days: index * 7)),
              location: 'Lusaka',
              price: 150.0 + (index * 50),
              currency: 'ZMW',
              onTap: () => context.go('/event/event_$index'),
            ),
          );
        },
        childCount: 3,
      ),
    );
  }
}

/// Upcoming events list
class _UpcomingEventsList extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    // TODO: Replace with actual data from provider
    return SliverList(
      delegate: SliverChildBuilderDelegate(
        (context, index) {
          return Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: EventCard(
              eventId: 'upcoming_$index',
              title: 'Upcoming Event ${index + 1}',
              imageUrl: null,
              date: DateTime.now().add(Duration(days: (index + 1) * 14)),
              location: index.isEven ? 'Lusaka' : 'Kitwe',
              price: 200.0 + (index * 75),
              currency: 'ZMW',
              onTap: () => context.go('/event/upcoming_$index'),
            ),
          );
        },
        childCount: 5,
      ),
    );
  }
}
