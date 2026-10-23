# T29 — Sistema de diseño y pulido de la interfaz

- **Estado:** pendiente (T29a hecha en esta PR; sigue pendiente por T29b+)
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

## Objetivo
Yago da libertad para mejorar la interfaz (D-015). Antes de construir muchas pantallas nuevas (eventos, notificaciones, moderación), conviene tener una base visual coherente para que todo parezca la misma app.

## Enfoque
- **T29a**: auditoría de `src/styles/global.css` y los CSS por componente → tokens en variables CSS (colores, tipografía, espaciado, radios, sombras), modo claro y oscuro si encaja, sin cambiar la identidad (logo y nombre). Componentes base en `src/shared/` (Button, Input, Card, Avatar, Modal, Toast, EmptyState, Skeleton) reutilizando los existentes.
- **T29b…n**: una PR por pantalla aplicando la base y mejorando la UX (estados vacíos, cargando y error, feedback de acciones, accesibilidad: contraste, foco y tamaños táctiles). Orden sugerido: Chat → Comunidades → Perfil → Ajustes → Acceso.
- Antes de cada PR visual: capturas antes/después en móvil (360 px) y escritorio, incluidas en la descripción.
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
