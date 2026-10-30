# T29 — Sistema de diseño y pulido de la interfaz

- **Estado:** pendiente (T29a y T29b-chat hechas; T29c–T29h hechas salvo lo que indique cada fila)
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
| **T29f — formularios unificados** ✅ | `CommunityCreatorPanel`, `CommunityEditForm` y formularios de moderación pasan a `Input`/`Button`/`SectionCard`, con el patrón de `EditProfile`. `EventForm`: una columna (máx. 2 campos cortos por fila), opcionales en "Más opciones". Botones sin mayúsculas ("Guardar cambios") | Sin cambios de comportamiento: los tests existentes deben seguir pasando |
| **T29g — navegación de 5 pestañas** ✅ | Barra inferior: Inicio · Buscar · Quedadas · Comunidades · Perfil. Notificaciones y Mensajes como iconos con contador en la cabecera de Inicio | Aprobada por Yago (2026-10-27). Hecha: Notificaciones y Mensajes en la cabecera de Inicio |
| **T29h — perfil e inicio** ✅ | Perfil tipo Instagram (avatar a la izquierda, contadores, bio, pestañas Quedadas · Comunidades), sin texto de relleno. Inicio: quitar las publicaciones e historias de mentira; mientras no haya publicaciones (fuera de alcance), mostrar próximas quedadas de mis comunidades y de quien sigo | El contenido real del feed depende de backend: si no hay endpoint, usar los listados existentes y anotarlo. Hecha (con el backend de [T31b](T31b-profile-and-home-backend.md)): Inicio es de publicaciones ([T22](T22-community-feed.md)); hasta que existan muestra el estado vacío y las filas deslizables «Usuarias para seguir» y «Comunidades recomendadas», sin quedadas. Buscar sin texto queda en blanco hasta la cuadrícula de T22; al tocar el campo aparecen ya las pestañas Todo · Personas · Comunidades · Quedadas, sin esperar a que escriba (Yago, 2026-10-28); dentro de una pestaña no se repite su nombre como título de sección. «Tus comunidades» pasa a la pestaña Comunidades ([T29i](T29i-communities-tab.md)). Perfil propio y ajeno con pestañas Quedadas · Comunidades (`/api/users/{username}/events` y `/communities`) |

## Tercera ronda (2026-10-29, revisión y auditoría con Yago)

Yago revisó la app tras la segunda ronda y se hizo una auditoría con capturas a 390 px (claro y oscuro) de todas las pantallas. Las decisiones están reflejadas en `DESIGN.md` (§3, §4 y §7). **Prioridad: por delante de T22b/c.** Una PR por fila, `feat(ui)` / `refactor(front)`, con `/ui-review`.

| Sub-PR | Qué | Notas |
|---|---|---|
| **T29j — notificaciones** ✅ | Al entrar en `/notifications`, todas pasan a vistas (`markAllRead` al montar; el contador de Inicio se limpia) y las que eran nuevas siguen resaltadas mientras dure la visita. Se quita el botón de doble check. En «te ha empezado a seguir», botón «Seguir también» / «Siguiendo» a la derecha (mismo componente que `UserCard`); el resto de la fila abre el perfil | Necesita saber si ya la sigo: si el DTO de la notificación no trae `actor.followedByMe`, añadirlo en el backend (proyección, sin N+1) en la misma PR o en una `feat(notifications)` previa. Las solicitudes de T23 llevarán Aceptar/Rechazar en la fila con el mismo patrón |
| **T29k — crear unificado y sin atajos de búsqueda** ✅ | `+` arriba a la derecha en Quedadas y en Comunidades (directo a crear); **Inicio no lleva `+`** (Yago, revisión de la PR #143). Crear comunidad pasa a pantalla propia `/communities/new` (hoy es un panel dentro de la lista). Se quita el `Fab` de Quedadas y la lupa de su cabecera (queda mapa/lista + `+`). Se quitan los atajos de búsqueda de todas las pestañas | Cabecera de Inicio sin cambios: logo · Notificaciones · Mensajes |
| **T29l — pestaña Comunidades** | Sin el título «Tus comunidades» (la sección «Pendiente» conserva el suyo). Subtítulo de cada fila útil: último mensaje si lo hay; si no, «8 miembros · Próxima quedada sáb 31». Se sustituye el enlace «Descubrir comunidades cerca de ti» por una fila deslizable **«Sugeridas para ti»**: 5–10 comunidades de las que no soy miembro, primero las de mi ciudad y luego las más grandes, con «Unirse» | Reutilizar lo que ya existe (`getCommunitiesRequest` por ciudad + orden por miembros). Si hace falta un endpoint `GET /api/communities/suggested`, va en una PR de backend previa; más adelante lo ordenará T32 sin cambiar la pantalla |
| **T29m — detalle de quedada** ✅ | Cabecera con el título. Portada solo si hay foto (sin imagen no se muestra nada). Un solo botón principal (Apuntarme / Ya vas · Desapuntarme). Fila de iconos con texto **debajo del mapa** (Yago): Chat · Calendario · Compartir (Web Share API, si no copia el enlace) · Cómo llegar. «Eres la organizadora» como etiqueta junto al título. Metadatos en una línea. Asistentes con avatares apilados y «Ver todas» | Cierra el pendiente de la auditoría visual (tres botones apilados). Comprobar que el mapa carga sus teselas también al entrar directamente (en una captura salió gris) |
| **T29n — perfil de comunidad** ✅ | Mismo esqueleto que el perfil de usuaria: contadores Miembros · Quedadas, ciudad, botones con el estilo de Seguir/Mensaje. Pestañas **Quedadas · Miembros** (Publicaciones se añade como primera con T22b). Miembros en filas `UserCard`, paginadas, con buscador si hay más de 20 | Quita la cuadrícula completa de avatares de la portada. Hecha: el contador «Quedadas» sale de la primera página de la lista de quedadas (hasta 10, «10+» si hay más) porque la API no devuelve el total; el buscador de miembros filtra los ya cargados (sin parámetro `q` en backend) |
| **T29o — formulario de quedada** ✅ | Se mantiene el formulario de antes (Yago rechazó la primera versión con fecha/hora separadas, chips y botón fijo). Solo dos retoques: la **comunidad va justo después del título** (condiciona quién la ve) y el **mapa se abre centrado en la ciudad del perfil**, no en la península. Tipo y nivel siguen como desplegables y el botón «Crear quedada» al final del formulario, no fijo | Mismo formulario para editar. Patrón de `DESIGN.md` «Formularios» |
| **T29q — conversación (chat)** | **Backend primero** (PR `feat(chat)` previa o en la misma): `MessageDto` con `senderAvatarUrl` y `senderFullName` (en la proyección, sin N+1; también en el frame de WebSocket). **Front:** en grupos, los mensajes seguidos de la misma persona (misma autora, < 5 min, sin separador de día ni mensaje del sistema por medio) forman un bloque: **avatar pequeño (~28 px) a la izquierda de la primera burbuja, sin nombre ni @** (WhatsApp). Al tocar el avatar se abre su perfil. Las siguientes burbujas del bloque van alineadas sin avatar. **Sin etiqueta de rol** (Propietaria/Moderadora) en el chat: se ve solo en la info. La lógica de T20e que oculta la papelera según `authorRole` se mantiene. Botón de enviar: **flecha redonda de 44 px** (icono de enviar, color principal), apagada sin texto; el campo crece hasta 4–5 líneas; Intro envía en escritorio. Cabecera: se quita el «En linea» falso de los privados (debajo del nombre va el `@usuaria`). En grupos se quitan «Toca para ver el grupo» y el icono ⓘ: tocar el nombre o la foto abre la info y debajo del nombre va «Ana, Lucía, Marta y 4 más» o «7 participantes» | La hora sigue como ahora (marcadores centrados y separadores Hoy/Ayer, Yago). Presencia real («en línea») descartada por ahora. `aria-label` de cada bloque con el nombre de la autora, para que el lector de pantalla diga quién habla |
| **T29r — info del grupo** ✅ | Como WhatsApp: arriba y centrados, la foto grande (si eres admin, se toca para cambiarla con `ImageUploader`), el nombre con un lápiz para **editarlo en el sitio** (sin el campo duplicado ni el botón «Guardar nombre») y «Grupo · 7 participantes». Debajo, ajustes en filas (`ToggleSwitch` del historial para las nuevas). Después, Participantes con la fila «+ Añadir participantes» arriba (abre la búsqueda). Cada fila muestra solo avatar, nombre, `@usuaria` y la etiqueta «Admin». **Al tocar la fila**, una hoja inferior con Ver perfil · Enviar mensaje · Hacer admin / Quitar admin · Expulsar (en rojo, con confirmación en la misma hoja: es irreversible); las no admins solo ven las dos primeras. Al final, «Salir del grupo» en rojo. **Grupos gestionados** (comunidad o quedada): en lugar del texto largo, una fila «Chat de la comunidad X ›» (o de la quedada) que lleva a su página; sin añadir ni expulsar | Backend: `GroupDetailsDto` necesita el origen del grupo gestionado (`origin {type: COMMUNITY\|EVENT, slug\|id, name}`); si no existe, añadirlo en la misma PR `feat(chat)` de T29q. Arregla de paso los nombres partidos letra a letra en las filas |
| **T29s — sin pop-ups** | Aplicar la regla de `DESIGN.md` «Diálogos y avisos» en toda la app: quitar los 15 `toast.success` (al crear se navega a lo creado: comunidad → `/communities/{slug}`, quedada → su detalle, grupo → su chat); pasar los ~20 `toast.error` a errores en el sitio (`FormError` o texto junto al control); los 7 sitios con `Modal`/`ConfirmModal` → confirmación en `BottomSheet` **solo** para lo irreversible (borrar comunidad, transferir propiedad, expulsar, salir del grupo), y sin confirmar lo reversible (bloquear/desbloquear, dejar de seguir, silenciar, cerrar sesión). Al final, retirar `Toast`/`ToastProvider`, `Modal` y `ConfirmModal` si ya no los usa nadie (con sus tests) | Puede ir **en cualquier momento** de la ronda (no depende de las otras) y conviene hacerla pronto, para que las demás nazcan sin pop-ups. Si crece, partir en 2 PRs: éxitos y errores / confirmaciones. Tests: que cada acción muestre su resultado o su error en el sitio |
| **T29p — pulido general** | Mensajes en filas sin borde con contador numérico de no leídos (en privados sigue el `@usuaria`). Tarjetas de sugerencias de Inicio más compactas (≈ 140 px de ancho, avatar 64 px: que se vean 2,5 por fila). Ajustes: título propio en cada subpantalla («Editar perfil»…), «Cerrar sesión» en texto rojo sin caja (sin confirmar, T29s) y nueva subpantalla «Seguridad» con «Cerrar sesión en todos los dispositivos» (ahí irán contraseña y borrar cuenta, T24). Textos en femenino en toda la app («Seguidoras», «Nombre de usuaria»…). Quitar el desplazamiento sobrante de ~80 px en las páginas con `PageHeader` | Puede partirse en 2 PRs si crece. **PR 1 (listas, textos femeninos, layout)**: Mensajes, tarjetas, textos y `padding` sobrante. **PR 2 (Ajustes)**: títulos propios, «Cerrar sesión» y «Seguridad» |

**Fuera de esta ronda (decidido, en otras tareas):**
- Perfil con publicaciones: pestaña **Publicaciones** primera y en **lista** → T22b.
- Buscar sin texto: **se queda en blanco hasta T22c** (Yago).
- Recomendaciones con puntuación → [T32](T32-recommendations.md). Comunidades verificadas → [T33](T33-verified-communities.md).
- Barra inferior: **sin etiquetas** (Yago).

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
