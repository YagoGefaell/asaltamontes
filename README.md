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

Every push to `main` runs `.github/workflows/deploy.yml` on the server: it builds the new images while the current version keeps running, recreates only what changed, waits for the healthchecks and smoke-tests the app through nginx. A failing healthcheck fails the job (the logs are printed in it). Changes only in `docs/`, `*.md` or `.claude/` do not deploy. After a successful deploy it removes build cache older than 24 h and unused images older than 72 h (never volumes), and warns in the job when the disk passes 80 %.

- Variables: `/etc/github-runner.env` (permissions `600`, owned by the runner user). It needs every variable of `.env.example`.
- All services have `restart: unless-stopped`: they come back after a reboot of the server.
- **Memory limits**: Raspberry Pi OS boots with `cgroup_disable=memory`, so Docker ignores the `mem_limit` values (it prints a warning). The backend heap is capped anyway with `-Xmx512m` (`BACKEND_JAVA_OPTS`). To enforce the limits, add `cgroup_enable=memory cgroup_memory=1` to `/boot/firmware/cmdline.txt` and reboot.
- **Backups**: the `backup` service (`db_backup`) runs `pg_dump` every day at `BACKUP_TIME` (default `03:30`, `TZ` Europe/Madrid) and once on start if the newest dump is older than a day. It keeps 7 daily and 4 weekly (Sunday) dumps in the `postgres_backups` volume, and turns `unhealthy` if the newest dump is older than 26 h. If the database is not ready (e.g. after a restart) it waits up to `BACKUP_DB_WAIT_SECONDS` (300) and retries failed dumps after 1, 5, 15 and 60 min (`BACKUP_RETRY_DELAYS`, in seconds). Test: `bash scripts/backup/test/test-backup-retry.sh`.
  - List: `docker compose exec backup ls -lh /backups/daily /backups/weekly`
  - Copy one to the host: `docker compose cp backup:/backups/daily/<file>.dump .`
  - Check a dump by restoring it into a scratch database: `docker compose exec backup /scripts/restore.sh /backups/daily/<file>.dump restore_check`, then `docker compose exec backup dropdb restore_check`.
  - Restore over the live database (data loss since that dump!): `docker compose stop backend`, `docker compose exec backup /scripts/restore.sh /backups/daily/<file>.dump asaltamontes --force`, `docker compose start backend`.
  - The dumps live on the same SD card as the database: copy them elsewhere from time to time.
- **Uploaded images** (T17a, T17c): `POST /api/media` validates the real file type, re-encodes it as JPEG without EXIF/GPS and stores three sizes in the `media_data` volume (`MEDIA_DIR`, default `/data/media`, mounted read-write in the backend and read-only in nginx, which serves `/media/` with a one-year cache).
  - **References**: a profile, community or group points to its image by media id (`avatar_media_id`, `cover_media_id`, `group_media_id`). Free external image URLs are no longer accepted (BUG-057); an already stored external URL (e.g. Google's) is kept until changed.
  - **Orphan cleanup**: every day at `MEDIA_CLEANUP_CRON` (default `0 15 4 * * *`) the backend deletes, from disk and database, the images nobody references once they are older than `MEDIA_ORPHAN_GRACE_PERIOD` (default `24h`; keep it well above zero so an upload can still be linked). Replacing or removing a picture deletes the previous one right away.
  - **Quota**: `MEDIA_MAX_BYTES_PER_USER` (default `50MB`, sum of the stored variants per user); beyond it an upload gets `409` with a message in Spanish.
  - **Backup**: right after the database dump, the `backup` service archives `media_data` (mounted read-only) into `/backups/media/media-<date>.tar.gz`, keeping **only the newest `BACKUP_MEDIA_KEEP` (3)**. Before copying it checks that the disk has room for the whole folder plus `BACKUP_MIN_FREE_MB` (1024) of margin; if not, it copies nothing, keeps the old copies, logs `SKIPPED: not enough free disk space` and writes it in `/backups/media/status`. This does **not** make the service `unhealthy` (a skipped copy must not fail the deploy): watch it with `docker compose exec backup cat /backups/media/status` (first word `ok`, `skipped` or `failed`, then date), or `docker compose logs backup | grep backup-media`. Manual run: `docker compose exec backup bash /scripts/backup-media.sh`.
    - List: `docker compose exec backup ls -lh /backups/media; docker compose exec backup cat /backups/media/status`
    - **Restore** (images lost or volume replaced; the backend does not need the old files, so stop it to avoid uploads during the restore): `docker compose stop backend`, then extract the chosen copy over the volume with a one-off container (`<project>` is the compose project name, find the exact volume names with `docker volume ls | grep -e media_data -e postgres_backups`): `docker run --rm -v <project>_media_data:/m -v <project>_postgres_backups:/b:ro postgres:17 tar xzf /b/media/media-<date>.tar.gz -C /m`, then `docker compose start backend`. Add `find /m -mindepth 1 -delete &&` before `tar` for an exact copy of the backup (this removes images uploaded after it). The database and the images must come from the same day: after restoring a dump, images uploaded later are orphans and are collected by the cleanup; rows that point to images newer than the archive show a broken image until they are replaced. To verify an archive without touching anything: `docker compose exec backup tar -tzf /backups/media/media-<date>.tar.gz | head`.
    - Like the dumps, the copies live on the same disk as the data: copy them elsewhere from time to time.
- **PostgreSQL 17** (since T08) on the `postgres17_data` volume. The 13 → 17 move is a manual, guided step done once before deploying T08: `ENV_FILE=/etc/github-runner.env scripts/db/upgrade-13-to-17.sh` copies the data into the new volume and compares row counts table by table. The old `postgres_data` volume stays untouched for rollback (point `db` back to `postgres:13` + `postgres_data`).
- Test stack on the same server, never touching production: `docker compose -p asaltamontes-dev -f docker-compose.yml -f docker-compose.dev.yml --env-file <file> up -d --build` (app on `:8081`, backend `127.0.0.1:8091`, DB `127.0.0.1:5434`).

### Email (T24a)

The backend sends emails (for now the "verify your email" link) through any SMTP server, configured with `MAIL_*` in the `.env` (see `.env.example`). **With `MAIL_HOST` empty (the default) nothing is sent: each email is written to the backend log** (`docker compose logs backend | grep "email NOT sent"`), links included, so the demo works without a provider. That also means the log holds valid links: only use this mode on a private server.

Setting up [Resend](https://resend.com) (free plan, sends through SMTP):

1. Create an account and add your domain in *Domains*. Resend shows the DNS records to create at your registrar (SPF `TXT`, DKIM `TXT`/`CNAME` and optionally DMARC); wait until the domain shows *Verified*. Without a verified domain Resend only delivers to your own address.
2. In *API Keys* create a key with the *Sending access* permission for that domain.
3. In the `.env` (and `/etc/github-runner.env` on the server): `MAIL_HOST=smtp.resend.com`, `MAIL_PORT=587`, `MAIL_USER=resend`, `MAIL_PASSWORD=<the API key>`, `MAIL_FROM=Asaltamontes Female <hola@yourdomain.com>` (an address of the verified domain) and `APP_BASE_URL=<the public URL of the app>` (e.g. the Tailscale URL), which the links use.
4. Redeploy. Registering a user should now deliver the email.

`VERIFICATION_ENFORCE` (default `false`): when `true`, accounts with an unverified email cannot create communities or events, publish, or open new chats (they get `403` with `code: EMAIL_NOT_VERIFIED`). Turn it on only once emails really arrive, or nobody could verify. Google accounts are verified on creation. Existing accounts stay unverified until they use the link ("Resend" in the banner / `POST /api/users/me/verification-email`).

### Demo data (seed)

Fictional data for local work, the test stack or a demo (T02b): 24 users, 6 communities with members, follows, private chats and a group chat. No real personal data.

- Enable the `seed` profile next to the usual one: `SPRING_PROFILES_ACTIVE=prod,seed` (or `dev,seed`) in the `.env`, then start the backend. It runs once at startup and logs `Seed: created …`.
- Login: **`demo` / `Montana-2026`** (every seeded user has the same password; e.g. `lucia.trail`, `marta_monte`).
- It only seeds a database **without users**. To add the demo data to one that already has some (e.g. the test stack), also pass `-Dasaltamontes.seed.force=true` (`BACKEND_JAVA_OPTS=-Xms128m -Xmx512m -Dasaltamontes.seed.force=true`). If the `demo` user exists it does nothing, so restarting is safe.
- Reset the test stack database: `docker compose -p asaltamontes-dev down -v` (deletes its volumes), then `up` again with the `seed` profile.
- Never enable `seed` in production.

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
