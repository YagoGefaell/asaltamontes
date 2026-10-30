# BUG-084 — El registro puede usarse para enviar correo a terceros y el modo log expone tokens

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (revisión de seguridad de T24a)
- **Área:** backend/auth, infra
- **Tarea que lo resuelve:** T24b (antes de reutilizar `AccountTokenService` para `RESET_PASSWORD`)
- **Detectado:** 2026-10-30, security-reviewer sobre T24a

## Dónde
`infrastructure/mail/LoggingMailSender.java`, `auth/controller/AuthController.java` (register), `infrastructure/ratelimit/AuthRateLimitFilter.java`, config de nginx / `application-prod.properties`

## Qué pasa
1. Con `MAIL_HOST` vacío se registra el cuerpo del correo con el token en claro (D-025 lo acepta en el servidor privado, pero un `MAIL_HOST` vacío por error en prod vuelve a este modo en silencio). Con reset de contraseña sería toma de cuentas para quien lea los logs.
2. `POST /auth/register` envía un correo a cualquier dirección: solo lo frena 3 registros/min por IP. Con muchas IP se puede dañar la reputación del dominio de envío.
3. El límite por IP usa `getRemoteAddr` resuelto desde `X-Forwarded-For`; si nginx conserva la cabecera del cliente (`$proxy_add_x_forwarded_for`) se evita con una IP falsa.
4. Menores: `SmtpMailSender` registra el email del destinatario en los fallos; `VerifiedGuard` no cubre unirse a comunidades, comentar ni dar like (decisión de producto pendiente); dos `issue()` concurrentes pueden chocar con la restricción única y el reenvío falla en silencio.

## Cómo reproducir
Registrar cuentas con emails ajenos desde IPs distintas / enviando `X-Forwarded-For` aleatorio.

## Arreglo propuesto
Registrar el cuerpo solo con opt-in (`MAIL_LOG_BODY=true`) o enmascarar el token fuera de dev; tope diario global y por dirección destino de correos de verificación; nginx con `proxy_set_header X-Forwarded-For $remote_addr` e `internal-proxies` en Tomcat; captcha más adelante.
