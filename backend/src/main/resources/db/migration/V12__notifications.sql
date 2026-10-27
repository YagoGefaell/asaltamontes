-- T18: in-app notifications. The server stores type + structured payload; the text is composed by the client (D-019).
-- New tables only; nothing that existed before Flyway is touched (BUG-049).

CREATE TABLE notifications (
    id           bigserial    PRIMARY KEY,
    public_id    uuid         NOT NULL,
    recipient_id bigint       NOT NULL,
    actor_id     bigint,
    type         varchar(40)  NOT NULL,
    target_type  varchar(20),
    -- Public reference of the target (event/chat UUID, community slug, username); never a bigint
    target_ref   varchar(255),
    payload      jsonb        NOT NULL DEFAULT '{}'::jsonb,
    read_at      timestamptz,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_target_type CHECK (target_type IS NULL OR target_type IN ('EVENT', 'COMMUNITY', 'CONVERSATION', 'USER'))
);

CREATE UNIQUE INDEX uk_notifications_public_id ON notifications (public_id);
-- The list, newest first
CREATE INDEX idx_notifications_recipient_created ON notifications (recipient_id, created_at DESC, id DESC);
-- The unread counter
CREATE INDEX idx_notifications_recipient_unread ON notifications (recipient_id) WHERE read_at IS NULL;
-- Retention job
CREATE INDEX idx_notifications_created_at ON notifications (created_at);
-- Follow, unfollow, follow again must not pile up unread notifications from the same person
CREATE UNIQUE INDEX uk_notifications_new_follower_unread ON notifications (recipient_id, actor_id)
    WHERE type = 'NEW_FOLLOWER' AND read_at IS NULL;
-- Same for someone who joins, leaves and joins the same event again
CREATE UNIQUE INDEX uk_notifications_event_joined_unread ON notifications (recipient_id, actor_id, target_ref)
    WHERE type = 'EVENT_JOINED' AND read_at IS NULL;

-- A missing row means the type is enabled
CREATE TABLE notification_preferences (
    user_id bigint      NOT NULL,
    type    varchar(40) NOT NULL,
    enabled boolean     NOT NULL,
    CONSTRAINT pk_notification_preferences PRIMARY KEY (user_id, type),
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
