import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import Navbar from './NavBar.jsx'
import { NotificationsContext } from '../../features/notifications/hooks/notifications.context'
import { ConversationsContext } from '../../features/messages/hooks/conversations.context'

function renderBar(unreadCount) {
  return render(
    <MemoryRouter>
      <ConversationsContext.Provider value={{ totalUnreadCount: 0 }}>
        <NotificationsContext.Provider value={{ unreadCount }}>
          <Navbar />
        </NotificationsContext.Provider>
      </ConversationsContext.Provider>
    </MemoryRouter>,
  )
}

describe('NavBar notifications badge', () => {
  it('is hidden at zero', () => {
    renderBar(0)
    expect(screen.getByRole('link', { name: 'Notificaciones' })).toBeTruthy()
    expect(document.querySelector('a[href="/notifications"] .unread-count')).toBeNull()
  })

  it('shows the counter with an accessible label', () => {
    renderBar(4)
    expect(screen.getByRole('link', { name: 'Notificaciones, 4 sin leer' })).toBeTruthy()
    expect(document.querySelector('a[href="/notifications"] .unread-count').textContent).toBe('4')
  })

  it('caps at 9+', () => {
    renderBar(10)
    expect(document.querySelector('a[href="/notifications"] .unread-count').textContent).toBe('9+')
  })
})
