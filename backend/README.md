# Asaltamontes Backend - REST API

Backend REST API for the Asaltamontes Female platform. Built with Spring Boot and PostgreSQL, this API currently serves as the foundational architecture for user authentication and profile management, with a clear roadmap for expanding into community event management.

## Table of Contents

  - [Overview](#overview)
  - [Architecture & Security Flow](#architecture--security-flow)
  - [Tech Stack](#tech-stack)
  - [Project Structure](#project-structure)
  - [Roadmap & Future Implementation](#roadmap--future-implementation)
  - [Prerequisites](#prerequisites)
  - [Configuration](#configuration)
  - [Installation & Running](#installation--running)
  - [API Endpoints](#api-endpoints)

-----

## Overview

The Asaltamontes backend is currently in its initial development phase (MVP). It provides a secure foundation for the platform, focusing strictly on user registration, authentication, and basic data validation.

**Current Core Features:**

  - Stateless JWT-based authentication using a dual-token strategy.
  - Robust data validation using Spring Boot Starter Validation (`@Valid`, `@NotBlank`, etc.).
  - Basic User profile management.
  - Centralized exception handling.

-----

## Architecture & Security Flow

The application implements a secure, stateless authentication mechanism designed to protect against common vulnerabilities like Cross-Site Scripting (XSS).

1.  **Token Strategy:**

      - **Access Token:** Sent to the frontend upon a successful login as a JSON response. The frontend stores this token in-memory (e.g., as a variable, not in `localStorage` or `sessionStorage`) and passes it via the `Authorization: Bearer <token>` header in all subsequent protected requests.
      - **Refresh Token:** Sent by the server via a secure, `HttpOnly` cookie upon login. Because the Access Token is stored in-memory, it is lost if the user reloads the page. When this happens, the frontend calls the `/api/auth/refresh` endpoint. The browser automatically includes the `HttpOnly` cookie in this request. The backend validates the refresh token and issues a new Access Token, allowing the user to remain logged in seamlessly.

2.  **Filter Chain Execution:**

      - The Spring `SecurityFilterChain` is configured to allow public access to `/api/auth/**` endpoints.
      - For all other endpoints, a custom `OncePerRequestFilter` intercepts the request before the standard Spring Security filters.
      - This custom filter extracts and validates the JWT Access Token. If valid, it establishes a stateless authentication session within the `SecurityContextHolder`, allowing the request to proceed to the controllers.

-----

## Tech Stack

  - **Core:** Java 21, Spring Boot 3.5.10
  - **Security:** Spring Security, JJWT (JSON Web Tokens)
  - **Data & ORM:** Spring Data JPA, Hibernate, PostgreSQL
  - **Validation:** Spring Boot Starter Validation
  - **Environment Management:** Spring Dotenv (`me.paulschwarz:spring-dotenv`)
  - **Utilities:** Lombok

-----

## Project Structure

The project follows a modular, domain-driven package structure.

```text
backend/
├── src/main/java/.../asaltamontes/
│   ├── auth/                  # Authentication controllers, services, and DTOs
│   ├── user/                  # User management, entities, and repositories
│   ├── infrastructure/        # Security filter chains, custom JWT filters, global configs
│   └── common/                # Shared exceptions, utilities, and constants
├── src/main/resources/
│   └── application.properties # Main Spring configuration
├── .env.example               # Template for environment variables
└── pom.xml                    # Maven dependencies
```

*(Note: As the project grows, new domains such as `events` or `communities` will be added as sibling directories to `auth` and `user`.)*

-----

## Roadmap & Future Implementation

This project is actively evolving. The following features and architectural improvements are planned:

### Infrastructure Upgrades

  - **Docker Integration:** Implementation of a `docker-compose.yml` file to spin up the PostgreSQL database and the application itself to standardize the development environment.
  - **Database Migrations:** Transitioning from Hibernate's automatic schema generation (`spring.jpa.hibernate.ddl-auto=update`) to a professional migration tool like **Flyway** or **Liquibase** for version-controlled database schemas.

### Feature Expansion

  - **Events / "Quedadas" Module:** Creating the core domain for organizing, searching, and joining local trail running meetups.
  - **Messaging System:** Implementing direct and group messaging capabilities.
  - **Role-Based Access Control (RBAC):** Roles currently exist at the database level but will be fully integrated into frontend capabilities and backend method-level security (`@PreAuthorize`).

-----

## Prerequisites

  - **Java 21 or later**
  - **PostgreSQL 12 or later**
  - **Maven 3.8+** (or use the included wrapper)

-----

## Configuration

The application uses `me.paulschwarz:spring-dotenv` to seamlessly load environment variables from a `.env` file located in the root of the `backend` directory.

1.  **Create the Database:**

    ```sql
    CREATE DATABASE asaltamontes;
    CREATE USER asaltamontes_user WITH PASSWORD 'your_password';
    GRANT ALL PRIVILEGES ON DATABASE asaltamontes TO asaltamontes_user;
    ```

2.  **Create the `.env` file:**
    Create a `.env` file in the `backend` folder based on this template:

    ```env
    # Database Configuration
    DATABASE_URL=jdbc:postgresql://localhost:5432/asaltamontes
    DATABASE_USERNAME=asaltamontes_user
    DATABASE_PASSWORD=your_password

    # JWT Configuration (Ensure the secret is at least 32 characters long)
    JWT_SECRET=your_super_secret_jwt_key_that_is_long_enough
    JWT_EXPIRATION_MS=86400000
    JWT_REFRESH_EXPIRATION_MS=604800000

    # Server Config
    SERVER_PORT=8080
    ```

    *(Running the project via your IDE or terminal will automatically inject these variables into your `application.properties` thanks to the `spring-dotenv` dependency.)*

-----

## Installation & Running

1.  **Navigate to the backend directory:**

    ```bash
    cd backend
    ```

2.  **Build the project:**

    ```bash
    ./mvnw clean install
    ```

3.  **Run the application:**

    ```bash
    ./mvnw spring-boot:run
    ```

    The server will start on `http://localhost:8080`.

-----

## API Endpoints

The following are the currently active endpoints for the MVP phase. All endpoints outside of `/api/auth/` require a valid JWT Access Token passed in the Authorization header.

### Authentication Endpoints (Public)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Registers a new user account and validates input data. |
| POST | `/api/auth/login` | Authenticates user credentials. Returns the Access Token in the response body and sets the Refresh Token in an `HttpOnly` cookie. |
| POST | `/api/auth/refresh` | Validates the `HttpOnly` Refresh Token cookie and issues a new Access Token. |
| POST | `/api/auth/logout` | Invalidates the user session and clears the `HttpOnly` Refresh Token cookie. |

### User Endpoints (Protected)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/users` | Retrieves a list of users registered on the platform. |
| GET | `/api/users/{id}` | Retrieves the profile information for a specific user. |
| PUT | `/api/users/{id}` | Updates a specific user's basic profile details. |
| DELETE | `/api/users/{id}` | Removes a user account from the system. |

*(Note: API documentation is maintained directly alongside the code. No Swagger/OpenAPI UI is currently generated or planned for this project.)*

-----
