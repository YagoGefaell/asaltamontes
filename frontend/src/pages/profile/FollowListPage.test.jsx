import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import FollowListPage from './FollowListPage.jsx'

vi.mock('../../features/auth/hooks/useAuth', () => ({ useAuth: () => ({ currentUser: { username: 'yo' } }) }))
vi.mock('../../shared/components/FollowUserButton', () => ({
  default: ({ userId, initialIsFollowing }) => (
    <button>{`${initialIsFollowing ? 'Siguiendo' : 'Seguir'} ${userId}`}</button>
  ),
}))

const page = {
  content: [
    { username: 'ana', fullName: 'Ana Pérez', isFollowing: true },
    { username: 'lucia', fullName: 'Lucía Gómez', isFollowing: false },
    { username: 'yo', fullName: 'Yo misma', isFollowing: false },
  ],
  last: true,
}

const renderList = () =>
  render(
    <MemoryRouter>
      <FollowListPage
        type="followers"
        title="Seguidores"
        fetchFunction={vi.fn().mockResolvedValue(page)}
        emptyMessage="Sin seguidores"
        searchPlaceholder="Buscar en seguidores..."
        username="marta"
      />
    </MemoryRouter>,
  )

describe('FollowListPage', () => {
  it('shows follow or unfollow for every user except herself', async () => {
    renderList()
    expect(await screen.findByRole('button', { name: 'Siguiendo ana' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Seguir lucia' })).toBeTruthy()
    expect(screen.getByText('Yo misma')).toBeTruthy()
    expect(screen.queryByRole('button', { name: /yo$/ })).toBeNull()
  })

  it('filters with the same search field as Search and clears it', async () => {
    renderList()
    await screen.findByText('Ana Pérez')
    expect(screen.getByRole('search')).toBeTruthy()
    const field = screen.getByRole('searchbox', { name: 'Buscar en seguidores...' })
    fireEvent.change(field, { target: { value: 'luc' } })
    expect(screen.queryByText('Ana Pérez')).toBeNull()
    expect(screen.getByText('Lucía Gómez')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Borrar búsqueda' }))
    expect(screen.getByText('Ana Pérez')).toBeTruthy()
  })
})
