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

## D-004 · Imágenes en el sistema de archivos detrás de `MediaStorage` (2026-10-21, revisada)
- **Decisión (Yago, 2026-10-21):** para la demo, los ficheros se guardan en un volumen de Docker (`media_data`) detrás de la interfaz `MediaStorage` (`FileSystemMediaStorage`). Las subidas pasan por el backend, que valida, quita el EXIF y genera las variantes; nginx sirve `/media/` directamente desde el volumen (solo lectura). Cuando haya hosting, se añade una implementación S3/R2 de la misma interfaz (y, con ella, las URLs prefirmadas).
- **Motivo:** MinIO dejó de publicar imágenes Docker comunitarias en octubre de 2025 y su repositorio está archivado; un servicio S3 adicional (Garage, SeaweedFS) no compensa en la Pi (D-016).
- **Consecuencias:** nunca se guardan binarios en Postgres. El volumen `media_data` necesita su propio backup (el de T07b solo cubre la BD). El código fuera de `media/` no conoce rutas de disco: solo `MediaStorage` y URLs públicas.
- **Historial:** la versión inicial elegía MinIO + AWS SDK v2 con URLs prefirmadas.

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

## D-019 · La plataforma será internacional (2026-10-23)
- **Decisión (Yago):** habrá eventos y usuarias en todo el mundo. Regla de zonas horarias: **la hora pertenece al lugar del evento**.
- **Consecuencias:**
  1. Al crear un evento, la zona por defecto es la del navegador de la organizadora (no `Europe/Madrid` fijo).
  2. Selector de zona IANA con buscador, en un apartado "Avanzado" del formulario.
  3. El backend valida con `ZoneId` y **nunca acepta desfases fijos** (`+02:00`); solo identificadores IANA.
  4. Al mostrar: hora local del evento y, si la zona de quien lo ve es distinta, también la suya.
  5. Deducir la zona a partir del punto del mapa queda para **después de la demo** (memoria de la Pi).
  6. En general: **nada de supuestos solo-España** en zonas horarias, formatos de fecha/número ni unidades (km/m ↔ mi/ft…). Textos y formatos pasan por `Intl` con la configuración regional de la usuaria; las unidades se guardan en SI y se formatean al mostrar. La traducción de la interfaz a otros idiomas es un tema aparte (no decidido).
- **Implementación:** PR `fix(events): time zone selector and clearing optional fields` (ver T15/T16).

## D-020 · PATCH: `null` explícito vacía, campo ausente no cambia (2026-10-23)
- **Decisión (Yago):** en los `PATCH` de la API, un campo con `null` explícito **vacía** el valor (si es opcional) y un campo ausente significa **sin cambios**. Se documenta en `docs/api-contract-v1.json`.
- **Consecuencias:** los DTO de actualización deben distinguir "ausente" de `null` (p. ej. `JsonNullable` o un mapa de campos presentes). Empieza por eventos; el resto de PATCH se alinea cuando se toquen.

## D-021 · Identificadores: `bigint` interno + `public_id` UUID v7 hacia fuera (2026-10-24)
- **Contexto (Yago):** se busca la mejor práctica en seguridad y rendimiento; perder los datos actuales no importa (D-014).
- **Decisión:** la clave primaria de todas las tablas sigue siendo `bigint` (índices y FK pequeños, joins rápidos, menos escritura en la microSD, y se respeta la arquitectura de Yago, D-012). Todo recurso que se exponga en la API, en URLs o en eventos de WebSocket tiene además una columna **`public_id uuid` (v7, ordenado por tiempo) única y no nula**, y **solo ese id sale hacia fuera**. Usuarias por `username` y comunidades por `slug` siguen como están. No se migran las PK a UUID: cuesta rendimiento sin añadir seguridad.
- **Consecuencias:**
  - Nada se puede enumerar (`/events/124`, `/events/125`…) ni revela volúmenes de negocio (usuarias, eventos, mensajes), lo que importa de cara a las marcas.
  - La defensa principal sigue siendo la **autorización por recurso** en cada petición; el UUID es una capa extra.
  - Un **test de arquitectura en la CI** impide que un DTO de respuesta exponga el `Long id` interno.
  - Los **cursores de paginación son opacos** (no se expone el id interno ni el orden real). T30c los codifica (base64url) y los valida contra la BD y los permisos; **no van firmados** (Yago, 2026-10-27: no por ahora). Si algún día un cursor lleva filtros o permisos dentro, habrá que firmarlos (HMAC con secreto en `.env`).
  - Postgres 17 no genera UUID v7 de forma nativa: se generan en Java al crear la entidad (`@PrePersist` o generador de Hibernate si lo soporta la versión).
  - **Todo recurso nuevo que se exponga nace con `public_id`.** Implementación de lo existente: **T30**.

## D-022 · Grupos, bloqueos, límites y moderación: ajustes de Yago (2026-10-28)
- **Decisión (Yago):**
  1. Los **administradores de un grupo de chat** pueden expulsar o degradar a otra administradora, salvo a la creadora (T12).
  2. El **`dm_policy` se aplica también al añadir a alguien a un grupo** y al crear un grupo con participantes; un **bloqueo en cualquier dirección lo impide siempre**. Mismo código y mensaje que en los privados, sin revelar el bloqueo (T13).
  3. **Límites por usuaria**, generosos y configurables: ~30 ediciones de evento/hora, 10 grupos creados/día, 100 follows/hora, con `429` y `Retry-After`. Las notificaciones de una actora bloqueada se ocultan al leer, sin borrarlas. Cooldown de 24 h de `NEW_FOLLOWER` aceptado (T18).
  4. **Cooldown de 7 días** (configurable) tras rechazar una solicitud de unión; **una moderadora no puede readmitir a quien expulsó la propietaria** (T19).
  5. **Expulsiones anteriores a V14** sin entrada en el registro de moderación cuentan como hechas por una moderadora (lectura permisiva: las puede readmitir cualquier moderadora). Aceptado por Yago (2026-10-28); los datos son desechables (D-014).
  6. **Bloqueo en grupos compartidos** (Yago, 2026-10-29): se mantiene; siguen juntas en el grupo y los participantes no se filtran. En el frontend, los mensajes de una usuaria que yo he bloqueado salen plegados como «Mensaje de una usuaria bloqueada · Ver» (solo para quien bloquea, resuelto en cliente).
- **Consecuencias:** detalles y propiedades en las secciones "Decisiones de Yago (2026-10-28)" de T12, T13, T18 y T19. Los límites por usuaria van en `UserActionLimiter`; futuros límites del mismo tipo (T20+) deben usarlo.

## D-023 · Visibilidad en perfiles ajenos y en Inicio (2026-10-28)
- **Decisión (Yago):** en el perfil de otra usuaria se muestran **solo** (a) las quedadas que **organiza** y que **quien mira puede ver**, y (b) sus **comunidades públicas**. **Nunca** su asistencia a quedadas futuras (revelaría dónde y cuándo estará). El filtro «de quienes sigo» de Inicio aplica **las mismas reglas de visibilidad**.
- **Consecuencias:** los endpoints de T31b filtran por visibilidad en la consulta (no en memoria), paginan, respetan bloqueos (D-022) y llevan tests de que no se filtran quedadas o comunidades privadas. El rol de moderación en comunidades (propietaria/moderadora) es público a propósito (T20).

## D-024 · Búsqueda de lugares con Photon (OpenStreetMap) (2026-10-28)
- **Contexto:** al crear una quedada, Yago quiere escribir el lugar («vialia») y elegirlo de una lista que lo marque en el mapa. Google Places exige clave con facturación y prohíbe mostrar sus resultados sobre un mapa que no sea de Google, lo que chocaría con D-005.
- **Decisión (Yago):** autocompletado con **Photon** (geocodificador de komoot sobre datos de OSM), llamado desde el navegador, sin clave. Los resultados se ordenan cerca del punto marcado o de la zona que se ve en el mapa. El nombre del lugar sigue siendo texto libre: la sugerencia es una ayuda.
- **Consecuencias:** la instancia pública `photon.komoot.io` es de uso razonable y sin garantías; vale para la demo. Con tráfico real, autoalojar Photon o pasar a un proveedor (MapTiler, Geoapify…), cambiando solo `places.service.js`. Atribución a OpenStreetMap visible bajo las sugerencias.
- **Referencia de ubicación:** las sugerencias se ordenan cerca del punto marcado, de la zona ampliada del mapa o, en su defecto, de la ciudad del perfil (localizada en segundo plano al abrir el formulario). El botón «Usar mi ubicación» pide permiso al navegador solo cuando ella lo pulsa (requiere HTTPS).
- **Pendiente (Yago, 2026-10-28):** la instancia pública es lenta: **1,2-1,6 s por búsqueda**, casi todo tiempo de servidor (medido). Se acepta para la demo, pero conviene migrar a una opción mejor: Geoapify (~0,2-0,3 s, 3.000 peticiones/día gratis) o MapTiler (100.000/mes, y sirve también para los tiles de D-005), con la clave en una variable `VITE_` restringida al dominio, o Photon autoalojado.
