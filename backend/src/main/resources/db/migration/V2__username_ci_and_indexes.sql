-- T05: usernames and emails unique regardless of case, and the indexes the current queries need.

-- 1. Rename case-insensitive duplicates deterministically before creating the unique indexes:
--    the oldest account (lowest id) keeps the name, the others get "_<id>" (username) or "+dup<id>" (email).
UPDATE users u
SET username = left(u.username, 50 - length('_' || u.id)) || '_' || u.id
WHERE EXISTS (
    SELECT 1 FROM users older
    WHERE lower(older.username) = lower(u.username) AND older.id < u.id
);

UPDATE users u
SET email = regexp_replace(u.email, '@', '+dup' || u.id || '@')
WHERE EXISTS (
    SELECT 1 FROM users older
    WHERE lower(older.email) = lower(u.email) AND older.id < u.id
);

-- 2. Case-insensitive uniqueness (the app always looks users up with lower()/IgnoreCase)
CREATE UNIQUE INDEX ux_users_username_lower ON users (lower(username));
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

-- 3. Indexes for the main queries
-- Chat history of a conversation, newest first (also ready for cursor pagination, T10)
CREATE INDEX idx_messages_conversation_id_desc ON messages (conversation_id, id DESC);
-- Conversations of a user
CREATE INDEX idx_conversation_participants_user ON conversation_participants (user_id);
-- Communities of a user
CREATE INDEX idx_community_members_user ON community_members (user_account_id);

-- 4. Username search: LOWER(username) LIKE '%q%' can only use a trigram index
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_users_username_trgm ON users USING gin (lower(username) gin_trgm_ops);
