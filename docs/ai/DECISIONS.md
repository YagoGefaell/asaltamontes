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
- **Revisión pendiente (2026-10-21):** MinIO dejó de publicar imágenes Docker comunitarias en octubre de 2025 y su repositorio está archivado. Alternativas y recomendación en `tasks/T17-media-storage.md` ("Decisión pendiente"); falta la decisión de Yago.

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

## D-012 · Respetar la arquitectura actual del backend, sobre todo la autenticación (2026-10-21)
- **Contexto (Yago):** el backend lo diseñó y escribió Yago casi sin IA y le tiene aprecio, especialmente a la autenticación: `JwtUtil`, `JwtAuthFilter`, `AuthService`, `AuthController`, `CookieUtil`, el refresh por cookie httpOnly, el access token en memoria del frontend y `WebSocketJwtInterceptor`.
- **Decisión:** se **extiende** lo que hay, no se sustituye. Los arreglos se hacen dentro de las clases y flujos existentes, con cambios mínimos y en el estilo de Yago. No se introducen frameworks alternativos (Spring Authorization Server, OAuth2 Resource Server, Keycloak…) ni se reescriben módulos que funcionan.
- **Consecuencias:** cualquier cambio **estructural** en la autenticación (otro tipo de token, otras clases, otro flujo) se propone primero en la descripción de la PR o en una PR `docs/` y no se implementa sin el OK explícito de Yago. Lo mismo aplica, en menor grado, al resto del backend: preferir el arreglo local al refactor.

## D-013 · Servidor solo en la LAN de casa (2026-10-21)
- **Contexto:** el servidor privado no es accesible desde internet; está en la red local de Yago, y el runner self-hosted y Claude trabajan en esa misma máquina.
- **Decisión (Yago, 2026-10-21):** no hay dominio ni TLS público. Para HTTPS se usará **Tailscale** (certificados válidos vía MagicDNS + `tailscale serve`, acceso también desde fuera de casa y para las testers invitadas). Tailscale no necesita abrir puertos en el router (solo conexiones salientes, funciona tras CG-NAT con relés DERP); Yago tuvo problemas con su router/ISP, así que T07 **verifica primero** que funciona. Plan B: CA local con mkcert.
- **Consecuencias:** la cookie `Secure` es configurable (T03) y por defecto `false` mientras no haya HTTPS. Las testers de la demo necesitarán acceso a la red (VPN/Tailscale) o habrá que exponerlo; se decidirá antes de T25.

## D-014 · Los datos de la BD del servidor son desechables (2026-10-21)
- **Contexto (Yago):** no hay datos reales; existe un archivo con datos de ejemplo cargados como base, pero si se pierden no importa.
- **Decisión:** las migraciones (T02, T05) y el upgrade de Postgres (T08) pueden recrear la BD desde cero en lugar de migrar datos con cuidado. Aun así, se mantiene la disciplina de migraciones (cuando haya datos reales, deberá funcionar igual).
- **Consecuencias:** se localiza el archivo de datos de ejemplo del servidor y se convierte en un seed versionado y reproducible (`scripts/seed/`, sin datos personales reales) dentro de T02.

## D-015 · Libertad para mejorar la interfaz (2026-10-21)
- **Decisión (Yago):** Claude puede proponer mejoras visuales y de UX.
- **Consecuencias:** las mejoras de UI van en **PRs propias** (`feat(ui)`/`refactor(front)`), nunca mezcladas con PRs funcionales. Las pantallas nuevas deben ser coherentes con un sistema de diseño común (T29). Mobile first. No se cambia la identidad (logo, nombre) sin consultar.

## D-016 · El servidor es una Raspberry Pi 5 (2026-10-21)
- **Contexto:** la app desplegada, el runner self-hosted y Claude Code con sus agentes corren en la misma Raspberry Pi 5 (ARM64, RAM limitada y compartida).
- **Decisión:**
  - Toda imagen Docker nueva debe tener variante **linux/arm64** (verificarlo antes de añadirla).
  - Límites de memoria explícitos en el compose (JVM con `MaxRAMPercentage`, Postgres ajustado a la RAM disponible).
  - **Agentes en paralelo: máximo 2**, y el segundo solo si hay margen de memoria (WORKFLOW §10). Empezar con 1 y medir.
  - Las pruebas de carga (T28) se lanzan **desde otra máquina** (el portátil), no desde la Pi.
- **Consecuencias:** la Pi es el entorno de la demo, no el de producción a escala; los objetivos de rendimiento se fijan para la demo (D-017) y el diseño sigue pensando en escalar después (hosting).

## D-017 · Alcance de la demo privada (2026-10-21)
- **Decisión (Yago):** de 20 a 50 testers, **sin fecha**: prima la calidad sobre la velocidad.
- **Consecuencias:** objetivos de T28 para la Pi: 100 usuarias virtuales concurrentes con p95 < 500 ms en REST y entrega de mensajes < 1 s, con margen sobre las 50 reales. No se recorta alcance para llegar antes.

## D-018 · Mensajes privados abiertos por defecto (2026-10-21)
- **Decisión (Yago):** `dm_policy` por defecto = `EVERYONE` (T13). Cada usuaria puede restringirlo en Ajustes; la seguridad se apoya en bloquear (T13) y denunciar (T23).
