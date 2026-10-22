# BUG-056 — Si falla el refresco de la lista tras crear un grupo, se muestra error y reintentar crea un grupo duplicado

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** frontend/chat
- **Tarea que lo resuelve:** T12 (gestión de grupos) o `fix/` propia
- **Detectado:** 2026-10-22, bug hunt de frontend-chat (Claude)

## Dónde
`frontend/src/features/messages/components/conversations.provider.jsx:293-312` (`createGroupConversation`: `await refreshChats()` sin `try/catch` tras el `POST /group` ya realizado). El error lo captura `GroupCreatorPanel.jsx:248-250`.

## Qué pasa
El grupo ya existe en el servidor cuando se llama a `refreshChats()`. Si ese `GET /api/conversations` falla (timeout, red inestable), la promesa se rechaza y el panel muestra "Error obteniendo conversaciones" / "No se pudo crear el grupo". La usuaria pulsa "Crear grupo" otra vez: el backend crea otro grupo con los mismos miembros (no hay unicidad) y las participantes reciben dos grupos idénticos.

## Cómo reproducir
Mockear `getMyConversationsRequest` con rechazo tras un `createGroupConversationRequest` correcto y comprobar que `createGroupConversation` rechaza.

## Arreglo propuesto
Envolver `refreshChats()` en `try/catch`: si falla, usar la rama de fallback que ya construye el chat con los datos enviados y entrar igualmente.
