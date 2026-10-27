import { apiClient } from "../../../lib/api-client.js";

// One page of the chat list, most recent first (T10b). Without a cursor, the first page.
// `before` is the opaque nextBefore token of the previous page (T30c).
export async function getMyConversationsRequest({ before = null } = {}) {
  const params = new URLSearchParams();
  if (before != null) params.set("before", before);
  const query = params.toString() ? `?${params}` : "";
  try {
    const page = await apiClient.get(`/api/conversations${query}`);
    return {
      items: Array.isArray(page?.items) ? page.items : [],
      hasMore: Boolean(page?.hasMore),
      nextBefore: page?.nextBefore ?? null,
      unreadChats: Number(page?.unreadChats) || 0,
    };
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

export async function createGroupConversationRequest({ groupName, groupMediaId = null, users }) {
  try {
    return await apiClient.post("/api/conversations/group", {
      groupName,
      groupMediaId,
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

// Messages newer than `after` (the opaque `cursor` of a message, T30c), oldest first, following the pages (T11: catching up after a reconnection).
// complete = false if there were more than maxPages pages: the caller should reload the chat instead.
export async function getMessagesAfterRequest(conversationId, after, maxPages = 5) {
  const items = [];
  let cursor = after;
  try {
    for (let page = 0; page < maxPages; page++) {
      const result = await apiClient.get(
        `/api/conversations/${conversationId}/messages?after=${encodeURIComponent(cursor)}&limit=100`
      );
      const pageItems = Array.isArray(result?.items) ? result.items : [];
      items.push(...pageItems);
      if (!result?.hasMore || pageItems.length === 0) {
        return { items, complete: true };
      }
      cursor = pageItems[pageItems.length - 1].cursor;
      // Without a cursor we cannot follow the pages: the caller reloads the chat
      if (!cursor) return { items, complete: false };
    }
    return { items, complete: false };
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

// --- Group management (T12). The server decides who may do what; errors carry its Spanish message. ---

// Details of a group and one page of its participants: { id, name, imageUrl, imageMediaId, participantCount, myRole,
// historyVisibleToNew, managed, canModerateMessages, participants: { content, totalElements, last... } }
export async function getGroupDetailsRequest(conversationId, { page = 0, size = 20 } = {}) {
  try {
    return await apiClient.get(`/api/conversations/${conversationId}?page=${page}&size=${size}`);
  } catch (error) {
    throw new Error(error.message || "Error cargando el grupo");
  }
}

// `changes` only has the fields to change: groupName, groupMediaId (null removes the image), historyVisibleToNew
export async function updateGroupRequest(conversationId, changes) {
  try {
    return await apiClient.patch(`/api/conversations/${conversationId}`, changes);
  } catch (error) {
    throw new Error(error.message || "Error actualizando el grupo");
  }
}

export async function addParticipantsRequest(conversationId, usernames) {
  try {
    return await apiClient.post(`/api/conversations/${conversationId}/participants`, { usernames });
  } catch (error) {
    throw new Error(error.message || "Error añadiendo participantes");
  }
}

export async function removeParticipantRequest(conversationId, username) {
  try {
    await apiClient.delete(`/api/conversations/${conversationId}/participants/${encodeURIComponent(username)}`);
  } catch (error) {
    throw new Error(error.message || "Error expulsando a la participante");
  }
}

export async function changeParticipantRoleRequest(conversationId, username, role) {
  try {
    await apiClient.patch(`/api/conversations/${conversationId}/participants/${encodeURIComponent(username)}`, { role });
  } catch (error) {
    throw new Error(error.message || "Error cambiando el rol");
  }
}

export async function leaveGroupRequest(conversationId) {
  try {
    await apiClient.post(`/api/conversations/${conversationId}/leave`);
  } catch (error) {
    throw new Error(error.message || "Error saliendo del grupo");
  }
}

// Moderation of a community chat (T20d): the owner or a moderator deletes a message. The server applies the hierarchy
// (a moderator cannot delete those of the owner or other moderators: 403 with its Spanish message).
export async function deleteMessageRequest(conversationId, messageId) {
  try {
    await apiClient.delete(`/api/conversations/${conversationId}/messages/${messageId}`);
  } catch (error) {
    throw new Error(error.message || "No se pudo eliminar el mensaje");
  }
}
