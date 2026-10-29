import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import Avatar from './Avatar.jsx'
import EmptyState from './EmptyState.jsx'
import SectionCard from './SectionCard.jsx'
import Button from '../forms/Button.jsx'

describe('Button', () => {
  it('applies the variant class and falls back to primary', () => {
    const { rerender } = render(<Button variant="danger">Borrar</Button>)
    expect(screen.getByRole('button', { name: 'Borrar' })).toHaveClass('btn', 'danger')
    rerender(<Button variant="ghost">Borrar</Button>)
    expect(screen.getByRole('button')).toHaveClass('ghost')
    rerender(<Button variant="nope">Borrar</Button>)
    expect(screen.getByRole('button')).toHaveClass('primary')
  })

  it('disables itself and does not fire onClick while loading', () => {
    const onClick = vi.fn()
    render(<Button loading onClick={onClick}>Guardar</Button>)
    const button = screen.getByRole('button')
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'true')
    fireEvent.click(button)
    expect(onClick).not.toHaveBeenCalled()
  })
})

describe('Avatar', () => {
  it('shows initials when there is no image', () => {
    render(<Avatar name="laura" />)
    expect(screen.getByRole('img', { name: 'laura' })).toHaveTextContent('L')
  })

  it('falls back to initials when the image fails to load', () => {
    render(<Avatar src="http://x/broken.png" name="Marta" />)
    fireEvent.error(screen.getByRole('img'))
    expect(screen.getByRole('img', { name: 'Marta' })).toHaveTextContent('M')
  })

  it('takes a named size or a number of pixels', () => {
    const { rerender } = render(<Avatar name="Ana" size="lg" />)
    expect(screen.getByRole('img')).toHaveStyle({ width: '64px', height: '64px' })
    rerender(<Avatar name="Ana" size={80} />)
    expect(screen.getByRole('img')).toHaveStyle({ width: '80px', height: '80px' })
  })
})

describe('EmptyState', () => {
  it('renders title, description and action', () => {
    render(<EmptyState title="Sin datos" description="Prueba otra búsqueda" action={<button>Reintentar</button>} />)
    expect(screen.getByText('Sin datos')).toBeInTheDocument()
    expect(screen.getByText('Prueba otra búsqueda')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })
})

describe('SectionCard', () => {
  it('renders an optional title above its content', () => {
    const { rerender } = render(<SectionCard title="Imagen"><p>contenido</p></SectionCard>)
    expect(screen.getByRole('heading', { name: 'Imagen' })).toBeInTheDocument()
    expect(screen.getByText('contenido')).toBeInTheDocument()
    rerender(<SectionCard><p>contenido</p></SectionCard>)
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
  })
})
