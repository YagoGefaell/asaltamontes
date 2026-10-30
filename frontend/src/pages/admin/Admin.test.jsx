import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Admin from './Admin.jsx'
import AdminReportDetail from './AdminReportDetail.jsx'
import { AdminGuard } from '../../app/guards/adminGuard.jsx'
import * as service from '../../features/admin/services/admin.service.js'

vi.mock('../../features/admin/services/admin.service.js', () => ({
  getAdminReportsRequest: vi.fn(),
  getAdminReportRequest: vi.fn(),
  dismissReportRequest: vi.fn(),
  removeReportedContentRequest: vi.fn(),
}))

const report = (over = {}) => ({
  id: 'r1',
  targetType: 'POST',
  target: 'post-uuid',
  reason: 'SPAM',
  details: null,
  contentSnapshot: null,
  status: 'OPEN',
  createdAt: '2026-10-29T10:00:00Z',
  reporterUsername: 'ana',
  reportedUsername: 'bea',
  openReportsOnTarget: 1,
  contentAvailable: true,
  handledByUsername: null,
  handledAt: null,
  resolution: null,
  ...over,
})

const page = (items, extra = {}) => ({ items, hasMore: false, nextBefore: null, ...extra })

function renderAdmin() {
  return render(
    <MemoryRouter initialEntries={['/admin']}>
      <Routes>
        <Route path="/admin" element={<Admin />} />
        <Route path="/admin/reports/:id" element={<AdminReportDetail />} />
      </Routes>
    </MemoryRouter>,
  )
}

function renderDetail() {
  return render(
    <MemoryRouter initialEntries={['/admin/reports/r1']}>
      <Routes>
        <Route path="/admin/reports/:id" element={<AdminReportDetail />} />
        <Route path="/admin" element={<p>cola</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('Admin queue', () => {
  beforeEach(() => vi.clearAllMocks())

  it('lists open reports by default and filters by status and type', async () => {
    service.getAdminReportsRequest.mockResolvedValue(page([report()]))
    renderAdmin()

    expect(await screen.findByText(/Publicación · Spam/)).toBeTruthy()
    expect(service.getAdminReportsRequest).toHaveBeenLastCalledWith({ status: 'OPEN', type: undefined, before: undefined })

    fireEvent.click(screen.getByRole('button', { name: 'Descartadas' }))
    await waitFor(() =>
      expect(service.getAdminReportsRequest).toHaveBeenLastCalledWith({ status: 'DISMISSED', type: undefined, before: undefined }))

    fireEvent.click(screen.getByRole('button', { name: 'Filtros' }))
    fireEvent.change(screen.getByLabelText('Tipo de contenido'), { target: { value: 'EVENT' } })
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar' }))
    await waitFor(() =>
      expect(service.getAdminReportsRequest).toHaveBeenLastCalledWith({ status: 'DISMISSED', type: 'EVENT', before: undefined }))
  })

  it('shows the empty state and an inline error with retry', async () => {
    service.getAdminReportsRequest.mockResolvedValueOnce(page([]))
    renderAdmin()
    expect(await screen.findByText('No hay denuncias abiertas')).toBeTruthy()
  })

  it('shows an inline error and retries', async () => {
    service.getAdminReportsRequest.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(page([report()]))
    renderAdmin()
    expect(await screen.findByRole('alert')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText(/Publicación · Spam/)).toBeTruthy()
  })

  it('loads the next page with the cursor', async () => {
    service.getAdminReportsRequest
      .mockResolvedValueOnce(page([report()], { hasMore: true, nextBefore: 'c1' }))
      .mockResolvedValueOnce(page([report({ id: 'r2', reason: 'HATE' })]))
    renderAdmin()
    fireEvent.click(await screen.findByRole('button', { name: 'Cargar más' }))
    expect(await screen.findByText(/Publicación · Odio/)).toBeTruthy()
    expect(service.getAdminReportsRequest).toHaveBeenLastCalledWith({ status: 'OPEN', type: undefined, before: 'c1' })
    expect(screen.getByText(/Publicación · Spam/)).toBeTruthy()
  })
})

describe('Admin report detail', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders user-written text as plain text, never as HTML', async () => {
    service.getAdminReportRequest.mockResolvedValue(
      report({ details: '<img src=x onerror=alert(1)> hola', contentSnapshot: '<b>copia</b>' }))
    const { container } = renderDetail()

    expect(await screen.findByText('<img src=x onerror=alert(1)> hola')).toBeTruthy()
    expect(screen.getByText('<b>copia</b>')).toBeTruthy()
    expect(container.querySelector('img')).toBeNull()
    expect(container.querySelector('b')).toBeNull()
  })

  it('dismisses after confirming in the sheet and shows the new status', async () => {
    service.getAdminReportRequest.mockResolvedValue(report())
    service.dismissReportRequest.mockResolvedValue(report({ status: 'DISMISSED', handledByUsername: 'mod', handledAt: '2026-10-29T11:00:00Z' }))
    renderDetail()

    fireEvent.click(await screen.findByRole('button', { name: 'Descartar denuncia' }))
    fireEvent.change(screen.getByLabelText('Nota (opcional)'), { target: { value: ' sin problema ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Descartar' }))

    await waitFor(() => expect(service.dismissReportRequest).toHaveBeenCalledWith('r1', 'sin problema'))
    expect(await screen.findByText('Resolución')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Descartar denuncia' })).toBeNull()
  })

  it('removes the content after confirming, and keeps the sheet open with an inline error on failure', async () => {
    service.getAdminReportRequest.mockResolvedValue(report({ targetType: 'COMMUNITY' }))
    service.removeReportedContentRequest.mockRejectedValue(Object.assign(new Error('x'), { status: 403 }))
    renderDetail()

    fireEvent.click(await screen.findByRole('button', { name: 'Borrar contenido' }))
    fireEvent.click(screen.getAllByRole('button', { name: 'Borrar contenido' }).at(-1))

    expect(await screen.findByText('No tienes permiso para hacer esto.')).toBeTruthy()
    expect(screen.getByRole('dialog')).toBeTruthy()
  })

  it('offers no content removal for a user report', async () => {
    service.getAdminReportRequest.mockResolvedValue(report({ targetType: 'USER', target: 'bea' }))
    renderDetail()
    expect(await screen.findByRole('button', { name: 'Descartar denuncia' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Borrar contenido' })).toBeNull()
  })

  it('shows no actions on a resolved report', async () => {
    service.getAdminReportRequest.mockResolvedValue(report({ status: 'ACTIONED', handledByUsername: 'mod', resolution: 'borrado' }))
    renderDetail()
    expect(await screen.findByText(/borrado/)).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Descartar denuncia' })).toBeNull()
  })
})

describe('AdminGuard', () => {
  beforeEach(() => vi.clearAllMocks())

  function renderGuard() {
    return render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route element={<AdminGuard />}>
            <Route path="/admin" element={<p>panel</p>} />
          </Route>
          <Route path="/" element={<p>inicio</p>} />
        </Routes>
      </MemoryRouter>,
    )
  }

  it('lets staff in', async () => {
    service.getAdminReportsRequest.mockResolvedValue(page([]))
    renderGuard()
    expect(await screen.findByText('panel')).toBeTruthy()
  })

  it('sends everybody else home', async () => {
    service.getAdminReportsRequest.mockRejectedValue(Object.assign(new Error('Acceso denegado'), { status: 403 }))
    renderGuard()
    expect(await screen.findByText('inicio')).toBeTruthy()
  })
})
