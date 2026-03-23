# Asaltamontes Frontend - React Web Application

Modern, responsive React web application for the Asaltamontes Female social network. This project provides the user interface for community connection, focusing on trail running and mountain sports for women.

## Table of Contents

- [Asaltamontes Frontend - React Web Application](#asaltamontes-frontend---react-web-application)
  - [Table of Contents](#table-of-contents)
  - [Overview](#overview)
  - [Authentication Strategy](#authentication-strategy)
  - [Tech Stack](#tech-stack)
  - [Project Structure](#project-structure)
  - [Features \& Roadmap](#features--roadmap)
    - [Currently Functional](#currently-functional)
    - [Planned for Future Release](#planned-for-future-release)
  - [Prerequisites](#prerequisites)
  - [Installation \& Deployment](#installation--deployment)
    - [1. Using Docker (Recommended)](#1-using-docker-recommended)
    - [2. Local Standalone Development](#2-local-standalone-development)
  - [Configuration](#configuration)
    - [Build for Production](#build-for-production)
    - [Testing](#testing)

## Overview

The Asaltamontes frontend is a Single Page Application (SPA) currently in its MVP phase. It focuses on user identity, social discovery, and secure session management. The application is built to be lightweight, using native Web APIs like **Fetch** for network requests and **React Context** for global state management.

## Authentication Strategy

The application implements a robust security flow to handle JWT tokens without exposing them to XSS attacks via LocalStorage.

1. **In-Memory Access Token:** Upon login, the Access Token is stored in a React Context variable. It is lost on page refresh to ensure maximum security.
2. **Silent Refresh:** - The application uses an `AuthContext` with a `useEffect` hook that triggers on mount.
      - This hook calls the `/api/auth/refresh` endpoint using `credentials: 'include'` to send the `HttpOnly` Refresh Token cookie automatically.
      - If the cookie is valid, the backend returns a new Access Token, restoring the session silently after a page reload.
3. **Interceptor Logic:** Manual interceptors are implemented within the fetch wrappers. If a request fails due to an expired token, the application attempts to call the refresh endpoint before forcing a logout, ensuring a seamless user experience.

## Tech Stack

- **Core:** React 19
- **Build Tool:** Vite 7.2.4 (SWC)
- **Routing:** React Router (BrowserRouter)
- **State Management:** React Context API
- **API Client:** Native Fetch API
- **Styling:** Standard CSS (Modular approach)
- **Icons:** React Icons 5.5
- **Testing:** Vitest 2.0 & React Testing Library
- **Containerization:** Docker

## Project Structure

```text
frontend/
├── src/
│   ├── app/
│   │   ├── App.jsx            # Root component & Providers
│   │   ├── router.jsx         # Browser Router definitions
│   │   └── guards/            # Protected Routes (AuthGuard)
│   ├── modules/               # Page-level modules
│   │   ├── auth/              # Login & Registration
│   │   ├── profile/           # User's personal profile
│   │   ├── search/            # User discovery
│   │   └── settings/          # Account configuration
│   ├── features/              # Business logic hooks & Fetch services
│   ├── shared/                # Reusable UI components
│   ├── hooks/                 # Global utility hooks (useAuth, etc.)
│   └── styles/                # Global and component-specific CSS
├── Dockerfile                 # Production-ready Docker image (Nginx)
├── vite.config.js             # Vite configuration
└── package.json               # Dependencies and scripts
```

## Features & Roadmap

### Currently Functional

- **User Authentication:** Complete Login and Registration flows.
- **Identity Management:** View and edit personal profiles and account settings.
- **Social Discovery:** Search functionality to find other users within the Asaltamontes community.
- **Social Interaction:** Fully functional "Follow/Unfollow" system to connect with other runners.
- **Route Protection:** Guarded routes to prevent unauthenticated access to the dashboard and profile.

### Planned for Future Release

- **Community Groups:** Modules for creating and joining trail running meetups.
- **Event Management:** Integration for race registration and event discovery.
- **Direct Messaging:** Real-time chat interface for user coordination.
- **Notification System:** In-app alerts for new followers and upcoming events.

## Prerequisites

- **Docker & Docker Compose** (Highly recommended for full-stack integration)
- **Node.js 18.0.0 or higher** (For local development only)
- **npm 9.0.0 or higher**

## Installation & Deployment

### 1\. Using Docker (Recommended)

To run the frontend as part of the entire Asaltamontes ecosystem, navigate to the **project root** and run:

```bash
docker-compose --env-file .env up --build
```

*The frontend will be served at `http://localhost:5173`.*

### 2\. Local Standalone Development

If you only wish to run the frontend independently:

1. **Navigate to directory:** `cd frontend`
2. **Install dependencies:** `npm install`
3. **Start Dev Server:** `npm run dev`

## Configuration

The application uses Vite environment variables. For Dockerized environments, variables are managed via the root `.env`. For local development, create a `.env.local` file in the `frontend` directory:

```env
VITE_API_BASE_URL=http://localhost:8080/api
VITE_APP_NAME=Asaltamontes Female
```

> **Note:** When running via Docker, ensure `VITE_API_BASE_URL` points to the public address of your backend (usually `localhost:8080`) so the browser can reach the API.

### Build for Production

To generate an optimized bundle:

```bash
npm run build
```

### Testing

To run the Vitest suite:

```bash
npm run test         # Watch mode
npm run test:run     # Single run
```

*Last Updated:* March 2026

For information regarding the API and database, please refer to the [Backend README](../backend/README.md).
