import { apiClient } from '../../../lib/api-client.js'

export const COMMUNITIES_PAGE_SIZE = 20

// -------- GET ALL COMMUNITIES --------
// One page (Spring Page) of the communities, or of those whose name contains `query` (T14)
export async function getCommunitiesRequest({ page = 0, query = '' } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(COMMUNITIES_PAGE_SIZE) })
  if (query.trim()) params.set('query', query.trim())
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
export async function getCommunityMembersRequest(communityId) {
  try {
    const response = await apiClient.get(`/api/communities/${communityId}/members?page=0&size=${COMMUNITIES_PAGE_SIZE}`)
    return Array.isArray(response?.content) ? response.content : []
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

// -------- JOIN COMMUNITY --------
export async function joinCommunityRequest(communityId) {
  try {
    return await apiClient.post(`/api/communities/${communityId}/join`, {})
  } catch (error) {
    throw new Error(error.message || "Error al unirse a la comunidad")
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

// -------- DELETE COMMUNITY --------
export async function deleteCommunityRequest(communityId) {
  try {
    return await apiClient.delete(`/api/communities/${communityId}`)
  } catch (error) {
    throw new Error(error.message || "Error al borrar la comunidad")
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
