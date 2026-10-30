import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const service = vi.hoisted(() => ({
  getGroupDetailsRequest: vi.fn(),
  updateGroupRequest: vi.fn(),
  addParticipantsRequest: vi.fn(),
  removeParticipantRequest: vi.fn(),
  changeParticipantRoleRequest: vi.fn(),
  leaveGroupRequest: vi.fn(),
}))
vi.mock('../../../features/messages/services/messages.service.js', () => service)
const users = vi.hoisted(() => ({ getUsersByUsername: vi.fn().mockResolvedValue([]) }))
vi.mock('../../../features/users/services/users.service.js', () => users)
vi.mock('../../../shared/ui/ImageUploader.jsx', () => ({ default: () => <div>uploader</div> }))

import GroupInfoPanel from './GroupInfoPanel.jsx'

const member = (username, role = 'MEMBER') => ({ username, fullName: username, profilePictureUrl: null, role })
const details = (myRole, extra = {}) => ({
  id: 'g1', name: 'Trail', imageUrl: null, participantCount: 3, myRole, historyVisibleToNew: false, managed: false,
  participants: { content: [member('ana', 'ADMIN'), member('bea'), member('cris')], totalElements: 3 },
  ...extra,
})

function renderPanel(props = {}) {
  return render(
    <GroupInfoPanel chat={{ id: 'g1', name: 'Trail' }} currentUsername="bea" onClose={vi.fn()} onGroupChanged={vi.fn()} onLeft={vi.fn()} {...props} />,
  )
}

describe('GroupInfoPanel (T12, T29r)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows the header, the participants and no admin controls to a plain member', async () => {
    service.getGroupDetailsRequest.mockResolvedValue(details('MEMBER'))
    renderPanel()

    expect(await screen.findByText('Grupo · 3 participantes')).toBeInTheDocument()
    expect(screen.getByText('Admin')).toBeInTheDocument()
    expect(screen.getByText(/@bea \(tú\)/)).toBeInTheDocument()
    expect(screen.queryByLabelText('Editar nombre del grupo')).not.toBeInTheDocument()
    expect(screen.queryByText('Añadir participantes')).not.toBeInTheDocument()
    expect(screen.queryByText('Guardar nombre')).not.toBeInTheDocument()
    expect(screen.getByText('Salir del grupo')).toBeInTheDocument()
  })

  it('a plain member only gets view profile and send message in the sheet', async () => {
    service.getGroupDetailsRequest.mockResolvedValue(details('MEMBER'))
    const onOpenProfile = vi.fn()
    const onSendMessage = vi.fn()
    renderPanel({ onOpenProfile, onSendMessage })

    fireEvent.click(await screen.findByLabelText('Opciones de cris'))
    expect(screen.queryByText('Expulsar')).not.toBeInTheDocument()
    expect(screen.queryByText('Hacer admin')).not.toBeInTheDocument()
    fireEvent.click(screen.getByText('Enviar mensaje'))
    expect(onSendMessage).toHaveBeenCalledWith(expect.objectContaining({ username: 'cris' }))

    fireEvent.click(screen.getByLabelText('Opciones de ana'))
    fireEvent.click(screen.getByText('Ver perfil'))
    expect(onOpenProfile).toHaveBeenCalledWith('ana')
  })

  it('lets any participant report another one from the sheet', async () => {
    service.getGroupDetailsRequest.mockResolvedValue(details('MEMBER'))
    renderPanel()

    fireEvent.click(await screen.findByLabelText('Opciones de cris'))
    fireEvent.click(screen.getByText('Denunciar'))
    expect(screen.getByRole('heading', { name: 'Denunciar a @cris' })).toBeInTheDocument()
    expect(screen.getByLabelText('Motivo')).toBeInTheDocument()
  })

  it('lets an admin rename the group in place', async () => {
    service.getGroupDetailsRequest.mockResolvedValue(details('ADMIN'))
    service.updateGroupRequest.mockResolvedValue(details('ADMIN', { name: 'Montaña' }))
    renderPanel({ currentUsername: 'ana' })

    fireEvent.click(await screen.findByLabelText('Editar nombre del grupo'))
    fireEvent.change(screen.getByLabelText('Nombre del grupo'), { target: { value: 'Montaña' } })
    fireEvent.click(screen.getByLabelText('Guardar nombre'))

    await waitFor(() => expect(service.updateGroupRequest).toHaveBeenCalledWith('g1', { groupName: 'Montaña' }))
    expect(await screen.findByText('Montaña')).toBeInTheDocument()
  })

  it('asks for confirmation before expelling and then reloads the group', async () => {
    service.getGroupDetailsRequest.mockResolvedValue(details('ADMIN'))
    service.removeParticipantRequest.mockResolvedValue(undefined)
    renderPanel({ currentUsername: 'ana' })

    fireEvent.click(await screen.findByLabelText('Opciones de cris'))
    fireEvent.click(screen.getByText('Expulsar'))
    expect(service.removeParticipantRequest).not.toHaveBeenCalled()
    expect(screen.getByText('¿Expulsar a cris?')).toBeInTheDocument()
    fireEvent.click(screen.getAllByRole('button', { name: 'Expulsar' }).at(-1))

    await waitFor(() => expect(service.removeParticipantRequest).toHaveBeenCalledWith('g1', 'cris'))
    await waitFor(() => expect(service.getGroupDetailsRequest).toHaveBeenCalledTimes(2))
  })

  it('does not expel if the admin cancels the confirmation', async () => {
    service.getGroupDetailsRequest.mockResolvedValue(details('ADMIN'))
    renderPanel({ currentUsername: 'ana' })

    fireEvent.click(await screen.findByLabelText('Opciones de cris'))
    fireEvent.click(screen.getByText('Expulsar'))
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(service.removeParticipantRequest).not.toHaveBeenCalled()
    expect(screen.queryByText('¿Expulsar a cris?')).not.toBeInTheDocument()
  })

  it('shows the server reason inside the sheet when an action is refused', async () => {
    service.getGroupDetailsRequest.mockResolvedValue(details('ADMIN'))
    service.changeParticipantRoleRequest.mockRejectedValue(new Error('El grupo debe tener al menos una administradora'))
    renderPanel({ currentUsername: 'ana' })

    fireEvent.click(await screen.findByLabelText('Opciones de bea'))
    fireEvent.click(screen.getByText('Hacer admin'))

    expect(await screen.findByRole('alert')).toHaveTextContent('al menos una administradora')
  })

  it('adds a participant from the search view', async () => {
    service.getGroupDetailsRequest.mockResolvedValue(details('ADMIN'))
    users.getUsersByUsername.mockResolvedValue([{ username: 'dani', fullName: 'Dani Gómez' }])
    service.addParticipantsRequest.mockResolvedValue(details('ADMIN'))
    renderPanel({ currentUsername: 'ana' })

    fireEvent.click(await screen.findByText('Añadir participantes'))
    fireEvent.change(screen.getByLabelText('Buscar usuarias'), { target: { value: 'dan' } })
    fireEvent.click(await screen.findByText('Dani Gómez'))

    await waitFor(() => expect(service.addParticipantsRequest).toHaveBeenCalledWith('g1', ['dani']))
  })

  it('a managed group links to its community and cannot add, expel or be left', async () => {
    service.getGroupDetailsRequest.mockResolvedValue(details('ADMIN', {
      managed: true,
      origin: { type: 'COMMUNITY', slug: 'trail-vigo', id: null, name: 'Trail Vigo' },
    }))
    const onOpenOrigin = vi.fn()
    renderPanel({ currentUsername: 'ana', onOpenOrigin })

    fireEvent.click(await screen.findByText('Chat de la comunidad Trail Vigo'))
    expect(onOpenOrigin).toHaveBeenCalledWith(expect.objectContaining({ slug: 'trail-vigo' }))
    expect(screen.queryByText('Añadir participantes')).not.toBeInTheDocument()
    expect(screen.queryByText('Salir del grupo')).not.toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Opciones de cris'))
    expect(screen.queryByText('Expulsar')).not.toBeInTheDocument()
    expect(screen.queryByText('Hacer admin')).not.toBeInTheDocument()
  })

  it('asks for confirmation before leaving', async () => {
    service.getGroupDetailsRequest.mockResolvedValue(details('MEMBER'))
    service.leaveGroupRequest.mockResolvedValue(undefined)
    const onLeft = vi.fn()
    renderPanel({ onLeft })

    fireEvent.click(await screen.findByText('Salir del grupo'))
    expect(service.leaveGroupRequest).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Salir' }))

    await waitFor(() => expect(service.leaveGroupRequest).toHaveBeenCalledWith('g1'))
    await waitFor(() => expect(onLeft).toHaveBeenCalled())
  })

  it('loads more participants on demand', async () => {
    service.getGroupDetailsRequest
      .mockResolvedValueOnce(details('MEMBER', { participantCount: 4, participants: { content: [member('ana', 'ADMIN'), member('bea'), member('cris')], totalElements: 4 } }))
      .mockResolvedValueOnce(details('MEMBER', { participants: { content: [member('dani')], totalElements: 4 } }))
    renderPanel()

    fireEvent.click(await screen.findByText('Cargar más'))

    expect(await screen.findByText(/@dani/)).toBeInTheDocument()
    expect(service.getGroupDetailsRequest).toHaveBeenLastCalledWith('g1', { page: 1, size: 20 })
  })
})
