import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PostCreate from './PostCreate.jsx'
import { createPostRequest } from '../../features/posts/services/posts.service'
import { getOrganizableCommunitiesRequest } from '../../features/communities/services/communities.service'
import { uploadImage } from '../../features/media/services/media.service'

vi.mock('../../features/posts/services/posts.service', () => ({
  POST_BODY_MAX: 2000,
  POST_MEDIA_MAX: 4,
  createPostRequest: vi.fn(),
}))
vi.mock('../../features/communities/services/communities.service', () => ({ getOrganizableCommunitiesRequest: vi.fn() }))
vi.mock('../../features/media/services/media.service', () => ({
  MEDIA_PURPOSE: { POST_IMAGE: 'POST_IMAGE' },
  validateImageFile: () => null,
  uploadImage: vi.fn(),
}))

const renderAt = (url) => render(
  <MemoryRouter initialEntries={[url]}>
    <Routes>
      <Route path="/posts/new" element={<PostCreate />} />
      <Route path="/posts/:id" element={<p>detalle</p>} />
    </Routes>
  </MemoryRouter>,
)

beforeEach(() => {
  vi.clearAllMocks()
  getOrganizableCommunitiesRequest.mockResolvedValue([{ id: 'c1', slug: 'cabras', name: 'Cabras' }])
})

describe('PostCreate', () => {
  it('publishes on her profile and lands on the new publication', async () => {
    createPostRequest.mockResolvedValue({ id: 'p9' })
    renderAt('/posts/new')
    fireEvent.change(screen.getByLabelText('Texto'), { target: { value: ' Hola montaña ' } })
    expect(screen.getByText('14/2000')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Publicar' }))
    expect(await screen.findByText('detalle')).toBeInTheDocument()
    expect(createPostRequest).toHaveBeenCalledWith({ communitySlug: null, body: 'Hola montaña', mediaIds: [] })
  })

  it('publishes in a community chosen in the form, listing only those she moderates', async () => {
    createPostRequest.mockResolvedValue({ id: 'p9' })
    renderAt('/posts/new')
    await screen.findByRole('option', { name: 'Cabras' })
    expect(screen.getByLabelText('Publicar en')).toHaveValue('')
    fireEvent.change(screen.getByLabelText('Publicar en'), { target: { value: 'cabras' } })
    fireEvent.change(screen.getByLabelText('Texto'), { target: { value: 'Quedada del sábado' } })
    fireEvent.click(screen.getByRole('button', { name: 'Publicar' }))
    await waitFor(() => expect(createPostRequest).toHaveBeenCalledWith({ communitySlug: 'cabras', body: 'Quedada del sábado', mediaIds: [] }))
  })

  it('does not send an empty text', () => {
    renderAt('/posts/new')
    fireEvent.click(screen.getByRole('button', { name: 'Publicar' }))
    expect(screen.getByText('Escribe algo para publicar')).toBeInTheDocument()
    expect(createPostRequest).not.toHaveBeenCalled()
  })

  it('uploads photos with the POST_IMAGE purpose and sends their ids; she can remove one', async () => {
    uploadImage
      .mockResolvedValueOnce({ id: 'm1', urls: { medium: 'u1' } })
      .mockResolvedValueOnce({ id: 'm2', urls: { medium: 'u2' } })
    createPostRequest.mockResolvedValue({ id: 'p9' })
    renderAt('/posts/new')
    const file = (name) => new File(['x'], name, { type: 'image/png' })
    fireEvent.change(screen.getByTestId('post-photos-input'), { target: { files: [file('a.png'), file('b.png')] } })
    await waitFor(() => expect(screen.getAllByRole('button', { name: 'Quitar foto' })).toHaveLength(2))
    expect(uploadImage).toHaveBeenCalledWith(expect.any(File), 'POST_IMAGE')
    fireEvent.click(screen.getAllByRole('button', { name: 'Quitar foto' })[0])
    fireEvent.change(screen.getByLabelText('Texto'), { target: { value: 'Con foto' } })
    fireEvent.click(screen.getByRole('button', { name: 'Publicar' }))
    await waitFor(() => expect(createPostRequest).toHaveBeenCalledWith({ communitySlug: null, body: 'Con foto', mediaIds: ['m2'] }))
  })

  it('explains a 429 and a 403 above the button', async () => {
    createPostRequest.mockRejectedValueOnce(Object.assign(new Error('x'), { status: 429, retryAfter: 600 }))
    renderAt('/posts/new')
    fireEvent.change(screen.getByLabelText('Texto'), { target: { value: 'Hola' } })
    fireEvent.click(screen.getByRole('button', { name: 'Publicar' }))
    expect(await screen.findByText('Has publicado mucho hoy. Inténtalo de nuevo en 10 min.')).toBeInTheDocument()
  })

  it('tells her to verify her email on EMAIL_NOT_VERIFIED, not the community message', async () => {
    createPostRequest.mockRejectedValueOnce(
      Object.assign(new Error('Verifica tu email para poder hacer esto'), { status: 403, code: 'EMAIL_NOT_VERIFIED' }))
    renderAt('/posts/new')
    fireEvent.change(screen.getByLabelText('Texto'), { target: { value: 'Hola' } })
    fireEvent.click(screen.getByRole('button', { name: 'Publicar' }))
    expect(await screen.findByText('Verifica tu email para poder hacer esto')).toBeInTheDocument()
  })
})
