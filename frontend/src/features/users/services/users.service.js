import { apiClient } from '../../../lib/api-client.js'

// ---------------- LOAD USER INFORMATION ----------------
export async function getMeRequest() {
  try {
    const userData = await apiClient.get('/api/users/me')
    return userData
  } catch (error) {
    if (error.status === 401 || error.status === 403) {
      return null
    }
    throw new Error("Error al cargar la información del usuario")
  }
}

// ---------------- UPDATE USER PROFILE ----------------
export async function updateUserProfileRequest(profileData) {
  try {
    const updatedData = await apiClient.patch('/api/users/me', profileData)
    return updatedData
  } catch (error) {
    throw new Error(error.message || "Error actualizando perfil")
  }
}

export async function getUsersByUsername(query) {
  try {
    const pagedUsers = await apiClient.get(`/api/users/search?query=${query}`)
    return pagedUsers.content || []
  } catch (error) {
    throw new Error(error.message || "Error buscando usuarios")
  }
}

// ---------------- GET ALL USERS WITH PAGINATION ----------------
export async function getAllUsersRequest(page = 0, size = 20, query = "") {
  try {
    let url = `/api/users/search?page=${page}&size=${size}`
    if (query && query.trim()) {
      url += `&query=${encodeURIComponent(query.trim())}`
    }
    const result = await apiClient.get(url)
    return result // Return the full Page object
  } catch (error) {
    throw new Error(error.message || "Error obteniendo usuarios")
  }
}

// ---------------- GET USER BY USERNAME ----------------
export async function getUserByUsername(username) {
  try {
    const user = await apiClient.get(`/api/users/${username}`)
    return user
  } catch (error) {
    if (error.status === 404) {
      throw new Error("Usuario no encontrado")
    }
    throw new Error(error.message || "Error obteniendo información del usuario")
  }
}

// ---------------- USERS TO FOLLOW (T31b) ----------------
export async function getSuggestionsRequest({ page = 0, size = 10 } = {}) {
  try {
    const response = await apiClient.get(`/api/users/suggestions?page=${page}&size=${size}`)
    return {
      items: Array.isArray(response?.content) ? response.content : [],
      hasMore: response?.last === false,
    }
  } catch (error) {
    throw new Error(error.message || "Error obteniendo sugerencias")
  }
}

// ---------------- FOLLOW / UNFOLLOW USERS ----------------
export async function followUserRequest(username) {
  try {
    const result = await apiClient.post(`/api/users/follow/${username}`)
    return result
  } catch {
    throw "Error al seguir al usuario"
  }
}

export async function unfollowUserRequest(username) {
  try {
    const result = await apiClient.delete(`/api/users/follow/${username}`)
    return result
  } catch {
    throw "Error al dejar de seguir al usuario"
  }
}

// ---------------- GET FOLLOWERS / FOLLOWING ----------------
export async function getFollowingRequest(username = null, page = 0, size = 20) {
  try {
    if (!username) {
      const following = await apiClient.get(`/api/users/following?page=${page}&size=${size}`)
      return following
    }
    const following = await apiClient.get(`/api/users/following/${username}?page=${page}&size=${size}`)
    return following
  } catch (error) {
    throw new Error(error.message || "Error obteniendo lista de seguidos")
  }
}

export async function getFollowersRequest(username = null, page = 0, size = 20) {
  try {
    if (!username) {
      const followers = await apiClient.get(`/api/users/followers?page=${page}&size=${size}`)
      return followers
    }
    const followers = await apiClient.get(`/api/users/followers/${username}?page=${page}&size=${size}`)
    return followers
  } catch (error) {
    throw new Error(error.message || "Error obteniendo lista de seguidores")
  }
}

// ---------------- BLOCKS (T13) ----------------
export async function blockUserRequest(username) {
  try {
    await apiClient.post(`/api/users/${encodeURIComponent(username)}/block`)
  } catch (error) {
    throw new Error(error.message || "Error al bloquear a la usuaria")
  }
}

export async function unblockUserRequest(username) {
  try {
    await apiClient.delete(`/api/users/${encodeURIComponent(username)}/block`)
  } catch (error) {
    throw new Error(error.message || "Error al desbloquear a la usuaria")
  }
}

// One page of the users I blocked (Spring page: { content, last, ... })
export async function getBlockedUsersRequest(page = 0, size = 20) {
  try {
    return await apiClient.get(`/api/users/me/blocks?page=${page}&size=${size}`)
  } catch (error) {
    throw new Error(error.message || "Error obteniendo las usuarias bloqueadas")
  }
}
