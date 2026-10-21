# BUG-029 — La cookie de refresh se emite sin `Secure`

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/auth
- **Tarea que lo resuelve:** T03
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/common/cookies/CookieUtil.java` (`secure(false)` hardcodeado)

## Qué pasa
Cuando el servidor sirva HTTPS, la cookie seguirá pudiendo viajar por HTTP en claro. Tiene que ser configurable por entorno.

## Cómo reproducir
—

## Arreglo propuesto
Propiedad `asaltamontes.cookies.secure` (T03).
