import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Notifications from './Notifications.jsx'
import { NotificationsContext } from '../../features/notifications/hooks/notifications.context'

vi.mock('../../shared/components/FollowUserButton', () => ({
  default: ({ userId, initialIsFollowing, followLabel, unfollowLabel }) => (
    <button type="button">{initialIsFollowing ? unfollowLabel : followLabel} {userId}</button>
  ),
}))

const actions = { loadMore: vi.fn(), refresh: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn() }

function renderPage(value) {
  return render(
    <MemoryRouter initialEntries={['/notifications']}>
      <NotificationsContext.Provider value={{ unreadCount: 0, items: [], hasMore: false, loading: false, error: null, ...actions, ...value }}>
        <Routes>
          <Route path="/notifications" element={<Notifications />} />
          <Route path="/events/:id" element={<p>detalle del evento</p>} />
        </Routes>
      </NotificationsContext.Provider>
    </MemoryRouter>,
  )
}

const follower = {
  id: 'n1', type: 'NEW_FOLLOWER', actor: { username: 'ana', fullName: 'Ana Ruiz' }, target: { type: 'USER', ref: 'ana' },
  payload: {}, readAt: null, createdAt: new Date().toISOString(),
}
const cancelled = {
  id: 'n2', type: 'EVENT_CANCELLED', actor: null, target: { type: 'EVENT', ref: 'ev-1' },
  payload: { title: 'Trail', reason: 'Viento' }, readAt: '2026-10-27T10:00:00Z', createdAt: new Date().toISOString(),
}

describe('Notifications page', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows the empty state', () => {
    renderPage({})
    expect(screen.getByText('No tienes notificaciones')).toBeTruthy()
  })

  it('lists them and keeps the new ones highlighted while marking everything as read on entering', () => {
    renderPage({ items: [follower, cancelled], unreadCount: 1 })

    expect(screen.getByText('Ana Ruiz ha empezado a seguirte')).toBeTruthy()
    expect(screen.getByText('«Trail» se ha cancelado')).toBeTruthy()
    expect(screen.getByText('Ana Ruiz ha empezado a seguirte').closest('li').className).toContain('unread')
    expect(screen.getByText('«Trail» se ha cancelado').closest('li').className).not.toContain('unread')
    expect(actions.markAllRead).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('button', { name: 'Marcar todas como leídas' })).toBeNull()
  })

  it('does not mark anything when nothing is unread', () => {
    renderPage({ items: [cancelled] })
    expect(actions.markAllRead).not.toHaveBeenCalled()
  })

  it('a new follower gets a follow-back button, or "Siguiendo" if already followed', () => {
    const followed = { ...follower, id: 'n3', actor: { ...follower.actor, username: 'bea', followedByMe: true } }
    renderPage({ items: [follower, followed, cancelled] })

    expect(screen.getByText('Seguir también ana')).toBeTruthy()
    expect(screen.getByText('Siguiendo bea')).toBeTruthy()
    expect(screen.getAllByText(/Seguir también|Siguiendo/)).toHaveLength(2)
  })

  it('a click marks it as read and goes to its target', () => {
    renderPage({ items: [cancelled] })

    fireEvent.click(screen.getByText('«Trail» se ha cancelado'))

    expect(actions.markRead).toHaveBeenCalledWith(cancelled)
    expect(screen.getByText('detalle del evento')).toBeTruthy()
  })

  it('"Cargar más" asks for the next page', () => {
    renderPage({ items: [cancelled], hasMore: true })
    fireEvent.click(screen.getByText('Cargar más'))
    expect(actions.loadMore).toHaveBeenCalled()
  })

  it('shows a load error with a retry', () => {
    renderPage({ error: 'Sin conexión' })
    expect(screen.getByRole('alert').textContent).toContain('Sin conexión')
    fireEvent.click(screen.getByText('Reintentar'))
    expect(actions.refresh).toHaveBeenCalled()
  })
})
