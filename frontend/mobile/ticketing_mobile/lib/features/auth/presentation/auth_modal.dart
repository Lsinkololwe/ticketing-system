import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/auth/auth_service.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';

/// Auth modal for login/signup
class AuthModal extends ConsumerStatefulWidget {
  final String? redirectPath;

  const AuthModal({super.key, this.redirectPath});

  @override
  ConsumerState<AuthModal> createState() => _AuthModalState();
}

class _AuthModalState extends ConsumerState<AuthModal> {
  bool _isLoading = false;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Drag handle
              Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: AppColors.border,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 24),

              // Header
              Icon(
                Icons.confirmation_number,
                size: 48,
                color: AppColors.primary,
              ),
              const SizedBox(height: 16),
              Text(
                'Sign in to continue',
                style: AppTypography.headlineMedium,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 8),
              Text(
                'Sign in to purchase tickets and manage your events.',
                style: AppTypography.bodyMedium.copyWith(
                  color: AppColors.textSecondary,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 32),

              // Login buttons
              _AuthButton(
                icon: Icons.phone,
                label: 'Continue with Phone',
                onPressed: _isLoading ? null : _handlePhoneLogin,
                isPrimary: true,
              ),
              const SizedBox(height: 12),
              _AuthButton(
                icon: Icons.email,
                label: 'Continue with Email',
                onPressed: _isLoading ? null : _handleEmailLogin,
              ),

              const SizedBox(height: 24),

              // Divider
              Row(
                children: [
                  const Expanded(child: Divider()),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Text(
                      'or',
                      style: AppTypography.bodySmall.copyWith(
                        color: AppColors.textHint,
                      ),
                    ),
                  ),
                  const Expanded(child: Divider()),
                ],
              ),

              const SizedBox(height: 24),

              // Social logins
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  _SocialButton(
                    icon: Icons.g_mobiledata,
                    label: 'Google',
                    onPressed: _isLoading ? null : _handleGoogleLogin,
                  ),
                  const SizedBox(width: 16),
                  _SocialButton(
                    icon: Icons.facebook,
                    label: 'Facebook',
                    onPressed: _isLoading ? null : _handleFacebookLogin,
                  ),
                ],
              ),

              const SizedBox(height: 24),

              // Terms
              Text(
                'By continuing, you agree to our Terms of Service\nand Privacy Policy.',
                style: AppTypography.bodySmall.copyWith(
                  color: AppColors.textHint,
                ),
                textAlign: TextAlign.center,
              ),

              // Loading indicator
              if (_isLoading) ...[
                const SizedBox(height: 24),
                const CircularProgressIndicator(),
              ],

              const SizedBox(height: 16),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _handlePhoneLogin() async {
    setState(() => _isLoading = true);

    try {
      final success = await ref.read(authServiceProvider.notifier).login();

      if (success && mounted) {
        // Navigate to redirect path or close modal
        if (widget.redirectPath != null) {
          context.go(widget.redirectPath!);
        } else {
          context.pop();
        }
      }
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  Future<void> _handleEmailLogin() async {
    // Same as phone login - Keycloak handles the method selection
    await _handlePhoneLogin();
  }

  Future<void> _handleGoogleLogin() async {
    // TODO: Implement Google social login via Keycloak
    await _handlePhoneLogin();
  }

  Future<void> _handleFacebookLogin() async {
    // TODO: Implement Facebook social login via Keycloak
    await _handlePhoneLogin();
  }
}

class _AuthButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback? onPressed;
  final bool isPrimary;

  const _AuthButton({
    required this.icon,
    required this.label,
    required this.onPressed,
    this.isPrimary = false,
  });

  @override
  Widget build(BuildContext context) {
    if (isPrimary) {
      return SizedBox(
        width: double.infinity,
        child: ElevatedButton.icon(
          onPressed: onPressed,
          icon: Icon(icon),
          label: Text(label),
          style: ElevatedButton.styleFrom(
            padding: const EdgeInsets.symmetric(vertical: 16),
          ),
        ),
      );
    }

    return SizedBox(
      width: double.infinity,
      child: OutlinedButton.icon(
        onPressed: onPressed,
        icon: Icon(icon),
        label: Text(label),
        style: OutlinedButton.styleFrom(
          padding: const EdgeInsets.symmetric(vertical: 16),
        ),
      ),
    );
  }
}

class _SocialButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback? onPressed;

  const _SocialButton({
    required this.icon,
    required this.label,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return OutlinedButton(
      onPressed: onPressed,
      style: OutlinedButton.styleFrom(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 20),
          const SizedBox(width: 8),
          Text(label),
        ],
      ),
    );
  }
}
