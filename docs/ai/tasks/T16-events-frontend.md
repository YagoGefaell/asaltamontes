# T16 — Eventos: frontend (lista, detalle con mapa, crear y editar)

- **Estado:** pendiente
- **Fase:** 3 — Quedadas / eventos
- **Depende de:** T15
- **Rama sugerida:** `feat/T16-events-frontend` (dividir: `T16a` lista + detalle, `T16b` crear/editar + mapa selector)
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
