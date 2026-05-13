# Performance Optimization Guide

## Target

- **Lighthouse Mobile Performance Score:** 90+
- **Simulated Connection:** 4G (1.6 Mbps download, 750ms RTT)
- **Viewport:** 375px width

## Key Optimizations Applied

### Code Splitting

- Next.js App Router automatic code splitting per route
- Dynamic imports for heavy components (quiz editor, analytics charts)
- Lazy loading of Framer Motion animations on non-critical paths

### Image Optimization

- Next.js `<Image>` component with automatic WebP/AVIF conversion
- Responsive `sizes` attribute for appropriate image delivery
- Cover images use `priority` prop only on above-the-fold content

### Font Loading

- System font stack as primary (no external font requests)
- If custom fonts are added: `font-display: swap` with preload links
- Subset fonts to Latin characters only

### Bundle Size

- Tree-shaking enabled via ES modules
- Tailwind CSS purging unused styles in production
- Minimal runtime dependencies (zustand, clsx, tailwind-merge)

### Cumulative Layout Shift (CLS)

- Skeleton loaders with explicit dimensions matching final content
- `aspect-ratio` on media containers
- Reserved space for dynamic content (leaderboard, question area)
- Fixed header height (64px) prevents layout shift

### Largest Contentful Paint (LCP)

- Critical CSS inlined by Next.js
- Preload key resources (fonts, hero images)
- Server-side rendering for initial page content

### First Input Delay (FID) / Interaction to Next Paint (INP)

- Minimal JavaScript on initial load
- Event handlers are lightweight (state updates via zustand)
- WebSocket operations are non-blocking

## Known Areas for Improvement

1. **Audio preloading** — Audio files loaded during lobby phase; could benefit from service worker caching for repeat visits
2. **WebSocket reconnection** — Reconnection logic adds JS weight; consider code-splitting the WebSocket module
3. **Analytics charts** — If added, should be dynamically imported to avoid impacting initial load
4. **Third-party scripts** — OAuth providers (Google, GitHub) add external script weight; load only on auth pages
5. **Image CDN** — Cover images should be served via CDN with proper cache headers for production

## How to Run Lighthouse Audit

```bash
# Build production bundle
npm run build

# Start production server
npm start

# Run Lighthouse via Chrome DevTools:
# 1. Open Chrome DevTools (F12)
# 2. Navigate to Lighthouse tab
# 3. Select "Mobile" device
# 4. Check "Performance" category
# 5. Click "Analyze page load"

# Or via CLI:
npx lighthouse http://localhost:3000 --preset=perf --emulated-form-factor=mobile
```
