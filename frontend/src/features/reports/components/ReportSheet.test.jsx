import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ReportSheet from './ReportSheet.jsx'
import ReportMenu from './ReportMenu.jsx'
import { createReportRequest } from '../services/reports.service'

vi.mock('../services/reports.service', async (importOriginal) => ({
  ...(await importOriginal()),
  createReportRequest: vi.fn(),
}))

const renderSheet = (onClose = vi.fn()) =>
  render(<ReportSheet open title="Denunciar publicación" targetType="POST" target="p1" onClose={onClose} />)
const pick = (reason) => fireEvent.change(screen.getByLabelText('Motivo'), { target: { value: reason } })
const send = () => fireEvent.click(screen.getByRole('button', { name: 'Enviar denuncia' }))

beforeEach(() => vi.clearAllMocks())

describe('ReportSheet', () => {
  it('needs a reason before sending', () => {
    renderSheet()
    expect(screen.getByRole('button', { name: 'Enviar denuncia' })).toBeDisabled()
  })

  it('sends the report and says it was received', async () => {
    createReportRequest.mockResolvedValue({})
    renderSheet()
    pick('SPAM')
    send()
    expect(await screen.findByText(/Hemos recibido tu denuncia/)).toBeInTheDocument()
    expect(createReportRequest).toHaveBeenCalledWith({ targetType: 'POST', target: 'p1', reason: 'SPAM', details: '' })
  })

  it('requires details for «Otro motivo»', () => {
    renderSheet()
    pick('OTHER')
    expect(screen.getByRole('button', { name: 'Enviar denuncia' })).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Cuéntanos qué ha pasado'), { target: { value: 'Me escribe sin parar' } })
    expect(screen.getByRole('button', { name: 'Enviar denuncia' })).toBeEnabled()
  })

  it.each([
    [{ status: 409 }, 'Ya has denunciado esto. Lo revisaremos.'],
    [{ status: 404 }, 'Esto ya no está disponible.'],
    [{ status: 429, retryAfter: 7200 }, 'Has enviado muchas denuncias hoy. Podrás volver a denunciar en 2 horas.'],
  ])('shows %j inside the sheet, which stays open', async (failure, text) => {
    createReportRequest.mockRejectedValue(Object.assign(new Error('x'), failure))
    renderSheet()
    pick('HATE')
    send()
    expect(await screen.findByText(text)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enviar denuncia' })).toBeEnabled()
  })

  it('closes with «Cerrar» after sending', async () => {
    createReportRequest.mockResolvedValue({})
    const onClose = vi.fn()
    renderSheet(onClose)
    pick('SPAM')
    send()
    fireEvent.click(await screen.findByRole('button', { name: 'Cerrar' }))
    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })
})

describe('ReportMenu', () => {
  it('opens the menu, then the report form for the target', () => {
    render(<ReportMenu menuTitle="Quedada" reportTitle="Denunciar quedada" actionLabel="Denunciar quedada" targetType="EVENT" target="e1" />)
    fireEvent.click(screen.getByRole('button', { name: 'Más opciones' }))
    fireEvent.click(screen.getByRole('button', { name: 'Denunciar quedada' }))
    expect(screen.getByRole('heading', { name: 'Denunciar quedada' })).toBeInTheDocument()
    expect(screen.getByLabelText('Motivo')).toBeInTheDocument()
  })
})
