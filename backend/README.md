
# Asaltamontes Backend - REST API & Real-Time Messaging

Backend REST API for the **Asaltamontes** platform. Built with **Java 21**, **Spring Boot 3.5**, and **PostgreSQL**, this API provides the foundation for user authentication, social networking, and high-performance real-time messaging.

## Table of Contents

- [About](#about)
- [Core Features](#core-features)
- [Architecture & Messaging Flow](#architecture--messaging-flow)
  - [1. Security Strategy](#1-security-strategy)
  - [2. Messaging Engine](#2-messaging-engine)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Installation & Running](#installation--running)
  - [1. Environment Configuration](#1-environment-configuration)
  - [2. Running with Docker (Recommended)](#2-running-with-docker-recommended)
  - [3. Local Development](#3-local-development)
  - [4. Tests](#4-tests)
- [API & WebSocket](#api--websocket)
- [Roadmap](#roadmap)

## About

Asaltamontes provides a secure and scalable foundation for a female-focused trail running community. It manages complex user relationships and real-time communication with a focus on data integrity and stateless security.

## Core Features

- **Stateless Authentication:** Dual-token strategy (JWT Access Token + HttpOnly Refresh Cookie).
- **Social Graph:** Robust follow/unfollow system and user profile management.
- **Scalable Messaging:** 1-to-1 and group-ready chat architecture using a 3-table schema.
- **Real-Time Communication:** Bi-directional messaging via WebSockets (STOMP).
- **Validation & Security:** Centralized exception handling and strict input validation.

## Architecture & Messaging Flow

### 1. Security Strategy

- **Access Token:** Short-lived, passed via `Authorization: Bearer` header.
- **Refresh Token:** Long-lived, delivered via `HttpOnly`, `Secure`, `SameSite=Strict` cookie for silent renewal.

### 2. Messaging Engine

The system uses a **Conversation-Participant-Message** pattern. This ensures:

- Fetching the "Inbox" (list of chats) is an $O(1)$ operation relative to the total number of messages.
- Support for group chats is native to the database schema.
- Unread message counters are tracked per participant for maximum performance.

## Tech Stack

- **Core:** Java 21 (LTS), Spring Boot 3.5.x
- **Security:** Spring Security, JJWT (JSON Web Tokens)
- **Messaging:** Spring WebSocket, STOMP Protocol
- **Persistence:** Spring Data JPA, Hibernate 6, PostgreSQL 15
- **Tooling:** Lombok, Maven, Docker & Docker Compose
- **Environment:** Spring Dotenv for secure variable management


## Project Structure

```text
backend/
├── src/main/java/.../asaltamontes/
│   ├── auth/                  # JWT logic, Login/Register controllers
│   ├── user/                  # Social Graph and Profile management
│   ├── message/               # Messaging domain
│   │   ├── domain/            # Entities: Conversation, Participant, Message
│   │   ├── dto/               # Immutable Java Records (PrivateMessage, ChatSummary)
│   │   ├── repository/        # Optimized JPQL for chat threading
│   │   └── controller/        # WebSocket handlers (@MessageMapping)
│   ├── infrastructure/        # WebSocketConfig, Security Filters, Global CORS
│   └── common/                # Global Exception Handlers & Constants
├── Dockerfile                 # Optimized multi-stage build
└── .env.example               # Environment template
```


## Installation & Running

### 1. Environment Configuration

Create a `.env` file in the root directory:

```env
DATABASE_URL=jdbc:postgresql://db:5432/asaltamontes
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your_secure_password
JWT_SECRET=your_minimum_32_characters_secret_key
JWT_EXPIRATION_MS=900000
JWT_REFRESH_EXPIRATION_MS=604800000
CORS_ALLOWED_ORIGINS=http://localhost,http://127.0.0.1
```

`CORS_ALLOWED_ORIGINS` is a comma-separated list of origins allowed to call the API. Add any machine-specific origins (LAN IPs, browser extensions, etc.) to your local `.env` only — never hardcode them in source.

### 2. Running with Docker (Recommended)

```bash
docker compose up --build
```

### 3. Local Development

```bash
./mvnw spring-boot:run
```

### 4. Tests

```bash
./mvnw verify                          # unit tests (*Test) + integration tests (*IT)
./mvnw test -Dtest=<ClassName>Test     # a single unit test class
./mvnw verify -Dit.test=<ClassName>IT  # a single integration test class (plus the unit tests)
```

- Requires **Docker**: integration tests start a PostgreSQL 17 container with Testcontainers. No `.env` is needed; the `test` profile (`src/test/resources/application-test.properties`) provides fixed values.
- Integration tests extend `support/AbstractIntegrationTest` (shared container and Spring context) and create their own data with `TestDataFactory` (unique names, so tests never assume an empty database). `AuthTestHelper.bearer(user)` adds a valid access token to a MockMvc request.
- Scheduled maintenance jobs are disabled in the `test` profile (`SchedulingConfig`).
- CI runs `./mvnw verify` on every pull request.


## API & WebSocket

The backend exposes REST endpoints and WebSocket channels for authentication, user management, and real-time messaging.

- See the [OpenAPI contract](../docs/api-contract-v1.json) for the full specification of endpoints, parameters, and responses.

### Main Example Endpoints

- `POST /api/auth/login` — Login and token issuance
- `GET /api/users/{username}` — Public user profile
- `POST /api/users/follow/{username}` — Follow user
- `GET /api/messages/chats` — List active conversations

### WebSocket (STOMP)

- `/app/chat.private` — Send private message
- `/user/queue/messages` — Receive private messages
- `/topic/**` — Public/group announcements

## Roadmap

- [ ] **Push Notifications:** Firebase Cloud Messaging for offline alerts.
- [ ] **Media Support:** Image and GPX file sharing in chats.
- [ ] **Events Module:** "Meetups" module (trail running events).

---
*Last updated: May 2026*
