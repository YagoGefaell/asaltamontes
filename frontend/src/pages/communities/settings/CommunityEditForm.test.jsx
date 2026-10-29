import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import CommunityEditForm from './CommunityEditForm.jsx'

vi.mock('../../../features/communities/services/communities.service', () => ({ updateCommunityRequest: vi.fn() }))

const community = { id: 'c1', name: 'Trail Vigo', role: 'OWNER', visibility: 'PUBLIC', imageUrl: '/media/community-cover/x/medium.jpg' }

describe('CommunityEditForm', () => {
  it('starts with the round picture to tap, as in the creator, before the data', () => {
    const { container } = render(
      
        <CommunityEditForm community={community} />
      ,
    )
    const picture = screen.getByRole('button', { name: 'Cambiar imagen: imagen de la comunidad' })
    expect(container.querySelector('form').firstElementChild).toBe(picture.closest('.image-uploader'))
    expect(picture.closest('.image-uploader')).toHaveClass('image-uploader--round', 'image-uploader--tap')
    expect(screen.queryByRole('heading', { name: 'Imagen' })).toBeNull()
  })
})
