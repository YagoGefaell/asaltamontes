import { act, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const joinCommunityRequest = vi.fn()
const leaveCommunityRequest = vi.fn()
vi.mock('../../features/communities/services/communities.service', () => ({
  joinCommunityRequest: (...args) => joinCommunityRequest(...args),
  leaveCommunityRequest: (...args) => leaveCommunityRequest(...args),
}))

const { default: CommunityJoinButton } = await import('./CommunityJoinButton.jsx')

// Parent like Communities.jsx / CommunityProfileDetail.jsx: applies the change to role and memberCount
function Card({ initialRole = null, initialCount = 3 }) {
  const [community, setCommunity] = useState({ id: 7, role: initialRole, memberCount: initialCount })
  const onToggle = (newRole) => setCommunity((prev) => {
    const delta = newRole && !prev.role ? 1 : !newRole && prev.role ? -1 : 0
    return { ...prev, role: newRole, memberCount: prev.memberCount + delta }
  })
  return (
    <>
      <span data-testid="count">{community.memberCount}</span>
      <span data-testid="role">{String(community.role)}</span>
      <CommunityJoinButton communityId={community.id} role={community.role} onToggle={onToggle} delayBeforeRequest={10} />
    </>
  )
}

const flush = () => act(async () => { vi.advanceTimersByTime(20); await Promise.resolve(); await Promise.resolve() })

describe('CommunityJoinButton (BUG-045)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    joinCommunityRequest.mockReset()
    leaveCommunityRequest.mockReset()
  })
  afterEach(() => vi.useRealTimers())

  it('rolls back the count and the role when joining fails', async () => {
    joinCommunityRequest.mockRejectedValue(new Error('Ya eres miembro de esta comunidad'))
    render(<Card />)

    fireEvent.click(screen.getByRole('button'))
    expect(screen.getByTestId('count').textContent).toBe('4')
    await flush()

    expect(screen.getByTestId('count').textContent).toBe('3')
    expect(screen.getByTestId('role').textContent).toBe('null')
    expect(screen.getByText('Ya eres miembro de esta comunidad')).toBeTruthy()
  })

  it('gives a moderator back her role when leaving fails', async () => {
    leaveCommunityRequest.mockRejectedValue(new Error('Error de red'))
    render(<Card initialRole="MODERATOR" />)

    fireEvent.click(screen.getByRole('button'))
    expect(screen.getByTestId('role').textContent).toBe('null')
    await flush()

    expect(screen.getByTestId('role').textContent).toBe('MODERATOR')
    expect(screen.getByTestId('count').textContent).toBe('3')
  })

  it('after a failure, the next click tries again', async () => {
    joinCommunityRequest.mockRejectedValueOnce(new Error('Error de red')).mockResolvedValueOnce(null)
    render(<Card />)

    fireEvent.click(screen.getByRole('button'))
    await flush()
    fireEvent.click(screen.getByRole('button'))
    await flush()

    expect(joinCommunityRequest).toHaveBeenCalledTimes(2)
    expect(screen.getByTestId('count').textContent).toBe('4')
    expect(screen.getByTestId('role').textContent).toBe('MEMBER')
  })
})
