import { apiClient } from '../../../lib/api-client.js'

// ---------------- LOAD USER INFORMATION ----------------
export async function getMeRequest() {
  try {
    const userData = await apiClient.get('/api/users/me')
    return userData
  } catch (error) {
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

// ---------------- FOLLOW / UNFOLLOW USERS ----------------
export async function followUserRequest(username) {
  try {
    const result = await apiClient.post(`/api/users/follow/${username}`)
    return result
  } catch (error) {
    throw new Error(error.message || "Error siguiendo usuario")
  }
}

export async function unfollowUserRequest(username) {
  try {
    const result = await apiClient.delete(`/api/users/follow/${username}`)
    return result
  } catch (error) {
    throw new Error(error.message || "Error dejando de seguir usuario")
  }
}

// ---------------- GET FOLLOWERS / FOLLOWING ----------------
export async function getFollowingRequest(page = 0, size = 20) {
  try {
    // Try with pagination first
    try {
      const following = await apiClient.get(`/api/users/following?page=${page}&size=${size}`)
      return following // Return the full Page object
    } catch (paginationError) {
      // If pagination fails, try without pagination (fallback)
      if (page === 0) {
        console.warn('Pagination not supported for following endpoint, using fallback')
        const following = await apiClient.get('/api/users/following')
        // Convert to Page-like structure for consistency
        const users = Array.isArray(following) ? following : (following.content || [])
        return {
          content: users,
          totalElements: users.length,
          totalPages: 1,
          last: true,
          first: true,
          numberOfElements: users.length
        }
      }
      throw paginationError
    }
  } catch (error) {
    throw new Error(error.message || "Error obteniendo lista de seguidos")
  }
}

export async function getFollowersRequest(page = 0, size = 20) {
  try {
    // Try with pagination first
    try {
      const followers = await apiClient.get(`/api/users/followers?page=${page}&size=${size}`)
      return followers // Return the full Page object
    } catch (paginationError) {
      // If pagination fails, try without pagination (fallback)
      if (page === 0) {
        console.warn('Pagination not supported for followers endpoint, using fallback')
        const followers = await apiClient.get('/api/users/followers')
        // Convert to Page-like structure for consistency
        const users = Array.isArray(followers) ? followers : (followers.content || [])
        return {
          content: users,
          totalElements: users.length,
          totalPages: 1,
          last: true,
          first: true,
          numberOfElements: users.length
        }
      }
      throw paginationError
    }
  } catch (error) {
    throw new Error(error.message || "Error obteniendo lista de seguidores")
  }
}
