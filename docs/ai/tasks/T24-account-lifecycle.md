# T24 — Verificación de email, reset de contraseña y borrar/exportar cuenta

- **Estado:** pendiente (spec refinada; se divide en T24a–T24d)
- **Fase:** 4 — Seguridad y cuentas
- **Depende de:** T06
- **Rama sugerida:** `feat/T24-account-lifecycle`
- **Cierra bugs:** BUG-025, BUG-042
- **Tamaño:** L
- **Spec:** refinada (2026-10-29). Código actual: `auth/` (`AuthService`, `RefreshSession`, `SessionController`), sin dependencia de correo ni variables SMTP. **Toca autenticación (D-012): se extiende `AuthService`, no se sustituye; cada PR pasa por `security-reviewer`.**

## Objetivo
El ciclo de vida completo de una cuenta, con lo que exige el RGPD: verificar el email, recuperar la contraseña, borrar la cuenta y exportar los datos.

## Enfoque
- Envío de email por SMTP configurable (`spring-boot-starter-mail`). Para la demo, un proveedor con plan gratuito (Brevo, Resend o similar; decidir con Yago). Plantillas HTML sencillas en español.
- Tokens de un solo uso con hash y caducidad (tabla `account_tokens`, `purpose` `VERIFY_EMAIL|RESET_PASSWORD|CHANGE_EMAIL`).
- `isVerified` empieza a tener efecto: sin verificar se puede entrar, pero con funciones limitadas (no crear eventos, comunidades ni mensajes privados) y un banner "Verifica tu email". Las cuentas de Google se marcan como verificadas.
- Cambiar el email exige verificar el nuevo.
- Borrar la cuenta: confirmación con contraseña, borrado o anonimización en cascada (los mensajes en grupos pasan a "Usuaria eliminada"), revocación de sesiones y borrado de medias.
- Exportar datos: JSON con perfil, posts, mensajes propios y eventos (descarga asíncrona o directa si es pequeño).

## Refinado (2026-10-29)

### División
| PR | Contenido | Depende de |
|---|---|---|
| **T24a** | Infra de correo (`MailSender` tras una interfaz, SMTP configurable, perfil `dev` con log en vez de envío), tabla `account_tokens`, verificación de email (BUG-025) | — |
| **T24b** | Reset de contraseña (olvidé la contraseña) y cambio de email con verificación del nuevo (BUG-042) | T24a |
| **T24c** | Borrar cuenta (confirmación con contraseña; Google: reautenticación reciente) y resolución de BUG-065/068 | T23c (destino de datos) |
| **T24d** | Exportar datos (JSON) | T22a |

### Decisiones propuestas (Yago confirma)
- Proveedor de correo: **SMTP genérico** configurable por variables (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM`); para la demo, Brevo o Resend en plan gratuito. Variables nuevas → `.env.example`, `docker-compose.yml`, README.
- Tokens: 32 bytes aleatorios, se guarda solo el SHA-256; un solo uso; caducidad 24 h (verificación) y 30 min (reset); un token vivo por (usuaria, propósito): pedir otro invalida el anterior. Enlace a `/verify-email?token=…` y `/reset-password?token=…` (el front los consume por POST; el token no se loguea, BUG-040).
- Respuestas uniformes anti-enumeración: «olvidé la contraseña» siempre responde 202; límite por IP y por email (429 + `Retry-After`).
- Sin verificar: se puede entrar y usar lectura, chat con quien ya te sigue y apuntarse a quedadas; **no** crear comunidades ni quedadas, ni abrir DMs nuevos, ni publicar (T22). Una única comprobación central (`VerifiedGuard`), no repartida por servicios.
- Cuentas de Google: `isVerified = true` al crearlas (ya comprobamos `email_verified`, BUG-024).
- Reset de contraseña revoca todas las `RefreshSession` (T06) y avisa por correo del cambio. Cuentas solo-Google no tienen contraseña: «olvidé» responde igual pero no envía enlace de reset (envía aviso de «entra con Google»).
- Borrado: anonimización en lugar de borrado físico donde hay contenido ajeno (mensajes de grupo → «Usuaria eliminada»); borrado físico de perfil, follows, bloqueos, notificaciones, medias y sesiones. Periodo de gracia de 14 días con cuenta desactivada y posibilidad de cancelar (propuesta; decidir con Yago).
- Exportación: ZIP/JSON generado bajo demanda, límite de una por día, enlace enviado por correo si tarda (asíncrono); solo datos propios (no mensajes de otras).

### Frontend
- Banner «Verifica tu email» con «Reenviar» (con cooldown), páginas `/verify-email`, `/forgot-password`, `/reset-password`, ajustes: «Cambiar email», «Descargar mis datos», «Eliminar cuenta» (ya hay entrada en Ajustes: comprobar que no sea una ruta muerta, ver #121). Textos en español, patrón de formularios de `DESIGN.md`.

## Criterios de aceptación
- [ ] Un token usado, caducado o sustituido falla con error uniforme; la BD no guarda tokens en claro.
- [ ] El flujo de reset no permite enumerar emails y revoca las sesiones.
- [ ] Las acciones limitadas fallan con un error claro («Verifica tu email») y el front lo muestra.
- [ ] Tras borrar la cuenta no queda dato personal en tablas propias; el contenido ajeno conserva sentido.
- [ ] La exportación solo contiene datos de quien la pide.

## Tests requeridos
- Integración de los flujos completos con `MailSender` falso; caducidad y reutilización; rate limits; guardia de verificación en cada endpoint limitado; borrado con otras filas dependientes (BUG-065/068); exportación sin datos ajenos.

## Preguntas para Yago
- ¿Proveedor de correo y dominio remitente para la demo?
- ¿Periodo de gracia de 14 días al borrar la cuenta, o borrado inmediato?
- ¿Qué se limita exactamente a cuentas sin verificar (propuesta arriba)?
