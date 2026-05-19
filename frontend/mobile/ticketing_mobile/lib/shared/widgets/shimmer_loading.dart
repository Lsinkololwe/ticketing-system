import 'package:flutter/material.dart';
import 'package:shimmer/shimmer.dart';

import '../../core/theme/app_colors.dart';

/// Shimmer loading placeholder for content
class ShimmerLoading extends StatelessWidget {
  final double width;
  final double height;
  final double borderRadius;

  const ShimmerLoading({
    super.key,
    required this.width,
    required this.height,
    this.borderRadius = 8,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Shimmer.fromColors(
      baseColor: isDark ? AppColors.darkSurfaceVariant : AppColors.surfaceVariant,
      highlightColor: isDark ? AppColors.darkSurface : AppColors.surface,
      child: Container(
        width: width,
        height: height,
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(borderRadius),
        ),
      ),
    );
  }
}

/// Shimmer loading for event cards
class EventCardShimmer extends StatelessWidget {
  const EventCardShimmer({super.key});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Row(
        children: [
          // Image placeholder
          const ShimmerLoading(
            width: 100,
            height: 100,
            borderRadius: 0,
          ),

          // Content
          Expanded(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const ShimmerLoading(width: 150, height: 16),
                  const SizedBox(height: 8),
                  const ShimmerLoading(width: 100, height: 12),
                  const SizedBox(height: 4),
                  const ShimmerLoading(width: 80, height: 12),
                  const SizedBox(height: 8),
                  const ShimmerLoading(width: 60, height: 14),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Shimmer loading for event list
class EventListShimmer extends StatelessWidget {
  final int count;

  const EventListShimmer({super.key, this.count = 3});

  @override
  Widget build(BuildContext context) {
    return SliverList(
      delegate: SliverChildBuilderDelegate(
        (context, index) => const Padding(
          padding: EdgeInsets.only(bottom: 12),
          child: EventCardShimmer(),
        ),
        childCount: count,
      ),
    );
  }
}

/// Shimmer for category chips
class CategoryChipShimmer extends StatelessWidget {
  const CategoryChipShimmer({super.key});

  @override
  Widget build(BuildContext context) {
    return const SizedBox(
      width: 80,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ShimmerLoading(width: 48, height: 48, borderRadius: 12),
          SizedBox(height: 8),
          ShimmerLoading(width: 60, height: 12),
        ],
      ),
    );
  }
}
