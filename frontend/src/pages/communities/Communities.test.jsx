import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const hookState = vi.hoisted(() => ({ value: null }))

vi.mock('../../features/communities/hooks/useCommunities.js', () => ({
  useCommunities: () => hookState.value,
}))
vi.mock('../../shared/components/CommunityJoinButton', () => ({
  default: () => <button type="button">Unirse</button>,
}))

import Communities from './Communities.jsx'
import { ToastProvider } from '../../shared/ui/Toast.jsx'

function state(overrides = {}) {
  return {
    communities: [],
    setCommunities: vi.fn(),
    loading: false,
    loadingMore: false,
    hasMore: false,
    loadMore: vi.fn(),
    error: null,
    refetch: vi.fn(),
    ...overrides,
  }
}

function renderPage() {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <Communities />
      </ToastProvider>
    </MemoryRouter>,
  )
}

describe('Communities (T29b)', () => {
  beforeEach(() => {
    hookState.value = state()
  })

  it('shows skeleton cards while loading', () => {
    hookState.value = state({ loading: true })
    renderPage()
    expect(screen.getAllByTestId('community-skeleton').length).toBeGreaterThan(0)
  })

  it('shows the error with a retry button', () => {
    hookState.value = state({ error: 'Error obteniendo comunidades' })
    renderPage()
    expect(screen.getByRole('alert')).toHaveTextContent('Error obteniendo comunidades')
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(hookState.value.refetch).toHaveBeenCalled()
  })

  it('offers to create a community when there are none', () => {
    renderPage()
    expect(screen.getByText('Todavía no hay comunidades')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Crear comunidad' })).toHaveLength(2)
  })

  it('links each card to its community without nesting buttons in the link', () => {
    hookState.value = state({
      communities: [{ id: 1, name: 'Trail Madrid', slug: 'trail-madrid', memberCount: 1, role: null, imageUrl: null }],
    })
    renderPage()
    const link = screen.getByRole('link', { name: /Trail Madrid/ })
    expect(link).toHaveAttribute('href', '/communities/trail-madrid')
    expect(link).toHaveTextContent('1 miembro')
    expect(link.querySelector('button')).toBeNull()
  })

  it('disables load more while loading the next page', () => {
    hookState.value = state({
      communities: [{ id: 1, name: 'A', slug: 'a', memberCount: 2, role: null }],
      hasMore: true,
      loadingMore: true,
    })
    renderPage()
    expect(screen.getByRole('button', { name: /Cargando/ })).toBeDisabled()
  })
})
