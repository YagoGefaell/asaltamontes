import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

vi.mock('../../../shared/ui/ImageUploader.jsx', () => ({
  default: ({ onChange, tapToChoose }) => (
    <button type="button" data-tap={String(Boolean(tapToChoose))} onClick={() => onChange('/media/community-cover/abc/medium.jpg', 'abc')}>
      subir portada
    </button>
  ),
}))

import CommunityCreatorPanel from './CommunityCreatorPanel.jsx'

describe('CommunityCreatorPanel', () => {
  it('sends the uploaded media id as coverMediaId and never a free URL', async () => {
    const onSubmitCommunity = vi.fn().mockResolvedValue(undefined)
    render(<CommunityCreatorPanel creatingCommunity={false} onClose={vi.fn()} onSubmitCommunity={onSubmitCommunity} />)

    fireEvent.change(screen.getByLabelText('Nombre de la comunidad'), { target: { value: 'Trail Madrid' } })
    fireEvent.click(screen.getByText('subir portada'))
    fireEvent.submit(screen.getByLabelText('Nombre de la comunidad').closest('form'))

    await waitFor(() => expect(onSubmitCommunity).toHaveBeenCalled())
    const payload = onSubmitCommunity.mock.calls[0][0]
    expect(payload).toEqual({ name: 'Trail Madrid', description: null, coverMediaId: 'abc' })
    expect(payload).not.toHaveProperty('imageUrl')
  })

  it('starts with the picture to tap, as in the group creator, and then the information', () => {
    render(<CommunityCreatorPanel creatingCommunity={false} onClose={vi.fn()} onSubmitCommunity={vi.fn()} />)
    const form = screen.getByLabelText('Nombre de la comunidad').closest('form')
    expect(form.firstElementChild).toBe(screen.getByText('subir portada'))
    expect(screen.getByText('subir portada')).toHaveAttribute('data-tap', 'true')
  })
})
