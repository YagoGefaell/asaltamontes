# BUG-022 — El contenido de los mensajes no se valida

- **Estado:** resuelto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/chat
- **Tarea que lo resuelve:** T09
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/message/dto/SendMessageCommandDto.java`, `MessageService.saveMessage()`

## Qué pasa
Se aceptan mensajes vacíos, nulos (NPE en `content.length()`) o de tamaño ilimitado (columna `TEXT`). Un cliente malicioso puede llenar la BD o colgar a las receptoras con mensajes de megas.

## Cómo reproducir
Enviar `content: null` o un texto de 5 MB.

## Arreglo propuesto
`@NotBlank @Size(max = 2000)` y validación en el servicio (T09).
