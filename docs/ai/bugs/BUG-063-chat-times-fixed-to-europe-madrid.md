# BUG-063 — Las horas del chat se formatean siempre en `Europe/Madrid` y con `es-ES`

- **Estado:** resuelto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** frontend/messages (D-019: nada de supuestos solo-España)
- **Tarea que lo resuelve:** ninguna (PR `fix(front)` pequeña)
- **Detectado:** 2026-10-23, al implementar la PR de zona horaria de eventos (Claude)

## Dónde
- `frontend/src/pages/messages/Messages.jsx` (~línea 87)
- `frontend/src/pages/messages/components/ChatPanel.jsx` (~línea 84)

## Qué pasa
Ambos fijan `timeZone: "Europe/Madrid"` al formatear la hora de los mensajes. Una usuaria fuera de la zona horaria de Madrid ve las horas desplazadas. Contradice D-019 (punto 6).

## Cómo reproducir
Poner la zona horaria del dispositivo en `America/Bogota` y abrir un chat: las horas siguen en hora de Madrid.

## Arreglo propuesto
Quitar `timeZone` (usar la del navegador) y pasar el locale del navegador a `Intl.DateTimeFormat`; test del formateo con una zona distinta.
