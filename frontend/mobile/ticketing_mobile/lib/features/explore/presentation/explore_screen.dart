import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/widgets/event_card.dart';
import '../../../shared/widgets/category_chip.dart';

/// Explore screen with search and category filters
class ExploreScreen extends ConsumerStatefulWidget {
  const ExploreScreen({super.key});

  @override
  ConsumerState<ExploreScreen> createState() => _ExploreScreenState();
}

class _ExploreScreenState extends ConsumerState<ExploreScreen> {
  final _searchController = TextEditingController();
  String? _selectedCategory;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: CustomScrollView(
        slivers: [
          // Search AppBar
          SliverAppBar(
            floating: true,
            pinned: true,
            expandedHeight: 120,
            flexibleSpace: FlexibleSpaceBar(
              background: Container(
                padding: const EdgeInsets.fromLTRB(16, 80, 16, 16),
                child: TextField(
                  controller: _searchController,
                  decoration: InputDecoration(
                    hintText: 'Search events...',
                    prefixIcon: const Icon(Icons.search),
                    suffixIcon: _searchController.text.isNotEmpty
                        ? IconButton(
                            icon: const Icon(Icons.clear),
                            onPressed: () {
                              _searchController.clear();
                              setState(() {});
                            },
                          )
                        : null,
                  ),
                  onSubmitted: (query) {
                    if (query.isNotEmpty) {
                      context.go('/explore/search?q=$query');
                    }
                  },
                  onChanged: (_) => setState(() {}),
                ),
              ),
            ),
            title: const Text('Explore'),
          ),

          // Filter Chips
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  FilterChip(
                    label: const Text('Today'),
                    selected: false,
                    onSelected: (_) {},
                  ),
                  FilterChip(
                    label: const Text('This Week'),
                    selected: false,
                    onSelected: (_) {},
                  ),
                  FilterChip(
                    label: const Text('This Month'),
                    selected: false,
                    onSelected: (_) {},
                  ),
                  FilterChip(
                    label: const Text('Free'),
                    selected: false,
                    onSelected: (_) {},
                  ),
                ],
              ),
            ),
          ),

          // Categories
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: Text(
                'Categories',
                style: AppTypography.titleMedium,
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: SizedBox(
              height: 100,
              child: ListView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 12),
                children: [
                  _buildCategoryChip('All', Icons.apps, AppColors.primary, null),
                  _buildCategoryChip('Music', Icons.music_note, AppColors.categoryMusic, 'music'),
                  _buildCategoryChip('Drama', Icons.theater_comedy, AppColors.categoryDrama, 'drama'),
                  _buildCategoryChip('Sports', Icons.sports_soccer, AppColors.categorySports, 'sports'),
                  _buildCategoryChip('Comedy', Icons.sentiment_very_satisfied, AppColors.categoryComedy, 'comedy'),
                  _buildCategoryChip('Festivals', Icons.celebration, AppColors.categoryFestival, 'festivals'),
                ],
              ),
            ),
          ),

          // Events List Header
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    _selectedCategory ?? 'All Events',
                    style: AppTypography.titleLarge,
                  ),
                  IconButton(
                    icon: const Icon(Icons.filter_list),
                    onPressed: () {
                      // TODO: Show filter bottom sheet
                    },
                  ),
                ],
              ),
            ),
          ),

          // Events List
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
            sliver: SliverList(
              delegate: SliverChildBuilderDelegate(
                (context, index) {
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: EventCard(
                      eventId: 'explore_$index',
                      title: 'Explore Event ${index + 1}',
                      imageUrl: null,
                      date: DateTime.now().add(Duration(days: index * 3)),
                      location: index.isEven ? 'Lusaka' : 'Ndola',
                      price: 100.0 + (index * 25),
                      currency: 'ZMW',
                      onTap: () => context.go('/event/explore_$index'),
                    ),
                  );
                },
                childCount: 10,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCategoryChip(String name, IconData icon, Color color, String? categoryId) {
    final isSelected = _selectedCategory == categoryId;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: CategoryChip(
        name: name,
        icon: icon,
        color: color,
        isSelected: isSelected,
        onTap: () {
          setState(() {
            _selectedCategory = categoryId;
          });
        },
      ),
    );
  }
}
