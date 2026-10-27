import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import Navbar from './NavBar.jsx'

function renderBar(path = '/home') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Navbar />
    </MemoryRouter>,
  )
}

describe('NavBar', () => {
  it('has exactly the 5 destinations, in order, with accessible names', () => {
    renderBar()
    const links = screen.getAllByRole('link')
    expect(links.map((l) => l.getAttribute('aria-label'))).toEqual([
      'Inicio', 'Buscar', 'Quedadas', 'Comunidades', 'Perfil',
    ])
    expect(links.map((l) => l.getAttribute('href'))).toEqual([
      '/home', '/search', '/events', '/communities', '/me',
    ])
  })

  it('no longer holds notifications or messages', () => {
    renderBar()
    expect(screen.queryByRole('link', { name: /Notificaciones/ })).toBeNull()
    expect(screen.queryByRole('link', { name: /Mensajes/ })).toBeNull()
  })

  it('marks the current section with aria-current', () => {
    renderBar('/communities/mi-club')
    expect(screen.getByRole('link', { name: 'Comunidades' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Inicio' })).not.toHaveAttribute('aria-current')
  })
})
