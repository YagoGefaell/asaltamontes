import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import Home from './Home.jsx'
import { NotificationsContext } from '../../features/notifications/hooks/notifications.context'
import { ConversationsContext } from '../../features/messages/hooks/conversations.context'

function renderHome({ notifications = 0, messages = 0 } = {}) {
  return render(
    <MemoryRouter>
      <ConversationsContext.Provider value={{ totalUnreadCount: messages }}>
        <NotificationsContext.Provider value={{ unreadCount: notifications }}>
          <Home />
        </NotificationsContext.Provider>
      </ConversationsContext.Provider>
    </MemoryRouter>,
  )
}

describe('Home header', () => {
  it('links to notifications and messages without counters at zero', () => {
    renderHome()
    expect(screen.getByRole('link', { name: 'Notificaciones' })).toHaveAttribute('href', '/notifications')
    expect(screen.getByRole('link', { name: 'Mensajes' })).toHaveAttribute('href', '/messages')
    expect(document.querySelector('.home-header__badge')).toBeNull()
  })

  it('shows each counter with an accessible label', () => {
    renderHome({ notifications: 4, messages: 2 })
    expect(screen.getByRole('link', { name: 'Notificaciones, 4 sin leer' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Mensajes, 2 sin leer' })).toBeTruthy()
  })

  it('caps counters at 9+', () => {
    renderHome({ notifications: 10, messages: 25 })
    const badges = [...document.querySelectorAll('.home-header__badge')].map((b) => b.textContent)
    expect(badges).toEqual(['9+', '9+'])
  })
})
