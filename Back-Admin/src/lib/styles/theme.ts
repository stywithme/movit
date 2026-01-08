/**
 * Global Theme & Styles
 * =====================
 * 
 * Single source of truth for colors, spacing, typography, and component styles.
 * Use these constants throughout the application for consistency.
 */

// ============================================
// COLORS
// ============================================

export const colors = {
  // Primary
  primary: {
    50: '#eff6ff',
    100: '#dbeafe',
    200: '#bfdbfe',
    300: '#93c5fd',
    400: '#60a5fa',
    500: '#3b82f6', // Main blue
    600: '#2563eb',
    700: '#1d4ed8',
    800: '#1e40af',
    900: '#1e3a8a',
  },
  
  // Success
  success: {
    50: '#f0fdf4',
    100: '#dcfce7',
    200: '#bbf7d0',
    300: '#86efac',
    400: '#4ade80',
    500: '#22c55e', // Main green
    600: '#16a34a',
    700: '#15803d',
    800: '#166534',
    900: '#14532d',
  },
  
  // Warning
  warning: {
    50: '#fffbeb',
    100: '#fef3c7',
    200: '#fde68a',
    300: '#fcd34d',
    400: '#fbbf24',
    500: '#f59e0b', // Main amber
    600: '#d97706',
    700: '#b45309',
    800: '#92400e',
    900: '#78350f',
  },
  
  // Error
  error: {
    50: '#fef2f2',
    100: '#fee2e2',
    200: '#fecaca',
    300: '#fca5a5',
    400: '#f87171',
    500: '#ef4444', // Main red
    600: '#dc2626',
    700: '#b91c1c',
    800: '#991b1b',
    900: '#7f1d1d',
  },
  
  // Gray
  gray: {
    50: '#f9fafb',
    100: '#f3f4f6',
    200: '#e5e7eb',
    300: '#d1d5db',
    400: '#9ca3af',
    500: '#6b7280',
    600: '#4b5563',
    700: '#374151',
    800: '#1f2937',
    900: '#111827',
  },
  
  // Difficulty Colors
  difficulty: {
    beginner: {
      bg: '#f0fdf4',
      border: '#86efac',
      text: '#16a34a',
      emoji: '🟢',
    },
    normal: {
      bg: '#eff6ff',
      border: '#93c5fd',
      text: '#2563eb',
      emoji: '🔵',
    },
    advanced: {
      bg: '#fef2f2',
      border: '#fca5a5',
      text: '#dc2626',
      emoji: '🔴',
    },
  },
  
  // Severity Colors
  severity: {
    error: {
      bg: '#fef2f2',
      text: '#dc2626',
      border: '#fecaca',
    },
    warning: {
      bg: '#fffbeb',
      text: '#d97706',
      border: '#fde68a',
    },
    tip: {
      bg: '#eff6ff',
      text: '#2563eb',
      border: '#bfdbfe',
    },
  },
} as const;

// ============================================
// SPACING
// ============================================

export const spacing = {
  xs: '0.25rem',   // 4px
  sm: '0.5rem',    // 8px
  md: '1rem',      // 16px
  lg: '1.5rem',    // 24px
  xl: '2rem',      // 32px
  '2xl': '3rem',   // 48px
  '3xl': '4rem',   // 64px
} as const;

// ============================================
// TYPOGRAPHY
// ============================================

export const typography = {
  fontFamily: {
    sans: ['Inter', 'system-ui', 'sans-serif'],
    mono: ['Fira Code', 'monospace'],
  },
  fontSize: {
    xs: '0.75rem',    // 12px
    sm: '0.875rem',   // 14px
    base: '1rem',      // 16px
    lg: '1.125rem',   // 18px
    xl: '1.25rem',    // 20px
    '2xl': '1.5rem',  // 24px
    '3xl': '1.875rem', // 30px
    '4xl': '2.25rem', // 36px
  },
  fontWeight: {
    normal: '400',
    medium: '500',
    semibold: '600',
    bold: '700',
  },
  lineHeight: {
    tight: '1.25',
    normal: '1.5',
    relaxed: '1.75',
  },
} as const;

// ============================================
// BORDER RADIUS
// ============================================

export const borderRadius = {
  sm: '0.25rem',   // 4px
  md: '0.5rem',    // 8px
  lg: '0.75rem',   // 12px
  xl: '1rem',      // 16px
  full: '9999px',
} as const;

// ============================================
// SHADOWS
// ============================================

export const shadows = {
  sm: '0 1px 2px 0 rgb(0 0 0 / 0.05)',
  md: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
  lg: '0 10px 15px -3px rgb(0 0 0 / 0.1)',
  xl: '0 20px 25px -5px rgb(0 0 0 / 0.1)',
} as const;

// ============================================
// TRANSITIONS
// ============================================

export const transitions = {
  fast: '150ms',
  normal: '200ms',
  slow: '300ms',
  easing: {
    default: 'cubic-bezier(0.4, 0, 0.2, 1)',
    in: 'cubic-bezier(0.4, 0, 1, 1)',
    out: 'cubic-bezier(0, 0, 0.2, 1)',
  },
} as const;

// ============================================
// COMPONENT STYLES
// ============================================

export const componentStyles = {
  // Input
  input: {
    base: 'w-full px-4 py-3 rounded-lg border-2 transition-all duration-200 focus:outline-none focus:ring-2',
    default: 'border-gray-200 focus:border-blue-500 focus:ring-blue-200',
    error: 'border-red-300 bg-red-50 focus:border-red-500 focus:ring-red-200',
    disabled: 'bg-gray-100 text-gray-400 cursor-not-allowed',
  },
  
  // Button
  button: {
    base: 'px-6 py-3 rounded-xl font-medium transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-offset-2',
    primary: 'bg-blue-600 text-white hover:bg-blue-700 focus:ring-blue-500',
    secondary: 'bg-gray-200 text-gray-700 hover:bg-gray-300 focus:ring-gray-500',
    success: 'bg-green-600 text-white hover:bg-green-700 focus:ring-green-500',
    danger: 'bg-red-600 text-white hover:bg-red-700 focus:ring-red-500',
    outline: 'border-2 border-gray-300 text-gray-700 hover:bg-gray-50 focus:ring-gray-500',
    ghost: 'text-gray-700 hover:bg-gray-100 focus:ring-gray-500',
    disabled: 'opacity-50 cursor-not-allowed',
  },
  
  // Card
  card: {
    base: 'bg-white border rounded-xl p-6 shadow-sm',
    hover: 'hover:shadow-md transition-shadow duration-200',
  },
  
  // Badge
  badge: {
    base: 'px-2 py-1 text-xs font-medium rounded-full',
    primary: 'bg-blue-100 text-blue-700',
    success: 'bg-green-100 text-green-700',
    warning: 'bg-yellow-100 text-yellow-700',
    error: 'bg-red-100 text-red-700',
    gray: 'bg-gray-100 text-gray-700',
  },
} as const;
