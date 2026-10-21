

# Asaltamontes Frontend

SPA frontend for the **Asaltamontes** platform, built with **React 19** and **Vite**. Provides the user interface and interaction logic for the community.

## Table of Contents

- [Description](#description)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Installation & Usage](#installation--usage)
- [Environment Variables](#environment-variables)
- [Docker Deployment](#docker-deployment)
- [Additional Documentation](#additional-documentation)

## Description

SPA frontend for the Asaltamontes Female community. Enables registration, login, profile management, real-time messaging, and exploration of communities and events.

## Tech Stack

- **React 19**
- **Vite**
- **React Router**
- **CSS** (custom styles and optional Tailwind)
- **Docker** (for deployment)

## Project Structure

```
src/
  app/           # Routing, providers, and guards
  features/      # Functional modules (auth, users, messages, etc.)
  hooks/         # Reusable custom hooks
  lib/           # API client and utilities
  pages/         # Main views
  shared/        # Shared components
  styles/        # Global styles
  utils/         # Helpers and validation
```

## Installation & Usage

1. Install dependencies:
   ```bash
   npm install
   ```
2. Start the development server:
   ```bash
   npm run dev
   ```
3. Open [http://localhost:5173](http://localhost:5173)

### Useful Scripts

Tests live next to the code they test (`*.test.js` / `*.test.jsx`); shared setup is in `src/test/setup.js`.

- `npm run build`: Build the app for production in `/dist`.
- `npm test`: Run the unit tests once (Vitest + Testing Library, jsdom). `npm run test:watch` keeps them running while you code.
- `npm run lint`: Run ESLint.
- `npm run preview`: Serve the production build locally.

## Environment Variables

The app calls the backend **on its own origin** (`/api`, `/auth`, `/ws-asaltamontes`):
- In production, nginx (`nginx.conf`) proxies those paths to the backend container.
- With `npm run dev`, the Vite dev server proxies them to `http://localhost:8080` (change it with `VITE_DEV_BACKEND`).

So no variable is needed. `VITE_API_URL` (in `frontend/.env`) is only for pointing the app at a backend on **another** origin; the backend must then list this origin in `CORS_ALLOWED_ORIGINS`.

## Docker Deployment

The image (nginx) serves the build and proxies the backend paths to the `backend` service, so it is meant to run inside the project's `docker compose` stack (see the root README).

## Additional Documentation

- [Backend OpenAPI contract](../docs/api-contract-v1.json)
- [Project main README](../README.md)

---
*Last updated: May 2026*
