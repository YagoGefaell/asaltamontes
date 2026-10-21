-- T10b: the chat list is read by cursor over (last_message_at DESC NULLS LAST, id DESC)
CREATE INDEX idx_conversations_last_message ON conversations (last_message_at DESC NULLS LAST, id DESC);
