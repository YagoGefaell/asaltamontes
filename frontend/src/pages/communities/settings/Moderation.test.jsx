import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import MemberAdminList from './MemberAdminList'
import { BannedList, JoinRequestList } from './PersonRequestList'
import CommunityRequestButton from '../../../shared/components/CommunityRequestButton'
import * as service from '../../../features/communities/services/communities.service'

vi.mock('../../../features/communities/services/communities.service', () => ({
  getCommunityMembersPageRequest: vi.fn(),
  setMemberRoleRequest: vi.fn(),
  muteMemberRequest: vi.fn(),
  unmuteMemberRequest: vi.fn(),
  banMemberRequest: vi.fn(),
  unbanMemberRequest: vi.fn(),
  transferOwnershipRequest: vi.fn(),
  getBannedRequest: vi.fn(),
  getJoinRequestsRequest: vi.fn(),
  approveJoinRequest: vi.fn(),
  rejectJoinRequest: vi.fn(),
  joinCommunityRequest: vi.fn(),
}))

const wrap = (ui) => render(ui)
const members = [
  { username: 'ana', fullName: 'Ana', role: 'MODERATOR', status: 'ACTIVE' },
  { username: 'eva', fullName: 'Eva', role: 'MEMBER', status: 'MUTED' },
]

describe('MemberAdminList', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    service.getCommunityMembersPageRequest.mockResolvedValue({ items: members, hasMore: false })
  })

  it('owner sees every action and the muted mark', async () => {
    wrap(<MemberAdminList community={{ id: 'c1', role: 'OWNER' }} />)
    expect(await screen.findByText('Silenciada')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Quitar moderación' })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Transferir propiedad' })).toHaveLength(2)
  })

  it('moderator cannot act on another moderator', async () => {
    wrap(<MemberAdminList community={{ id: 'c1', role: 'MODERATOR' }} />)
    await screen.findByText('Eva')
    expect(screen.getAllByRole('button', { name: 'Expulsar' })).toHaveLength(1)
    expect(screen.queryByRole('button', { name: 'Hacer moderadora' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Transferir propiedad' })).toBeNull()
  })

  it('expels with a reason and removes her from the list', async () => {
    service.banMemberRequest.mockResolvedValue(null)
    wrap(<MemberAdminList community={{ id: 'c1', role: 'OWNER' }} />)
    await screen.findByText('Eva')
    fireEvent.click(screen.getAllByRole('button', { name: 'Expulsar' })[1])
    fireEvent.change(screen.getByLabelText('Motivo (opcional)'), { target: { value: 'spam' } })
    fireEvent.click(within_dialog('Expulsar'))
    await waitFor(() => expect(service.banMemberRequest).toHaveBeenCalledWith('c1', 'eva', 'spam'))
    await waitFor(() => expect(screen.queryByText('Eva')).toBeNull())
  })

  it('shows a clear message when the action is forbidden', async () => {
    service.muteMemberRequest.mockRejectedValue(Object.assign(new Error('x'), { status: 404 }))
    wrap(<MemberAdminList community={{ id: 'c1', role: 'OWNER' }} />)
    await screen.findByText('Ana')
    fireEvent.click(screen.getAllByRole('button', { name: /Silenciar/ })[0])
    expect(await screen.findByText(/no tienes permiso para gestionarlo/)).toBeInTheDocument()
  })
})

function within_dialog(name) {
  const dialog = screen.getByRole('dialog')
  return Array.from(dialog.querySelectorAll('button')).find((b) => b.textContent === name)
}

describe('BannedList and JoinRequestList', () => {
  beforeEach(() => vi.resetAllMocks())

  it('readmits an expelled member and shows who expelled her', async () => {
    service.getBannedRequest.mockResolvedValue({
      items: [{ id: 'b1', username: 'eva', fullName: 'Eva', bannedAt: '2026-10-20T10:00:00Z', reason: 'spam', bannedBy: 'ana' }],
      nextBefore: null,
    })
    service.unbanMemberRequest.mockResolvedValue(null)
    wrap(<BannedList community={{ id: 'c1' }} />)
    expect(await screen.findByText(/por @ana/)).toBeInTheDocument()
    expect(screen.getByText('Motivo: spam')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Readmitir' }))
    await waitFor(() => expect(service.unbanMemberRequest).toHaveBeenCalledWith('c1', 'eva'))
    await waitFor(() => expect(screen.queryByText('Eva')).toBeNull())
  })

  it('loads the next page with the opaque cursor', async () => {
    service.getBannedRequest
      .mockResolvedValueOnce({ items: [{ id: 'b1', username: 'eva', bannedAt: '2026-10-20T10:00:00Z' }], nextBefore: 'CURSOR' })
      .mockResolvedValueOnce({ items: [{ id: 'b2', username: 'lia', bannedAt: '2026-10-19T10:00:00Z' }], nextBefore: null })
    wrap(<BannedList community={{ id: 'c1' }} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Cargar más' }))
    expect(await screen.findByText('@lia')).toBeInTheDocument()
    expect(service.getBannedRequest).toHaveBeenLastCalledWith('c1', 'CURSOR')
  })

  it('approves and rejects requests', async () => {
    service.getJoinRequestsRequest.mockResolvedValue({
      items: [
        { id: 'r1', username: 'eva', requestedAt: '2026-10-20T10:00:00Z' },
        { id: 'r2', username: 'lia', requestedAt: '2026-10-20T11:00:00Z' },
      ],
      nextBefore: null,
    })
    service.approveJoinRequest.mockResolvedValue(null)
    service.rejectJoinRequest.mockResolvedValue(null)
    wrap(<JoinRequestList community={{ id: 'c1' }} />)
    await screen.findByText('@eva')
    fireEvent.click(screen.getAllByRole('button', { name: 'Aceptar' })[0])
    await waitFor(() => expect(service.approveJoinRequest).toHaveBeenCalledWith('c1', 'eva'))
    await waitFor(() => expect(screen.queryByText('@eva')).toBeNull())
    fireEvent.click(screen.getByRole('button', { name: 'Rechazar' }))
    await waitFor(() => expect(service.rejectJoinRequest).toHaveBeenCalledWith('c1', 'lia'))
  })
})

describe('CommunityRequestButton', () => {
  beforeEach(() => vi.resetAllMocks())

  it('sends the request and then shows it as sent', async () => {
    service.joinCommunityRequest.mockResolvedValue(null)
    const onRequested = vi.fn()
    render(<CommunityRequestButton communityId="c1" onRequested={onRequested} />)
    fireEvent.click(screen.getByRole('button', { name: 'Solicitar unirse' }))
    const sent = await screen.findByRole('button', { name: 'Solicitud enviada' })
    expect(sent).toBeDisabled()
    expect(onRequested).toHaveBeenCalled()
  })

  it('starts as sent when the backend says there is a pending request', () => {
    render(<CommunityRequestButton communityId="c1" joinRequested />)
    expect(screen.getByRole('button', { name: 'Solicitud enviada' })).toBeDisabled()
  })

  it('shows the cooldown from Retry-After on 429', async () => {
    service.joinCommunityRequest.mockRejectedValue(Object.assign(new Error('x'), { status: 429, retryAfter: 7200 }))
    render(<CommunityRequestButton communityId="c1" />)
    fireEvent.click(screen.getByRole('button', { name: 'Solicitar unirse' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('2 horas')
    expect(screen.getByRole('button', { name: 'Solicitar unirse' })).toBeEnabled()
  })

  it('shows the neutral message on 403 without inventing a reason', async () => {
    service.joinCommunityRequest.mockRejectedValue(Object.assign(new Error('No puedes unirte a esta comunidad'), { status: 403 }))
    render(<CommunityRequestButton communityId="c1" />)
    fireEvent.click(screen.getByRole('button', { name: 'Solicitar unirse' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('No puedes unirte a esta comunidad')
  })
})
