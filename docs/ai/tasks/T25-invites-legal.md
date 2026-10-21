# T25 — Invitaciones para la demo y páginas legales

- **Estado:** pendiente
- **Fase:** 4 — Demo privada
- **Depende de:** T24
- **Rama sugerida:** `feat/T25-invites-legal`
- **Cierra bugs:** —
- **Tamaño:** M
- **Spec:** 🔍 alto nivel — **refinar antes de implementar** (completar con la plantilla `docs/ai/templates/TASK.md` mirando el código de ese momento).

## Objetivo
Controlar quién entra en la demo privada y tener lo mínimo legal para tratar datos personales en España/UE.

## Enfoque
- Códigos de invitación (`invite_codes`: código, creado por, usos máximos, usados, caduca) generados desde el panel de administración. Modo `REGISTRATION_MODE=INVITE_ONLY|OPEN` por configuración. Registro con Google también sujeto al código.
- Páginas: política de privacidad, términos de uso, normas de la comunidad y cookies (solo cookies técnicas → aviso informativo). **El texto lo valida Yago o la empresa**; Claude prepara un borrador marcado como tal.
- Checkbox de aceptación en el registro, guardando la versión aceptada y la fecha.
- **Copia externa de los backups (obligatoria antes de abrir la demo, Yago 2026-10-21):** hoy los backups de T07b viven en la misma microSD que la BD; mientras los datos sean desechables (D-014) basta. En cuanto haya datos de testers reales, copiar los dumps fuera de la tarjeta (recomendado: USB en la Pi, montado y con rotación propia) y probar una restauración desde esa copia.
- Enlace "Enviar feedback" en el menú (formulario → tabla `feedback` visible en administración) para recoger opiniones de las testers.
