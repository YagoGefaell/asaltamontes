# T29 — Sistema de diseño y pulido de la interfaz

- **Estado:** pendiente (T29a y T29b-chat hechas; siguen T29c–T29h, ver abajo)
- **Fase:** Transversal (UI)
- **Depende de:** T01 (tests de frontend)
- **Rama sugerida:** `refactor/T29a-design-tokens`, `feat/T29b-ui-polish-<pantalla>`…
- **Cierra bugs:** —
- **Tamaño:** L (varias PRs pequeñas, una por pantalla o área)
- **Spec:** 🔍 alto nivel — **refinar antes de implementar**.

## T29a refinada (2026-10-21)

### Auditoría del estado actual
- 34 archivos CSS (~4 400 líneas). `styles/global.css` ya define 28 variables (colores de superficie, texto, borde, `--primary`, `--error`…) con modo oscuro por `prefers-color-scheme`.
- Pero muchos componentes usan **colores sueltos**: `#fff` (12), `#0a66c2` (6, el azul de LinkedIn), `#dc2626`/`#ef4444`/`#fee`/`#fef2f2` (rojos distintos para lo mismo), `#6366f1`, grises `#6c757d`/`#6b7280`/`#111827`. En modo oscuro se ven mal.
- **Tamaños de letra** sin escala: 0.75, 0.85, 0.9, 0.95, 1, 1.1, 1.2, 1.5 rem… **Radios**: 6, 8, 10, 12, 18 y 20 px.
- Componentes base que ya existen: `shared/forms/{Button,Input,FormError}`, `shared/ui/{SectionCard,UserCard,ToggleSwitch,NavBar,AvatarUploader}`, `shared/components/Loading`. Hay unos 39 `<button>` a mano que no usan `Button`.

### Alcance de T29a (una PR, sin cambios visuales intencionados salvo unificar)
1. `styles/tokens.css` (importado desde `global.css`): escala tipográfica (`--text-xs` 0.75 … `--text-2xl` 1.5 rem), espaciado (`--space-1` 4 px … `--space-8` 32 px), radios (`--radius-sm` 8, `--radius-md` 12, `--radius-pill` 999), sombras (2 niveles), colores semánticos (`--success`, `--warning`, `--error`, `--error-bg`, `--info`), foco (`--focus-ring`), en claro y oscuro.
2. Sustituir los colores sueltos por tokens (sobre todo rojos y grises), los `font-size` por la escala más cercana y los radios por los tres tokens. Sin cambiar la identidad (`--primary`, logo, nombre).
3. Componentes base nuevos en `shared/ui/`: `Avatar` (imagen + iniciales de respaldo, tamaños), `EmptyState`, `Skeleton`, `Toast` (con un `ToastProvider` mínimo, que también usarán T09b y T17b para los errores) y `Modal` (foco atrapado, `Esc`, `aria-modal`). `Button` gana variantes `secondary`/`danger`/`ghost` y estado `loading`.
4. Accesibilidad base: `:focus-visible` con el token de foco, tamaño táctil mínimo de 44 px en botones e iconos.
5. Tests (Vitest + Testing Library): `Button` (variantes, `loading` deshabilita), `Modal` (Esc cierra, foco), `Toast` (aparece y desaparece), `Avatar` (iniciales si no hay imagen o la imagen falla).
6. (Sin capturas en T29a por RAM de la Pi: la PR lista las pantallas a revisar a mano.) Capturas antes/después en 360 px y escritorio de 3 pantallas (Chat, Comunidades, Perfil) en la descripción de la PR.

### Criterios de aceptación de T29a
- [x] Ningún color hexadecimal (paso de `npm run lint`: `frontend/scripts/check-colors.mjs`) fuera de `tokens.css`/`global.css` (comprobable con un `grep`; se puede añadir como paso de lint).
- [x] Modo oscuro sin textos ilegibles en las pantallas existentes (por tokens; **sin verificar en navegador**, lo revisa Yago).
- [x] Componentes base con tests y usados al menos en una pantalla cada uno.

## Segunda ronda (2026-10-27, revisión de Yago)

Yago revisó la interfaz y pide un estilo **tipo Instagram** con las quedadas como lo distintivo. Problemas que señala: filtros con muchas opciones que ocupan media pantalla, un buscador por pantalla en vez de uno único, y formularios que no se parecen entre sí. La guía completa está en [`docs/ai/DESIGN.md`](../DESIGN.md) y toda PR de UI pasa por `/ui-review` (capturas a 390/360 px, claro y oscuro, antes/después en la PR).

Orden recomendado (una PR por punto, `feat(ui)` / `refactor(front)`, sin cambios de API salvo que se diga):

| Sub-PR | Qué | Notas |
|---|---|---|
| **T29c — componentes de navegación** | `shared/ui`: `PageHeader` (título + acciones, atrás), `Tabs`, `Chip`/`ChipRow` deslizable, `BottomSheet` (foco atrapado, `Esc`, arrastre opcional), `Fab`. Con tests | Base del resto. Sustituye `window.confirm()` por `Modal` en `SettingsMenu`, `PrivacySettings` y `Profile` |
| **T29d — buscador único** ✅ | `/search` con pestañas Todo · Personas · Comunidades · Quedadas (DESIGN §4 "Buscar"). Sin texto: recientes (en `localStorage`) y sugerencias. Comunidades y Quedadas pierden sus buscadores propios (lupa → `/search?tab=…`) | Usa los endpoints actuales (`getAllUsersRequest`, `getCommunitiesRequest`, listado de eventos con texto si existe; si no, solo Personas + Comunidades y anotar la necesidad de backend) |
| **T29e — filtros de quedadas y comunidades** | Una fila de chips (Próximas/Hoy/Este finde/Mis quedadas…) + chip "Filtros · n" que abre `BottomSheet` con tipo, nivel y ciudad. Lista/Mapa como icono en la cabecera; "Crear quedada" como `+`/`Fab`. Comunidades: ciudad y provincia en la hoja; tarjetas en filas compactas | Criterio: primer resultado visible por encima de ~20 % de la pantalla a 390×844 |
| **T29f — formularios unificados** | `CommunityCreatorPanel`, `CommunityEditForm` y formularios de moderación pasan a `Input`/`Button`/`SectionCard`, con el patrón de `EditProfile`. `EventForm`: una columna (máx. 2 campos cortos por fila), opcionales en "Más opciones". Botones sin mayúsculas ("Guardar cambios") | Sin cambios de comportamiento: los tests existentes deben seguir pasando |
| **T29g — navegación de 5 pestañas** ✅ | Barra inferior: Inicio · Buscar · Quedadas · Comunidades · Perfil. Notificaciones y Mensajes como iconos con contador en la cabecera de Inicio | Aprobada por Yago (2026-10-27). Hecha: Notificaciones y Mensajes en la cabecera de Inicio |
| **T29h — perfil e inicio** | Perfil tipo Instagram (avatar a la izquierda, contadores, bio, pestañas Quedadas · Comunidades), sin texto de relleno. Inicio: quitar las publicaciones e historias de mentira; mientras no haya publicaciones (fuera de alcance), mostrar próximas quedadas de mis comunidades y de quien sigo | El contenido real del feed depende de backend: si no hay endpoint, usar los listados existentes y anotarlo |

## Objetivo
Yago da libertad para mejorar la interfaz (D-015). Antes de construir muchas pantallas nuevas (eventos, notificaciones, moderación), conviene tener una base visual coherente para que todo parezca la misma app.

## Enfoque
- **T29a**: auditoría de `src/styles/global.css` y los CSS por componente → tokens en variables CSS (colores, tipografía, espaciado, radios, sombras), modo claro y oscuro si encaja, sin cambiar la identidad (logo y nombre). Componentes base en `src/shared/` (Button, Input, Card, Avatar, Modal, Toast, EmptyState, Skeleton) reutilizando los existentes.
- **T29b…n**: una PR por pantalla aplicando la base y mejorando la UX (estados vacíos, cargando y error, feedback de acciones, accesibilidad: contraste, foco y tamaños táctiles). Orden sugerido: Chat → Comunidades → Perfil → Ajustes → Acceso.
- Antes de cada PR visual: `/ui-review` (capturas antes/después a 390 y 360 px, claro y oscuro, publicadas en la PR).
- Encaje en el roadmap: **T29a antes de T16** (frontend de eventos), para que las pantallas nuevas nazcan con la base. Las PRs T29b+ se intercalan cuando el límite WIP impide abrir PRs de backend, o después de cada fase.

## Notas de implementación de T29a
- Añadido `--radius-lg` (20 px) a los tres radios previstos, para burbujas de chat y chips que eran de 18-20 px.
- Estaban sin definir (y por tanto transparentes o con fallback): `--card-bg`, `--hover-bg`, `--pressed-bg`, `--text-secondary`, `--primary-dark`, `--error-color`. Ahora usan tokens existentes (`--surface`, `--hover-color`, `--background-alt`, `--text-muted`, `--pressed-color`, `--error-text`). Efecto visible: tarjetas y barra inferior con fondo de superficie también en oscuro.
- Uso: `Avatar` en `UserCard`; `EmptyState` en Comunidades y Chat; `Skeleton` en la lista de conversaciones; `Modal` + `Toast` en el borrado de comunidad; `Toast` en cerrar sesión en todos los dispositivos. `Button` ya estaba en uso.
- Pendiente para T29b+: sustituir los ~39 `<button>` a mano por `Button`, y el tamaño táctil de 44 px de iconos sueltos (solo `.btn` lo cumple ahora).

## T29b-comunidades (lista de Comunidades)

- Skeletons de tarjetas al cargar; error con botón "Reintentar"; estado vacío con acción (crear comunidad / limpiar búsqueda); "Cargar más" con `Button` y estado `loading`.
- Tarjeta: `Avatar` compartido y enlace real (`Link`) en lugar de `div role="button"` con un botón anidado (HTML inválido y mala accesibilidad); el botón de unirse queda fuera del enlace.
- Botón de crear a 44 px mediante `Button`; aviso `Toast` al crear la comunidad.
- Fuera de alcance (siguientes PRs de T29b): detalle de comunidad (`CommunityProfileDetail`), ajustes y panel de creación. Estado de T29: sigue pendiente (Perfil, Ajustes, Acceso).

## T29b-chat (hecha, 2026-10-23)
- Alcance: solo `/messages` (lista, panel de chat, creador de grupo). Sin cambios funcionales ni de API.
- `Button` sustituye a los `<button>` de acción (enviar, volver, crear grupo, reintentar, cargar más, enviar el grupo, quitar participante). Las filas de conversación y de resultados de búsqueda siguen siendo `<button>` nativos (son filas de lista, no acciones), con `min-height` de 44 px.
- Estados: esqueleto de mensajes al cargar el historial, `EmptyState` en chat vacío, error de historial (con "Reintentar") y error de la lista de conversaciones; "Sin resultados" al buscar participantes; `Toast` "Grupo creado".
- Fechas (D-019): `features/messages/utils/messageDates.js` con `Intl` en el idioma y la zona del navegador (antes `es-ES` / `Europe/Madrid` fijos); separadores de día "Hoy" / "Ayer" / fecha larga.
- Accesibilidad: lista de mensajes con `role="log"` y `aria-live="polite"`; objetivos táctiles de 44 px; foco visible en el campo de mensaje; textos pequeños con `--text-muted` (más contraste) en lugar de `--text-soft`; alertas con `role="alert"`.
- Pendiente de T29b: Comunidades, Perfil, Ajustes, Acceso.

## Notas de implementación de T29d (buscador único)
- `/search` (`pages/search/Search.jsx`): campo único + `Tabs` Todo · Personas · Comunidades · Quedadas. El texto y la pestaña viven en la URL (`?q=…&tab=users|communities|events`), así la lupa de Comunidades y Quedadas enlaza a `/search?tab=…`. "Todo" muestra 3 de cada tipo con "Ver todas" (abre la pestaña); en cada pestaña, "Cargar más".
- Sin texto: recientes (`localStorage` `asaltamontes.recentSearches`, máx. 8, siempre con try/catch; se guardan al enviar el campo o al pulsar un resultado) y sugerencias (comunidades y próximas quedadas, 5 de cada una); nunca la lista de todas las usuarias.
- Comunidades: se elimina el campo de nombre y se añade la lupa en la cabecera. **Se mantienen** Ciudad y Provincia hasta T29e (pasan a la hoja de filtros). Quedadas: lupa en la cabecera; los filtros no se tocan (T29e).
- **Necesidad de backend (no incluida):** `GET /api/events` no tiene búsqueda por texto (solo `city`). La pestaña Quedadas busca el texto como ciudad y lo avisa con una nota. Falta un parámetro `q` (título, lugar, ciudad) y, para sugerencias, "comunidades/quedadas cercanas" (necesita ubicación de la usuaria).
- Fuera de alcance: el endpoint de personas sigue ordenando como antes; no hay búsqueda de personas por comunidad.

## Nota T29d — sugerencias «Cerca de mí» (decisión de Yago, 2026-10-27)
Por ahora se hacen **sin GPS**: se sugieren quedadas y comunidades de la ciudad del perfil y de las ciudades de mis comunidades. La geolocalización queda para más adelante, opcional y solo al pulsar «Cerca de mí». El backend que falta para la UI (`q`, `attendeePreview`, `city`) está en [T31](T31-backend-for-ui.md).
