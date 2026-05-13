# Real-Time Quiz Platform

A production-grade real-time quiz platform (similar to Kahoot) supporting 1000+ concurrent participants per session with real-time leaderboards, timer synchronization, and an engaging animated UI.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React + Next.js 14 (App Router) |
| Backend | Java 21 + Spring Boot 3.2 |
| Database | PostgreSQL 16 |
| Cache/Pub-Sub | Redis 7 |
| Real-time | WebSockets |
| API Gateway | Spring Cloud Gateway |
| Containerization | Podman / Docker |
| Orchestration | Kubernetes |

## Prerequisites

- **Podman** 5.x+ (installed at `/opt/podman/bin/podman`)
- **docker-compose** (works with Podman socket)
- **Java 21** (for local backend development)
- **Node.js 20+** (for local frontend development)
- **Maven 3.9+** (for backend builds)

## Quick Start (Podman)

### Option 1: Infrastructure only (recommended for development)

Start PostgreSQL and Redis, then run services from your IDE:

```bash
./scripts/start-infra.sh
```

Then:
- Run backend services from IntelliJ/VS Code with `dev` profile
- Run frontend: `cd frontend && npm install && npm run dev`

### Option 2: Full stack in containers

Build and run everything:

```bash
./scripts/start-all.sh
```

### Option 3: Manual commands

```bash
# Ensure podman machine is running (macOS)
podman machine start

# Start infrastructure
docker-compose -f podman-compose.yml up -d postgresql redis

# Start all services
docker-compose -f podman-compose.yml up -d --build

# With monitoring (Prometheus + Grafana)
docker-compose -f podman-compose.yml --profile monitoring up -d

# View logs
docker-compose -f podman-compose.yml logs -f session-service

# Stop everything
docker-compose -f podman-compose.yml down

# Stop and remove data
docker-compose -f podman-compose.yml down -v
```

## Service Ports

| Service | Port | URL |
|---------|------|-----|
| Frontend | 3000 | http://localhost:3000 |
| API Gateway | 8080 | http://localhost:8080 |
| Auth Service | 8081 | http://localhost:8081 |
| Quiz Service | 8082 | http://localhost:8082 |
| Session Service | 8083 | http://localhost:8083 |
| WebSocket Service | 8084 | ws://localhost:8084 |
| Analytics Service | 8085 | http://localhost:8085 |
| PostgreSQL | 5432 | - |
| Redis | 6379 | - |
| Prometheus | 9090 | http://localhost:9090 (monitoring profile) |
| Grafana | 3001 | http://localhost:3001 (monitoring profile) |

## Project Structure

```
quiz_app/
├── backend/
│   ├── pom.xml                    # Parent POM (multi-module)
│   ├── common/                    # Shared library (DTOs, events, exceptions)
│   ├── api-gateway/               # Spring Cloud Gateway
│   ├── auth-service/              # Authentication & authorization
│   ├── quiz-service/              # Quiz CRUD
│   ├── session-service/           # Live session management, scoring, leaderboard
│   ├── websocket-service/         # WebSocket connections, real-time messaging
│   └── analytics-service/         # Reports, metrics, data retention
├── frontend/
│   ├── src/
│   │   ├── app/                   # Next.js App Router pages
│   │   ├── components/            # React components
│   │   ├── hooks/                 # Custom hooks (WebSocket, timer, audio)
│   │   ├── lib/                   # Utilities (API client, WS protocol)
│   │   ├── stores/                # Zustand state stores
│   │   └── types/                 # TypeScript types
│   └── public/audio/              # Sound effects and music
├── infrastructure/
│   ├── podman/                    # Podman-specific configs
│   ├── k8s/                       # Kubernetes manifests
│   └── monitoring/                # Prometheus, Grafana configs
├── scripts/
│   ├── start-infra.sh             # Start PostgreSQL + Redis only
│   ├── start-all.sh               # Start full stack
│   └── stop-all.sh                # Stop everything
├── podman-compose.yml             # Podman/Docker compose file
└── README.md
```

## Development Workflow

1. **Start infrastructure:** `./scripts/start-infra.sh`
2. **Backend:** Open `backend/` in IntelliJ, run services with `dev` profile
3. **Frontend:** `cd frontend && npm install && npm run dev`
4. **Test:** Access http://localhost:3000

## Database Access

```bash
# Connect to PostgreSQL
podman exec -it quiz-postgresql psql -U quiz -d quizplatform

# Connect to Redis
podman exec -it quiz-redis redis-cli
```

## Podman Notes

- This project uses `docker.io/library/` prefixed images for Podman compatibility
- The `podman-compose.yml` works with both `docker-compose` (via Podman socket) and `podman-compose`
- On macOS, ensure `podman machine start` has been run before using compose
- All images use Alpine variants for smaller footprint
- Services run as non-root users inside containers

## Documentation

Detailed technical documentation is available in the [`docs/`](docs/) folder:

- [Architecture](docs/ARCHITECTURE.md) — High-level architecture, service decomposition, WebSocket scaling strategy, Kubernetes deployment, correctness properties
- [API Reference](docs/API.md) — All service endpoints, WebSocket events, authentication, rate limiting
- [Database Schema](docs/DATABASE.md) — PostgreSQL schema, Redis data structures, Pub/Sub channels
