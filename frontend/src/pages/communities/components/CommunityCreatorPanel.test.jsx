import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

vi.mock('../../../shared/ui/ImageUploader.jsx', () => ({
  default: ({ onChange }) => (
    <button type="button" onClick={() => onChange('/media/community-cover/abc/medium.jpg', 'abc')}>
      subir portada
    </button>
  ),
}))

import CommunityCreatorPanel from './CommunityCreatorPanel.jsx'

describe('CommunityCreatorPanel', () => {
  it('sends the uploaded media id as coverMediaId and never a free URL', async () => {
    const onSubmitCommunity = vi.fn().mockResolvedValue(undefined)
    render(<CommunityCreatorPanel isOpen creatingCommunity={false} onClose={vi.fn()} onSubmitCommunity={onSubmitCommunity} />)

    fireEvent.change(screen.getByLabelText('Nombre de la comunidad'), { target: { value: 'Trail Madrid' } })
    fireEvent.click(screen.getByText('subir portada'))
    fireEvent.submit(screen.getByLabelText('Nombre de la comunidad').closest('form'))

    await waitFor(() => expect(onSubmitCommunity).toHaveBeenCalled())
    const payload = onSubmitCommunity.mock.calls[0][0]
    expect(payload).toEqual({ name: 'Trail Madrid', description: null, coverMediaId: 'abc' })
    expect(payload).not.toHaveProperty('imageUrl')
  })
})
