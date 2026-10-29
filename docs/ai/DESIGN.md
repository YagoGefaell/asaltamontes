# DESIGN.md — Guía de diseño de Asaltamontes Female

**Lectura obligatoria antes de tocar cualquier pantalla** (`frontend/src/pages`, `features/*/components`, `shared/ui`, CSS). Complementa a T29 (tokens y componentes) y a D-015 (libertad para mejorar la UI). Antes de abrir una PR con cambios visibles: **`/ui-review`**.

## 1. Qué app estamos haciendo

Una **red social para mujeres que corren por montaña**, en el móvil. La referencia general es **Instagram**: contenido a pantalla completa, interfaz mínima, un único buscador, perfil con cuadrícula, navegación inferior con 5 destinos. Lo que nos diferencia son las **quedadas** (eventos para correr juntas) y las **comunidades**: son lo que tiene que destacar, lo que no tiene Instagram.

Principios, por orden:
1. **Móvil primero, de verdad.** Se diseña a 360–390 px de ancho y se comprueba con capturas. El escritorio solo tiene que verse bien (columna central de 600 px como máximo), no aprovechar el espacio.
2. **El contenido manda.** Personas, fotos, quedadas. Controles, filtros y cabeceras ocupan lo mínimo: **antes del primer resultado no puede haber más de ~20 % de la pantalla** (unos 170 px a 390×844, sin contar la barra inferior).
3. **Coherencia antes que creatividad.** Si no sabes cómo hacer algo, copia el patrón de la pantalla más parecida que ya exista (§6). Nunca inventes un estilo nuevo de formulario, tarjeta o botón.
4. **Una acción principal por pantalla**, al alcance del pulgar.
5. **Cercana y en femenino.** Textos cortos, tuteo, **siempre en femenino** ("Bienvenida", "Te has unido", "Seguidoras", "Usuarias", "Nombre de usuaria", "Eres la organizadora"; Yago, 2026-10-29), sin jerga técnica ("Error 403" → "No tienes acceso a esta comunidad").

## 2. Identidad

- **Logo**: `frontend/src/assets/logo01.png` (cabra naranja sobre sol amarillo y montañas turquesa). No se cambia ni se redibuja (D-015). Favicon: `frontend/public/favicon.png`.
- **Instagram de la marca**: https://www.instagram.com/asaltamontes_female (referencia de tono y fotografía: deporte real, grupos de mujeres, montaña). Si Yago añade capturas de referencia, irán en `docs/ai/design/`.
- **Colores** (definidos en `frontend/src/styles/global.css`; nunca hex sueltos, `npm run lint` lo comprueba):

| Token | Valor | Uso |
|---|---|---|
| `--primary` | `#178B9F` turquesa (montañas) | **Color principal.** Acción principal, enlaces, pestaña activa, foco |
| `--secondary` | `#FFA61A` naranja (cabra) | **Color secundario.** Contadores de no leídos (cabecera de Inicio), bloque de fecha de las tarjetas de quedada. Siempre como fondo con el texto en `--on-secondary` |
| `--accent` | `#FFDA58` amarillo (sol) | **Tercer color, el de menos uso.** Anillo de "quedada hoy" y destacados puntuales. Nunca texto amarillo sobre blanco (texto encima: `--on-accent`) |
| `--background` / `--surface` | gris muy claro / blanco | Fondo de página / tarjetas, hojas y barras |
| `--text-color` / `--text-muted` | casi negro / gris | Texto principal / secundario. `--text-soft` solo para texto grande o deshabilitado (contraste) |
| `--error`, `--success`, `--warning`, `--info` | semánticos | Solo para estados, nunca decorativos |

  - Jerarquía de la marca (Yago, 2026-10-28): **turquesa → naranja → amarillo**, que coincide con los nombres de los tokens (`--primary`, `--secondary`, `--accent`).
  - Iconos de interfaz (cabecera y barra inferior) en gris medio (`--text-muted`), todos iguales, nunca en turquesa ni en negro; los títulos de la cabecera, en `--text-color`. El turquesa en un icono indica estado activo (barra inferior) o que es un botón principal.
  - Proporción orientativa: 85 % neutros, 10 % turquesa, ~4 % naranja, ~1 % amarillo. Si una pantalla parece "toda turquesa", sobra color (p. ej. avatares de iniciales grandes y botones rellenos repetidos).
  - Texto sobre `--primary` → `--on-primary`. Modo oscuro por `prefers-color-scheme`: todo con tokens y se revisa en la captura oscura.
- **Tipografía**: Inter / system-ui. Escala de `tokens.css` (`--text-xs` … `--text-2xl`). Títulos de pantalla `--text-xl` o `--text-2xl` en negrita; cuerpo `--text-base`; metadatos `--text-sm` en `--text-muted`. Nada de MAYÚSCULAS en botones o etiquetas (hoy "GUARDAR CAMBIOS", "PRINCIPIANTE": a corregir).
- **Iconos**: `react-icons/fa` (como `NavBar`). No mezclar con emojis como iconos de interfaz (el feed de `Home` usa 🤍💬📤: a sustituir).
- **Espaciado y radios**: solo tokens (`--space-*`, `--radius-sm|md|lg|pill`). Margen lateral de página `--space-4` (16 px).

## 3. Estructura y navegación

- **Barra inferior**: máximo **5 destinos** (como Instagram). Antes tenía 7 iconos; desde T29g (aprobada por Yago): **Inicio · Buscar · Quedadas · Comunidades · Perfil**, con **Notificaciones y Mensajes como iconos en la cabecera de Inicio** (arriba a la derecha, con su contador), igual que Instagram.
- **Cabecera de pantalla**: una línea, 56 px como mucho: título (o logo en Inicio) a la izquierda, 1–2 iconos de acción a la derecha. En pantallas de detalle: flecha atrás + título. Nada de botones grandes rellenos en la cabecera ("Crear quedada" → icono `+` o botón flotante).
- **Acción de crear** (Yago, 2026-10-29): **siempre un `+` arriba a la derecha de la cabecera**, nunca botón flotante ni botón ancho. En Inicio, el `+` abre una hoja inferior con **Publicación · Quedada · Comunidad**; en Quedadas y Comunidades, el `+` lleva directo a crear eso. Toda creación es una **pantalla aparte** (`/events/new`, `/communities/new`, `/posts/new`), nunca un panel dentro de la lista.
- **Sin atajos de búsqueda** en las pestañas: buscar se hace solo desde la pestaña Buscar.
- **Barra inferior solo con iconos**, sin etiquetas (Yago, 2026-10-29).
- **Zona segura**: la barra inferior y los botones fijos respetan `env(safe-area-inset-bottom)`; el contenido deja hueco para la barra (no queda tapado el último elemento).

## 4. Patrones obligatorios

### Buscar
- **Un único buscador** en `/search`, como Instagram: un campo arriba y, al escribir, pestañas **Todo · Personas · Comunidades · Quedadas** (pestañas de texto, deslizables). "Todo" muestra unas pocas de cada tipo con "Ver todas". Sin texto: búsquedas recientes y sugerencias (comunidades cercanas, próximas quedadas), no una lista de todas las usuarias.
- Las pantallas de Comunidades y Quedadas **no tienen buscadores propios ni lupa**: se busca desde la pestaña Buscar (Yago, 2026-10-29).

### Filtros
- **Máximo una fila de chips deslizable horizontalmente** (altura ~40 px). Los chips son los filtros de uso diario (p. ej. "Hoy", "Este finde", "Cerca de mí", "Mis quedadas").
- Todo lo demás (tipo, nivel, ciudad, provincia, distancia…) va en una **hoja inferior** (bottom sheet) que se abre con un chip "Filtros" que muestra cuántos hay activos ("Filtros · 2"). La hoja tiene "Aplicar" y "Quitar filtros".
- Prohibido: `<select>` o campos de texto sueltos como filtros visibles en la pantalla, varias filas de controles, campos de ciudad/provincia separados. Si un filtro casi nadie lo usa, se quita.
- "Lista | Mapa" no es un control segmentado a todo el ancho: es un icono en la cabecera (mapa ↔ lista), junto al `+`.

### Listas
- **Personas** y **resultados**: filas (`UserCard`: avatar 44–48 px, nombre, @usuario, una acción a la derecha). No tarjetas en cuadrícula.
- **Comunidades**: filas o tarjetas horizontales compactas (imagen 56 px, nombre, ciudad · miembros, botón Unirse pequeño). Una cuadrícula de 2 columnas con avatar enorme muestra 4 comunidades por pantalla: no.
- **Conversaciones** (Mensajes): filas sin borde, como Comunidades; contador de no leídos (número) en lugar de un punto; en privados, el `@usuaria`.
- **Quedadas**: tarjeta con la **fecha bien visible** (bloque día/mes en naranja), título, lugar, nivel y distancia/desnivel en una línea de metadatos, avatares de asistentes apilados y "Apuntarme". Es la tarjeta más característica de la app: cuidarla.
- Carga: `Skeleton` con la forma real de la fila, nunca un spinner a pantalla completa. Paginación con scroll infinito o "Cargar más" (`Button` `ghost`).
- Vacío: `EmptyState` con una frase y **una acción** ("Crear la primera quedada"). Error: mensaje + "Reintentar".

### Perfil (propio y ajeno)
- Como Instagram: cabecera compacta (avatar 80–96 px a la izquierda, contadores a la derecha: Quedadas · Seguidoras · Siguiendo), nombre, bio, nivel como etiqueta discreta, botón(es) de acción ("Editar perfil" / "Seguir" + "Mensaje").
- Debajo, **pestañas de contenido** (p. ej. Quedadas · Comunidades; más adelante Publicaciones). Nunca textos de relleno ("Aquí podrías agregar logros…").

### Perfil de comunidad
- Mismo esqueleto que el perfil de usuaria: avatar a la izquierda, contadores (Miembros · Quedadas), descripción, ciudad, botones con el **mismo estilo** que Seguir/Mensaje (Unirse/Miembro + Chat).
- Debajo, pestañas **Publicaciones · Quedadas · Miembros** (Publicaciones cuando exista T22). Miembros en filas (`UserCard`) con buscador si son muchas, nunca la cuadrícula completa de avatares en la portada.

### Detalle de quedada
- Cabecera con el título de la quedada (no «Quedada»). Arriba, foto de portada o, si no hay, un fondo con el color de quedadas.
- **Un solo botón principal** (Apuntarme / Ya vas). Debajo, una fila de iconos con texto: Chat · Calendario · Compartir · Cómo llegar. «Eres la organizadora» es una etiqueta junto al título, nunca un botón deshabilitado.
- Etiquetas (tipo, nivel, km · m+, plazas) en una sola línea de metadatos o una fila deslizable, sin saltar a una segunda línea.

### Notificaciones
- Como Instagram: **al entrar, todas cuentan como vistas** (el contador de Inicio se limpia); durante esa visita las nuevas siguen resaltadas. Sin botón «marcar todas como leídas».
- Las filas con acción la llevan a la derecha: «Seguir también» / «Siguiendo» en «te ha empezado a seguir»; Aceptar / Rechazar en solicitudes. El resto de la fila abre el destino.

### Chat
- Burbujas: las mías a la derecha en el color principal; las de otras a la izquierda.
- En grupos, los mensajes seguidos de la misma autora (< 5 min, sin separador de día ni mensaje del sistema por medio) forman un bloque con un **avatar pequeño a la izquierda de la primera burbuja, sin nombre ni @**. Al tocar el avatar se abre el perfil. Sin etiquetas de rol en el chat.
- Enviar: botón redondo con la flecha de enviar, apagado sin texto. El campo crece con el texto.
- Cabecera: avatar + nombre; debajo, `@usuaria` (privado) o los participantes (grupo). Tocar la cabecera abre el perfil o la info. Nunca estados falsos («En línea» sin presencia real).
- Info del grupo como WhatsApp: foto y nombre centrados y editables en el sitio, ajustes en filas, participantes con acciones en una hoja inferior al tocar la fila.

### Formularios
**El estilo de referencia es el original de Yago**: `LoginForm`, `RegisterForm` y `EditProfile`, construidos con `shared/forms/Input` y `shared/forms/Button`.
- Siempre `Input` (también `as="select"` y `as="textarea"`), con `label` visible encima del campo y el error debajo (`FormError`, ya incluido en `Input`). Nunca `<input>`/`<select>` con clases propias por pantalla (`community-creator-*`, `moderation-input`…).
- Una columna en móvil. Como mucho dos campos cortos en la misma fila (fecha + hora); nunca tres.
- Botón principal `Button` `primary` a todo el ancho al final del formulario (o fijo abajo si el formulario es largo). Un solo botón principal; el resto `secondary`/`ghost`.
- Teclados correctos: `type="email"`, `inputMode="numeric|decimal"`, `autoComplete`. Tamaño de letra de los campos ≥ 16 px (si no, iOS hace zoom).
- Formularios largos (crear quedada): secciones con un título corto (`SectionCard` o `fieldset` con `legend`) y, si hace falta, pasos. Los opcionales, plegados en "Más opciones".
- Validación al salir del campo y al enviar, con los mismos mensajes que el backend (`config/constants.js`).

### Ajustes
- `SettingsLayout` + `SectionCard` + filas con `ToggleSwitch` o con chevron que llevan a la subpantalla. Mismo patrón en ajustes de usuaria y de comunidad.
- La subpantalla lleva su propio título («Editar perfil», «Privacidad»…), no «Ajustes».
- «Cerrar sesión» al final, en **texto rojo sin caja ni fondo**. Lo delicado de la cuenta (cerrar sesión en todos los dispositivos, cambiar contraseña, borrar cuenta) va en una subpantalla **Seguridad**.

### Diálogos y avisos
**Ningún pop-up intrusivo, en ninguna parte** (Yago, 2026-10-29): ni avisos flotantes (`Toast`), ni ventanas en medio de la pantalla, ni `alert()`/`confirm()`.
- **Éxito: sin aviso.** El resultado lo dice solo. Al crear algo (comunidad, quedada, grupo, publicación), entras directamente en lo creado. Al guardar, vuelves a la pantalla anterior con el cambio a la vista. Los controles cambian ellos mismos («Seguir» → «Siguiendo», el interruptor se mueve, la foto nueva aparece).
- **Errores: en el sitio**, junto a lo que falló. En formularios, `FormError` encima del botón o el error del campo. En listas y botones sueltos, un texto rojo pequeño junto al control, que desaparece al reintentar. Sin conexión: el aviso inline que ya tiene el chat.
- **Confirmar solo lo que no se puede deshacer** (borrar comunidad, transferir la propiedad, expulsar, salir de un grupo sin poder volver, borrar la cuenta). Se pregunta en una **hoja inferior** (`BottomSheet`), nunca en una ventana centrada. Lo reversible se hace al momento, sin preguntar: silenciar, bloquear o desbloquear, dejar de seguir, cerrar sesión.
- Menús de "más opciones" (⋯): hoja inferior con la lista de acciones; la destructiva en rojo y al final.

### Táctil y accesibilidad
- Objetivo táctil ≥ 44×44 px (`--touch-min`), también iconos.
- Nada que dependa de `hover`. Foco visible (`:focus-visible`). Iconos sin texto con `aria-label`.
- Contraste AA: texto pequeño nunca en `--text-soft` ni sobre `--primary-soft`.

## 5. Componentes disponibles (`frontend/src/shared/`)

Antes de crear uno, busca aquí. Si falta algo genérico (p. ej. `BottomSheet`, `Tabs`, `Chip`, `PageHeader`, `Fab`), créalo en `shared/ui/` con test, en su propia PR o en la primera que lo necesite, y úsalo en todas partes.

| Componente | Para qué |
|---|---|
| `forms/Button` | Toda acción. Variantes `primary`, `secondary`, `danger`, `ghost`, `outline`; `loading` |
| `forms/Input` | Todo campo (`as="select"`, `as="textarea"`), con `label` y `error` |
| `forms/FormError` | Error de formulario general |
| `ui/Avatar` | Foto o iniciales; tamaños |
| `ui/UserCard` | Fila de persona con botón de seguir |
| `ui/SectionCard` | Bloque con título en ajustes y formularios |
| `ui/ToggleSwitch` | Interruptor en ajustes |
| `ui/EmptyState`, `ui/Skeleton` | Vacío y carga |
| `ui/BottomSheet` (confirmación) | Confirmar lo irreversible (ver «Diálogos y avisos»). `Modal`, `ConfirmModal` y `Toast` se retiran en T29s: no usarlos en pantallas nuevas |
| `ui/PageHeader`, `ui/Tabs`, `ui/Chip` + `ChipRow`, `ui/BottomSheet`, `ui/Fab` | Cabecera de pantalla, pestañas, fila de chips, hoja inferior y botón flotante (T29c) |
| `ui/ImageUploader`, `ui/AvatarUploader` | Subida de imágenes |
| `ui/NavBar`, `layouts/MainLayout`, `layouts/SettingsLayout` | Estructura |

## 6. Si no sabes por dónde tirar

1. ¿Existe ya una pantalla parecida? Copia su estructura y sus componentes. Orden de preferencia: pantallas originales de Yago (acceso, perfil, editar perfil) → chat (T29b-chat) → el resto.
2. ¿Qué haría Instagram? (Buscar, perfil, listas, menús ⋯, hojas inferiores.) Para lo propio (quedadas): apps deportivas como Strava o Komoot.
3. Elige lo **más simple**: menos controles, menos colores, menos texto.
4. Si la decisión cambia la estructura (navegación, qué hay en cada pestaña), propónla en la PR y no la impongas.

## 7. Antipatrones vistos en la app (no repetir)

Detectados en las capturas del 2026-10-27 (390×844):
- **Quedadas**: cabecera con botón ancho "Crear quedada", control Lista/Mapa a todo el ancho, 5 chips, 2 `<select>` y un campo "Ciudad": ~35 % de la pantalla antes del primer resultado.
- **Comunidades**: tres buscadores (nombre, ciudad, provincia) y tarjetas en cuadrícula con avatar enorme: solo caben 4 comunidades.
- **Buscar**: solo busca personas y, vacío, lista a todas las usuarias.
- **Formularios distintos para lo mismo**: crear/editar comunidad con `<input>` propios (`CommunityCreatorPanel`, `CommunityEditForm`) frente a `Input`/`Button` en perfil y quedadas; el formulario de quedada pone 3 campos por fila.
- **Perfil** con texto de relleno y sin contenido; **Inicio** con publicaciones e historias de mentira ("User 1", "120 likes" en inglés) y emojis como iconos.
- Barra inferior con 7 iconos; mayúsculas en botones y etiquetas.
- `window.confirm()` para cerrar sesión, desbloquear y dejar de seguir (`SettingsMenu`, `PrivacySettings`, `Profile`) en lugar de `Modal`.

Detectados en las capturas del 2026-10-29 (auditoría de la tercera ronda, T29j–T29p):
- Dos patrones de crear: `+` en la cabecera que abre un panel en Comunidades y botón flotante que abre otra pantalla en Quedadas.
- Detalle de quedada con tres botones apilados a todo el ancho, uno de ellos deshabilitado («Eres la organizadora»).
- Perfil de comunidad con todos los miembros en cuadrícula de avatares grandes en la portada.
- Mensajes en tarjetas con borde mientras el resto de listas son filas.
- Tarjetas de sugerencias de Inicio tan grandes que dos filas llenan la pantalla.
- Subtítulo de relleno «Todavía no hay mensajes» en cada comunidad; «Seguidores» en masculino; subpantalla de perfil titulada «Ajustes»; cerrar sesión en cajas rosas.
- Páginas que se pueden desplazar ~80 px sin contenido debajo.
- Avisos flotantes de éxito en 15 sitios («Comunidad creada», «Cambios guardados», «Imagen subida»…), unos 20 errores también flotantes y ventanas centradas para confirmar acciones reversibles.
- Chat: `@usuaria` encima de cada mensaje en los grupos, botón «Enviar» de texto, «En linea» fijo en los privados (no hay presencia), «Toca para ver el grupo» junto a un icono ⓘ que hace lo mismo. Info del grupo con el nombre repetido (título + campo + «Guardar nombre») y «Hacer admin» / «Expulsar» en cada fila, partiendo los nombres letra a letra.

## 8. Verificación

- **`/ui-review`** (`.claude/skills/ui-review/SKILL.md`): capturas a 390 px y 360 px, claro y oscuro, con `npm run shots`, revisión contra esta guía y capturas antes/después en la PR.
- Las PRs de UI siguen D-015: propias (`feat(ui)` / `refactor(front)`), sin mezclar con cambios funcionales.
