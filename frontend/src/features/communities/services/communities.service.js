import { apiClient } from '../../../lib/api-client.js'

export const COMMUNITIES_PAGE_SIZE = 20

// -------- GET ALL COMMUNITIES --------
// One page (Spring Page) of the communities, or of those whose name contains `query` (T14)
export async function getCommunitiesRequest({ page = 0, query = '', city = '', province = '' } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(COMMUNITIES_PAGE_SIZE) })
  if (query.trim()) params.set('query', query.trim())
  if (city.trim()) params.set('city', city.trim())
  if (province.trim()) params.set('province', province.trim())
  try {
    const response = await apiClient.get(`/api/communities?${params}`)
    return {
      items: Array.isArray(response?.content) ? response.content : [],
      hasMore: response?.last === false,
      page: Number.isInteger(response?.number) ? response.number : page,
    }
  } catch (error) {
    throw new Error(error.message || "Error obteniendo comunidades")
  }
}

// -------- GET COMMUNITY --------
// By slug (T14); the backend also accepts the name of links shared before
export async function getCommunityRequest(slug) {
  try {
    const community = await apiClient.get(`/api/communities/${encodeURIComponent(slug)}`)
    return community
  } catch (error) {
    throw new Error(error.message || "Error obteniendo comunidad")
  }
}

// -------- GET MEMBERS --------
// First page of the members other than her. Only members get it: others receive 404 (BUG-066)
export async function getCommunityMembersRequest(communityId, page = 0) {
  const page_ = await getCommunityMembersPageRequest(communityId, page)
  return page_.items
}

// One page of members with `hasMore`; the staff also get each member's `status` (MUTED) (T19)
export async function getCommunityMembersPageRequest(communityId, page = 0) {
  try {
    const response = await apiClient.get(`/api/communities/${communityId}/members?page=${page}&size=${COMMUNITIES_PAGE_SIZE}`)
    return {
      items: Array.isArray(response?.content) ? response.content : [],
      hasMore: response?.last === false,
    }
  } catch (error) {
    throw new Error(error.message || "Error obteniendo los miembros")
  }
}

// -------- CREATE COMMUNITY --------
export async function createCommunityRequest(communityData) {
  try {
    return await apiClient.post('/api/communities', communityData)
  } catch (error) {
    throw new Error(error.message || "Error creando comunidad")
  }
}

// Keeps status and retryAfter so the screens can tell 403/404/429 apart (T19b)
function keepStatus(error, fallback) {
  const wrapped = new Error(error.message || fallback)
  wrapped.status = error.status
  wrapped.retryAfter = error.retryAfter
  wrapped.code = error.code
  return wrapped
}

// -------- JOIN COMMUNITY --------
// 201 = member; 202 = request sent to a private community (T19). The body is empty either way
export async function joinCommunityRequest(communityId) {
  try {
    return await apiClient.post(`/api/communities/${communityId}/join`, {})
  } catch (error) {
    throw keepStatus(error, "Error al unirse a la comunidad")
  }
}

// -------- LEAVE COMMUNITY --------
export async function leaveCommunityRequest(communityId) {
  try {
    return await apiClient.post(`/api/communities/${communityId}/leave`, {})
  } catch (error) {
    throw new Error(error.message || "Error al salir de la comunidad")
  }
}

// -------- COMMUNITY CHAT (T20) --------
// { conversationId, enabled, canWrite } for a member (muted members read only); 404 for anyone else
export async function getCommunityChatRequest(communityId) {
  try {
    return await apiClient.get(`/api/communities/${communityId}/chat`)
  } catch (error) {
    throw new Error(error.message || "Error abriendo el chat de la comunidad")
  }
}

// Owner and moderators switch the chat off (history stays readable) or on again
export async function setCommunityChatEnabledRequest(communityId, enabled) {
  try {
    return await apiClient.patch(`/api/communities/${communityId}`, { chatEnabled: enabled })
  } catch (error) {
    throw new Error(error.message || "Error cambiando el chat de la comunidad")
  }
}

// -------- DELETE COMMUNITY --------
export async function deleteCommunityRequest(communityId) {
  try {
    return await apiClient.delete(`/api/communities/${communityId}`)
  } catch (error) {
    throw new Error(error.message || "Error al borrar la comunidad")
  }
}

// -------- MY COMMUNITIES --------
// One page of the communities she belongs to (with her role in each one)
export async function getMyCommunitiesRequest({ page = 0, size = COMMUNITIES_PAGE_SIZE } = {}) {
  try {
    const response = await apiClient.get(`/api/communities/me?page=${page}&size=${size}`)
    return {
      items: Array.isArray(response?.content) ? response.content : [],
      hasMore: response?.last === false,
    }
  } catch (error) {
    throw new Error(error.message || 'Error obteniendo tus comunidades')
  }
}

// -------- PENDING (T31c) --------
// { toApprove: [{ community, pendingCount, latestRequestAt }], mine: [{ id, community, requestedAt }] }
export async function getPendingCommunitiesRequest() {
  try {
    const response = await apiClient.get('/api/communities/me/pending')
    return {
      toApprove: Array.isArray(response?.toApprove) ? response.toApprove : [],
      mine: Array.isArray(response?.mine) ? response.mine : [],
    }
  } catch (error) {
    throw new Error(error.message || 'Error obteniendo lo pendiente')
  }
}

// -------- PUBLIC COMMUNITIES OF ANOTHER USER (T31b) --------
export async function getUserCommunitiesRequest(username, { page = 0, size = COMMUNITIES_PAGE_SIZE } = {}) {
  try {
    const response = await apiClient.get(`/api/users/${encodeURIComponent(username)}/communities?page=${page}&size=${size}`)
    return {
      items: Array.isArray(response?.content) ? response.content : [],
      hasMore: response?.last === false,
    }
  } catch (error) {
    throw new Error(error.message || 'Error obteniendo sus comunidades')
  }
}

// -------- COMMUNITIES SHE CAN ORGANIZE IN --------
// GET /api/communities/me has her role in each one; only OWNER and MODERATOR can create events (T16b)
export async function getOrganizableCommunitiesRequest() {
  try {
    const result = []
    for (let page = 0; page < 5; page++) {
      const response = await apiClient.get(`/api/communities/me?page=${page}&size=50`)
      result.push(...(Array.isArray(response?.content) ? response.content : []))
      if (response?.last !== false) break
    }
    return result.filter((community) => community.role === 'OWNER' || community.role === 'MODERATOR')
  } catch (error) {
    throw new Error(error.message || 'Error obteniendo tus comunidades')
  }
}

// ================= MODERATION (T19b) =================
const base = (communityId) => `/api/communities/${communityId}`
const user = (username) => encodeURIComponent(username)

// -------- EDIT COMMUNITY --------
// PATCH by public id; absent field = unchanged, null clears it (D-020)
export async function updateCommunityRequest(communityId, changes) {
  try {
    return await apiClient.patch(base(communityId), changes)
  } catch (error) {
    throw keepStatus(error, "No se pudo guardar la comunidad")
  }
}

// -------- MEMBER ACTIONS --------
async function post(path, body, fallback) {
  try {
    return await apiClient.post(path, body)
  } catch (error) {
    throw keepStatus(error, fallback)
  }
}

export function setMemberRoleRequest(communityId, username, role) {
  return apiClient.put(`${base(communityId)}/members/${user(username)}/role`, { role })
    .catch((error) => { throw keepStatus(error, "No se pudo cambiar el rol") })
}
export const muteMemberRequest = (communityId, username) =>
  post(`${base(communityId)}/members/${user(username)}/mute`, {}, "No se pudo silenciar")
export const unmuteMemberRequest = (communityId, username) =>
  post(`${base(communityId)}/members/${user(username)}/unmute`, {}, "No se pudo quitar el silencio")
export const banMemberRequest = (communityId, username, reason) =>
  post(`${base(communityId)}/members/${user(username)}/ban`, reason?.trim() ? { reason: reason.trim() } : {}, "No se pudo expulsar")
export const unbanMemberRequest = (communityId, username) =>
  post(`${base(communityId)}/members/${user(username)}/unban`, {}, "No se pudo readmitir")
export const transferOwnershipRequest = (communityId, username) =>
  post(`${base(communityId)}/transfer-ownership/${user(username)}`, {}, "No se pudo transferir la propiedad")
export const approveJoinRequest = (communityId, username) =>
  post(`${base(communityId)}/requests/${user(username)}/approve`, {}, "No se pudo aceptar la solicitud")
export const rejectJoinRequest = (communityId, username) =>
  post(`${base(communityId)}/requests/${user(username)}/reject`, {}, "No se pudo rechazar la solicitud")

// -------- CURSOR LISTS (opaque `before` / `nextBefore`) --------
async function getCursorPage(path, before, fallback) {
  const params = new URLSearchParams({ limit: '20' })
  if (before) params.set('before', before)
  try {
    const response = await apiClient.get(`${path}?${params}`)
    return {
      items: Array.isArray(response?.items) ? response.items : [],
      nextBefore: response?.nextBefore ?? null,
    }
  } catch (error) {
    throw keepStatus(error, fallback)
  }
}

export const getBannedRequest = (communityId, before) =>
  getCursorPage(`${base(communityId)}/banned`, before, "No se pudo cargar la lista de expulsadas")
export const getJoinRequestsRequest = (communityId, before) =>
  getCursorPage(`${base(communityId)}/requests`, before, "No se pudieron cargar las solicitudes")
export const getModerationLogRequest = (communityId, before) =>
  getCursorPage(`${base(communityId)}/moderation-log`, before, "No se pudo cargar el registro")
