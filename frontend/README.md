

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

- `npm run build`: Build the app for production in `/dist`.
- `npm run preview`: Serve the production build locally.

## Environment Variables

Configure the backend API URL using the `VITE_API_URL` variable in a `.env` file at the root of `/frontend`:

```
VITE_API_URL=http://localhost:8080
```

## Docker Deployment

You can build and run the frontend in a container:

```bash
docker build -t asaltamontes-frontend .
docker run -p 5173:80 --env VITE_API_URL=http://backend:8080 asaltamontes-frontend
```
Or use `docker-compose` from the project root to launch the entire stack.

## Additional Documentation

- [Backend OpenAPI contract](../docs/api-contract-v1.json)
- [Project main README](../README.md)

---
*Last updated: May 2026*
