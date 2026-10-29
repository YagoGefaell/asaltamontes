import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import GroupCreatorPanel from './GroupCreatorPanel.jsx'

vi.mock('../../../features/users/services/users.service.js', () => ({ getUsersByUsername: vi.fn() }))

describe('GroupCreatorPanel', () => {
  it('opens with the group picture as a centered tap target before the name', () => {
    const { container } = render(
      
        <GroupCreatorPanel isOpen creatingGroup={false} currentUsername="yo" onClose={vi.fn()} onSubmitGroup={vi.fn()} />
      ,
    )
    const picture = screen.getByRole('button', { name: 'Elegir imagen: imagen del grupo' })
    const firstField = container.querySelector('.group-creator-form').querySelector('button, input:not([type=file])')
    expect(firstField).toBe(picture)
    expect(picture.closest('.image-uploader--tap')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Elegir imagen' })).toBeNull()
    expect(screen.getByLabelText('Nombre del grupo')).toBeTruthy()
  })
})
