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

## About The Project

**Asaltamontes Female** is a modern social web application currently in its initial development phase, designed specifically as the digital home for the Asaltamontes Female community.

Born from the initiative to promote women's participation in trail running and mountain sports, this platform aims to provide a safe, motivating space for women to connect. The project is currently laying its foundational architecture. As it evolves, it will bridge the gap between digital connection and real-world outdoor adventure, allowing users to organize local meetups, register for races, and find training partners.

Built with a robust **Spring Boot 3.5.10** backend and a responsive **React 19** frontend, the platform is being engineered from the ground up to support scalable community building and event management.

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

## Tech Stack

### Backend

- **Core:** Java 21, Spring Boot 3.5.10
- **Security:** Spring Security + JWT (JSON Web Tokens)
- **Data:** Spring Data JPA, Hibernate, PostgreSQL 15
- **Build:** Maven

### Frontend

- **Core:** React 19, Vite
- **Routing:** React Router
- **Styling:** CSS/Tailwind

### Infrastructure

- **Containerization:** Docker & Docker Compose
- **Environment Management:** Dotenv (.env)

## Architecture & Infrastructure

### Containerized Setup

The project is fully containerized, allowing for a seamless "one-command" setup. By using **Docker Compose**, we ensure that the database, backend, and frontend environments are identical for every developer, eliminating configuration drift.



## Quick Start

### Prerequisites

- **Docker** and **Docker Compose** installed.
- A `.env` file in the root directory with the required variables (`JWT_SECRET`, `DATABASE_PASSWORD`, etc.).

### 0. Prepare the .env file

```bash
cp .env.example .env
nano .env
```

### 1\. Launch the Full Stack

The easiest way to run the entire project (Frontend, Backend, and Database) is using Docker Compose from the root directory:

```bash
docker compose --env-file .env up --build
```

### 2\. Access the Services

Once the containers are healthy, the platform will be available at:

- **App:** `http://localhost` (nginx serves the frontend and proxies `/api`, `/auth`, `/ws-asaltamontes` and `/actuator/health` to the backend; `FRONTEND_PORT` changes the port)
- **Backend API (direct):** `http://127.0.0.1:8080`, only from the machine itself
- **PostgreSQL DB:** `127.0.0.1:5433`, only from the machine itself (from another computer: SSH tunnel, e.g. `ssh -L 5433:127.0.0.1:5433 administrador@<server>`, then DBeaver on `localhost:5433`)

### Server deployment (self-hosted runner)

Every push to `main` runs `.github/workflows/deploy.yml` on the server: it builds the new images while the current version keeps running, recreates only what changed, waits for the healthchecks and smoke-tests the app through nginx. A failing healthcheck fails the job (the logs are printed in it). Changes only in `docs/`, `*.md` or `.claude/` do not deploy.

- Variables: `/etc/github-runner.env` (permissions `600`, owned by the runner user). It needs every variable of `.env.example`.
- All services have `restart: unless-stopped`: they come back after a reboot of the server.
- **Memory limits**: Raspberry Pi OS boots with `cgroup_disable=memory`, so Docker ignores the `mem_limit` values (it prints a warning). The backend heap is capped anyway with `-Xmx512m` (`BACKEND_JAVA_OPTS`). To enforce the limits, add `cgroup_enable=memory cgroup_memory=1` to `/boot/firmware/cmdline.txt` and reboot.
- Test stack on the same server, never touching production: `docker compose -p asaltamontes-dev -f docker-compose.yml -f docker-compose.dev.yml --env-file <file> up -d --build` (app on `:8081`, backend `127.0.0.1:8091`, DB `127.0.0.1:5434`).

### 3\. Manual Development (Optional)

If you prefer to run services individually for debugging:

- **Backend:** Use VS Code's `F5` (Run and Debug) to load the `.env` file automatically via `launch.json`. Add `SPRING_PROFILES_ACTIVE=dev` (in `launch.json` or your `.env`) to get SQL logs and allow the Vite dev server origin.
- **Frontend:** Run `npm install` and `npm run dev` inside the `/frontend` folder.

## Project Structure

```text
asaltamontes/
├── backend/                # Spring Boot REST API (Dockerized)
│   ├── src/main/java/...   # Domain logic (Auth, Users, Communities)
│   ├── src/main/resources/ # Application properties & DB configurations
│   └── Dockerfile          # Backend container definition
├── frontend/               # React Single Page Application (SPA)
│   ├── src/                # React components, hooks, and views
│   ├── Dockerfile          # Frontend container definition
│   └── package.json        # Node dependencies
├── docs/                   # OpenAPI contracts & technical specs
├── docker-compose.yml      # Orchestration for all services
└── README.md               # Project overview (You are here)
```



## Documentation

For technical details and architecture of each part:

- **[Backend: installation & API](./backend/README.md)**
- **[Frontend: architecture & usage](./frontend/README.md)**
- **OpenAPI contract:** [docs/api-contract-v1.json](./docs/api-contract-v1.json).

## Contributing

We welcome contributions as we build this platform.

1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/FeatureName`).
3. Commit your changes (`git commit -m 'feat: add FeatureName'`).
4. Push to the branch (`git push origin feature/FeatureName`).
5. Open a Pull Request.


---
*Last updated: May 2026*
