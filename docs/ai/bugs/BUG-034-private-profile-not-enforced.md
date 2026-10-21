# BUG-034 — El perfil privado no se aplica en ningún endpoint

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/user
- **Tarea que lo resuelve:** T23
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/user/controller/UserFollowController.java`, `UserController.getUserByUsername`, `UserProfile.isPublicProfile`

## Qué pasa
Todos los perfiles se crean con `isPublicProfile=false`, pero cualquier usuaria puede ver el perfil completo, los seguidores y los seguidos de cualquier otra. La UI ofrece un ajuste de privacidad que no hace nada: hay que revisarlo antes de la demo.

## Cómo reproducir
Con un perfil privado, otra cuenta llama a `GET /api/users/followers/<username>` → 200 con la lista.

## Arreglo propuesto
Solicitudes de seguimiento y restricción de la visibilidad (T23).
