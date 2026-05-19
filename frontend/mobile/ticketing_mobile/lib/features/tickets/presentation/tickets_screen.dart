import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/auth/auth_service.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';

/// My Tickets screen showing purchased tickets
class TicketsScreen extends ConsumerStatefulWidget {
  const TicketsScreen({super.key});

  @override
  ConsumerState<TicketsScreen> createState() => _TicketsScreenState();
}

class _TicketsScreenState extends ConsumerState<TicketsScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authServiceProvider);

    // Show login prompt if not authenticated
    if (!authState.isAuthenticated) {
      return _LoginPrompt();
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('My Tickets'),
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(text: 'Upcoming'),
            Tab(text: 'Past'),
          ],
          indicatorColor: AppColors.primary,
          labelColor: AppColors.primary,
          unselectedLabelColor: AppColors.textSecondary,
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          // Upcoming tickets
          _TicketsList(isPast: false),
          // Past tickets
          _TicketsList(isPast: true),
        ],
      ),
    );
  }
}

class _TicketsList extends StatelessWidget {
  final bool isPast;

  const _TicketsList({required this.isPast});

  @override
  Widget build(BuildContext context) {
    // TODO: Replace with actual data from provider
    final tickets = List.generate(isPast ? 5 : 3, (index) => index);

    if (tickets.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              isPast ? Icons.history : Icons.confirmation_number_outlined,
              size: 64,
              color: AppColors.textHint,
            ),
            const SizedBox(height: 16),
            Text(
              isPast ? 'No past tickets' : 'No upcoming tickets',
              style: AppTypography.titleMedium.copyWith(
                color: AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              isPast
                  ? 'Your ticket history will appear here'
                  : 'Browse events and get your tickets!',
              style: AppTypography.bodyMedium.copyWith(
                color: AppColors.textHint,
              ),
            ),
            if (!isPast) ...[
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: () => context.go('/explore'),
                child: const Text('Explore Events'),
              ),
            ],
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: tickets.length,
      itemBuilder: (context, index) {
        return Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: _TicketCard(
            ticketId: 'ticket_$index',
            eventTitle: '${isPast ? "Past" : "Upcoming"} Event ${index + 1}',
            date: isPast
                ? DateTime.now().subtract(Duration(days: (index + 1) * 7))
                : DateTime.now().add(Duration(days: (index + 1) * 7)),
            location: index.isEven ? 'Lusaka' : 'Kitwe',
            ticketType: index.isEven ? 'VIP' : 'General',
            quantity: index + 1,
            isPast: isPast,
          ),
        );
      },
    );
  }
}

class _TicketCard extends StatelessWidget {
  final String ticketId;
  final String eventTitle;
  final DateTime date;
  final String location;
  final String ticketType;
  final int quantity;
  final bool isPast;

  const _TicketCard({
    required this.ticketId,
    required this.eventTitle,
    required this.date,
    required this.location,
    required this.ticketType,
    required this.quantity,
    required this.isPast,
  });

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat('EEE, MMM d • h:mm a');

    return Card(
      child: InkWell(
        onTap: () => context.go('/tickets/$ticketId'),
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              // Event image placeholder
              Container(
                width: 64,
                height: 64,
                decoration: BoxDecoration(
                  color: AppColors.surfaceVariant,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(
                  Icons.event,
                  color: AppColors.textHint,
                ),
              ),
              const SizedBox(width: 16),

              // Ticket info
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      eventTitle,
                      style: AppTypography.titleMedium,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      dateFormat.format(date),
                      style: AppTypography.bodySmall.copyWith(
                        color: AppColors.textSecondary,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 2,
                          ),
                          decoration: BoxDecoration(
                            color: AppColors.primaryVeryLight,
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            ticketType,
                            style: AppTypography.labelSmall.copyWith(
                              color: AppColors.primary,
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          '$quantity ticket${quantity > 1 ? 's' : ''}',
                          style: AppTypography.bodySmall.copyWith(
                            color: AppColors.textSecondary,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),

              // QR button or status
              if (!isPast)
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: AppColors.primaryVeryLight,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(
                    Icons.qr_code,
                    color: AppColors.primary,
                  ),
                )
              else
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppColors.ticketUsed.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    'Used',
                    style: AppTypography.labelSmall.copyWith(
                      color: AppColors.ticketUsed,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _LoginPrompt extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('My Tickets'),
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.confirmation_number_outlined,
                size: 80,
                color: AppColors.textHint,
              ),
              const SizedBox(height: 24),
              Text(
                'Sign in to view your tickets',
                style: AppTypography.titleLarge,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 8),
              Text(
                'Your purchased tickets will appear here after you sign in.',
                style: AppTypography.bodyMedium.copyWith(
                  color: AppColors.textSecondary,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 32),
              ElevatedButton(
                onPressed: () => context.push('/login?redirect=/tickets'),
                child: const Text('Sign In'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
