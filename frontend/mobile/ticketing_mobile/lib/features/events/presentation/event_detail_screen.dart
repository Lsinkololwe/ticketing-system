import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:share_plus/share_plus.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';

/// Event detail screen
class EventDetailScreen extends ConsumerWidget {
  final String eventId;

  const EventDetailScreen({super.key, required this.eventId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // TODO: Replace with actual provider
    // final eventAsync = ref.watch(eventDetailProvider(eventId));

    return Scaffold(
      body: CustomScrollView(
        slivers: [
          // Hero image with app bar
          _SliverHeroAppBar(eventId: eventId),

          // Event content
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Title
                  Text(
                    'Amazing Event Title',
                    style: AppTypography.headlineMedium,
                  ),
                  const SizedBox(height: 16),

                  // Date & Time
                  _InfoRow(
                    icon: Icons.calendar_today,
                    title: 'Saturday, January 25, 2025',
                    subtitle: '7:00 PM - 11:00 PM',
                  ),
                  const SizedBox(height: 12),

                  // Location
                  _InfoRow(
                    icon: Icons.location_on,
                    title: 'Mulungushi Conference Center',
                    subtitle: 'Lusaka, Zambia',
                    trailing: TextButton(
                      onPressed: () {
                        // TODO: Open map
                      },
                      child: const Text('View Map'),
                    ),
                  ),
                  const SizedBox(height: 24),

                  // Ticket Options
                  Text(
                    'Ticket Options',
                    style: AppTypography.titleLarge,
                  ),
                  const SizedBox(height: 12),
                  _TicketTierCard(
                    name: 'General Admission',
                    description: 'Standard entry',
                    price: 150,
                    available: 100,
                    currency: 'ZMW',
                  ),
                  const SizedBox(height: 8),
                  _TicketTierCard(
                    name: 'VIP',
                    description: 'Premium seating • Front rows',
                    price: 500,
                    available: 20,
                    currency: 'ZMW',
                  ),
                  const SizedBox(height: 8),
                  _TicketTierCard(
                    name: 'VVIP',
                    description: 'Exclusive access • Meet & Greet',
                    price: 1000,
                    available: 0, // Sold out
                    currency: 'ZMW',
                  ),
                  const SizedBox(height: 24),

                  // About section
                  Text(
                    'About This Event',
                    style: AppTypography.titleLarge,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Lorem ipsum dolor sit amet, consectetur adipiscing elit. '
                    'Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. '
                    'Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris '
                    'nisi ut aliquip ex ea commodo consequat.\n\n'
                    'Duis aute irure dolor in reprehenderit in voluptate velit esse '
                    'cillum dolore eu fugiat nulla pariatur.',
                    style: AppTypography.bodyMedium.copyWith(
                      color: AppColors.textSecondary,
                    ),
                  ),
                  const SizedBox(height: 24),

                  // Organizer
                  Text(
                    'Organizer',
                    style: AppTypography.titleLarge,
                  ),
                  const SizedBox(height: 12),
                  _OrganizerCard(),

                  // Bottom padding for sticky button
                  const SizedBox(height: 100),
                ],
              ),
            ),
          ),
        ],
      ),

      // Sticky buy button
      bottomNavigationBar: _StickyBuyButton(
        minPrice: 150,
        currency: 'ZMW',
        onPressed: () {
          context.push('/event/$eventId/purchase');
        },
      ),
    );
  }
}

class _SliverHeroAppBar extends StatelessWidget {
  final String eventId;

  const _SliverHeroAppBar({required this.eventId});

  @override
  Widget build(BuildContext context) {
    return SliverAppBar(
      expandedHeight: 250,
      pinned: true,
      stretch: true,
      backgroundColor: AppColors.primaryDark,
      leading: IconButton(
        icon: Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: Colors.black26,
            shape: BoxShape.circle,
          ),
          child: const Icon(Icons.arrow_back, color: Colors.white),
        ),
        onPressed: () => context.pop(),
      ),
      actions: [
        IconButton(
          icon: Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: Colors.black26,
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.favorite_border, color: Colors.white),
          ),
          onPressed: () {
            // TODO: Add to favorites
          },
        ),
        IconButton(
          icon: Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: Colors.black26,
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.share, color: Colors.white),
          ),
          onPressed: () {
            Share.share('Check out this amazing event!');
          },
        ),
      ],
      flexibleSpace: FlexibleSpaceBar(
        background: Stack(
          fit: StackFit.expand,
          children: [
            // Image placeholder
            Container(
              decoration: BoxDecoration(
                gradient: AppColors.heroGradient,
              ),
            ),
            // Gradient overlay
            Container(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Colors.transparent,
                    Colors.black.withValues(alpha: 0.5),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final Widget? trailing;

  const _InfoRow({
    required this.icon,
    required this.title,
    required this.subtitle,
    this.trailing,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: AppColors.primaryVeryLight,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Icon(icon, color: AppColors.primary),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: AppTypography.titleSmall),
              Text(
                subtitle,
                style: AppTypography.bodySmall.copyWith(
                  color: AppColors.textSecondary,
                ),
              ),
            ],
          ),
        ),
        if (trailing != null) trailing!,
      ],
    );
  }
}

class _TicketTierCard extends StatelessWidget {
  final String name;
  final String description;
  final double price;
  final int available;
  final String currency;

  const _TicketTierCard({
    required this.name,
    required this.description,
    required this.price,
    required this.available,
    required this.currency,
  });

  bool get isSoldOut => available == 0;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text(name, style: AppTypography.titleMedium),
                      if (isSoldOut) ...[
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 2,
                          ),
                          decoration: BoxDecoration(
                            color: AppColors.error.withValues(alpha: 0.1),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            'SOLD OUT',
                            style: AppTypography.badge.copyWith(
                              color: AppColors.error,
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    description,
                    style: AppTypography.bodySmall.copyWith(
                      color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ),
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  'K${price.toStringAsFixed(0)}',
                  style: AppTypography.priceLarge.copyWith(
                    color: isSoldOut ? AppColors.textHint : AppColors.primary,
                  ),
                ),
                if (!isSoldOut)
                  Text(
                    '$available left',
                    style: AppTypography.bodySmall.copyWith(
                      color: available < 10 ? AppColors.warning : AppColors.textSecondary,
                    ),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _OrganizerCard extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            CircleAvatar(
              radius: 24,
              backgroundColor: AppColors.primaryLight,
              child: const Text('E', style: TextStyle(color: Colors.white)),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Event Company', style: AppTypography.titleMedium),
                  Text(
                    '15 events hosted',
                    style: AppTypography.bodySmall.copyWith(
                      color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ),
            ),
            TextButton(
              onPressed: () {
                // TODO: View organizer profile
              },
              child: const Text('View'),
            ),
          ],
        ),
      ),
    );
  }
}

class _StickyBuyButton extends StatelessWidget {
  final double minPrice;
  final String currency;
  final VoidCallback onPressed;

  const _StickyBuyButton({
    required this.minPrice,
    required this.currency,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.1),
            blurRadius: 10,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: SafeArea(
        child: Row(
          children: [
            Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'From',
                  style: AppTypography.bodySmall.copyWith(
                    color: AppColors.textSecondary,
                  ),
                ),
                Text(
                  'K${minPrice.toStringAsFixed(0)}',
                  style: AppTypography.priceLarge,
                ),
              ],
            ),
            const SizedBox(width: 16),
            Expanded(
              child: ElevatedButton(
                onPressed: onPressed,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.accent,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                ),
                child: const Text('Buy Tickets'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
