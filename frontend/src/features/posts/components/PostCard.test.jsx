import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PostCard from './PostCard.jsx'
import { deletePostRequest, likePostRequest, unlikePostRequest } from '../services/posts.service'

vi.mock('../services/posts.service', () => ({
  likePostRequest: vi.fn(),
  unlikePostRequest: vi.fn(),
  deletePostRequest: vi.fn(),
}))

const post = {
  id: 'p1',
  body: 'Salida larga por el Moncayo',
  createdAt: new Date().toISOString(),
  author: { username: 'ana', fullName: 'Ana Ruiz', profilePictureUrl: null },
  community: { slug: 'cabras', name: 'Cabras' },
  media: [{ thumb: 't1', medium: 'm1', large: 'l1' }],
  likeCount: 2,
  commentCount: 3,
  likedByMe: false,
  canDelete: false,
}
const renderCard = (over = {}, props = {}) =>
  render(<MemoryRouter><PostCard post={{ ...post, ...over }} {...props} /></MemoryRouter>)

beforeEach(() => vi.clearAllMocks())

describe('PostCard', () => {
  it('shows author, community, text, photo and counters', () => {
    renderCard()
    expect(screen.getByText('Ana Ruiz')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Cabras' })).toHaveAttribute('href', '/communities/cabras')
    expect(screen.getByText('Salida larga por el Moncayo')).toBeInTheDocument()
    expect(document.querySelector('.post-card__media img')).toHaveAttribute('src', 'l1')
    expect(screen.getByRole('link', { name: '3 comentarios' })).toHaveAttribute('href', '/posts/p1')
  })

  it('likes with the state the server answers and undoes it', async () => {
    likePostRequest.mockResolvedValue({ likedByMe: true, likeCount: 3 })
    unlikePostRequest.mockResolvedValue({ likedByMe: false, likeCount: 2 })
    renderCard()
    fireEvent.click(screen.getByRole('button', { name: 'Me gusta' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Quitar me gusta' })).toHaveTextContent('3'))
    fireEvent.click(screen.getByRole('button', { name: 'Quitar me gusta' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Me gusta' })).toHaveTextContent('2'))
  })

  it('shows the error in place when the like fails', async () => {
    likePostRequest.mockRejectedValue(new Error('No se ha podido dar me gusta'))
    renderCard()
    fireEvent.click(screen.getByRole('button', { name: 'Me gusta' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('No se ha podido dar me gusta')
    expect(screen.getByRole('button', { name: 'Me gusta' })).toHaveTextContent('2')
  })

  it('offers no options when she cannot delete', () => {
    renderCard()
    expect(screen.queryByRole('button', { name: 'Más opciones' })).toBeNull()
  })

  it('deletes after confirming in the sheet, only when canDelete', async () => {
    deletePostRequest.mockResolvedValue()
    const onDeleted = vi.fn()
    renderCard({ canDelete: true }, { onDeleted })
    fireEvent.click(screen.getByRole('button', { name: 'Más opciones' }))
    fireEvent.click(screen.getByRole('button', { name: 'Eliminar publicación' }))
    expect(deletePostRequest).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Eliminar' }))
    await waitFor(() => expect(onDeleted).toHaveBeenCalled())
    expect(deletePostRequest).toHaveBeenCalledWith('p1')
  })
})
