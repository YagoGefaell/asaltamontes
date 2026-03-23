# Asaltamontes Backend - REST API

Backend REST API for the Asaltamontes Female platform. Built with Spring Boot and PostgreSQL, this API serves as the foundational architecture for user authentication and profile management, designed for scalability and secure community management.

## Table of Contents

  - [Overview](#overview)
  - [Architecture & Security Flow](#architecture--security-flow)
  - [Tech Stack](#tech-stack)
  - [Project Structure](#project-structure)
  - [Roadmap & Future Implementation](#roadmap--future-implementation)
  - [Prerequisites](#prerequisites)
  - [Configuration & Environment](#configuration--environment)
  - [Installation & Running](#installation--running)
  - [API Endpoints](#api-endpoints)

-----

## Overview

The Asaltamontes backend provides a secure foundation for the platform, focusing on user registration, authentication, and robust data validation.

**Current Core Features:**

  - Stateless JWT-based authentication using a dual-token strategy.
  - Robust data validation using Spring Boot Starter Validation (`@Valid`, `@NotBlank`, etc.).
  - Basic User profile management and Social Graph (Follow/Unfollow).
  - Centralized exception handling and CORS configuration.

-----

## Architecture & Security Flow

The application implements a secure, stateless authentication mechanism.

1.  **Token Strategy:**

      - **Access Token:** Short-lived. Stored in-memory by the frontend. Passed via `Authorization: Bearer <token>` header.
      - **Refresh Token:** Long-lived. Sent via a secure, `HttpOnly` cookie. Used to silently renew the Access Token via `/api/auth/refresh` without user intervention.

2.  **Filter Chain Execution:**

      - Public access is granted to `/api/auth/**`.
      - All other requests are intercepted by a custom `JwtAuthFilter` that validates the token and sets the `SecurityContextHolder` before reaching the controllers.

-----

## Tech Stack

  - **Core:** Java 21, Spring Boot 3.5.10
  - **Security:** Spring Security, JJWT (JSON Web Tokens)
  - **Data & ORM:** Spring Data JPA, Hibernate, PostgreSQL 15
  - **Infrastructure:** Docker & Docker Compose
  - **Environment Management:** Spring Dotenv (`me.paulschwarz:spring-dotenv`)
  - **Utilities:** Lombok, Maven

-----

## Project Structure

```text
backend/
├── src/main/java/.../asaltamontes/
│   ├── auth/                  # Auth controllers, services, and DTOs
│   ├── user/                  # User entities, repositories, and logic
│   ├── infrastructure/        # Security filters, JWT logic, and Global Configs
│   └── common/                # Shared exceptions and constants
├── src/main/resources/
│   └── application.properties # Main Spring configuration
├── Dockerfile                 # Multi-stage build for the API
├── .env.example               # Template for environment variables
└── pom.xml                    # Maven dependencies
```

-----

## Roadmap & Future Implementation

### Infrastructure Upgrades

  - **Database Migrations:** Transitioning from Hibernate's `ddl-auto` to **Flyway** for version-controlled schemas.
  - **CI/CD:** Automated testing and deployment pipelines.

### Feature Expansion

  - **Events Module:** Core domain for trail running meetups ("Quedadas").
  - **Messaging:** Real-time chat using WebSockets.
  - **RBAC:** Advanced Role-Based Access Control integration.

-----

## Prerequisites

  - **Docker & Docker Compose** (Recommended)
  - **Java 21** (For local development without Docker)
  - **Maven 3.8+**

-----

## Configuration & Environment

The backend uses `spring-dotenv` to load variables from a `.env` file.

1.  **Environment Variables:** Create a `.env` file in the **root of the project** (or the `backend/` folder if running standalone) with these keys:
    ```env
    DATABASE_URL=jdbc:postgresql://db:5432/asaltamontes
    DATABASE_USERNAME=postgres
    DATABASE_PASSWORD=your_secure_password
    JWT_SECRET=your_32_plus_characters_secret_key
    JWT_EXPIRATION_MS=900000
    JWT_REFRESH_EXPIRATION_MS=604800000
    ```

-----

## Installation & Running

### 1\. Using Docker (Preferred)

To launch the backend along with the database and frontend, run from the project root:

```bash
docker-compose --env-file .env up --build
```

### 2\. Local Standalone (Manual)

If you have a local PostgreSQL instance running:

1.  **Build:** `./mvnw clean install`
2.  **Run:** `./mvnw spring-boot:run`

*Note: If running manually, ensure your `.env` points to `localhost:5432` (or your specific local port).*

-----

## API Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/auth/register` | Create a new account | Public |
| POST | `/api/auth/login` | Login & receive tokens | Public |
| POST | `/api/auth/refresh` | Renew Access Token | Public (Cookie) |
| GET | `/api/users` | List all users | JWT |
| GET | `/api/users/{id}` | Get user profile | JWT |
| POST | `/api/users/{id}/follow` | Follow a user | JWT |

-----

**Last Updated:** March 2026

For frontend integration details, please refer to the [Frontend README](../frontend/README.md).

-----
