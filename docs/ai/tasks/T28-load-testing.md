# T28 — Pruebas de carga con k6 y tuning

- **Estado:** pendiente
- **Fase:** 4 — Demo privada
- **Depende de:** T07, T10, T15
- **Rama sugerida:** `perf/T28-load-testing`
- **Cierra bugs:** —
- **Tamaño:** M
- **Spec:** 🔍 alto nivel — **refinar antes de implementar** (completar con la plantilla `docs/ai/templates/TASK.md` mirando el código de ese momento).

## Objetivo
Comprobar con datos que el servidor aguanta la demo y saber cuál es el límite antes de pensar en hosting.

## Enfoque
- Script de seed con datos sintéticos realistas (`scripts/seed/`): 5.000 usuarias, 100 comunidades, 2.000 eventos, 500.000 mensajes y 50.000 follows. **Nunca contra la BD real**: se usa un entorno de pruebas en el mismo servidor con otro proyecto de compose.
- Escenarios k6 (`load/`): navegación (login → home → eventos → detalle), chat (N conexiones STOMP concurrentes enviando cada X s), ráfaga de inscripciones a un evento popular.
- Objetivos (D-017, Raspberry Pi 5): 100 usuarias virtuales concurrentes, p95 < 500 ms en REST, entrega de mensajes p95 < 1 s, 0 errores 5xx. **k6 se ejecuta desde el portátil**, no desde la Pi (D-016). Documentar también hasta dónde aguanta la Pi (punto de saturación) como dato para la futura migración a hosting.
- Seed reducido acorde a la Pi (p. ej. 2.000 usuarias, 200.000 mensajes) si el completo no cabe.
- Tuning según los resultados: pool de Hikari, índices, N+1 (activar `hibernate.generate_statistics` en el entorno de carga), límites de memoria de la JVM, `worker_connections` de nginx y el tamaño del broker WS.
- Informe en `docs/ai/reports/load-test-<fecha>.md` con los resultados y los cuellos de botella.

## Checklist del hito "Demo privada"
- [ ] T01–T27 en estado `hecho`.
- [ ] Sin bugs críticos ni altos abiertos.
- [ ] Backups automáticos verificados en la última semana.
- [ ] Prueba de carga superada con los objetivos acordados.
- [ ] Yago ha hecho una prueba completa en el móvil y da el OK.
