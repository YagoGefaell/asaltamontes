# BUG-016 — Contador de no leídos con lost updates y N escrituras por mensaje

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/chat
- **Tarea que lo resuelve:** T09
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/message/service/MessageService.java` → bucle sobre `participants` con `setUnreadCount(+1)` + `save`

## Qué pasa
Leer, sumar y escribir sin bloqueo: dos mensajes simultáneos al mismo chat pueden dejar `unreadCount` en +1 en lugar de +2. Además se hace un `UPDATE` por participante y mensaje (un grupo de 100 personas → 100 updates por mensaje). Lo mismo ocurre con `lastMessage`/`lastMessageAt` de la conversación.

## Cómo reproducir
IT con N hilos enviando a la vez al mismo chat y comprobación del contador.

## Arreglo propuesto
Un único `UPDATE … SET unread_count = unread_count + 1 WHERE conversation_id = ? AND user_id <> ?` (T09).
