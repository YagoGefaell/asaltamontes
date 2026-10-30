import { apiClient } from '../../../lib/api-client.js'

export const POST_BODY_MAX = 2000
export const COMMENT_BODY_MAX = 1000
export const POST_MEDIA_MAX = 4

// Keeps status and retryAfter so the screens can tell 403/404/429 apart
async function call(request, fallback) {
  try {
    return await request()
  } catch (error) {
    const wrapped = new Error(error.message || fallback)
    wrapped.status = error.status
    wrapped.retryAfter = error.retryAfter
    wrapped.errors = error.errors
    throw wrapped
  }
}

function cursorQuery(name, cursor, limit) {
  const params = new URLSearchParams({ limit: String(limit) })
  if (cursor) params.set(name, cursor)
  return params.toString()
}

// Every list answers { items, nextBefore } (nextBefore is also the `after` of the comments)
const toPage = (response) => ({
  items: Array.isArray(response?.items) ? response.items : [],
  nextBefore: response?.nextBefore ?? null,
})

/** `communitySlug` null = on her own profile. `mediaIds` are the ids returned by POST /api/media (POST_IMAGE). */
export const createPostRequest = ({ communitySlug = null, body, mediaIds = [] }) =>
  call(() => apiClient.post('/api/posts', { communitySlug, body, mediaIds }), 'No se ha podido publicar')

export const getPostRequest = (id) =>
  call(() => apiClient.get(`/api/posts/${encodeURIComponent(id)}`), 'No se ha podido cargar la publicación')

export const deletePostRequest = (id) =>
  call(() => apiClient.delete(`/api/posts/${encodeURIComponent(id)}`), 'No se ha podido eliminar la publicación')

export const getCommunityPostsRequest = (slug, before = null, limit = 10) =>
  call(() => apiClient.get(`/api/communities/${encodeURIComponent(slug)}/posts?${cursorQuery('before', before, limit)}`), 'No se han podido cargar las publicaciones')
    .then(toPage)

export const getUserPostsRequest = (username, before = null, limit = 10) =>
  call(() => apiClient.get(`/api/users/${encodeURIComponent(username)}/posts?${cursorQuery('before', before, limit)}`), 'No se han podido cargar las publicaciones')
    .then(toPage)

/** Home: posts of her communities and of the people she follows, newest first. */
export const getFeedRequest = (before = null, limit = 10) =>
  call(() => apiClient.get(`/api/feed?${cursorQuery('before', before, limit)}`), 'No se ha podido cargar el inicio')
    .then(toPage)

/** Search grid: posts with a photo to discover what she does not follow. */
export const getExplorePostsRequest = (before = null, limit = 21) =>
  call(() => apiClient.get(`/api/explore/posts?${cursorQuery('before', before, limit)}`), 'No se han podido cargar las publicaciones')
    .then(toPage)

/** Idempotent: both answer { likedByMe, likeCount } as they are after the call. */
export const likePostRequest = (id) =>
  call(() => apiClient.put(`/api/posts/${encodeURIComponent(id)}/like`, {}), 'No se ha podido dar me gusta')

export const unlikePostRequest = (id) =>
  call(() => apiClient.delete(`/api/posts/${encodeURIComponent(id)}/like`), 'No se ha podido quitar el me gusta')

/** Oldest first; `after` is the nextBefore of the previous page. */
export const getCommentsRequest = (id, after = null, limit = 20) =>
  call(() => apiClient.get(`/api/posts/${encodeURIComponent(id)}/comments?${cursorQuery('after', after, limit)}`), 'No se han podido cargar los comentarios')
    .then(toPage)

export const createCommentRequest = (id, body) =>
  call(() => apiClient.post(`/api/posts/${encodeURIComponent(id)}/comments`, { body }), 'No se ha podido comentar')

export const deleteCommentRequest = (id, commentId) =>
  call(() => apiClient.delete(`/api/posts/${encodeURIComponent(id)}/comments/${encodeURIComponent(commentId)}`), 'No se ha podido eliminar el comentario')
