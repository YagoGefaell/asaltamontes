import { apiClient } from '../../../lib/api-client.js'

// -------- GET ALL COMMUNITIES --------
export async function getCommunitiesRequest() {
  try {
    const response = await apiClient.get('/api/communities')
    return response.content || []
  } catch (error) {
    throw new Error(error.message || "Error obteniendo comunidades")
  }
}

// -------- GET COMMUNITY BY NAME --------
export async function getCommunityByNameRequest(communityName) {
  try {
    const community = await apiClient.get(`/api/communities/${communityName}`)
    return community
  } catch (error) {
    throw new Error(error.message || "Error obteniendo comunidad")
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
