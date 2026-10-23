import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

vi.mock('../../features/events/hooks/useEvents.js', () => ({ useEvents: vi.fn() }))
vi.mock('../../features/events/components/EventsMapSection.jsx', () => ({
  default: ({ filters }) => <div data-testid="map-section">{filters.type || 'sin tipo'}</div>,
}))

import Events from './Events.jsx'
import { useEvents } from '../../features/events/hooks/useEvents.js'

const list = { events: [], loading: false, loadingMore: false, hasMore: false, loadMore: vi.fn(), error: null, refetch: vi.fn() }

function renderPage() {
  useEvents.mockReturnValue(list)
  return render(<MemoryRouter><Events /></MemoryRouter>)
}

describe('Events page views', () => {
  it('starts as a list and switches to the map with the same filters', () => {
    renderPage()
    expect(screen.queryByTestId('map-section')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Lista' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('Aún no hay quedadas')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Tipo'), { target: { value: 'RACE' } })
    fireEvent.click(screen.getByRole('button', { name: 'Mapa' }))
    expect(screen.getByTestId('map-section')).toHaveTextContent('RACE')
    expect(screen.queryByText('Aún no hay quedadas')).not.toBeInTheDocument()
    // The list stops searching while the map is showing
    expect(useEvents).toHaveBeenLastCalledWith(expect.objectContaining({ type: 'RACE' }), false)
  })

  it('goes back to the list', () => {
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: 'Mapa' }))
    fireEvent.click(screen.getByRole('button', { name: 'Lista' }))
    expect(screen.queryByTestId('map-section')).not.toBeInTheDocument()
    expect(useEvents).toHaveBeenLastCalledWith(expect.anything(), true)
  })
})
