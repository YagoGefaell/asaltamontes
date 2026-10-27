-- T21: chat of each event and reminders. Each event has one managed group conversation (managed_by = 'EVENT',
-- managed_ref_id = events.id) whose participants are the GOING attendees.
-- New columns and index only; nothing that existed before Flyway is altered (BUG-049).

-- Reminders 24 h and 2 h before the start: the job claims each one with a single UPDATE ... WHERE ... IS NULL, so
-- it is sent once however many times (or from however many instances) the job runs. Moving the start resets them.
ALTER TABLE events ADD COLUMN reminder_24h_sent_at timestamp(6) with time zone;
ALTER TABLE events ADD COLUMN reminder_2h_sent_at timestamp(6) with time zone;

-- One chat per event (the application creates it lazily; this makes a race harmless)
CREATE UNIQUE INDEX uk_conversations_event_chat ON conversations (managed_ref_id) WHERE managed_by = 'EVENT';

-- Backfill: a chat for every event that is not cancelled and whose archive date (7 days after it ends) has not
-- passed, with its GOING attendees. Ids are UUID v7 as in V9.
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
SELECT pg_temp.uuid_v7_at(now()), 'GROUP', e.title, e.image_url, 'EVENT', e.id, now()
FROM events e
WHERE e.status <> 'CANCELLED'
  AND coalesce(e.ends_at, e.starts_at + interval '12 hours') + interval '7 days' > now();

INSERT INTO conversation_participants (conversation_id, user_id, role, unread_count, joined_at)
SELECT conv.id, a.user_id, 'MEMBER', 0, now()
FROM event_attendees a
JOIN conversations conv ON conv.managed_by = 'EVENT' AND conv.managed_ref_id = a.event_id
WHERE a.status = 'GOING';
