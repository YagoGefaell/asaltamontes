import { useState } from 'react'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import EventForm from './EventForm.jsx'
import CommunityEvents from './CommunityEvents.jsx'
import { EMPTY_FORM } from '../utils/eventForm.js'
import { getCommunityEventsRequest } from '../services/events.service.js'
import { searchPlaces } from '../services/places.service.js'

// Leaflet needs a real browser layout: the picker is replaced by a stub that "taps" a point
vi.mock('./LocationPicker.jsx', () => ({
  default: ({ onChange, focus }) => (
    <>
      <button type="button" onClick={() => onChange(40.5, -3.7)}>mapa</button>
      {focus && <span>{`mapa centrado en ${focus.latitude}, ${focus.longitude}`}</span>}
    </>
  ),
}))
vi.mock('../services/events.service.js', () => ({ getCommunityEventsRequest: vi.fn() }))
vi.mock('../services/places.service.js', () => ({ searchPlaces: vi.fn() }))

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

  it('suggests places as she types and fills the name, the city and the point with the one she picks', async () => {
    searchPlaces.mockResolvedValue([
      { id: 'W1', name: 'Centro Comercial Vialia', detail: 'Vigo, Galicia, España', city: 'Vigo', latitude: 42.2338, longitude: -8.7114 },
    ])
    const onSubmit = await renderForm()
    const field = screen.getByRole('combobox', { name: 'Lugar' })
    fireEvent.focus(field)
    fireEvent.change(field, { target: { value: 'vialia' } })
    const option = await screen.findByRole('option', { name: /Centro Comercial Vialia/ })
    expect(searchPlaces).toHaveBeenCalledWith('vialia', expect.objectContaining({ near: null }))

    fireEvent.click(option)
    expect(field).toHaveValue('Centro Comercial Vialia')
    expect(screen.getByLabelText('Ciudad')).toHaveValue('Vigo')
    expect(screen.getByText('42.23380, -8.71140')).toBeInTheDocument()
    expect(screen.getByText('mapa centrado en 42.2338, -8.7114')).toBeInTheDocument()
    expect(screen.queryByRole('listbox')).toBeNull()

    fireEvent.change(screen.getByLabelText('Título'), { target: { value: 'Rodaje' } })
    fireEvent.change(screen.getByLabelText('Empieza'), { target: { value: '2999-01-02T09:30' } })
    fireEvent.click(screen.getByRole('button', { name: 'Crear quedada' }))
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      meetingPointName: 'Centro Comercial Vialia', city: 'Vigo', latitude: 42.2338, longitude: -8.7114,
    }))
  })

  it('keeps a free-text place, picks with the keyboard and says when nothing is found', async () => {
    searchPlaces.mockResolvedValueOnce([])
    await renderForm()
    const field = screen.getByRole('combobox', { name: 'Lugar' })
    fireEvent.focus(field)
    fireEvent.change(field, { target: { value: 'Fuente de la Teja' } })
    expect(await screen.findByText(/Sin resultados/)).toBeInTheDocument()
    expect(field).toHaveValue('Fuente de la Teja')

    searchPlaces.mockResolvedValueOnce([
      { id: 'N1', name: 'Jaca', detail: 'Aragón', city: 'Jaca', latitude: 42.57, longitude: -0.55 },
    ])
    fireEvent.change(field, { target: { value: 'jaca' } })
    await screen.findByRole('option', { name: /Jaca/ })
    fireEvent.keyDown(field, { key: 'ArrowDown' })
    expect(screen.getByRole('option', { name: /Jaca/ })).toHaveAttribute('aria-selected', 'true')
    fireEvent.keyDown(field, { key: 'Enter' })
    expect(screen.getByLabelText('Ciudad')).toHaveValue('Jaca')
  })

  it('defaults to the device zone and lets her pick another under "Más opciones" with a search box', async () => {
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

  it('opens "Más opciones" when a field hidden inside has an error', async () => {
    await renderForm({ initial: { ...EMPTY_FORM, distanceKm: '-3' } })
    expect(screen.getByText('Más opciones').closest('details')).not.toHaveAttribute('open')
    fireEvent.click(screen.getByRole('button', { name: 'Crear quedada' }))
    expect(screen.getByText('Más opciones').closest('details')).toHaveAttribute('open')
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
