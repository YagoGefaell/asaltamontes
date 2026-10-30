import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PostComments from './PostComments.jsx'
import { createCommentRequest, deleteCommentRequest, getCommentsRequest } from '../services/posts.service'

const auth = vi.hoisted(() => ({ currentUser: { username: 'yo' } }))
vi.mock('../../auth/hooks/useAuth', () => ({ useAuth: () => auth }))
vi.mock('../services/posts.service', () => ({
  COMMENT_BODY_MAX: 1000,
  getCommentsRequest: vi.fn(),
  createCommentRequest: vi.fn(),
  deleteCommentRequest: vi.fn(),
}))

const author = (username) => ({ username, fullName: username.toUpperCase(), profilePictureUrl: null })
const comment = (id, over = {}) => ({ id, body: `texto ${id}`, createdAt: new Date().toISOString(), author: author('bea'), canDelete: false, ...over })

const renderComments = (onCountChange = vi.fn()) =>
  render(<MemoryRouter><PostComments postId="p1" onCountChange={onCountChange} /></MemoryRouter>)

beforeEach(() => vi.clearAllMocks())

describe('PostComments', () => {
  it('lists the comments and loads more with the cursor', async () => {
    getCommentsRequest
      .mockResolvedValueOnce({ items: [comment('c1')], nextBefore: 'cur' })
      .mockResolvedValueOnce({ items: [comment('c2')], nextBefore: null })
    renderComments()
    expect(await screen.findByText('texto c1')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Cargar más comentarios' }))
    expect(await screen.findByText('texto c2')).toBeInTheDocument()
    expect(getCommentsRequest).toHaveBeenLastCalledWith('p1', 'cur')
  })

  it('invites to comment when there are none', async () => {
    getCommentsRequest.mockResolvedValue({ items: [], nextBefore: null })
    renderComments()
    expect(await screen.findByText('Sé la primera en comentar.')).toBeInTheDocument()
  })

  it('adds her comment at the end and reports the new count', async () => {
    getCommentsRequest.mockResolvedValue({ items: [comment('c1')], nextBefore: null })
    createCommentRequest.mockResolvedValue(comment('c9', { body: 'Qué bonito' }))
    const onCountChange = vi.fn()
    renderComments(onCountChange)
    await screen.findByText('texto c1')
    fireEvent.change(screen.getByLabelText('Escribe un comentario'), { target: { value: '  Qué bonito ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Enviar comentario' }))
    expect(await screen.findByText('Qué bonito')).toBeInTheDocument()
    expect(createCommentRequest).toHaveBeenCalledWith('p1', 'Qué bonito')
    expect(onCountChange).toHaveBeenCalledWith(1)
  })

  it('shows why she cannot comment, in place', async () => {
    getCommentsRequest.mockResolvedValue({ items: [], nextBefore: null })
    createCommentRequest.mockRejectedValue(Object.assign(new Error('x'), { status: 403 }))
    renderComments()
    await screen.findByText('Sé la primera en comentar.')
    fireEvent.change(screen.getByLabelText('Escribe un comentario'), { target: { value: 'hola' } })
    fireEvent.click(screen.getByRole('button', { name: 'Enviar comentario' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('No puedes comentar en esta publicación.')
  })

  it('offers Eliminar only on the comments with canDelete (owner of the profile post, D-027) and asks first', async () => {
    getCommentsRequest.mockResolvedValue({
      items: [comment('c1', { canDelete: true }), comment('c2')],
      nextBefore: null,
    })
    deleteCommentRequest.mockResolvedValue()
    const onCountChange = vi.fn()
    renderComments(onCountChange)
    await screen.findByText('texto c1')
    expect(screen.getAllByRole('button', { name: 'Eliminar comentario' })).toHaveLength(1)
    fireEvent.click(screen.getByRole('button', { name: 'Eliminar comentario' }))
    expect(deleteCommentRequest).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Eliminar' }))
    await waitFor(() => expect(screen.queryByText('texto c1')).toBeNull())
    expect(deleteCommentRequest).toHaveBeenCalledWith('p1', 'c1')
    expect(onCountChange).toHaveBeenCalledWith(-1)
  })

  it('offers «Denunciar comentario» on other people\'s comments and not on her own', async () => {
    getCommentsRequest.mockResolvedValue({
      items: [comment('c1'), comment('c2', { author: author('yo') })],
      nextBefore: null,
    })
    renderComments()
    await screen.findByText('texto c1')
    expect(screen.getAllByRole('button', { name: 'Denunciar comentario' })).toHaveLength(1)
    fireEvent.click(screen.getByRole('button', { name: 'Denunciar comentario' }))
    expect(screen.getByLabelText('Motivo')).toBeInTheDocument()
  })
})
