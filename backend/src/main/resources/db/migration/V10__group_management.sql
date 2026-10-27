-- T12: group management. Roles, who created a group, hooks for groups managed by communities/events (T20/T21),
-- the "history visible to new members" option, and system messages (a message without a sender).
-- Only new columns and constraints are added; nothing that existed before Flyway is touched (BUG-049).

ALTER TABLE conversation_participants ADD COLUMN role varchar(10) NOT NULL DEFAULT 'MEMBER';
ALTER TABLE conversation_participants ADD CONSTRAINT ck_conversation_participants_role CHECK (role IN ('ADMIN', 'MEMBER'));

ALTER TABLE conversations ADD COLUMN created_by_id bigint;
ALTER TABLE conversations ADD CONSTRAINT fk_conversations_created_by FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE SET NULL;
ALTER TABLE conversations ADD COLUMN managed_by varchar(10) NOT NULL DEFAULT 'NONE';
ALTER TABLE conversations ADD CONSTRAINT ck_conversations_managed_by CHECK (managed_by IN ('NONE', 'COMMUNITY', 'EVENT'));
-- Internal reference to the community/event that manages the group; never leaves the backend
ALTER TABLE conversations ADD COLUMN managed_ref_id bigint;
-- Decision of Yago (2026-10-27): in manual groups a new member sees only what is sent after she joins, unless an
-- admin turns this on
ALTER TABLE conversations ADD COLUMN history_visible_to_new boolean NOT NULL DEFAULT false;

-- Backfill: the member who joined first (ties: lowest id) of every existing group is its admin and its creator.
WITH first_member AS (
    SELECT DISTINCT ON (cp.conversation_id) cp.id, cp.conversation_id, cp.user_id
    FROM conversation_participants cp
    JOIN conversations c ON c.id = cp.conversation_id AND c.chat_type = 'GROUP'
    ORDER BY cp.conversation_id, cp.joined_at, cp.id
), promoted AS (
    UPDATE conversation_participants cp SET role = 'ADMIN'
    FROM first_member f WHERE cp.id = f.id
    RETURNING cp.conversation_id, cp.user_id
)
UPDATE conversations c SET created_by_id = p.user_id
FROM promoted p WHERE c.id = p.conversation_id;

CREATE INDEX idx_conversation_participants_admins ON conversation_participants (conversation_id) WHERE role = 'ADMIN';

ALTER TABLE messages ADD COLUMN type varchar(10) NOT NULL DEFAULT 'USER';
ALTER TABLE messages ADD CONSTRAINT ck_messages_type CHECK (type IN ('USER', 'SYSTEM'));
ALTER TABLE messages ALTER COLUMN sender_id DROP NOT NULL;
ALTER TABLE messages ADD CONSTRAINT ck_messages_sender CHECK (type = 'SYSTEM' OR sender_id IS NOT NULL);
-- {"action": "MEMBER_ADDED", "actor": "ana", "targets": ["marta"]}: the client composes the text
ALTER TABLE messages ADD COLUMN system_payload jsonb;
