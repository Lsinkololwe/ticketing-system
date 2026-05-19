import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/auth/auth_service.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';

/// Purchase flow bottom sheet
class PurchaseFlow extends ConsumerStatefulWidget {
  final String eventId;

  const PurchaseFlow({super.key, required this.eventId});

  @override
  ConsumerState<PurchaseFlow> createState() => _PurchaseFlowState();
}

class _PurchaseFlowState extends ConsumerState<PurchaseFlow> {
  int _currentStep = 0;
  final Map<String, int> _selectedTiers = {};
  Timer? _reservationTimer;
  int _remainingSeconds = 600; // 10 minutes

  @override
  void dispose() {
    _reservationTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authServiceProvider);

    return Container(
      height: MediaQuery.of(context).size.height * 0.85,
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        children: [
          // Header
          _buildHeader(),

          // Content
          Expanded(
            child: _buildContent(authState),
          ),

          // Footer
          _buildFooter(authState),
        ],
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        border: Border(
          bottom: BorderSide(color: AppColors.border),
        ),
      ),
      child: Column(
        children: [
          // Drag handle
          Container(
            width: 40,
            height: 4,
            margin: const EdgeInsets.only(bottom: 16),
            decoration: BoxDecoration(
              color: AppColors.border,
              borderRadius: BorderRadius.circular(2),
            ),
          ),

          // Title & close
          Row(
            children: [
              Expanded(
                child: Text(
                  _getStepTitle(),
                  style: AppTypography.titleLarge,
                ),
              ),
              IconButton(
                icon: const Icon(Icons.close),
                onPressed: () => context.pop(),
              ),
            ],
          ),

          // Timer (when reservation active)
          if (_currentStep > 0 && _remainingSeconds > 0) ...[
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.timer, size: 16, color: AppColors.warning),
                const SizedBox(width: 4),
                Text(
                  'Reserved for ${_formatTime(_remainingSeconds)}',
                  style: AppTypography.bodySmall.copyWith(
                    color: AppColors.warning,
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildContent(AuthState authState) {
    switch (_currentStep) {
      case 0:
        return _TicketSelectionStep(
          selectedTiers: _selectedTiers,
          onChanged: (tierId, quantity) {
            setState(() {
              if (quantity > 0) {
                _selectedTiers[tierId] = quantity;
              } else {
                _selectedTiers.remove(tierId);
              }
            });
          },
        );
      case 1:
        return _OrderReviewStep(
          selectedTiers: _selectedTiers,
        );
      case 2:
        return _PaymentMethodStep();
      case 3:
        return _ProcessingStep();
      case 4:
        return _ConfirmationStep(
          onViewTickets: () {
            context.go('/tickets');
          },
          onBackToHome: () {
            context.go('/');
          },
        );
      default:
        return const SizedBox.shrink();
    }
  }

  Widget _buildFooter(AuthState authState) {
    if (_currentStep == 4) return const SizedBox.shrink(); // Confirmation has its own buttons

    final total = _calculateTotal();
    final hasSelection = _selectedTiers.isNotEmpty;

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
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Total
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  '${_getTotalTickets()} ticket${_getTotalTickets() != 1 ? 's' : ''}',
                  style: AppTypography.bodyMedium,
                ),
                Text(
                  'Total: K${total.toStringAsFixed(0)}',
                  style: AppTypography.priceLarge,
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Action button
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: hasSelection ? () => _handleContinue(authState) : null,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.accent,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                ),
                child: Text(_getButtonLabel()),
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _getStepTitle() {
    switch (_currentStep) {
      case 0:
        return 'Select Tickets';
      case 1:
        return 'Review Order';
      case 2:
        return 'Payment';
      case 3:
        return 'Processing';
      case 4:
        return 'Confirmed!';
      default:
        return '';
    }
  }

  String _getButtonLabel() {
    switch (_currentStep) {
      case 0:
        return 'Continue';
      case 1:
        return 'Pay Now';
      case 2:
        return 'Confirm Payment';
      default:
        return 'Continue';
    }
  }

  void _handleContinue(AuthState authState) {
    // Check auth before proceeding past ticket selection
    if (_currentStep == 0 && !authState.isAuthenticated) {
      context.push('/login?redirect=/event/${widget.eventId}/purchase');
      return;
    }

    setState(() {
      _currentStep++;

      // Start timer when reservation is created
      if (_currentStep == 1) {
        _startReservationTimer();
      }

      // Simulate payment processing
      if (_currentStep == 3) {
        Future.delayed(const Duration(seconds: 3), () {
          if (mounted) {
            setState(() => _currentStep = 4);
          }
        });
      }
    });
  }

  void _startReservationTimer() {
    _reservationTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_remainingSeconds > 0) {
        setState(() => _remainingSeconds--);
      } else {
        timer.cancel();
        // Handle expiration
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Reservation expired')),
        );
        context.pop();
      }
    });
  }

  String _formatTime(int seconds) {
    final minutes = seconds ~/ 60;
    final secs = seconds % 60;
    return '${minutes.toString().padLeft(2, '0')}:${secs.toString().padLeft(2, '0')}';
  }

  double _calculateTotal() {
    // Mock prices
    final prices = {'general': 150.0, 'vip': 500.0, 'vvip': 1000.0};
    return _selectedTiers.entries.fold(0.0, (sum, entry) {
      return sum + (prices[entry.key] ?? 0) * entry.value;
    });
  }

  int _getTotalTickets() {
    return _selectedTiers.values.fold(0, (sum, qty) => sum + qty);
  }
}

/// Ticket selection step
class _TicketSelectionStep extends StatelessWidget {
  final Map<String, int> selectedTiers;
  final void Function(String tierId, int quantity) onChanged;

  const _TicketSelectionStep({
    required this.selectedTiers,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _TicketTierSelector(
          tierId: 'general',
          name: 'General Admission',
          description: 'Standard entry',
          price: 150,
          available: 100,
          quantity: selectedTiers['general'] ?? 0,
          onChanged: (qty) => onChanged('general', qty),
        ),
        const SizedBox(height: 12),
        _TicketTierSelector(
          tierId: 'vip',
          name: 'VIP',
          description: 'Premium seating • Front rows',
          price: 500,
          available: 20,
          quantity: selectedTiers['vip'] ?? 0,
          onChanged: (qty) => onChanged('vip', qty),
        ),
        const SizedBox(height: 12),
        _TicketTierSelector(
          tierId: 'vvip',
          name: 'VVIP',
          description: 'Exclusive access • Meet & Greet',
          price: 1000,
          available: 5,
          quantity: selectedTiers['vvip'] ?? 0,
          onChanged: (qty) => onChanged('vvip', qty),
        ),
      ],
    );
  }
}

class _TicketTierSelector extends StatelessWidget {
  final String tierId;
  final String name;
  final String description;
  final double price;
  final int available;
  final int quantity;
  final ValueChanged<int> onChanged;

  const _TicketTierSelector({
    required this.tierId,
    required this.name,
    required this.description,
    required this.price,
    required this.available,
    required this.quantity,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final subtotal = price * quantity;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(name, style: AppTypography.titleMedium),
                      Text(
                        description,
                        style: AppTypography.bodySmall.copyWith(
                          color: AppColors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
                Text(
                  'K${price.toStringAsFixed(0)}',
                  style: AppTypography.priceLarge.copyWith(
                    color: AppColors.primary,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                // Quantity selector
                Container(
                  decoration: BoxDecoration(
                    border: Border.all(color: AppColors.border),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    children: [
                      IconButton(
                        icon: const Icon(Icons.remove),
                        onPressed: quantity > 0
                            ? () => onChanged(quantity - 1)
                            : null,
                      ),
                      SizedBox(
                        width: 40,
                        child: Text(
                          quantity.toString(),
                          style: AppTypography.titleMedium,
                          textAlign: TextAlign.center,
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.add),
                        onPressed: quantity < available
                            ? () => onChanged(quantity + 1)
                            : null,
                      ),
                    ],
                  ),
                ),
                const Spacer(),
                // Subtotal
                if (quantity > 0)
                  Text(
                    'K${subtotal.toStringAsFixed(0)}',
                    style: AppTypography.titleMedium,
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Order review step
class _OrderReviewStep extends StatelessWidget {
  final Map<String, int> selectedTiers;

  const _OrderReviewStep({required this.selectedTiers});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Order items
        ...selectedTiers.entries.map((entry) {
          final name = entry.key == 'general'
              ? 'General Admission'
              : entry.key.toUpperCase();
          final price = entry.key == 'general' ? 150 : (entry.key == 'vip' ? 500 : 1000);
          return ListTile(
            title: Text('$name x ${entry.value}'),
            trailing: Text('K${(price * entry.value).toStringAsFixed(0)}'),
          );
        }),
        const Divider(),
        // Fees
        ListTile(
          title: const Text('Service fee'),
          trailing: const Text('K10'),
        ),
        const Divider(),
      ],
    );
  }
}

/// Payment method step
class _PaymentMethodStep extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Select Payment Method', style: AppTypography.titleMedium),
        const SizedBox(height: 16),
        _PaymentOptionCard(
          icon: Icons.phone_android,
          title: 'Mobile Money',
          subtitle: 'MTN, Airtel, Zamtel',
          isSelected: true,
        ),
        const SizedBox(height: 8),
        _PaymentOptionCard(
          icon: Icons.credit_card,
          title: 'Card',
          subtitle: 'Visa, Mastercard',
          isSelected: false,
        ),
      ],
    );
  }
}

class _PaymentOptionCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final bool isSelected;

  const _PaymentOptionCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.isSelected,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      color: isSelected ? AppColors.primaryVeryLight : null,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(
          color: isSelected ? AppColors.primary : AppColors.border,
          width: isSelected ? 2 : 1,
        ),
      ),
      child: ListTile(
        leading: Icon(icon, color: isSelected ? AppColors.primary : null),
        title: Text(title),
        subtitle: Text(subtitle),
        trailing: isSelected
            ? Icon(Icons.check_circle, color: AppColors.primary)
            : null,
      ),
    );
  }
}

/// Processing step
class _ProcessingStep extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const CircularProgressIndicator(),
          const SizedBox(height: 24),
          Text('Processing payment...', style: AppTypography.titleMedium),
          const SizedBox(height: 8),
          Text(
            'Please wait while we confirm your payment.',
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.textSecondary,
            ),
          ),
        ],
      ),
    );
  }
}

/// Confirmation step
class _ConfirmationStep extends StatelessWidget {
  final VoidCallback onViewTickets;
  final VoidCallback onBackToHome;

  const _ConfirmationStep({
    required this.onViewTickets,
    required this.onBackToHome,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.check_circle,
            size: 80,
            color: AppColors.success,
          ),
          const SizedBox(height: 24),
          Text(
            'Payment Successful!',
            style: AppTypography.headlineMedium,
          ),
          const SizedBox(height: 8),
          Text(
            'Your tickets have been confirmed and sent to your email.',
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.textSecondary,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 32),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: onViewTickets,
              child: const Text('View My Tickets'),
            ),
          ),
          const SizedBox(height: 12),
          TextButton(
            onPressed: onBackToHome,
            child: const Text('Back to Home'),
          ),
        ],
      ),
    );
  }
}
