# BUG-059 — Un mensaje nuevo arrastra la vista al final aunque se lean mensajes antiguos, y no hay fechas

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** frontend/chat
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-22, bug hunt de frontend-chat (Claude)

## Dónde
- `frontend/src/pages/messages/Messages.jsx:187-192` (efecto que llama a `scrollToBottom()` cada vez que cambia `newestMessageId`).
- `frontend/src/pages/messages/components/ChatPanel.jsx:444-466` y `Messages.jsx:68-83` (solo se formatea la hora).

## Qué pasa
Cualquier mensaje nuevo (de otra persona o del catch-up tras reconectar) hace `scrollIntoView` al final. Si la usuaria ha subido para leer o ha cargado páginas anteriores, la vista salta abajo y pierde su posición, justo en chats activos. Además, tanto la lista de chats como los marcadores del chat muestran solo `HH:mm`, sin fecha: un mensaje de la semana pasada se ve igual que uno de hoy y los marcadores no separan días.

## Cómo reproducir
Abrir un chat con historial, subir dos páginas y recibir un mensaje: la vista salta al final.

## Arreglo propuesto
Scroll automático solo si la usuaria estaba cerca del final (`scrollHeight - scrollTop - clientHeight < 80`) o el mensaje es propio; si no, mostrar un indicador "Mensajes nuevos". Añadir separadores de día ("Hoy", "Ayer", fecha) y fecha corta en la lista cuando el último mensaje no es de hoy.
