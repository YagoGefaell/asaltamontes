# T24 — Verificación de email, reset de contraseña y borrar/exportar cuenta

- **Estado:** pendiente
- **Fase:** 4 — Seguridad y cuentas
- **Depende de:** T06
- **Rama sugerida:** `feat/T24-account-lifecycle`
- **Cierra bugs:** BUG-025
- **Tamaño:** L
- **Spec:** 🔍 alto nivel — **refinar antes de implementar** (completar con la plantilla `docs/ai/templates/TASK.md` mirando el código de ese momento).

## Objetivo
El ciclo de vida completo de una cuenta, con lo que exige el RGPD: verificar el email, recuperar la contraseña, borrar la cuenta y exportar los datos.

## Enfoque
- Envío de email por SMTP configurable (`spring-boot-starter-mail`). Para la demo, un proveedor con plan gratuito (Brevo, Resend o similar; decidir con Yago). Plantillas HTML sencillas en español.
- Tokens de un solo uso con hash y caducidad (tabla `account_tokens`, `purpose` `VERIFY_EMAIL|RESET_PASSWORD|CHANGE_EMAIL`).
- `isVerified` empieza a tener efecto: sin verificar se puede entrar, pero con funciones limitadas (no crear eventos, comunidades ni mensajes privados) y un banner "Verifica tu email". Las cuentas de Google se marcan como verificadas.
- Cambiar el email exige verificar el nuevo.
- Borrar la cuenta: confirmación con contraseña, borrado o anonimización en cascada (los mensajes en grupos pasan a "Usuaria eliminada"), revocación de sesiones y borrado de medias.
- Exportar datos: JSON con perfil, posts, mensajes propios y eventos (descarga asíncrona o directa si es pequeño).
