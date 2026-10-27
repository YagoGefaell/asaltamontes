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
vi.mock('../../../features/users/services/users.service.js', () => ({ getUsersByUsername: vi.fn().mockResolvedValue([]) }))
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

describe('GroupInfoPanel (T12)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows the participants and hides the admin controls from a plain member', async () => {
    service.getGroupDetailsRequest.mockResolvedValue(details('MEMBER'))
    renderPanel()

    expect(await screen.findByText('Administradora')).toBeInTheDocument()
    expect(screen.getByText(/@bea \(tú\)/)).toBeInTheDocument()
    expect(screen.queryByText('Expulsar')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Nombre del grupo')).not.toBeInTheDocument()
    expect(screen.getByText('Salir del grupo')).toBeInTheDocument()
  })

  it('lets an admin expel a participant and reloads the group', async () => {
    service.getGroupDetailsRequest.mockResolvedValue(details('ADMIN'))
    service.removeParticipantRequest.mockResolvedValue(undefined)
    renderPanel({ currentUsername: 'ana' })

    fireEvent.click(await screen.findByLabelText('Expulsar a cris'))

    await waitFor(() => expect(service.removeParticipantRequest).toHaveBeenCalledWith('g1', 'cris'))
    await waitFor(() => expect(service.getGroupDetailsRequest).toHaveBeenCalledTimes(2))
  })

  it('shows the server reason when an action is refused', async () => {
    service.getGroupDetailsRequest.mockResolvedValue(details('ADMIN'))
    service.changeParticipantRoleRequest.mockRejectedValue(new Error('El grupo debe tener al menos una administradora'))
    renderPanel({ currentUsername: 'ana' })

    fireEvent.click(await screen.findByLabelText('Hacer administradora a bea'))

    expect(await screen.findByRole('alert')).toHaveTextContent('al menos una administradora')
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
