#!/usr/bin/env node
// Mobile screenshots of the app for UI review (docs/ai/DESIGN.md, /ui-review skill).
//
//   node scripts/ui-shots.mjs /search /events /communities/<slug>
//
// Env: SHOTS_BASE_URL (default http://localhost:5173, i.e. `npm run dev`),
//      SHOTS_USER / SHOTS_PASSWORD (default: seed user demo / Montana-2026),
//      SHOTS_OUT (default ./ui-shots/<timestamp>), SHOTS_FULL=1 for full-page captures.
// Never point it at production: use `npm run dev` against the test stack (WORKFLOW §10).
// Needs Chromium once: `npx playwright install chromium`.
import { mkdirSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { chromium } from 'playwright'

const DEFAULT_ROUTES = ['/home', '/search', '/communities', '/events', '/messages', '/notifications', '/me', '/settings']
const VIEWPORTS = [
  { name: '390-light', width: 390, height: 844, colorScheme: 'light' },
  { name: '390-dark', width: 390, height: 844, colorScheme: 'dark' },
  { name: '360-light', width: 360, height: 740, colorScheme: 'light' },
]

const baseUrl = (process.env.SHOTS_BASE_URL || 'http://localhost:5173').replace(/\/$/, '')
const user = process.env.SHOTS_USER || 'demo'
const password = process.env.SHOTS_PASSWORD || 'Montana-2026'
const fullPage = process.env.SHOTS_FULL === '1'
const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
const outDir = resolve(process.env.SHOTS_OUT || join('ui-shots', stamp))
const routes = process.argv.slice(2).length ? process.argv.slice(2) : DEFAULT_ROUTES

const slug = (route) => route.replace(/^\//, '').replace(/[^a-zA-Z0-9]+/g, '-') || 'root'

async function settle(page) {
  // The STOMP socket keeps a connection open, so networkidle may never come: cap the wait.
  await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {})
  await page.waitForTimeout(400)
}

// Logs in through the API (not the form) so the screenshots do not depend on the login page;
// the refresh cookie ends up in the storage state and the app restores the session from it.
async function login(browser) {
  const context = await browser.newContext()
  const res = await context.request.post(`${baseUrl}/auth/login`, { data: { username: user, password } })
  if (!res.ok()) throw new Error(`Login de ${user} fallido: HTTP ${res.status()} (¿stack de pruebas con el perfil seed?)`)
  const state = await context.storageState()
  await context.close()
  return state
}

const browser = await chromium.launch()
const errors = []
try {
  mkdirSync(outDir, { recursive: true })
  const storageState = await login(browser)

  for (const vp of VIEWPORTS) {
    const context = await browser.newContext({
      viewport: { width: vp.width, height: vp.height },
      deviceScaleFactor: 2,
      isMobile: true,
      hasTouch: true,
      colorScheme: vp.colorScheme,
      reducedMotion: 'reduce',
      locale: 'es-ES',
      storageState,
    })
    const page = await context.newPage()
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(`[${vp.name}] ${page.url()} ${msg.text()}`)
    })
    page.on('pageerror', (err) => errors.push(`[${vp.name}] ${page.url()} ${err.message}`))

    // One full load per viewport; then client-side navigation, because every reload calls
    // /auth/refresh and the auth rate limit (30/min) would log the session out.
    await page.goto(`${baseUrl}${routes[0]}`)
    await settle(page) // let the app restore the session before navigating
    for (const route of routes) {
      await page.evaluate((path) => {
        window.history.pushState({}, '', path)
        window.dispatchEvent(new PopStateEvent('popstate'))
      }, route)
      await settle(page)
      if (new URL(page.url()).pathname.startsWith('/access')) {
        throw new Error(`Sesión perdida al abrir ${route} (¿límite de /auth/refresh? espera un minuto)`)
      }
      const file = join(outDir, `${slug(route)}__${vp.name}.png`)
      await page.screenshot({ path: file, fullPage })
      console.log(file)
    }
    await context.close()
  }
} finally {
  await browser.close()
}

writeFileSync(join(outDir, 'console-errors.txt'), errors.join('\n') + '\n')
console.log(`\n${routes.length * VIEWPORTS.length} capturas en ${outDir}; errores de consola: ${errors.length}`)
