/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  darkMode: ['class', '[data-theme="warehouse"]'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      colors: {
        background: 'var(--color-surface-raised)',
        muted: {
          DEFAULT: 'var(--color-surface-overlay)',
          foreground: 'var(--color-text-muted)',
        },
        surface: {
          DEFAULT: 'var(--color-surface)',
          raised: 'var(--color-surface-raised)',
          overlay: 'var(--color-surface-overlay)',
        },
        border: {
          DEFAULT: 'var(--color-border)',
          strong: 'var(--color-border-strong)',
        },
        text: {
          DEFAULT: 'var(--color-text)',
          muted: 'var(--color-text-muted)',
          inverse: 'var(--color-text-inverse)',
        },
        accent: {
          DEFAULT: 'var(--color-accent)',
          hover: 'var(--color-accent-hover)',
          muted: 'var(--color-accent-muted)',
        },
        success: 'var(--color-success)',
        warning: 'var(--color-warning)',
        danger: 'var(--color-danger)',
      },
      borderRadius: {
        sm: 'var(--radius-sm)',
        DEFAULT: 'var(--radius-md)',
        md: 'var(--radius-md)',
        lg: 'var(--radius-lg)',
        xl: 'var(--radius-xl)',
      },
      spacing: {
        'tap': '3.5rem',
      },
      boxShadow: {
        card: 'var(--shadow-card)',
        elevated: 'var(--shadow-elevated)',
      },
      animation: {
        'flash-success': 'flash-success 150ms ease-out',
        'flash-error': 'flash-error 200ms ease-out',
        'flash-pending': 'flash-pending 180ms ease-out',
        'scan-success-enter': 'scan-success-enter 220ms cubic-bezier(0.23, 1, 0.32, 1) both',
        'scan-error-shake': 'scan-error-shake 280ms cubic-bezier(0.22, 1, 0.36, 1) both',
      },
      keyframes: {
        'flash-success': {
          '0%': { backgroundColor: 'rgba(34, 197, 94, 0.35)' },
          '100%': { backgroundColor: 'transparent' },
        },
        'flash-error': {
          '0%': { backgroundColor: 'rgba(239, 68, 68, 0.4)' },
          '100%': { backgroundColor: 'transparent' },
        },
        'flash-pending': {
          '0%': { backgroundColor: 'rgba(234, 179, 8, 0.4)' },
          '100%': { backgroundColor: 'transparent' },
        },
        'scan-success-enter': {
          '0%': { opacity: '0', transform: 'scale(0.98)' },
          '100%': { opacity: '1', transform: 'scale(1)' },
        },
        'scan-error-shake': {
          '0%, 100%': { transform: 'translateX(0)' },
          '20%': { transform: 'translateX(-6px)' },
          '40%': { transform: 'translateX(6px)' },
          '60%': { transform: 'translateX(-4px)' },
          '80%': { transform: 'translateX(4px)' },
        },
      },
    },
  },
  plugins: [],
};
