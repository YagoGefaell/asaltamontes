# BUG-075 — La pantalla de acceso se queda en blanco sin `VITE_GOOGLE_CLIENT_ID`

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado
- **Área:** frontend/auth
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-27, al preparar `/ui-review` (capturas con Playwright contra `npm run dev` en un worktree sin `.env`)

## Dónde
`frontend/src/app/providers.jsx:11` (`GoogleOAuthProvider clientId={undefined}`) y `frontend/src/shared/components/GoogleLoginButton.jsx:6` (`useGoogleLogin`).

## Qué pasa
Si el build o `npm run dev` no tiene `VITE_GOOGLE_CLIENT_ID`, `GoogleLoginButton` lanza un error al renderizar y, como no hay `ErrorBoundary` alrededor, `/login`, `/register` y `/access` no pintan nada: tampoco el login con usuario y contraseña. En producción no pasa porque el `.env` tiene la variable, pero cualquier worktree nuevo, entorno de pruebas o despliegue mal configurado se queda sin poder entrar.

## Cómo reproducir
1. `cd frontend && npm run dev` sin `VITE_GOOGLE_CLIENT_ID` en el entorno ni en `frontend/.env`.
2. Abrir `/login`: página vacía; en consola, `An error occurred in the <GoogleLoginButton> component`.

## Arreglo propuesto
Si no hay `clientId`, no montar `GoogleOAuthProvider` ni `GoogleLoginButton` (ocultar el botón de Google) y dejar un `console.warn` en desarrollo. Test con Vitest: `LoginForm` renderiza los campos sin la variable.
