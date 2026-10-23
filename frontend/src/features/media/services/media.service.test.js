import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MEDIA_PURPOSE, uploadImage, validateImageFile } from './media.service.js'

class FakeXhr {
  static last
  constructor() {
    this.upload = {}
    this.headers = {}
    FakeXhr.last = this
  }
  open(method, url) { this.method = method; this.url = url }
  setRequestHeader(name, value) { this.headers[name] = value }
  send(body) { this.body = body }
  abort() { this.onabort?.() }
  respond(status, body) {
    this.status = status
    this.responseText = typeof body === 'string' ? body : JSON.stringify(body)
    this.onload()
  }
}

const file = (type = 'image/jpeg', size = 100) => {
  const f = new File(['x'], 'a.jpg', { type })
  Object.defineProperty(f, 'size', { value: size })
  return f
}

describe('media.service', () => {
  beforeEach(() => vi.stubGlobal('XMLHttpRequest', FakeXhr))
  afterEach(() => vi.unstubAllGlobals())

  it('validates type and size', () => {
    expect(validateImageFile(file())).toBeNull()
    expect(validateImageFile(file('application/pdf'))).toBe('El archivo no es una imagen')
    expect(validateImageFile(file('image/png', 11 * 1024 * 1024))).toMatch(/máximo 10 MB/)
  })

  it('posts the file and the purpose as multipart and reports progress', async () => {
    const onProgress = vi.fn()
    const promise = uploadImage(file(), MEDIA_PURPOSE.GROUP_IMAGE, { onProgress })
    const xhr = FakeXhr.last
    xhr.upload.onprogress({ lengthComputable: true, loaded: 50, total: 100 })
    xhr.respond(201, { id: 'x', urls: { medium: '/media/group-image/x/medium.jpg' } })

    await expect(promise).resolves.toEqual({ id: 'x', urls: { medium: '/media/group-image/x/medium.jpg' } })
    expect(xhr.method).toBe('POST')
    expect(xhr.url).toMatch(/\/api\/media$/)
    expect(xhr.body.get('purpose')).toBe('GROUP_IMAGE')
    expect(xhr.body.get('file')).toBeInstanceOf(File)
    expect(onProgress).toHaveBeenCalledWith(50)
  })

  it('uses the backend message for 400 and a clear one for 413', async () => {
    const bad = uploadImage(file(), 'AVATAR')
    FakeXhr.last.respond(400, { detail: 'La imagen es demasiado pequeña (mínimo 64 px por lado)' })
    await expect(bad).rejects.toThrow('La imagen es demasiado pequeña (mínimo 64 px por lado)')

    const big = uploadImage(file(), 'AVATAR')
    FakeXhr.last.respond(413, '')
    await expect(big).rejects.toThrow('La imagen es demasiado grande (máximo 10 MB)')
  })

  it('does not send an invalid file', async () => {
    FakeXhr.last = null
    await expect(uploadImage(file('text/plain'), 'AVATAR')).rejects.toThrow('El archivo no es una imagen')
    expect(FakeXhr.last).toBeNull()
  })
})
