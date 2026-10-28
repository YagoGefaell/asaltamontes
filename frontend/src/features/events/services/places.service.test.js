import { afterEach, describe, expect, it, vi } from 'vitest'
import { PHOTON_URL, searchPlaces, toPlace } from './places.service.js'

const feature = (properties, coordinates = [-8.7114, 42.2338]) => ({ properties, geometry: { coordinates } })

afterEach(() => vi.unstubAllGlobals())

describe('toPlace', () => {
  it('takes the name, the city and the [lon, lat] point of a place', () => {
    const place = toPlace(feature({
      osm_type: 'W', osm_id: 1, name: 'Centro Comercial Vialia', street: 'Rúa de Urzaiz', city: 'Vigo', state: 'Galicia', country: 'España',
    }))
    expect(place).toEqual({
      id: 'W1', name: 'Centro Comercial Vialia', detail: 'Rúa de Urzaiz, Vigo, Galicia, España', city: 'Vigo', latitude: 42.2338, longitude: -8.7114,
    })
  })

  it('uses the street when there is no name and the place itself when it is a city', () => {
    expect(toPlace(feature({ street: 'Calle Mayor', housenumber: '3', city: 'Jaca' })).name).toBe('Calle Mayor 3')
    const city = toPlace(feature({ type: 'city', name: 'Jaca', state: 'Aragón', country: 'España' }))
    expect(city).toMatchObject({ name: 'Jaca', city: 'Jaca', detail: 'Aragón, España' })
  })

  it('falls back to the county for places outside a city', () => {
    expect(toPlace(feature({ name: 'Pico Aneto', county: 'Huesca' })).city).toBe('Huesca')
  })
})

describe('searchPlaces', () => {
  it('asks Photon near the given point and drops results without a name or point', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ features: [feature({ osm_type: 'N', osm_id: 2, name: 'Vialia', city: 'Vigo' }), feature({}, [])] }),
    })
    vi.stubGlobal('fetch', fetchMock)
    const places = await searchPlaces('vialia', { near: { latitude: 42.2, longitude: -8.7 } })
    const url = new URL(fetchMock.mock.calls[0][0])
    expect(`${url.origin}${url.pathname}`).toBe(PHOTON_URL)
    expect(Object.fromEntries(url.searchParams)).toMatchObject({ q: 'vialia', lat: '42.2', lon: '-8.7' })
    expect(places.map((p) => p.name)).toEqual(['Vialia'])
  })

  it('fails with a message when Photon answers with an error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }))
    await expect(searchPlaces('vialia')).rejects.toThrow('No se pudo buscar el lugar')
  })
})
