import { describe, expect, it } from 'vitest'
import { buildBrokerUrl } from './useWebSocket.js'

describe('buildBrokerUrl', () => {
  it('uses the page origin when no API URL is configured (nginx / Vite proxy)', () => {
    expect(buildBrokerUrl('', { protocol: 'http:', host: '192.168.1.158' }))
      .toBe('ws://192.168.1.158/ws-asaltamontes')
  })

  it('uses wss on https pages', () => {
    expect(buildBrokerUrl('', { protocol: 'https:', host: 'asaltamontes.example.ts.net' }))
      .toBe('wss://asaltamontes.example.ts.net/ws-asaltamontes')
  })

  it('uses the configured API origin when there is one', () => {
    expect(buildBrokerUrl('http://localhost:8080/')).toBe('ws://localhost:8080/ws-asaltamontes')
  })
})
