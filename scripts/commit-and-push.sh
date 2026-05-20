#!/bin/bash
set -e

cd /Users/arpit.jindal/workspace/opensource/quiz_app

echo "=== Staging all changes ==="
git add -A

echo "=== Creating commit ==="
git commit -m "feat: implement dynamic scoring & animated leaderboard

- Enhanced ScoreCalculator with configurable time factor (0.3/0.5/0.7)
- DynamicScoreEngine orchestrating round scoring with atomic Redis ops
- LeaderboardSnapshotService for per-round rank delta computation
- LeaderboardBroadcaster with personalized WebSocket views & Kafka events
- ScoreEventConsumer in analytics-service for score persistence
- WebSocket reconnection with queued event delivery (FIFO)
- Scoring mode configuration (Speed Matters/Balanced/Knowledge First)
- Performance timeout handling with Micrometer metrics
- AnimatedLeaderboard component with Framer Motion animations
- SpeedBonusIndicator with counting animation & reduced-motion support
- ScoringModeSelector in quiz edit UI
- Property-based tests (jqwik + fast-check) for all correctness properties
- Comprehensive unit and integration tests
- Fixed composite score ZINCRBY bug (score × SCORE_MULTIPLIER)
- Added GET /api/sessions/{pin} endpoint for session info"

echo "=== Pushing to main ==="
git pull --rebase origin main
git push origin main

echo "=== Done! ==="
git log --oneline -1
