# BUG-021 — Se puede crear un chat privado consigo misma

- **Estado:** resuelto
- **Severidad:** baja
- **Confianza:** probable (verificar con test)
- **Área:** backend/chat
- **Tarea que lo resuelve:** T09
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/message/service/MessageService.java` → `createOrGetPrivateConversation()`

## Qué pasa
Con `recipient == current` se crean dos participantes iguales → viola `uk_conversation_user` → 409 genérico, o bien deja una conversación huérfana sin participantes si la excepción llega después del insert de la conversación.

## Cómo reproducir
`POST /api/conversations/private {username: <yo>}`.

## Arreglo propuesto
`BadRequestException` antes de crear nada (T09).
