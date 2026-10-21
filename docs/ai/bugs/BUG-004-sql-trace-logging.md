# BUG-004 — Logs de SQL con parámetros activados en la configuración única

- **Estado:** resuelto
- **Severidad:** alta
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/config
- **Tarea que lo resuelve:** T03
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/resources/application.properties` (`spring.jpa.show-sql=true`, `logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE`)

## Qué pasa
La misma configuración se usa en el servidor. Se registran todas las sentencias SQL y sus parámetros: emails, hashes de contraseña al registrarse y contenido de los mensajes privados acaban en los logs de Docker. También penaliza el rendimiento.

## Cómo reproducir
`docker logs backend_app` tras enviar un mensaje → aparece el contenido.

## Arreglo propuesto
Perfiles `dev`/`prod`; en `prod` sin logs de SQL (T03).
