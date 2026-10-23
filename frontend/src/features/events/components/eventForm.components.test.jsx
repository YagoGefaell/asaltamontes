import { useState } from 'react'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import EventForm from './EventForm.jsx'
import CommunityEvents from './CommunityEvents.jsx'
import { EMPTY_FORM } from '../utils/eventForm.js'
import { getCommunityEventsRequest } from '../services/events.service.js'

// Leaflet needs a real browser layout: the picker is replaced by a stub that "taps" a point
vi.mock('./LocationPicker.jsx', () => ({
  default: ({ onChange }) => <button type="button" onClick={() => onChange(40.5, -3.7)}>mapa</button>,
}))
vi.mock('../services/events.service.js', () => ({ getCommunityEventsRequest: vi.fn() }))

function Harness({ onSubmit, initial = EMPTY_FORM, ...props }) {
  const [values, setValues] = useState(initial)
  return <EventForm values={values} onChange={setValues} onSubmit={() => onSubmit(values)} {...props} />
}

async function renderForm(props = {}) {
  const onSubmit = vi.fn()
  render(<Harness onSubmit={onSubmit} {...props} />)
  await screen.findByText('mapa')
  return onSubmit
}

describe('EventForm', () => {
  it('shows the API-mirrored errors and does not submit an invalid form', async () => {
    const onSubmit = await renderForm()
    fireEvent.click(screen.getByRole('button', { name: 'Crear quedada' }))
    expect(screen.getByText('El título es obligatorio')).toBeInTheDocument()
    expect(screen.getByText('La fecha de inicio es obligatoria')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits the typed values and the point picked on the map', async () => {
    const onSubmit = await renderForm()
    fireEvent.change(screen.getByLabelText('Título'), { target: { value: 'Rodaje' } })
    fireEvent.change(screen.getByLabelText('Empieza'), { target: { value: '2999-01-02T09:30' } })
    fireEvent.click(screen.getByText('mapa'))
    expect(screen.getByText('40.50000, -3.70000')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Crear quedada' }))
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ title: 'Rodaje', latitude: 40.5, longitude: -3.7 }))
  })

  it('defaults to the device zone and lets her pick another under "Avanzado" with a search box', async () => {
    const onSubmit = await renderForm()
    const device = Intl.DateTimeFormat().resolvedOptions().timeZone
    const select = screen.getByLabelText('Zona horaria')
    expect(select.value).toBe(device)
    fireEvent.change(screen.getByLabelText('Buscar zona horaria'), { target: { value: 'tokyo' } })
    expect(screen.getByRole('option', { name: 'Asia/Tokyo' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'America/Lima' })).not.toBeInTheDocument()
    fireEvent.change(select, { target: { value: 'Asia/Tokyo' } })
    fireEvent.change(screen.getByLabelText('Título'), { target: { value: 'Rodaje' } })
    fireEvent.change(screen.getByLabelText('Empieza'), { target: { value: '2999-01-02T09:30' } })
    fireEvent.click(screen.getByRole('button', { name: 'Crear quedada' }))
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ timezone: 'Asia/Tokyo' }))
  })

  it('removes the point', async () => {
    await renderForm({ initial: { ...EMPTY_FORM, latitude: 40.5, longitude: -3.7 } })
    fireEvent.click(screen.getByRole('button', { name: 'Quitar punto' }))
    expect(screen.queryByText('40.50000, -3.70000')).not.toBeInTheDocument()
  })

  it('offers only the communities she can organize in, and visibility once one is chosen', async () => {
    await renderForm({ communities: [{ id: 4, name: 'Trail Madrid' }] })
    expect(screen.queryByLabelText('Visibilidad')).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Comunidad'), { target: { value: '4' } })
    expect(screen.getByRole('option', { name: 'Trail Madrid' })).toBeInTheDocument()
    expect(screen.getByLabelText('Visibilidad')).toBeInTheDocument()
  })

  it('does not offer a community picker when editing', async () => {
    await renderForm({ mode: 'edit', communityName: 'Trail Madrid', communities: [{ id: 4, name: 'Trail Madrid' }] })
    expect(screen.queryByLabelText('Comunidad')).not.toBeInTheDocument()
    expect(screen.getByText(/no se pueden cambiar/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeInTheDocument()
  })

  it('shows the field errors that come from the API', async () => {
    await renderForm({ serverErrors: { title: 'El título ya existe' } })
    expect(screen.getByText('El título ya existe')).toBeInTheDocument()
  })
})

describe('CommunityEvents', () => {
  beforeEach(() => vi.clearAllMocks())
  const community = { id: 4, slug: 'trail-madrid', name: 'Trail Madrid', role: 'MODERATOR' }
  const event = {
    id: 9, title: 'Rodaje', type: 'MEETUP', startsAt: '2999-01-02T08:30:00Z', timezone: 'Europe/Madrid', attendeeCount: 1,
  }
  const show = (c = community) => render(<MemoryRouter><CommunityEvents community={c} /></MemoryRouter>)

  it('lists the upcoming events and lets staff create one', async () => {
    getCommunityEventsRequest.mockResolvedValue({ items: [event] })
    show()
    expect(await screen.findByText('Rodaje')).toBeInTheDocument()
    expect(getCommunityEventsRequest).toHaveBeenCalledWith('trail-madrid', { size: 5 })
    expect(screen.getByRole('button', { name: 'Crear quedada' })).toBeInTheDocument()
  })

  it('hides "Crear quedada" for plain members and explains when there are no events', async () => {
    getCommunityEventsRequest.mockResolvedValue({ items: [] })
    show({ ...community, role: 'MEMBER' })
    expect(await screen.findByText('Todavía no hay quedadas programadas.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Crear quedada' })).not.toBeInTheDocument()
  })

  it('shows the error and retries', async () => {
    getCommunityEventsRequest.mockRejectedValueOnce(new Error('Sin conexión')).mockResolvedValueOnce({ items: [event] })
    show()
    expect(await screen.findByText(/Sin conexión/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    await waitFor(() => expect(screen.getByText('Rodaje')).toBeInTheDocument())
  })
})
