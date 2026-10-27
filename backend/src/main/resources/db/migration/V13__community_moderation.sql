-- T19a: community moderation, private communities and location.
-- Only new columns/tables and new indexes: nothing that existed before Flyway is altered (BUG-049).

ALTER TABLE communities ADD COLUMN visibility varchar(10) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE communities ADD COLUMN city       varchar(80);
ALTER TABLE communities ADD COLUMN province   varchar(80);
ALTER TABLE communities ADD CONSTRAINT ck_communities_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE'));
CREATE INDEX idx_communities_city ON communities (lower(city));
CREATE INDEX idx_communities_province ON communities (lower(province));

-- Who did what in a community. actor/target survive the deletion of the account as NULL.
CREATE TABLE community_moderation_log (
    id           bigserial    PRIMARY KEY,
    public_id    uuid         NOT NULL,
    community_id bigint       NOT NULL,
    actor_id     bigint,
    target_id    bigint,
    action       varchar(30)  NOT NULL,
    reason       varchar(300),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_moderation_log_community FOREIGN KEY (community_id) REFERENCES communities (id) ON DELETE CASCADE,
    CONSTRAINT fk_moderation_log_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_moderation_log_target FOREIGN KEY (target_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE UNIQUE INDEX uk_moderation_log_public_id ON community_moderation_log (public_id);
CREATE INDEX idx_moderation_log_community_created ON community_moderation_log (community_id, created_at DESC, public_id DESC);
-- "Expulsadas": the latest BAN of each target
CREATE INDEX idx_moderation_log_bans ON community_moderation_log (community_id, target_id, id DESC) WHERE action = 'BAN';

-- Requests to join a private community; a row exists only while pending (accepting or rejecting deletes it)
CREATE TABLE community_join_requests (
    id           bigserial   PRIMARY KEY,
    public_id    uuid        NOT NULL,
    community_id bigint      NOT NULL,
    user_id      bigint      NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_join_requests_community FOREIGN KEY (community_id) REFERENCES communities (id) ON DELETE CASCADE,
    CONSTRAINT fk_join_requests_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_join_requests_community_user UNIQUE (community_id, user_id)
);
CREATE UNIQUE INDEX uk_join_requests_public_id ON community_join_requests (public_id);
CREATE INDEX idx_join_requests_community_created ON community_join_requests (community_id, created_at DESC, public_id DESC);

-- Request, cancel, request again must not pile up unread notifications for the staff
CREATE UNIQUE INDEX uk_notifications_join_request_unread ON notifications (recipient_id, actor_id, target_ref)
    WHERE type = 'COMMUNITY_JOIN_REQUEST' AND read_at IS NULL;
