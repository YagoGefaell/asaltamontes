# Asaltamontes Backend - REST API & Real-Time Messaging

Backend REST API for the **Asaltamontes** platform. Built with **Java 21**, **Spring Boot 3.5**, and **PostgreSQL**, this API serves as the foundational architecture for user authentication, social networking, and high-performance real-time messaging.

## Table of Contents

- [Overview](#overview)
- [Core Features](#core-features)
- [Architecture & Messaging Flow](#architecture--messaging-flow)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Installation & Running](#installation--running)
- [API & WebSocket Endpoints](#api--websocket-endpoints)
- [Roadmap](#roadmap)

---

## Overview

Asaltamontes provides a secure and scalable foundation for a female-focused trail running community. It handles complex user relationships and real-time communication with a focus on data integrity and stateless security.

---

## Core Features

- **Stateless Authentication:** Dual-token strategy (JWT Access Token + HttpOnly Refresh Cookie).
- **Social Graph:** Robust follow/unfollow system and user profile management.
- **Scalable Messaging:** 1-to-1 and group-ready chat architecture using a 3-table schema.
- **Real-Time Communication:** Bi-directional messaging via WebSockets (STOMP).
- **Validation & Security:** Centralized exception handling and strict input validation.

---

## Architecture & Messaging Flow

### 1. Security Strategy
- **Access Token:** Short-lived, passed via `Authorization: Bearer` header.
- **Refresh Token:** Long-lived, delivered via `HttpOnly`, `Secure`, `SameSite=Strict` cookie for silent renewal.

### 2. Messaging Engine
The system uses a **Conversation-Participant-Message** pattern. This ensures that:
- Fetching the "Inbox" (list of chats) is an $O(1)$ operation relative to the total number of messages.
- Support for group chats is native to the database schema.
- Unread message counters are tracked per participant for maximum performance.



---

## Tech Stack

- **Core:** Java 21 (LTS), Spring Boot 3.5.x
- **Security:** Spring Security, JJWT (JSON Web Tokens)
- **Messaging:** Spring WebSocket, STOMP Protocol
- **Persistence:** Spring Data JPA, Hibernate 6, PostgreSQL 15
- **Tooling:** Lombok, Maven, Docker & Docker Compose
- **Environment:** Spring Dotenv for secure variable management

---

## Project Structure

```text
backend/
├── src/main/java/.../asaltamontes/
│   ├── auth/                  # JWT logic, Login/Register controllers
│   ├── user/                  # Social Graph and Profile management
│   ├── message/               # Messaging Domain
│   │   ├── domain/            # Entities: Conversation, Participant, Message
│   │   ├── dto/               # Immutable Java Records (PrivateMessage, ChatSummary)
│   │   ├── repository/        # Optimized JPQL for chat threading
│   │   └── controller/        # WebSocket handlers (@MessageMapping)
│   ├── infrastructure/        # WebSocketConfig, Security Filters, Global CORS
│   └── common/                # Global Exception Handlers & Constants
├── Dockerfile                 # Optimized multi-stage build
└── .env.example               # Environment template
```

---

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
```

### 2. Running with Docker (Recommended)
```bash
docker-compose up --build
```

### 3. Local Development
```bash
./mvnw spring-boot:run
```

---

## API & WebSocket Endpoints

### REST API
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/auth/login` | Authentication & Cookie issuance | Public |
| GET | `/api/users/{id}` | User profile data | JWT |
| POST | `/api/users/{id}/follow`| Follow/Unfollow toggle | JWT |
| GET | `/api/messages/chats` | Fetch active conversations | JWT |

### WebSocket (STOMP)
| Type | Destination | Description |
|------|-------------|-------------|
| **App Destination** | `/app/chat.private` | Send a 1-to-1 message |
| **User Queue** | `/user/queue/messages` | Subscribe to incoming private messages |
| **Broker** | `/topic/**` | Public/Group announcements |

---

## Roadmap

- [ ] **Push Notifications:** Firebase Cloud Messaging for offline alerts.
- [ ] **Media Support:** Image and GPX file sharing within chats.
- [ ] **Events Module:** Core domain for "Quedadas" (Trail running meetups).
- [ ] **Database Migrations:** Integrating Flyway for version-controlled schemas.

-----

*Last Updated:* March 2026

-----