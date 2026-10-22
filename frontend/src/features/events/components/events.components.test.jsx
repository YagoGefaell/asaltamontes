import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import AttendButton from './AttendButton.jsx'
import EventCard from './EventCard.jsx'
import EventFilters from './EventFilters.jsx'
import { EMPTY_FILTERS } from '../utils/eventFilters.js'

const event = { id: 1, status: 'SCHEDULED', capacity: 12, attendeeCount: 7, myStatus: null }

function button(props = {}, ev = {}) {
  const onJoin = vi.fn()
  const onLeave = vi.fn()
  render(<AttendButton event={{ ...event, ...ev }} onJoin={onJoin} onLeave={onLeave} {...props} />)
  return { onJoin, onLeave }
}

describe('AttendButton states', () => {
  it('lets her join', () => {
    const { onJoin } = button()
    fireEvent.click(screen.getByRole('button', { name: 'Apuntarme' }))
    expect(onJoin).toHaveBeenCalled()
  })

  it('shows she is inside and leaves on tap', () => {
    const { onLeave } = button({}, { myStatus: 'GOING' })
    fireEvent.click(screen.getByRole('button', { name: /Estás dentro/ }))
    expect(onLeave).toHaveBeenCalled()
  })

  it('shows the waitlist with her position', () => {
    button({ waitlistPosition: 3 }, { myStatus: 'WAITLIST' })
    expect(screen.getByRole('button', { name: /En lista de espera \(#3\)/ })).toBeInTheDocument()
  })

  it('offers the waitlist when the event is full', () => {
    const { onJoin } = button({}, { attendeeCount: 12 })
    fireEvent.click(screen.getByRole('button', { name: /Completo/ }))
    expect(onJoin).toHaveBeenCalled()
  })

  it('is disabled when cancelled, finished or she organizes it', () => {
    button({}, { status: 'CANCELLED' })
    expect(screen.getByRole('button', { name: 'Cancelado' })).toBeDisabled()
    button({}, { status: 'FINISHED' })
    expect(screen.getByRole('button', { name: 'Finalizada' })).toBeDisabled()
    button({ isOrganizer: true })
    expect(screen.getByRole('button', { name: /organizadora/ })).toBeDisabled()
  })

  it('is disabled while a request is pending', () => {
    button({ pending: true })
    expect(screen.getByRole('button', { name: 'Apuntarme' })).toBeDisabled()
  })
})

describe('EventCard', () => {
  it('shows date, title, places and links to the detail', () => {
    render(
      <MemoryRouter>
        <EventCard
          event={{
            id: 9, title: 'Subida al Mulhacén', type: 'TRAINING', level: 'ADVANCED', startsAt: '2026-10-24T07:30:00Z',
            timezone: 'Europe/Madrid', meetingPointName: 'Aparcamiento', city: 'Granada', distanceKm: 12.5,
            elevationGainM: 900, capacity: 12, attendeeCount: 12,
          }}
        />
      </MemoryRouter>,
    )
    expect(screen.getByRole('link')).toHaveAttribute('href', '/events/9')
    expect(screen.getByText('Subida al Mulhacén')).toBeInTheDocument()
    expect(screen.getByText(/12\/12 · completo/)).toBeInTheDocument()
    expect(screen.getByText('Avanzado')).toBeInTheDocument()
  })
})

describe('EventFilters', () => {
  it('changes the date preset and toggles "mis quedadas"', () => {
    const onChange = vi.fn()
    render(<EventFilters filters={EMPTY_FILTERS} onChange={onChange} />)
    fireEvent.click(screen.getByRole('button', { name: 'Este finde' }))
    expect(onChange).toHaveBeenLastCalledWith({ ...EMPTY_FILTERS, date: 'weekend' })
    fireEvent.click(screen.getByRole('button', { name: 'Mis quedadas' }))
    expect(onChange).toHaveBeenLastCalledWith({ ...EMPTY_FILTERS, mine: true })
  })

  it('offers to clear active filters', () => {
    const onChange = vi.fn()
    render(<EventFilters filters={{ ...EMPTY_FILTERS, type: 'RACE' }} onChange={onChange} />)
    fireEvent.click(screen.getByRole('button', { name: 'Quitar filtros' }))
    expect(onChange).toHaveBeenCalledWith(EMPTY_FILTERS)
  })
})
