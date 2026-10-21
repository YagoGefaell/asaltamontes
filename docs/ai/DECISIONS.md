# Decisiones

Registro de decisiones de arquitectura y producto. **Ya están tomadas**: no se re-discuten salvo que aparezca información nueva, y en ese caso se propone el cambio en una PR `docs/` explicando qué ha cambiado.
Formato: contexto → decisión → consecuencias. Las nuevas se añaden **al final** con el siguiente número.

## D-001 · Monolito modular en Spring Boot (2026-10-21)
- **Contexto:** objetivo de miles de usuarias concurrentes, un solo desarrollador y poco tiempo.
- **Decisión:** se mantiene un único backend Spring Boot organizado por dominios (`auth`, `user`, `community`, `message`, `event`…). Nada de microservicios.
- **Consecuencias:** los dominios se comunican mediante servicios o eventos de dominio de Spring (`ApplicationEventPublisher`), no accediendo a los repositorios de otros dominios. Esto facilita extraer un módulo en el futuro si hiciera falta.

## D-002 · Entorno objetivo: el servidor privado de Yago (2026-10-21)
- **Contexto:** la app está en desarrollo; primero se busca calidad y después hosting.
- **Decisión:** todo se diseña para funcionar en el servidor privado con Docker Compose, desplegado por el runner self-hosted al hacer merge a `main`. El hosting (VPS público o gestionado) se decide después de la demo privada.
- **Consecuencias:** se prefieren servicios autoalojables en el compose (MinIO, backups, Uptime Kuma) con APIs estándar (S3, SMTP) que permitan migrar a servicios gestionados sin reescribir código.

## D-003 · Migraciones con Flyway (2026-10-21)
- **Decisión:** el esquema se versiona con Flyway; Hibernate usa `ddl-auto=validate`. Una migración mergeada nunca se edita.

## D-004 · Imágenes en almacenamiento compatible con S3, MinIO en el servidor (2026-10-21)
- **Decisión:** se usa MinIO en el compose y el AWS SDK v2 en el backend. Las subidas van directas del navegador con URL prefirmada, el backend las procesa (quita EXIF y genera variantes) y nginx las sirve. Cuando haya hosting, se pasa a Cloudflare R2 o S3 cambiando solo la configuración.
- **Consecuencias:** nunca se guardan binarios en Postgres ni en el sistema de archivos del contenedor del backend.

## D-005 · Mapas con Leaflet y OpenStreetMap (2026-10-21)
- **Decisión:** Leaflet + `react-leaflet` con tiles de OSM y atribución visible. Sin PostGIS de momento (basta con un bounding box sobre lat/lng indexados).
- **Consecuencias:** hay que respetar la política de uso de tiles de OSM. Con tráfico real, pasar a un proveedor de tiles (MapTiler…) o servir tiles propios.

## D-006 · App móvil como PWA (2026-10-21)
- **Decisión:** la app se hace instalable como PWA. Una app nativa se valorará después de la demo.
- **Consecuencias:** diseño mobile first y HTTPS obligatorio en el servidor para el service worker.

## D-007 · WebSocket con SimpleBroker mientras haya una sola instancia (2026-10-21)
- **Decisión:** se mantiene el broker STOMP en memoria. Cuando haga falta más de una instancia del backend, se pasará a un relay (RabbitMQ STOMP o Redis pub/sub).
- **Consecuencias:** no se guarda estado de sesión WS fuera de lo que gestiona Spring. Los envíos a usuarias se hacen por id (`convertAndSendToUser(id)`), para que el cambio sea transparente.

## D-008 · Prioridades de producto (2026-10-21)
- **Decisión (Yago):** 1) Chat, 2) Quedadas/eventos, 3) Comunidades. El feed social genérico tiene menos prioridad.

## D-009 · Flujo de trabajo por PRs con revisión humana (2026-10-21)
- **Decisión (Yago):** cada unidad de trabajo va en su propia rama y su propia PR. Yago revisa y mergea siempre; Claude nunca mergea ni commitea en `main`. Claude puede tener varias PRs abiertas a la vez (límite WIP 4). El merge es squash con título en formato conventional commit.

## D-010 · Estado vivo local en `.ai/` (2026-10-21)
- **Contexto:** si el estado de trabajo (en curso, en revisión) estuviera en archivos versionados, las PRs paralelas chocarían entre sí.
- **Decisión:** las especificaciones de tareas y bugs se versionan, cada una en su archivo, y su `Estado` final se cambia en la PR que las cierra. El estado intermedio y el diario de sesiones viven en `.ai/`, que está ignorado por git, en el checkout principal.
- **Consecuencias:** la verdad sobre lo que está hecho es `main` + las PRs de GitHub; `.ai/` es contexto de trabajo y se puede reconstruir desde ahí.

## D-011 · Idiomas (2026-10-21)
- **Decisión:** código, identificadores y commits en inglés; UI, mensajes a la usuaria y documentación de trabajo (`docs/ai/`) en español.
