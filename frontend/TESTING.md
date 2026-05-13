# Real Device Testing Guide

## Target Devices

### Mobile

| Device | OS | Browser | Priority |
|--------|-----|---------|----------|
| iPhone 13/14 | iOS 16+ | Safari | High |
| iPhone SE (3rd gen) | iOS 16+ | Safari | High (small screen) |
| Samsung Galaxy S22 | Android 13+ | Chrome | High |
| Google Pixel 7 | Android 13+ | Chrome | Medium |
| iPad Air | iPadOS 16+ | Safari | Medium (tablet) |

### Desktop

| Device | OS | Browser | Priority |
|--------|-----|---------|----------|
| MacBook | macOS | Chrome 120+ | High |
| MacBook | macOS | Safari 17+ | Medium |
| Windows PC | Windows 11 | Chrome 120+ | High |
| Windows PC | Windows 11 | Firefox 120+ | Medium |
| Windows PC | Windows 11 | Edge 120+ | Low |

## Test Scenarios

### Critical Paths

1. **Join flow** — Enter PIN, set nickname, enter lobby
2. **Answer submission** — Tap answer option within timer
3. **Leaderboard view** — Scroll and view rank changes
4. **Quiz creation** — Add questions, reorder, save (host)
5. **Session control** — Start, pause, skip, end (host)

### Responsive Breakpoints

- 320px: Minimum supported width (iPhone SE)
- 480px (xs): Small phones in landscape
- 768px (md): Tablets in portrait
- 1024px (lg): Tablets in landscape / small laptops
- 1440px (2xl): Desktop monitors

### Touch Interactions

- [ ] All buttons meet 44x44px minimum tap target
- [ ] 8px minimum spacing between adjacent tap targets
- [ ] Answer options are easily tappable on small screens
- [ ] No accidental taps on adjacent elements
- [ ] Swipe gestures don't conflict with browser navigation

### Dark Mode

- [ ] All text is readable in dark mode
- [ ] No white flashes on page transitions
- [ ] System preference detection works
- [ ] Manual toggle persists across pages

### Animations

- [ ] Animations run at 60fps on target devices
- [ ] `prefers-reduced-motion` disables animations
- [ ] No jank during leaderboard rank swaps
- [ ] Question transitions feel smooth

### WebSocket / Real-time

- [ ] Reconnection works after network drop
- [ ] Timer stays synchronized across devices
- [ ] Leaderboard updates arrive within 300ms
- [ ] Works on unstable mobile connections (3G/4G)

## Known Issues and Workarounds

| Issue | Affected | Workaround |
|-------|----------|------------|
| iOS Safari audio autoplay blocked | iOS Safari | Show "Enable Audio" prompt on first interaction |
| 100vh includes address bar on mobile | iOS Safari, Android Chrome | Use `dvh` unit or JS-based viewport height |
| WebSocket disconnect on app background | iOS Safari | Reconnect on `visibilitychange` event |
| Keyboard pushes viewport on input focus | Android Chrome | Use `visualViewport` API for layout adjustments |
| Safe area insets on notched devices | iPhone X+ | Apply `env(safe-area-inset-*)` padding |

## Testing Tools

- **BrowserStack** — Cross-browser/device testing
- **Chrome DevTools** — Device emulation, network throttling
- **Safari Web Inspector** — iOS debugging via USB
- **Android Studio** — Android emulator testing
- **Lighthouse CI** — Automated performance regression testing

## How to Test Locally on Mobile

1. Start dev server: `npm run dev`
2. Find local IP: `ifconfig | grep inet`
3. Ensure mobile device is on same network
4. Navigate to `http://<local-ip>:3000` on mobile browser
5. For HTTPS (required for some APIs): use `ngrok` or `mkcert`
