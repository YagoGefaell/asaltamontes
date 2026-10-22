# BUG-057 — La imagen del grupo es una URL libre: fuga de IP a terceros y error de BD si pasa de 255 caracteres

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** probable (la fuga es inherente al diseño; el error de longitud hay que comprobarlo contra la BD)
- **Área:** frontend/chat + backend/message
- **Tarea que lo resuelve:** T17 (almacenamiento de media) para el diseño; validación de longitud en T12
- **Detectado:** 2026-10-22, bug hunt de frontend-chat (Claude)

## Dónde
- `frontend/src/pages/messages/components/GroupCreatorPanel.jsx:292-300` (`<input type="url">` sin `maxLength`).
- `frontend/src/pages/messages/components/ConversationsPanel.jsx:71-78` y `ChatPanel.jsx:494-500` (se pinta con `<img src>`).
- `backend/src/main/java/io/github/yagogefaell/asaltamontes/message/service/MessageService.java:241-245` y `db/migration/V1__baseline.sql:85-86` (`group_name`/`group_image` `varchar(255)`, sin validar longitud ni esquema).

## Qué pasa
Quien crea un grupo puede poner cualquier URL (`http://servidor-ajeno/pixel.png`). El navegador de cada miembra la descarga al abrir su lista de chats: el dueño de ese servidor obtiene IP, user-agent y hora de conexión de todas (tracking), y con `http://` en una página HTTPS el navegador la bloquea. Además, una URL de más de 255 caracteres (frecuente con parámetros) provoca un error de BD al insertar: la usuaria ve un error genérico de servidor sin saber por qué. El nombre del grupo tampoco se valida en el servidor (255 en BD, 80 solo en el front).

## Cómo reproducir
1. Crear un grupo con imagen `https://example.com/a.png?x=` + 300 caracteres: la petición falla con 5xx.
2. Crear un grupo con una URL a un servidor propio y observar en sus logs las peticiones de cada miembra.

## Arreglo propuesto
Corto plazo: validar en backend (`https` obligatorio, ≤255, nombre ≤80) con 400 y mensaje en español, y `maxLength` en el input. Largo plazo (T17): subida de imágenes a nuestro almacenamiento y dejar de aceptar URLs externas.
