# Architecture: Real-Time Quiz Platform

## Overview

A production-grade real-time quiz platform (similar to Kahoot) supporting 1000+ concurrent participants per session with real-time leaderboards, timer synchronization, and an engaging animated UI. Built with a microservices architecture deployed on Kubernetes.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                      │
│  │  Host Web    │  │ Participant  │  │   Admin      │                      │
│  │  (Next.js)   │  │  (Next.js)   │  │  (Next.js)   │                      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘                      │
└─────────┼──────────────────┼──────────────────┼─────────────────────────────┘
          │ HTTPS/WSS        │ HTTPS/WSS        │ HTTPS
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         INGRESS (NGINX / Traefik)                            │
│                         TLS Termination + Load Balancing                     │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     API GATEWAY (Spring Cloud Gateway)                        │
│  ┌─────────┐ ┌──────────┐ ┌───────────┐ ┌──────────┐ ┌─────────────────┐  │
│  │  Rate   │ │   JWT    │ │  Routing  │ │ Logging  │ │ Correlation ID  │  │
│  │ Limiter │ │ Validator│ │  Engine   │ │  Filter  │ │   Injection     │  │
│  └─────────┘ └──────────┘ └───────────┘ └──────────┘ └─────────────────┘  │
└──────┬──────────┬──────────────┬──────────────┬──────────────┬──────────────┘
       │          │              │              │              │
       ▼          ▼              ▼              ▼              ▼
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐ ┌──────────────┐
│   Auth   │ │   Quiz   │ │ Session  │ │  WebSocket   │ │  Analytics   │
│ Service  │ │ Service  │ │ Service  │ │   Service    │ │   Service    │
└────┬─────┘ └────┬─────┘ └────┬─────┘ └──────┬───────┘ └──────┬───────┘
     │             │            │               │                │
     ▼             ▼            ▼               ▼                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          DATA LAYER                                           │
│  ┌───────────────────┐  ┌───────────────────────────────────────────────┐   │
│  │    PostgreSQL      │  │              Redis Cluster                     │   │
│  │  ┌─────┐ ┌─────┐  │  │  ┌────────┐ ┌────────┐ ┌──────────────────┐ │   │
│  │  │Write│ │Read │  │  │  │Pub/Sub │ │Sorted  │ │  Session State   │ │   │
│  │  │Node │ │Repli│  │  │  │Channels│ │  Sets  │ │  + Streams       │ │   │
│  │  └─────┘ └─────┘  │  │  └────────┘ └────────┘ └──────────────────┘ │   │
│  └───────────────────┘  └───────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Architecture Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| API Gateway | Spring Cloud Gateway | Native Spring ecosystem integration, reactive non-blocking I/O, built-in rate limiting and circuit breaker support |
| WebSocket Protocol | Raw WebSocket (not STOMP) | Lower overhead per message, simpler scaling with Redis Pub/Sub |
| Session State | Redis (not DB) | Sub-millisecond reads required for real-time operations; 4-hour TTL auto-cleanup |
| Leaderboard | Redis Sorted Sets | O(log N) insert/rank operations; native support for score-based ranking |
| Event Bus | Redis Streams | Already in the stack, consumer groups for reliable delivery |
| Database | PostgreSQL | ACID for quiz data, JSON support for flexible question schemas |
| Frontend | Next.js (App Router) | SSR for SEO on public pages, client components for real-time UI |

## Service Decomposition

### Auth Service
User registration, login, JWT issuance, OAuth integration (Google, GitHub), password management.

### Quiz Service
Quiz CRUD, question management, quiz validation. Supports MCQ, True/False, and Poll question types.

### Session Service
Session lifecycle management, PIN generation, participant tracking, scoring, leaderboard computation. Handles the real-time state machine (LOBBY → QUESTION_OPEN → QUESTION_CLOSED → REVEAL → ENDED).

### WebSocket Service
WebSocket connection management, message routing, heartbeat, clock sync, real-time broadcast. Designed to handle 10,000 connections per pod.

### Analytics Service
Session report generation, metrics aggregation, data export (CSV/PDF), data retention management.

## WebSocket Scaling Strategy

```
                    ┌─────────────────────────────┐
                    │     Kubernetes Ingress       │
                    │  (Consistent Hash on PIN)    │
                    └──────────┬──────────────────┘
                               │
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
     ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
     │  WS Pod 1    │ │  WS Pod 2    │ │  WS Pod N    │
     │  (≤10K conn) │ │  (≤10K conn) │ │  (≤10K conn) │
     └──────┬───────┘ └──────┬───────┘ └──────┬───────┘
            │                │                │
            └────────────────┼────────────────┘
                             ▼
                    ┌─────────────────┐
                    │  Redis Cluster   │
                    │  (Pub/Sub Hub)   │
                    └─────────────────┘
```

Key design decisions:
- **Consistent hashing on PIN** — All participants of the same session route to the same WebSocket pod
- **Redis Pub/Sub for cross-pod** — State changes published through Redis, received by the subscribed WS pod
- **Fallback for pod failure** — Clients reconnect to any available pod; state restored from Redis
- **Capacity**: 1 pod = 10K connections ≈ 10 sessions; 20 pods max = 200 concurrent sessions

## Session Lifecycle State Machine

```
                    ┌─────────┐
                    │ CREATED │
                    └────┬────┘
                         │ host starts session (PIN generated)
                         ▼
                    ┌─────────┐
              ┌─────│  LOBBY  │─────┐
              │     └────┬────┘     │
              │          │ host starts first question
              │          ▼          │
              │   ┌──────────────┐  │
              │   │QUESTION_OPEN │◄─┼──── (next question from REVEAL)
              │   └──────┬───────┘  │
              │          │ timer expires
              │          ▼          │
              │   ┌──────────────┐  │
              │   │QUESTION_CLOSED│  │
              │   └──────┬───────┘  │
              │          │ host reveals answer
              │          ▼          │
              │   ┌──────────────┐  │
              │   │   REVEAL     │──┼──── (last question → ENDED)
              │   └──────────────┘  │
              │                     │
              │   ┌──────────────┐  │
              ├──►│   PAUSED     │◄─┤  (any state → PAUSED)
              │   └──────┬───────┘  │
              │          │ resume   │
              │          ▼          │
              │   (previous state)  │
              │                     │
              ▼                     ▼
                    ┌─────────┐
                    │  ENDED  │
                    └─────────┘
```

## Scoring Algorithm

```java
public int calculateScore(int basePoints, long timeTakenMs, int timeLimitMs, int streak) {
    if (timeTakenMs < 0 || timeTakenMs > timeLimitMs) return 0;
    
    double timeRatio = (double) timeTakenMs / timeLimitMs;
    double rawScore = basePoints * (1.0 - timeRatio * 0.5);
    
    int multiplier = getStreakMultiplier(streak);
    return (int) Math.round(rawScore * multiplier);
}

// Streak multipliers: 1x (0-2), 2x (3-4), 3x (5+)
```

## Timer Synchronization (NTP-like)

The platform uses a 3-sample NTP-like clock sync protocol:
1. Client sends `sync_request` with local timestamp `t1`
2. Server records receive time `t2`, responds with `{t1, t2, t3}` where `t3` is send time
3. Client records receive time `t4`
4. Offset = `((t2 - t1) + (t3 - t4)) / 2`
5. Repeat 3 times, use median offset

This ensures all participants see consistent countdown timers regardless of network latency.

## Anti-Cheating Measures

| Protection | Implementation |
|-----------|---------------|
| Answer secrecy | Correct answers never sent to clients until reveal |
| Speed detection | Flag participants with ≥3 answers under 200ms |
| Duplicate connections | Reject if participant already connected |
| Answer deduplication | Redis HSETNX ensures one answer per question |
| Late submission | Server-side enforcement with 500ms tolerance |
| Rate limiting | 1 answer per question per participant |

## Kubernetes Deployment

| Service | Min Pods | Max Pods | CPU Limit | Memory Limit |
|---------|----------|----------|-----------|--------------|
| API Gateway | 2 | 5 | 1000m | 512Mi |
| Auth Service | 2 | 4 | 500m | 512Mi |
| Quiz Service | 2 | 4 | 500m | 512Mi |
| Session Service | 2 | 6 | 1000m | 1Gi |
| WebSocket Service | 2 | 20 | 2000m | 2Gi |
| Analytics Service | 2 | 4 | 500m | 512Mi |

WebSocket Service scales on `websocket_active_connections` metric (target: 5000 avg per pod).

## Correctness Properties

The system guarantees these formal properties:

1. **Score Monotonicity** — A participant's total score never decreases across questions
2. **Leaderboard Consistency** — Higher score always means better rank
3. **Answer Uniqueness** — At most one answer per participant per question
4. **Timer Fairness** — No answer accepted after server timer + 500ms tolerance
5. **State Machine Validity** — Only valid state transitions occur
6. **PIN Uniqueness** — No two active sessions share the same PIN
7. **Streak Correctness** — Streak equals consecutive correct answers ending at current question
8. **Score Determinism** — Same inputs always produce same score output
