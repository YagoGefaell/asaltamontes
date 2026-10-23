import { render, screen } from '@testing-library/react'
import { useEffect } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('./EventsMapView.jsx', () => ({
  default: function FakeMap({ events, onBoundsChange }) {
    useEffect(() => {
      onBoundsChange({ minLat: 40, maxLat: 41, minLng: -4, maxLng: -3 })
    }, [onBoundsChange])
    return <ul aria-label="Chinchetas">{events.map((e) => <li key={e.id}>{e.title}</li>)}</ul>
  },
}))
vi.mock('../services/events.service.js', async (importOriginal) => ({
  ...(await importOriginal()),
  getEventsInBoundsRequest: vi.fn(),
}))

import EventsMapSection from './EventsMapSection.jsx'
import { getEventsInBoundsRequest } from '../services/events.service.js'

const FILTERS = { type: 'RACE' }
const ev = (id, extra = {}) => ({ id, title: `Quedada ${id}`, latitude: 40.4, longitude: -3.7, ...extra })

function renderSection() {
  return render(<MemoryRouter><EventsMapSection filters={FILTERS} /></MemoryRouter>)
}

beforeEach(() => getEventsInBoundsRequest.mockReset())

describe('EventsMapSection', () => {
  it('searches the visible area with the list filters and draws a pin per event with coordinates', async () => {
    getEventsInBoundsRequest.mockResolvedValue({ items: [ev(1), ev(2), ev(3, { latitude: null, longitude: null })], total: 3 })
    renderSection()
    expect(await screen.findByText('Quedada 1')).toBeInTheDocument()
    expect(screen.getByText('Quedada 2')).toBeInTheDocument()
    expect(screen.queryByText('Quedada 3')).not.toBeInTheDocument()
    expect(getEventsInBoundsRequest).toHaveBeenCalledWith({ minLat: 40, maxLat: 41, minLng: -4, maxLng: -3 }, FILTERS)
    expect(screen.getByRole('status')).toHaveTextContent('3 quedadas en esta zona')
  })

  it('tells her when the area has none', async () => {
    getEventsInBoundsRequest.mockResolvedValue({ items: [], total: 0 })
    renderSection()
    expect(await screen.findByText(/No hay quedadas en esta zona/)).toBeInTheDocument()
  })

  it('asks her to zoom in when the area has more than the cap', async () => {
    const items = Array.from({ length: 50 }, (_, i) => ev(i + 1))
    getEventsInBoundsRequest.mockResolvedValue({ items, total: 120 })
    renderSection()
    expect(await screen.findByText(/Mostrando las 50 primeras de 120/)).toBeInTheDocument()
  })

  it('shows the error with a retry', async () => {
    getEventsInBoundsRequest.mockRejectedValueOnce(new Error('Sin conexión'))
    renderSection()
    expect(await screen.findByText('Sin conexión')).toBeInTheDocument()
    getEventsInBoundsRequest.mockResolvedValueOnce({ items: [ev(1)], total: 1 })
    screen.getByRole('button', { name: 'Reintentar' }).click()
    expect(await screen.findByText('Quedada 1')).toBeInTheDocument()
  })
})
