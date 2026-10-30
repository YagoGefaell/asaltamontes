import "./Messages.css";
import { useEffect, useRef, useState, useContext, useCallback } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/hooks/useAuth";
import { formatters } from "../../utils/helpers";
import { formatMessageTime } from "../../features/messages/utils/messageDates";
import ConfirmSheet from "../../shared/ui/ConfirmSheet";
import { deleteMessageRequest, getGroupDetailsRequest } from "../../features/messages/services/messages.service";
import ConversationsPane from "./components/ConversationsPanel";
import ChatPane from "./components/ChatPanel";
import GroupCreatorPanel from "./components/GroupCreatorPanel";
import GroupInfoPanel from "./components/GroupInfoPanel";

import { ConversationsContext } from "../../features/messages/hooks/conversations.context";

const Messages = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { currentUser, sendMessage, websocketConnected, websocketStatus } = useAuth();

  const { 
    chats, 
    setChats,
    chatMessages,
    setActiveChatId,
    upsertConversation,
    ensurePrivateConversation,
    createGroupConversation,
    loadConversationMessages,
    loadOlderMessages,
    historyPaging,
    historyError,
    loadingChats, 
    chatsPaging,
    loadMoreChats,
    errorChats,
    chatError,
    clearChatError,
    removedChat,
    clearRemovedChat,
    markMessageDeleted
  } = useContext(ConversationsContext);

  const [selectedChat, setSelectedChat] = useState(() => {
    const selectedConversation = location.state?.selectedConversation;

    if (!selectedConversation || selectedConversation.conversationId) {
      return null;
    }

    const username = selectedConversation.displayName
      || selectedConversation.username
      || selectedConversation.name
      || "usuaria";

    return {
      id: null,
      name: username,
      avatar: username.trim().charAt(0).toUpperCase() || "?",
      avatarUrl: formatters.avatar(selectedConversation.displayImage || selectedConversation.avatarUrl, username, username),
      unreadCount: 0,
      lastMsg: selectedConversation.lastMessage || "Empieza la conversacion",
    };
  });
  const [draftMessage, setDraftMessage] = useState("");
  const [sendError, setSendError] = useState(null);
  // Text of the last message sent, to give it back to the input if the server rejects it
  const lastSentRef = useRef(null);
  const [creatingGroup, setCreatingGroup] = useState(false);
  const [isGroupCreatorOpen, setIsGroupCreatorOpen] = useState(false);
  const [isGroupInfoOpen, setIsGroupInfoOpen] = useState(false);

  // Owner or moderator of the community that manages the open chat (T20d): the server says it, with the group details
  const [moderationRole, setModerationRole] = useState(null);
  const [messageToDelete, setMessageToDelete] = useState(null);
  const [deletingMessage, setDeletingMessage] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const [removedNotice, setRemovedNotice] = useState("");

  const messagesEndRef = useRef(null);

  const currentMessages = selectedChat ? (chatMessages[selectedChat.id] || []) : [];

  // A chat without id is a new conversation: there is no history to load
  const historyStatus = !selectedChat?.id || historyPaging[selectedChat.id]
    ? "loaded"
    : historyError[selectedChat.id] ? "error" : "loading";

  const scrollToBottom = () => {
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: "auto", block: "end" });
    }
  };

  // Leaving /messages (navbar, logout...) with a chat open: its messages are no longer on screen (BUG-052)
  useEffect(() => () => setActiveChatId(null), [setActiveChatId]);

  const handleEnterChat = useCallback(async (chat) => {
    setSelectedChat(chat);
    setActiveChatId(chat.id || null);
    if (chat.id) {
      await loadConversationMessages(chat.id);
    }
  }, [setActiveChatId, loadConversationMessages]);

  const handleOpenUserProfile = useCallback((chat, event = null) => {
    event?.stopPropagation();

    if (!chat || chat.type !== "INDIVIDUAL" || !chat.name) return;

    navigate(`/users/${encodeURIComponent(chat.name)}`);
  }, [navigate]);

  const selectedChatId = selectedChat?.id;
  const selectedChatIsGroup = selectedChat?.type === "GROUP";
  useEffect(() => {
    setModerationRole(null);
    setMessageToDelete(null);
    if (!selectedChatId || !selectedChatIsGroup) return undefined;

    let cancelled = false;
    getGroupDetailsRequest(selectedChatId, { page: 0, size: 1 })
      .then((details) => { if (!cancelled) setModerationRole(details?.canModerateMessages ? (details.moderationRole ?? "MODERATOR") : null); })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [selectedChatId, selectedChatIsGroup]);

  const confirmDeleteMessage = useCallback(async () => {
    if (!messageToDelete || !selectedChatId) return;
    setDeletingMessage(true);
    setDeleteError("");
    try {
      await deleteMessageRequest(selectedChatId, messageToDelete.id);
      markMessageDeleted?.({ conversationId: selectedChatId, messageId: messageToDelete.id });
      setMessageToDelete(null);
    } catch (error) {
      setDeleteError(error.message);
    } finally {
      setDeletingMessage(false);
    }
  }, [messageToDelete, selectedChatId, markMessageDeleted]);

  const handleLeaveChat = useCallback(() => {
    setIsGroupInfoOpen(false);
    setSelectedChat(null);
    setActiveChatId(null);
    navigate("/messages", { replace: true, state: {} });
  }, [navigate, setActiveChatId]);

  useEffect(() => {
    const selectedConversation = location.state?.selectedConversation;

    if (!selectedConversation || selectedChat) return;

    const conversationId = selectedConversation.conversationId;

    if (!conversationId) {
      navigate(location.pathname, { replace: true, state: {} });
      return;
    }

    const existing = chats.find((c) => String(c.id) === String(conversationId));
    const targetChat = existing || upsertConversation(selectedConversation);

    if (targetChat) {
      setTimeout(() => {
        handleEnterChat(targetChat);
        navigate(location.pathname, { replace: true, state: {} });
      }, 0);
    }
  }, [
    location.state, 
    chats, 
    selectedChat, 
    upsertConversation,
    navigate, 
    location.pathname, 
    handleEnterChat,
  ]);

  // T12: the group info changed the name or image, or I left: keep the open chat and the list in step
  const handleGroupChanged = useCallback((details) => {
    const patch = { name: details.name, avatar: details.name.trim().charAt(0).toUpperCase() || "?", avatarUrl: details.imageUrl || null };
    setSelectedChat((prev) => (prev && String(prev.id) === String(details.id) ? { ...prev, ...patch } : prev));
    setChats((prevChats) => prevChats.map((chat) => (String(chat.id) === String(details.id) ? { ...chat, ...patch } : chat)));
  }, [setChats]);

  // T29r: actions of the group info that leave it
  const handleOpenParticipantProfile = useCallback((username) => {
    navigate(`/users/${encodeURIComponent(username)}`);
  }, [navigate]);

  const handleMessageParticipant = useCallback((participant) => {
    const existing = chats.find((chat) => chat.type === "INDIVIDUAL" && chat.name === participant.username);
    setIsGroupInfoOpen(false);
    if (existing) {
      handleEnterChat(existing);
      return;
    }
    setSelectedChat({
      id: null,
      name: participant.username,
      avatar: participant.username.trim().charAt(0).toUpperCase() || "?",
      avatarUrl: formatters.avatar(participant.profilePictureUrl, participant.fullName, participant.username),
      unreadCount: 0,
      lastMsg: "Empieza la conversacion",
    });
  }, [chats, handleEnterChat]);

  const handleOpenGroupOrigin = useCallback((origin) => {
    navigate(origin.type === "EVENT" ? `/events/${origin.id}` : `/communities/${encodeURIComponent(origin.slug)}`);
  }, [navigate]);

  const handleLeftGroup = useCallback(() => {
    const leftId = selectedChat?.id;
    if (leftId) setChats((prevChats) => prevChats.filter((chat) => String(chat.id) !== String(leftId)));
    handleLeaveChat();
  }, [selectedChat?.id, setChats, handleLeaveChat]);

  // The server removed me from the open chat (expelled, or I left in another session)
  useEffect(() => {
    if (!removedChat) return;
    if (selectedChat?.id && String(selectedChat.id) === String(removedChat.id)) {
      setIsGroupInfoOpen(false);
      setSelectedChat(null);
      navigate("/messages", { replace: true, state: {} });
      setRemovedNotice(removedChat.reason === "REMOVED" ? "Ya no formas parte de este grupo." : "");
    }
    clearRemovedChat();
  }, [removedChat, selectedChat?.id, clearRemovedChat, navigate]);

  useEffect(() => {
    const className = "chat-open";
    if (selectedChat) document.body.classList.add(className);
    else document.body.classList.remove(className);

    return () => document.body.classList.remove(className);
  }, [selectedChat]);

  useEffect(() => {
    if (!selectedChat || selectedChat.id) return;

    const matchedChat = chats.find(
      (chat) => chat.id && String(chat.name).toLowerCase() === String(selectedChat.name).toLowerCase()
    );

    if (!matchedChat) return;

    const syncId = setTimeout(() => {
      const syncedChat = {
        ...matchedChat,
        avatar: matchedChat.avatar || selectedChat.avatar,
        avatarUrl: matchedChat.avatarUrl || selectedChat.avatarUrl || null,
      };

      setSelectedChat(syncedChat);
      setActiveChatId(matchedChat.id);

      setChats((prevChats) => prevChats.map((chat) => (
        String(chat.id) === String(matchedChat.id)
          ? {
              ...chat,
              avatar: chat.avatar || selectedChat.avatar,
              avatarUrl: chat.avatarUrl || selectedChat.avatarUrl || null,
            }
          : chat
      )));

      loadConversationMessages(matchedChat.id);
    }, 0);

    return () => clearTimeout(syncId);
  }, [selectedChat, chats, setActiveChatId, setChats, loadConversationMessages]);

  // Scroll to the bottom when the newest message changes (open chat, send, receive), not when older pages load
  const newestMessageId = currentMessages.length > 0 ? currentMessages[currentMessages.length - 1].id : null;
  useEffect(() => {
    if (!selectedChat) return;
    scrollToBottom();
  }, [selectedChat, newestMessageId]);

  // The server rejected a message (not a participant, empty, too long…): show why and restore the text
  useEffect(() => {
    if (!chatError) return;
    const failedText = lastSentRef.current;
    lastSentRef.current = null;
    const restoreId = setTimeout(() => {
      setSendError(chatError.detail);
      if (failedText) {
        setDraftMessage((current) => current || failedText);
      }
      clearChatError();
    }, 0);
    return () => clearTimeout(restoreId);
  }, [chatError, clearChatError]);

  const handleDraftChange = (value) => {
    setDraftMessage(value);
    if (sendError) setSendError(null);
  };

  const handleSendMessage = async () => {
    const content = draftMessage.trim();

    if (!content || !selectedChat || !websocketConnected || !currentUser?.username) {
      return;
    }

    let activeChat = selectedChat;
    let conversationId = selectedChat.id;

    if (!conversationId) {
      try {
        const ensuredChat = await ensurePrivateConversation(selectedChat);
        activeChat = ensuredChat;
        conversationId = ensuredChat.id;

        setSelectedChat(ensuredChat);
        setActiveChatId(ensuredChat.id);
      } catch (error) {
        // E.g. a block or the other user's privacy setting (403 with a neutral message)
        setSendError(error.message || "No se ha podido abrir el chat");
        return;
      }
    }

    const payload = {
      conversationId,
      content,
    };

    const wasSent = sendMessage("/app/chat.send", payload);
    if (!wasSent) {
      setSendError("No hay conexión con el chat. Inténtalo de nuevo en unos segundos.");
      return;
    }
    lastSentRef.current = content;
    setSendError(null);

    setChats((prevChats) => {
      const isGroupChat = selectedChat?.type === "GROUP";
      const senderLabel = isGroupChat ? (currentUser?.username || "usuaria") : "Tu";
      const myPreview = `${senderLabel}: ${content}`;
      const sentAt = new Date().toISOString();

      const existing = prevChats.find(
        (chat) => String(chat.id) === String(conversationId)
      );

      const updatedChat = {
        ...(existing || activeChat),
        id: conversationId,
        lastMsg: myPreview,
        lastMessageAt: sentAt,
        unreadCount: 0,
      };

      const remainingChats = prevChats.filter(
        (chat) => String(chat.id) !== String(conversationId)
      );

      return [updatedChat, ...remainingChats];
    });

    setDraftMessage("");
  };

  const closeGroupCreator = useCallback(() => {
    if (creatingGroup) return;
    setIsGroupCreatorOpen(false);
  }, [creatingGroup]);

  const openGroupCreator = useCallback(() => {
    setIsGroupCreatorOpen(true);
  }, []);

  const handleCreateGroup = useCallback(async ({ groupName, groupMediaId, users }) => {
    if (creatingGroup) return;
    setCreatingGroup(true);

    try {
      const createdChat = await createGroupConversation({
        groupName,
        groupMediaId,
        users,
      });

      if (createdChat?.id) {
        setIsGroupCreatorOpen(false);
        await handleEnterChat(createdChat);
      }
    } finally {
      setCreatingGroup(false);
    }
  }, [
    creatingGroup,
    createGroupConversation,
    handleEnterChat,
  ]);

  return (
    <div className="messages-page">
      {!selectedChat && !isGroupCreatorOpen && removedNotice && (
        <p className="messages-page__notice" role="status">{removedNotice}</p>
      )}

      {!selectedChat && !isGroupCreatorOpen && (
        <ConversationsPane
          loadingChats={loadingChats}
          errorChats={errorChats}
          chats={chats}
          onEnterChat={handleEnterChat}
          onCreateGroup={openGroupCreator}
          onBack={() => navigate(-1)}
          creatingGroup={creatingGroup}
          hasMoreChats={chatsPaging.hasMore}
          loadingMoreChats={chatsPaging.loading}
          onLoadMoreChats={loadMoreChats}
          onAvatarError={(chatId) => {
            setChats((prevChats) => prevChats.map((existingChat) => (
              String(existingChat.id) === String(chatId)
                ? { ...existingChat, avatarUrl: null }
                : existingChat
            )));
          }}
          formatMessageTime={formatMessageTime}
        />
      )}

      {!selectedChat && isGroupCreatorOpen && (
        <GroupCreatorPanel
          isOpen={isGroupCreatorOpen}
          creatingGroup={creatingGroup}
          currentUsername={currentUser?.username}
          onClose={closeGroupCreator}
          onSubmitGroup={handleCreateGroup}
        />
      )}

      {selectedChat && isGroupInfoOpen && (
        <GroupInfoPanel
          chat={selectedChat}
          currentUsername={currentUser?.username}
          onClose={() => setIsGroupInfoOpen(false)}
          onGroupChanged={handleGroupChanged}
          onLeft={handleLeftGroup}
          onOpenProfile={handleOpenParticipantProfile}
          onSendMessage={handleMessageParticipant}
          onOpenOrigin={handleOpenGroupOrigin}
        />
      )}

      {selectedChat && !isGroupInfoOpen && (
        <ChatPane
          selectedChat={selectedChat}
          currentMessages={currentMessages}
          currentUsername={currentUser?.username}
          draftMessage={draftMessage}
          onDraftChange={handleDraftChange}
          sendError={sendError}
          hasMoreHistory={Boolean(selectedChat && historyPaging[selectedChat.id]?.hasMore)}
          loadingOlder={Boolean(selectedChat && historyPaging[selectedChat.id]?.loading)}
          olderError={Boolean(selectedChat && historyPaging[selectedChat.id]?.error)}
          onLoadOlder={() => selectedChat?.id && loadOlderMessages(selectedChat.id)}
          historyStatus={historyStatus}
          onRetryHistory={() => selectedChat?.id && loadConversationMessages(selectedChat.id)}
          onSendMessage={handleSendMessage}
          onLeaveChat={handleLeaveChat}
          onOpenUserProfile={handleOpenUserProfile}
          onOpenGroupInfo={() => setIsGroupInfoOpen(true)}
          moderationRole={moderationRole}
          onDeleteMessage={setMessageToDelete}
          onSelectedAvatarError={() => {
            setSelectedChat((prev) => (prev ? { ...prev, avatarUrl: null } : prev));
          }}
          websocketConnected={websocketConnected}
          websocketStatus={websocketStatus}
          messagesEndRef={messagesEndRef}
        />
      )}

      <ConfirmSheet
        open={Boolean(messageToDelete)}
        title="¿Eliminar este mensaje?"
        message="Se eliminará para todas las participantes y quedará registrado en el historial de moderación."
        confirmLabel="Eliminar"
        loading={deletingMessage}
        error={deleteError}
        destructive
        onConfirm={confirmDeleteMessage}
        onCancel={() => { if (!deletingMessage) { setMessageToDelete(null); setDeleteError(""); } }}
      />
    </div>
  );
};

export default Messages;