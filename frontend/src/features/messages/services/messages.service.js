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

// One page of the history, oldest first: { items, hasMore, nextBefore }. Without `before`, the latest messages.
export async function getConversationMessagesRequest(conversationId, before = null) {
  const query = before ? `?before=${encodeURIComponent(before)}` : "";
  try {
    const page = await apiClient.get(`/api/conversations/${conversationId}/messages${query}`);
    return {
      items: Array.isArray(page?.items) ? page.items : [],
      hasMore: Boolean(page?.hasMore),
      nextBefore: page?.nextBefore ?? null,
    };
  } catch (error) {
    throw new Error(error.message || "Error cargando mensajes");
  }
}

// Marks the conversation as read when the WebSocket is not available (otherwise chat.read does it)
export async function markConversationReadRequest(conversationId) {
  try {
    await apiClient.post(`/api/conversations/${conversationId}/read`);
  } catch {
    // Not critical: the unread badge is corrected the next time the chat is opened
  }
}