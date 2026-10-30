# T24 — Verificación de email, reset de contraseña y borrar/exportar cuenta

- **Estado:** en curso — T24a (backend) y T24b (backend) hechos en sus PRs; pendiente T24a2 (front), T24b2 (front), T24c y T24d
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
| **T24a** (hecho, backend) | Infra de correo (`MailSender` tras una interfaz, SMTP configurable, perfil `dev` con log en vez de envío), tabla `account_tokens`, verificación de email (BUG-025) | — |
| **T24b** (hecho, backend) | Reset de contraseña (olvidé la contraseña) y cambio de email con verificación del nuevo (BUG-042) | T24a |
| **T24b2** | Front de T24b: páginas públicas `/forgot-password` (+ enlace en el login), `/reset-password?token=` y `/confirm-email-change?token=`; en Ajustes, «Cambiar email» (nuevo email + contraseña actual; aviso «te hemos enviado un enlace al nuevo email»). Errores: 400 enlace inválido, 403 contraseña incorrecta, 409 en uso/Google, 429 con `Retry-After`. Con `/ui-review` | T24b |
| **T24c** | Borrar cuenta (confirmación con contraseña; Google: reautenticación reciente) y resolución de BUG-065/068 | T23c (destino de datos) |
| **T24d** | Exportar datos (JSON) | T22a |

| **T24a2** | Front de la verificación: banner «Verifica tu email» con «Reenviar» (cooldown, `POST /api/users/me/verification-email`), página pública `/verify-email?token=` (`POST /auth/verify-email`) y mensaje del error `403 code=EMAIL_NOT_VERIFIED`. Con `/ui-review` | T24a |

### Como quedó T24a (2026-10-30)
- `infrastructure/mail`: `MailSender` (interfaz), `SmtpMailSender` (asíncrono, activo con `MAIL_HOST`), `LoggingMailSender` (por defecto). Variables `MAIL_HOST/PORT/USER/PASSWORD/FROM`, `MAIL_STARTTLS`, `MAIL_SSL`, `APP_BASE_URL`, `VERIFICATION_ENFORCE`; README: alta de Resend.
- Migración `V21__account_tokens.sql` (numerada tras la `V20` de #157: **mergear #157 antes**). Un token vivo por (usuaria, propósito); se guarda solo el SHA-256; se borra al usarse o sustituirse (`DELETE ... RETURNING`, atómico).
- `POST /auth/verify-email` (público, límite por IP, 400 uniforme) y `POST /api/users/me/verification-email` (reenvío: 1/min y 5/h por usuaria, 409 si ya está verificada). El registro envía el enlace.
- `VerifiedGuard` (una sola comprobación) aplicado a crear comunidad, evento, publicación, grupo y DM **nuevo**; apagado por defecto (`asaltamontes.verification.enforce=false`). Error `403` con `code=EMAIL_NOT_VERIFIED`.
- Google: las cuentas nuevas nacen verificadas. `UserAccount.isEnabled()` ya no depende de `isVerified` (BUG-025).
- Deuda registrada: BUG-083 (pre-registro con el email ajeno).

### Como quedó T24b (2026-10-30)
- `POST /auth/forgot-password` (siempre 202; límites: 5/min por IP y 3/h por dirección tecleada, exista o no), `POST /auth/reset-password` (valida la contraseña antes de gastar el token; revoca todas las `RefreshSession`, marca el email como verificado, cancela un cambio de email pendiente y avisa por correo) y cuentas solo-Google reciben un aviso sin enlace.
- `POST /api/users/me/email-change` (contraseña actual; límite 5/h por usuaria contando los fallos) guarda el nuevo email como `payload` del token `CHANGE_EMAIL` (migración **V23**, columna nullable: mergear **después de #161**, que usa V22) y avisa al email anterior; `POST /auth/confirm-email-change` lo aplica, verifica y avisa de nuevo. Un reset o un cambio invalidan los enlaces pendientes del otro. `PATCH /api/users/me` ya no cambia el email (BUG-042).
- BUG-084: el log de correos sin SMTP ya no escribe el cuerpo salvo `MAIL_LOG_BODY=true`; tope de correos por dirección destino (3/h, 6/día).
- Los access tokens ya emitidos siguen valiendo hasta 15 min tras un reset (solo se revocan los refresh).

### Decisiones propuestas (Yago confirma)
- Proveedor de correo: **SMTP genérico** configurable por variables (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM`); para la demo, Brevo o Resend en plan gratuito. Variables nuevas → `.env.example`, `docker-compose.yml`, README.
- Tokens: 32 bytes aleatorios, se guarda solo el SHA-256; un solo uso; caducidad 24 h (verificación) y 30 min (reset); un token vivo por (usuaria, propósito): pedir otro invalida el anterior. Enlace a `/verify-email?token=…` y `/reset-password?token=…` (el front los consume por POST; el token no se loguea, BUG-040).
- Respuestas uniformes anti-enumeración: «olvidé la contraseña» siempre responde 202; límite por IP y por email (429 + `Retry-After`).
- Sin verificar: se puede entrar y usar lectura, chat con quien ya te sigue y apuntarse a quedadas; **no** crear comunidades ni quedadas, ni abrir DMs nuevos, ni publicar (T22). Una única comprobación central (`VerifiedGuard`), no repartida por servicios.
- Cuentas de Google: `isVerified = true` al crearlas (ya comprobamos `email_verified`, BUG-024).
- Reset de contraseña revoca todas las `RefreshSession` (T06) y avisa por correo del cambio. Cuentas solo-Google no tienen contraseña: «olvidé» responde igual pero no envía enlace de reset (envía aviso de «entra con Google»).
- Borrado: anonimización en lugar de borrado físico donde hay contenido ajeno (mensajes de grupo → «Usuaria eliminada»); borrado físico de perfil, follows, bloqueos, notificaciones, medias y sesiones, tras 14 días de gracia (confirmado).
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

## Decisiones de Yago (2026-10-29, D-025)
- **Correo:** Resend (plan gratuito) vía SMTP genérico, variables `MAIL_*`. Resend exige un dominio verificado y aún no lo hay: si `MAIL_HOST` está vacío, los correos **se escriben en el log** en lugar de enviarse (implementación `LoggingMailSender`, activa por defecto). El README documenta cómo dar de alta Resend (dominio, claves API, registros DNS, valores de `MAIL_*`).
- **Borrar la cuenta (T24c):** 14 días de gracia. La cuenta queda **desactivada y oculta** (no aparece en búsquedas, perfiles ni listados; sesiones revocadas) y, si vuelve a iniciar sesión dentro del plazo, se recupera. Pasado el plazo, un job hace el borrado definitivo (anonimización/borrado según la sección anterior).
- **Cuentas sin verificar:** se aplican los límites propuestos arriba (no crear comunidades ni quedadas, ni abrir DMs nuevos, ni publicar). **Debe poder apagarse por configuración** (p. ej. `app.verification.enforce=false`, por defecto apagado mientras no haya correo real) para que en la demo nadie quede bloqueada sin poder verificarse.
