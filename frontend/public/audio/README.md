# Audio Assets

This directory contains audio files used by the quiz platform for an immersive game-like experience.

## Required Audio Files

| File | Purpose | Recommended Duration |
|------|---------|---------------------|
| `lobby-music.mp3` | Background music during lobby/waiting phase | 30-60s (loops) |
| `countdown-tick.mp3` | Tick sound during last 5 seconds of timer | < 1s |
| `correct-answer.mp3` | Sound effect when answer is revealed as correct | 1-2s |
| `incorrect-answer.mp3` | Sound effect when answer is revealed as incorrect | 1-2s |
| `leaderboard-music.mp3` | Background music during leaderboard display | 15-30s (loops) |

## Placeholder Files

The `.gitkeep` files in this directory serve as placeholders. Replace them with actual MP3 audio assets before deploying to production.

## Audio Guidelines

- Keep file sizes small (< 500KB each) for fast preloading
- Use MP3 format for broad browser compatibility
- Lobby and leaderboard music should loop seamlessly
- Sound effects should be short and punchy
- Normalize audio levels across all files for consistent volume

## Licensing

Ensure all audio assets are properly licensed for your use case (royalty-free, Creative Commons, or custom licensed).
