import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PostCard from './PostCard.jsx'
import { deletePostRequest, likePostRequest, unlikePostRequest } from '../services/posts.service'

const auth = vi.hoisted(() => ({ currentUser: { username: 'yo' } }))
vi.mock('../../auth/hooks/useAuth', () => ({ useAuth: () => auth }))
vi.mock('./PostComments', () => ({ default: ({ postId }) => <div>comentarios de {postId}</div> }))
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
    expect(screen.getByRole('button', { name: '3 comentarios' })).toBeInTheDocument()
  })

  it('opens the comments in a bottom sheet from the comment button', () => {
    renderCard()
    expect(screen.queryByText('comentarios de p1')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: '3 comentarios' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('comentarios de p1')
    // Always half the screen, with 1 comment or 30: the list scrolls inside
    expect(screen.getByRole('dialog')).toHaveClass('bottom-sheet--half')
  })

  it('starts with the comments open when asked (notification link)', () => {
    renderCard({}, { openComments: true })
    expect(screen.getByText('comentarios de p1')).toBeInTheDocument()
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

  it('offers no options on her own post when she cannot delete', () => {
    renderCard({ author: { ...post.author, username: 'yo' } })
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

  it('offers «Denunciar publicación» on another author\'s post and not on her own', () => {
    renderCard()
    fireEvent.click(screen.getByRole('button', { name: 'Más opciones' }))
    expect(screen.getByRole('button', { name: 'Denunciar publicación' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Eliminar publicación' })).toBeNull()
  })
})
