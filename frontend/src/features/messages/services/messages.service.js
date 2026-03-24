import { apiClient } from "../../../lib/api-client.js";

export async function getMyConversationsRequest() {
  try {
    const conversations = await apiClient.get("/api/conversations");
    return Array.isArray(conversations) ? conversations : [];
  } catch (error) {
    throw new Error(error.message || "Error obteniendo conversaciones");
  }
}

export async function createOrGetPrivateConversationRequest(recipientUsername) {
  try {
    const safeUsername = encodeURIComponent(recipientUsername);
    return await apiClient.post(`/api/conversations/private/${safeUsername}`, {});
  } catch (error) {
    throw new Error(error.message || "Error creando conversación");
  }
}

export async function getConversationMessagesRequest(conversationId) {
  try {
    return await apiClient.get(`/api/conversations/${conversationId}/messages`);
  } catch (error) {
    throw new Error(error.message || "Error cargando mensajes");
  }
}