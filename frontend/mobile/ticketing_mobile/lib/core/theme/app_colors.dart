import 'package:flutter/material.dart';

/// App color palette
/// Based on design system: Purple primary with Orange accent
class AppColors {
  AppColors._();

  // ==========================================================================
  // PRIMARY COLORS (Purple)
  // ==========================================================================

  /// Primary dark - Deep purple for emphasis
  static const Color primaryDark = Color(0xFF4C1D95);

  /// Primary main - Main brand color
  static const Color primary = Color(0xFF7C3AED);

  /// Primary light - Light purple for containers
  static const Color primaryLight = Color(0xFFA78BFA);

  /// Primary very light - For subtle backgrounds
  static const Color primaryVeryLight = Color(0xFFEDE9FE);

  // ==========================================================================
  // ACCENT / CTA (Orange)
  // ==========================================================================

  /// Accent color - For CTAs, buy buttons, urgent actions
  static const Color accent = Color(0xFFF97316);

  /// Accent light
  static const Color accentLight = Color(0xFFFB923C);

  /// Accent dark
  static const Color accentDark = Color(0xFFEA580C);

  // ==========================================================================
  // SECONDARY COLORS
  // ==========================================================================

  /// Secondary color
  static const Color secondary = Color(0xFF6366F1);

  /// Secondary light
  static const Color secondaryLight = Color(0xFF818CF8);

  /// Secondary dark
  static const Color secondaryDark = Color(0xFF4F46E5);

  // ==========================================================================
  // SEMANTIC COLORS
  // ==========================================================================

  /// Success green
  static const Color success = Color(0xFF10B981);

  /// Warning amber
  static const Color warning = Color(0xFFF59E0B);

  /// Error red
  static const Color error = Color(0xFFEF4444);

  /// Info blue
  static const Color info = Color(0xFF3B82F6);

  // ==========================================================================
  // LIGHT MODE COLORS
  // ==========================================================================

  /// Background color
  static const Color background = Color(0xFFFAF5FF);

  /// Surface color (cards, containers)
  static const Color surface = Color(0xFFFFFFFF);

  /// Surface variant (input fields, chips)
  static const Color surfaceVariant = Color(0xFFF3E8FF);

  /// Border color
  static const Color border = Color(0xFFE9D5FF);

  /// Divider color
  static const Color divider = Color(0xFFE9D5FF);

  // Text colors (light mode)
  /// Primary text
  static const Color textPrimary = Color(0xFF0F172A);

  /// Secondary text
  static const Color textSecondary = Color(0xFF475569);

  /// Hint text
  static const Color textHint = Color(0xFF94A3B8);

  /// Disabled text
  static const Color textDisabled = Color(0xFFCBD5E1);

  // ==========================================================================
  // DARK MODE COLORS
  // ==========================================================================

  /// Dark background
  static const Color darkBackground = Color(0xFF1E1B4B);

  /// Dark surface
  static const Color darkSurface = Color(0xFF2E2A5F);

  /// Dark surface variant
  static const Color darkSurfaceVariant = Color(0xFF3D3874);

  /// Dark border
  static const Color darkBorder = Color(0xFF4C4785);

  // Text colors (dark mode)
  /// Dark primary text
  static const Color darkTextPrimary = Color(0xFFF8FAFC);

  /// Dark secondary text
  static const Color darkTextSecondary = Color(0xFFCBD5E1);

  /// Dark hint text
  static const Color darkTextHint = Color(0xFF94A3B8);

  // ==========================================================================
  // CATEGORY COLORS
  // ==========================================================================

  /// Music events
  static const Color categoryMusic = Color(0xFFEC4899);

  /// Drama/Theatre
  static const Color categoryDrama = Color(0xFF8B5CF6);

  /// Sports events
  static const Color categorySports = Color(0xFF10B981);

  /// Comedy shows
  static const Color categoryComedy = Color(0xFFF59E0B);

  /// Conferences
  static const Color categoryConference = Color(0xFF3B82F6);

  /// Festivals
  static const Color categoryFestival = Color(0xFFF97316);

  // ==========================================================================
  // TICKET STATUS COLORS
  // ==========================================================================

  /// Confirmed/Purchased ticket
  static const Color ticketConfirmed = Color(0xFF10B981);

  /// Pending ticket
  static const Color ticketPending = Color(0xFFF59E0B);

  /// Used/Validated ticket
  static const Color ticketUsed = Color(0xFF6366F1);

  /// Expired ticket
  static const Color ticketExpired = Color(0xFF94A3B8);

  /// Cancelled ticket
  static const Color ticketCancelled = Color(0xFFEF4444);

  // ==========================================================================
  // GRADIENT DEFINITIONS
  // ==========================================================================

  /// Primary gradient
  static const LinearGradient primaryGradient = LinearGradient(
    colors: [primaryDark, primary],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  /// Accent gradient
  static const LinearGradient accentGradient = LinearGradient(
    colors: [accent, accentLight],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  /// Hero gradient (for featured cards)
  static const LinearGradient heroGradient = LinearGradient(
    colors: [
      Color(0xFF4C1D95),
      Color(0xFF7C3AED),
      Color(0xFFA78BFA),
    ],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  /// Overlay gradient (for images with text)
  static const LinearGradient imageOverlayGradient = LinearGradient(
    colors: [
      Colors.transparent,
      Color(0x80000000),
    ],
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
  );
}
