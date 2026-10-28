import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ImageUploader from './ImageUploader.jsx'
import { ToastProvider } from './Toast.jsx'
import { uploadImage } from '../../features/media/services/media.service.js'

vi.mock('../../features/media/services/media.service.js', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, uploadImage: vi.fn() }
})

function setup(props = {}) {
  const onChange = vi.fn()
  const onUploadingChange = vi.fn()
  render(
    <ToastProvider>
      <ImageUploader value="" onChange={onChange} onUploadingChange={onUploadingChange} purpose="AVATAR" {...props} />
    </ToastProvider>,
  )
  return { onChange, onUploadingChange, input: screen.getByTestId('image-uploader-input') }
}

function pick(input, file) {
  fireEvent.change(input, { target: { files: [file] } })
}

const image = (name = 'foto.jpg', type = 'image/jpeg', size = 1000) => {
  const file = new File(['x'], name, { type })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

describe('ImageUploader', () => {
  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:preview')
    URL.revokeObjectURL = vi.fn()
  })
  afterEach(() => vi.clearAllMocks())

  it('rejects files over 10 MB without calling the API', () => {
    const { onChange, input } = setup()
    pick(input, image('enorme.jpg', 'image/jpeg', 10 * 1024 * 1024 + 1))

    expect(screen.getByRole('alert')).toHaveTextContent('La imagen es demasiado grande (máximo 10 MB)')
    expect(uploadImage).not.toHaveBeenCalled()
    expect(onChange).not.toHaveBeenCalled()
  })

  it('rejects files that are not images without calling the API', () => {
    const { input } = setup()
    pick(input, image('documento.pdf', 'application/pdf'))

    expect(screen.getByRole('alert')).toHaveTextContent('El archivo no es una imagen')
    expect(uploadImage).not.toHaveBeenCalled()
  })

  it('shows the local preview and the progress while uploading, then reports the uploaded url', async () => {
    let finish
    uploadImage.mockImplementation((file, purpose, { onProgress }) => {
      onProgress(40)
      return new Promise((resolve) => { finish = resolve })
    })
    const { onChange, onUploadingChange, input } = setup()

    pick(input, image())

    const bar = await screen.findByRole('progressbar', { name: 'Subiendo imagen' })
    expect(bar).toHaveAttribute('aria-valuenow', '40')
    expect(screen.getByRole('img', { name: 'Foto' })).toHaveAttribute('src', 'blob:preview')
    expect(uploadImage).toHaveBeenCalledWith(expect.any(File), 'AVATAR', expect.any(Object))
    expect(onUploadingChange).toHaveBeenLastCalledWith(true)

    await act(async () => finish({ id: 'abc', urls: { medium: '/media/avatar/abc/medium.jpg' } }))

    await waitFor(() => expect(onChange).toHaveBeenCalledWith('/media/avatar/abc/medium.jpg', 'abc'))
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    expect(onUploadingChange).toHaveBeenLastCalledWith(false)
  })

  it('shows the error of the backend and lets the user retry', async () => {
    uploadImage
      .mockRejectedValueOnce(new Error('El archivo no es una imagen válida (usa JPEG, PNG o WebP)'))
      .mockResolvedValueOnce({ id: 'abc', urls: { medium: '/media/avatar/abc/medium.jpg' } })
    const { onChange, input } = setup()

    pick(input, image())

    expect(await screen.findByRole('alert')).toHaveTextContent('El archivo no es una imagen válida (usa JPEG, PNG o WebP)')
    expect(onChange).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))

    await waitFor(() => expect(onChange).toHaveBeenCalledWith('/media/avatar/abc/medium.jpg', 'abc'))
    expect(uploadImage).toHaveBeenCalledTimes(2)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('removes the current image', () => {
    const { onChange } = setup({ value: '/media/avatar/abc/medium.jpg' })
    fireEvent.click(screen.getByRole('button', { name: 'Quitar' }))
    expect(onChange).toHaveBeenCalledWith('', null)
  })

  it('with tapToChoose the image itself opens the picker, without a separate button', () => {
    const { input } = setup({ tapToChoose: true, label: 'Foto de perfil', fallbackSrc: '/avatar.png' })
    const click = vi.spyOn(input, 'click')

    expect(screen.queryByRole('button', { name: 'Elegir foto' })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Elegir foto: foto de perfil' }))
    expect(click).toHaveBeenCalledTimes(1)
  })
})
