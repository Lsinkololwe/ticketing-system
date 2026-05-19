import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/auth/auth_service.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';

/// Profile screen
class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authServiceProvider);

    // Show login prompt if not authenticated
    if (!authState.isAuthenticated) {
      return _LoginPrompt();
    }

    final user = authState.user!;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Profile'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            onPressed: () {
              // TODO: Navigate to settings
            },
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Profile header
          _ProfileHeader(user: user),
          const SizedBox(height: 24),

          // Account section
          _SectionTitle(title: 'Account'),
          _ProfileMenuItem(
            icon: Icons.person_outline,
            title: 'Edit Profile',
            onTap: () => context.push('/profile/edit'),
          ),
          _ProfileMenuItem(
            icon: Icons.notifications_outlined,
            title: 'Notifications',
            onTap: () => context.push('/profile/notifications'),
          ),
          _ProfileMenuItem(
            icon: Icons.payment_outlined,
            title: 'Payment Methods',
            onTap: () {
              // TODO: Navigate to payment methods
            },
          ),

          const SizedBox(height: 24),

          // Support section
          _SectionTitle(title: 'Support'),
          _ProfileMenuItem(
            icon: Icons.help_outline,
            title: 'Help & FAQ',
            onTap: () {
              // TODO: Navigate to help
            },
          ),
          _ProfileMenuItem(
            icon: Icons.mail_outline,
            title: 'Contact Us',
            onTap: () {
              // TODO: Navigate to contact
            },
          ),
          _ProfileMenuItem(
            icon: Icons.description_outlined,
            title: 'Terms & Privacy',
            onTap: () {
              // TODO: Navigate to terms
            },
          ),

          const SizedBox(height: 24),

          // App info
          _SectionTitle(title: 'App'),
          _ProfileMenuItem(
            icon: Icons.info_outline,
            title: 'About',
            trailing: const Text(
              'v1.0.0',
              style: TextStyle(color: AppColors.textHint),
            ),
            onTap: () {
              // TODO: Show about dialog
            },
          ),

          const SizedBox(height: 32),

          // Logout button
          OutlinedButton.icon(
            onPressed: () async {
              final confirmed = await _showLogoutConfirmation(context);
              if (confirmed && context.mounted) {
                await ref.read(authServiceProvider.notifier).logout();
              }
            },
            icon: const Icon(Icons.logout),
            label: const Text('Sign Out'),
            style: OutlinedButton.styleFrom(
              foregroundColor: AppColors.error,
              side: const BorderSide(color: AppColors.error),
            ),
          ),
        ],
      ),
    );
  }

  Future<bool> _showLogoutConfirmation(BuildContext context) async {
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Sign Out'),
            content: const Text('Are you sure you want to sign out?'),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel'),
              ),
              TextButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Sign Out'),
              ),
            ],
          ),
        ) ??
        false;
  }
}

class _ProfileHeader extends StatelessWidget {
  final AuthUser user;

  const _ProfileHeader({required this.user});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        // Avatar
        CircleAvatar(
          radius: 40,
          backgroundColor: AppColors.primaryLight,
          child: Text(
            user.fullName?.isNotEmpty == true
                ? user.fullName![0].toUpperCase()
                : user.email?[0].toUpperCase() ?? '?',
            style: AppTypography.headlineLarge.copyWith(
              color: Colors.white,
            ),
          ),
        ),
        const SizedBox(width: 16),

        // User info
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                user.fullName ?? 'User',
                style: AppTypography.titleLarge,
              ),
              if (user.email != null) ...[
                const SizedBox(height: 4),
                Text(
                  user.email!,
                  style: AppTypography.bodyMedium.copyWith(
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
              if (user.phoneNumber != null) ...[
                const SizedBox(height: 2),
                Row(
                  children: [
                    Text(
                      user.phoneNumber!,
                      style: AppTypography.bodySmall.copyWith(
                        color: AppColors.textSecondary,
                      ),
                    ),
                    if (user.phoneVerified) ...[
                      const SizedBox(width: 4),
                      Icon(
                        Icons.verified,
                        size: 14,
                        color: AppColors.success,
                      ),
                    ],
                  ],
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final String title;

  const _SectionTitle({required this.title});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(
        title,
        style: AppTypography.labelMedium.copyWith(
          color: AppColors.textSecondary,
        ),
      ),
    );
  }
}

class _ProfileMenuItem extends StatelessWidget {
  final IconData icon;
  final String title;
  final Widget? trailing;
  final VoidCallback onTap;

  const _ProfileMenuItem({
    required this.icon,
    required this.title,
    this.trailing,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon, color: AppColors.textSecondary),
      title: Text(title, style: AppTypography.bodyLarge),
      trailing: trailing ?? const Icon(Icons.chevron_right),
      contentPadding: EdgeInsets.zero,
      onTap: onTap,
    );
  }
}

class _LoginPrompt extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Profile'),
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.person_outline,
                size: 80,
                color: AppColors.textHint,
              ),
              const SizedBox(height: 24),
              Text(
                'Sign in to your account',
                style: AppTypography.titleLarge,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 8),
              Text(
                'Manage your profile, view order history, and more.',
                style: AppTypography.bodyMedium.copyWith(
                  color: AppColors.textSecondary,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 32),
              ElevatedButton(
                onPressed: () => context.push('/login?redirect=/profile'),
                child: const Text('Sign In'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
