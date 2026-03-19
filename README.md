# Asaltamontes Female - Community Web Platform

## Table of Contents

  - [About The Project](#about-the-project)
  - [Features & Roadmap](#features--roadmap)
  - [Tech Stack](#tech-stack)
  - [Architecture & Infrastructure](#architecture--infrastructure)
  - [Quick Start](#quick-start)
  - [Project Structure](#project-structure)
  - [Documentation](#documentation)
  - [Contributing](#contributing)

-----

## About The Project

**Asaltamontes Female** is a modern social web application currently in its initial development phase, designed specifically as the digital home for the Asaltamontes Female community.

Born from the initiative to promote women's participation in trail running and mountain sports, this platform aims to provide a safe, motivating space for women to connect. The project is currently laying its foundational architecture. As it evolves, it will bridge the gap between digital connection and real-world outdoor adventure, allowing users to organize local meetups, register for races, and find training partners.

Built with a robust **Spring Boot 3.5.10** backend and a responsive **React 19** frontend, the platform is being engineered from the ground up to support scalable community building and event management.

-----

## Features & Roadmap

The project is in an early stage. Core foundational features are implemented, while the primary social features are actively being developed.

### Currently Implemented

  - **Secure Authentication:** JWT-based login and registration with role-based access control.
  - **User Profiles:** Basic profile creation establishing the foundation for user identity on the platform.
  - **Responsive UI:** Mobile-first design principles applied to the core layout.

### In Development (Roadmap)

  - **Community Groups:** Creation and management of local groups for trail running meetups.
  - **Event Discovery:** Search and registration functionality for upcoming trail running events.
  - **Media Uploads:** Cloud storage integration for users to upload and share their trail running photos.
  - **Direct Messaging:** Real-time peer-to-peer and group chats to coordinate training sessions directly within the app.
  - **Push Notifications:** Real-time alerts for upcoming events and community interactions.

-----

## Tech Stack

### Backend

  - **Core:** Java 21, Spring Boot 3.5.10
  - **Security:** Spring Security + JWT (JSON Web Tokens)
  - **Data:** Spring Data JPA, Hibernate, PostgreSQL 12+
  - **Build:** Maven

### Frontend

  - **Core:** React 19, Vite
  - **Routing:** React Router
  - **Testing:** Vitest
  - **Styling:** CSS/Tailwind

-----

## Architecture & Infrastructure

### Current Setup

The project currently runs as a standard localized setup. Developers need PostgreSQL, Java, and Node.js installed directly on their host machines to run the development environment.

### Deployment Roadmap

To ensure enterprise-level professionalism and scalability as the platform features are completed, the infrastructure is scheduled for the following upgrades:

1.  **Containerization:** Complete migration to **Docker** using `docker-compose`. This will allow new developers to spin up the database, backend, and frontend with a single command, standardizing the development environment.
2.  **Orchestration:** Future implementation of **Kubernetes (K8s)** for high availability, auto-scaling during high-traffic community events, and streamlined deployments.

-----

## Quick Start

### Prerequisites

  - **Java 21+**
  - **Node.js 18+**
  - **PostgreSQL 12+**

### 1\. Database Setup

Create your local PostgreSQL database:

```sql
CREATE DATABASE asaltamontes;
CREATE USER asaltamontes_user WITH PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE asaltamontes TO asaltamontes_user;
```

### 2\. Backend Initialization

Navigate to the backend directory, configure your environment variables with your database credentials and a secure `JWT_SECRET`, then run the server:

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

*The backend API will be available at `http://localhost:8080`*

### 3\. Frontend Initialization

In a new terminal, navigate to the frontend directory, install dependencies, and start the Vite development server:

```bash
cd frontend
npm install
npm run dev
```

*The web app will open at `http://localhost:5173`*

-----

## Project Structure

```text
asaltamontes/
├── backend/                  # Spring Boot REST API
│   ├── src/main/java/...     # Domain logic (Auth, Users, Communities)
│   ├── src/main/resources/   # Application properties & DB configurations
│   └── README.md             # Detailed Backend documentation
├── frontend/                 # React Single Page Application (SPA)
│   ├── src/                  # React components, hooks, and views
│   ├── package.json          # Node dependencies
│   └── README.md             # Detailed Frontend documentation
├── docs/                     # OpenAPI contracts & technical specs
└── README.md                 # Project overview (You are here)
```

-----

## Documentation

For deep dives into specific areas of the application, please refer to our dedicated documentation files:

  - **[Backend Setup & API Specs](./backend/README.md)**
  - **[Frontend Architecture & UI](./frontend/README.md)**
  - **API Contract:** Check the `/docs` folder for the OpenAPI specifications.

-----

## Contributing

We welcome contributions as we build this platform.

1.  Fork the repository.
2.  Create a feature branch (`git checkout -b feature/FeatureName`).
3.  Commit your changes (`git commit -m 'Add FeatureName'`).
4.  Push to the branch (`git push origin feature/FeatureName`).
5.  Open a Pull Request.

-----
