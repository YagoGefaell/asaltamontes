import { apiClient } from "../../../lib/api-client.js";

export async function getMyConversationsRequest() {
  try {
    const conversations = await apiClient.get("/api/conversations");
    return Array.isArray(conversations) ? conversations : [];
  } catch (error) {
    throw new Error(error.message || "Error obteniendo conversaciones");
  }
}

export async function getPrivateConversationRequest(recipientUsername) {
  try {
    return await apiClient.request(`/api/conversations/private/${recipientUsername}`, {
      method: "GET",
    });
  } catch (error) {
    if (error.status === 404) {
      return null;
    }

    throw new Error(error.message || "Error obteniendo conversación privada");
  }
}

export async function createPrivateConversationRequest(recipientUsername) {
  try {
    return await apiClient.post("/api/conversations/private", {
      username: recipientUsername,
    });
  } catch (error) {
    throw new Error(error.message || "Error creando conversación");
  }
}

export async function createGroupConversationRequest({ groupName, groupImage = null, users }) {
  try {
    return await apiClient.post("/api/conversations/group", {
      groupName,
      groupImage,
      users,
    });
  } catch (error) {
    throw new Error(error.message || "Error creando grupo");
  }
}

export async function getConversationMessagesRequest(conversationId) {
  try {
    return await apiClient.get(`/api/conversations/${conversationId}/messages`);
  } catch (error) {
    throw new Error(error.message || "Error cargando mensajes");
  }
}