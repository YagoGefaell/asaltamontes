# T16 — Eventos: frontend (lista, detalle con mapa, crear y editar)

- **Estado:** pendiente (T16a y T16b hechas en PRs apiladas; T16 sigue pendiente por T16c)
- **Fase:** 3 — Quedadas / eventos
- **Depende de:** T15, T29a
- **Rama sugerida:** `feat/T16-events-frontend` (dividida: `T16a` lista + detalle con mapa de solo lectura y filtros, **hecho**; `T16b` crear/editar + selector de punto en el mapa + sección "Próximas quedadas" en la comunidad, **hecho**; `T16c` vista de mapa de la lista por bbox, **bloqueada por BUG-062**)
- **Cierra bugs:** —
- **Tamaño:** L

## Objetivo
Que las usuarias descubran quedadas cerca y se apunten en dos toques desde el móvil.

## Alcance
- Nueva feature `src/features/events/{services,hooks,components}` y páginas `src/pages/events/`.
- Navegación: nueva pestaña "Quedadas" en `NavBar` (icono de montaña/calendario de `react-icons`).
- **Lista** `/events`: tarjetas (fecha grande, título, lugar, distancia/desnivel, nivel, plazas "7/12", estado propio). Filtros: fechas (hoy, este finde, próximos 30 días), tipo, nivel, ciudad, "mis quedadas". Scroll infinito. Vista alternativa en **mapa** (Leaflet + tiles de OpenStreetMap con atribución; ver `DECISIONS.md` D-005).
- **Detalle** `/events/:id`: datos, mapa con el punto de encuentro + "Cómo llegar" (enlace a Google/Apple Maps con lat/lng), organizadora, comunidad, asistentes (avatares + "ver todas"), botón principal con estados: Apuntarme · Estás dentro ✓ (salir) · En lista de espera (#3) · Completo (apuntarme a la lista) · Cancelado. Actualización optimista con rollback (reutilizar `useOptimisticToggle` si encaja).
- **Crear/editar** `/events/new`, `/events/:id/edit`: formulario con validación espejo de la API, selector de punto en el mapa (click/drag del marcador) + campo de texto, selector de comunidad (solo aquellas donde puede organizar), fecha y hora en hora local.
- Desde la página de comunidad: sección "Próximas quedadas" + botón "Crear quedada" para moderación.
- Fechas en español con `Intl.DateTimeFormat('es-ES', …)`; mostrar la zona del evento si difiere de la del navegador.
- Mobile first (la mayoría de uso será desde el móvil); comprobar a 360 px de ancho.

## Criterios de aceptación
- [ ] Flujo completo en móvil: ver lista → filtrar "este finde" → abrir → apuntarse → aparece en "mis quedadas".
- [ ] Evento lleno muestra lista de espera y la posición.
- [ ] Crear un evento con punto en el mapa y verlo en el detalle.
- [ ] Lint/build limpios; sin errores en consola.

## Tests requeridos
- Vitest + Testing Library: botón de inscripción (todos los estados), validación del formulario, formateo de fechas.

## Notas y riesgos
- Dependencias nuevas: `leaflet` + `react-leaflet` (versiones exactas). Respetar la política de uso de tiles de OSM (atribución visible, sin tráfico masivo); si el volumen crece, cambiar a MapTiler o a tiles propios.

## Registro T16a
- Hecho: servicios/hooks/componentes en `src/features/events`, páginas `/events` y `/events/:id`, pestaña "Quedadas" en `NavBar`, filtros (fechas, tipo, nivel, ciudad, "mis quedadas"), scroll infinito, inscripción con actualización optimista y rollback (`useEventAttendance`, no reutiliza `useOptimisticToggle` porque hay tres resultados: dentro / lista de espera / fuera), mapa Leaflet con carga perezosa en el detalle.
- Pasa a T16b: la **vista de mapa de la lista** (requiere consultar por bounding box y cargar Leaflet en la lista), el formulario crear/editar, el selector de punto y la sección "Próximas quedadas" en la comunidad.
- La posición en la lista de espera sale de `GET /api/events/{id}/attendees?status=WAITLIST` (el DTO del evento no la incluye).

## División T16b / T16c (2026-10-22)
- **T16b (hecha, apilada sobre T16a):** `/events/new` y `/events/:id/edit` (`EventEditor`), `EventForm` con validación espejo de la API (`utils/eventForm.js`), selector de punto con Leaflet (`LocationPicker`: toque + marcador arrastrable), fechas en la zona del evento, selector de comunidad con `GET /api/communities/me` filtrado por rol OWNER/MODERATOR (no hizo falta backend), botón "Editar" en el detalle (`canEdit`), "Crear quedada" en la lista y sección "Próximas quedadas" + "Crear quedada" (OWNER/MODERATOR) en la comunidad.
- **T16c (pendiente):** vista "Mapa" en `/events` con la API por bbox. `EventSummaryDto` no trae `latitude`/`longitude` (BUG-062): hay que añadirlos en backend antes. Después: `getEventsInBoundsRequest` (bbox de `moveend` con debounce, máx. 50 chinchetas, mismos filtros que la lista) y alternador Lista/Mapa.
- Huecos conocidos de la API para el editor: `PATCH` no permite vaciar un campo opcional ya guardado (null = sin cambios; solo la descripción se vacía con `""`), y la zona horaria se fija al crear (la del navegador) sin selector.
