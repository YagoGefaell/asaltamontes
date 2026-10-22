# BUG-058 — La búsqueda de participantes de un grupo no codifica la consulta en la URL

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** frontend/users (usado por el chat)
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-22, bug hunt de frontend-chat (Claude)

## Dónde
`frontend/src/features/users/services/users.service.js:28` (`/api/users/search?query=${query}`), llamado desde `GroupCreatorPanel.jsx:169`.

## Qué pasa
A diferencia de `getAllUsersRequest` (que usa `encodeURIComponent`), `getUsersByUsername` concatena el texto tal cual. Un `&`, `#`, `+` o `%` en lo escrito altera la query string: `ana&size=1000` inyecta parámetros, `ana#x` recorta la búsqueda y un `%` suelto produce una URL mal formada (400 y "Error buscando participantes"). Además solo se usa la primera página (`.content`) sin avisar de que hay más resultados.

## Cómo reproducir
En "Crear grupo", escribir `ma&size=1` o `100%` en "Buscar participantes" y observar la petición enviada.

## Arreglo propuesto
`?query=${encodeURIComponent(query.trim())}` y test en `users.service`. (Relacionado con BUG-043, que trata el lado servidor de los comodines `LIKE`.)
