-- T20: community chat. Each community has one managed group conversation (managed_by = 'COMMUNITY',
-- managed_ref_id = communities.id) whose participants are the active and muted members.
-- New column and index only; nothing that existed before Flyway is altered (BUG-049).

-- The moderation can switch the chat off: history stays readable, nobody writes
ALTER TABLE communities ADD COLUMN chat_enabled boolean NOT NULL DEFAULT true;

-- One chat per community (the application creates it lazily; this makes a race harmless)
CREATE UNIQUE INDEX uk_conversations_community_chat ON conversations (managed_ref_id) WHERE managed_by = 'COMMUNITY';

-- Backfill: a chat for every existing community, with its non-banned members. Ids are UUID v7 as in V9.
CREATE OR REPLACE FUNCTION pg_temp.uuid_v7_at(ts timestamptz) RETURNS uuid
LANGUAGE sql VOLATILE AS $$
    SELECT encode(
        set_bit(set_bit(
            overlay(uuid_send(gen_random_uuid())
                    PLACING substring(int8send(floor(extract(epoch FROM ts) * 1000)::bigint) FROM 3) FROM 1 FOR 6),
            52, 1), 53, 1),
        'hex')::uuid
$$;

INSERT INTO conversations (public_id, chat_type, group_name, group_image, managed_by, managed_ref_id, created_at)
SELECT pg_temp.uuid_v7_at(now()), 'GROUP', c.name, c.image_url, 'COMMUNITY', c.id, now()
FROM communities c;

INSERT INTO conversation_participants (conversation_id, user_id, role, unread_count, joined_at)
SELECT conv.id, m.user_account_id, 'MEMBER', 0, now()
FROM community_members m
JOIN conversations conv ON conv.managed_by = 'COMMUNITY' AND conv.managed_ref_id = m.community_id
WHERE m.status <> 'BANNED';
