import type { Config } from 'tailwindcss';

const config: Config = {
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  darkMode: 'class',
  theme: {
    screens: {
      xs: '480px',
      sm: '640px',
      md: '768px',
      lg: '1024px',
      xl: '1280px',
      '2xl': '1440px',
    },
    extend: {
      spacing: {
        'base': '4px',
      },
      borderRadius: {
        'card': '1rem',
        'pill': '9999px',
        'input': '0.5rem',
      },
      transitionDuration: {
        '75': '75ms',
        '200': '200ms',
        '250': '250ms',
        '300': '300ms',
        '1000': '1000ms',
      },
      colors: {
        primary: {
          50: '#f0f9ff',
          100: '#e0f2fe',
          200: '#bae6fd',
          300: '#7dd3fc',
          400: '#38bdf8',
          500: '#0ea5e9',
          600: '#0284c7',
          700: '#0369a1',
          800: '#075985',
          900: '#0c4a6e',
        },
        quiz: {
          red: '#e21b3c',
          blue: '#1368ce',
          yellow: '#d89e00',
          green: '#26890c',
        },
      },
      animation: {
        'slide-in': 'slideIn 0.3s ease-out',
        'fade-in': 'fadeIn 0.2s ease-out',
        'pulse-score': 'pulseScore 0.5s ease-in-out',
        'rank-swap': 'rankSwap 0.6s ease-in-out',
        'particle-burst': 'particleBurst 1000ms ease-out forwards',
        'pulse-multiplier': 'pulseMultiplier 300ms ease-in-out',
      },
      keyframes: {
        slideIn: {
          '0%': { transform: 'translateY(20px)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        pulseScore: {
          '0%, 100%': { transform: 'scale(1)' },
          '50%': { transform: 'scale(1.2)' },
        },
        rankSwap: {
          '0%': { transform: 'translateY(var(--rank-offset))' },
          '100%': { transform: 'translateY(0)' },
        },
        particleBurst: {
          '0%': { transform: 'scale(0)', opacity: '1' },
          '50%': { transform: 'scale(1.5)', opacity: '0.7' },
          '100%': { transform: 'scale(2)', opacity: '0' },
        },
        pulseMultiplier: {
          '0%, 100%': { transform: 'scale(1)' },
          '50%': { transform: 'scale(1.3)' },
        },
      },
    },
  },
  plugins: [],
};

export default config;
