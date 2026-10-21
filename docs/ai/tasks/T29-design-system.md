# T29 — Sistema de diseño y pulido de la interfaz

- **Estado:** pendiente
- **Fase:** Transversal (UI)
- **Depende de:** T01 (tests de frontend)
- **Rama sugerida:** `refactor/T29a-design-tokens`, `feat/T29b-ui-polish-<pantalla>`…
- **Cierra bugs:** —
- **Tamaño:** L (varias PRs pequeñas, una por pantalla o área)
- **Spec:** 🔍 alto nivel — **refinar antes de implementar**.

## Objetivo
Yago da libertad para mejorar la interfaz (D-015). Antes de construir muchas pantallas nuevas (eventos, notificaciones, moderación), conviene tener una base visual coherente para que todo parezca la misma app.

## Enfoque
- **T29a**: auditoría de `src/styles/global.css` y los CSS por componente → tokens en variables CSS (colores, tipografía, espaciado, radios, sombras), modo claro y oscuro si encaja, sin cambiar la identidad (logo y nombre). Componentes base en `src/shared/` (Button, Input, Card, Avatar, Modal, Toast, EmptyState, Skeleton) reutilizando los existentes.
- **T29b…n**: una PR por pantalla aplicando la base y mejorando la UX (estados vacíos, cargando y error, feedback de acciones, accesibilidad: contraste, foco y tamaños táctiles). Orden sugerido: Chat → Comunidades → Perfil → Ajustes → Acceso.
- Antes de cada PR visual: capturas antes/después en móvil (360 px) y escritorio, incluidas en la descripción.
- Encaje en el roadmap: **T29a antes de T16** (frontend de eventos), para que las pantallas nuevas nazcan con la base. Las PRs T29b+ se intercalan cuando el límite WIP impide abrir PRs de backend, o después de cada fase.
