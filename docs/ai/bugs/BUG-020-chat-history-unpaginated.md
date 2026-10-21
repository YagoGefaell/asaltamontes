# BUG-020 — El historial de chat se descarga entero

- **Estado:** resuelto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/chat + frontend
- **Tarea que lo resuelve:** T10
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/message/repository/MessageRepository.java` → `findMessagesByConversationId`

## Qué pasa
Abrir un chat devuelve todos los mensajes. Con chats de grupo o comunidad activos (miles de mensajes) dispara el tiempo de respuesta, la memoria del backend y el renderizado en móvil.

## Cómo reproducir
—

## Arreglo propuesto
Paginación por cursor (T10).
