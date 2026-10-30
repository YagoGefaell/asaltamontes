-- T23a: private profiles. Following a private profile creates a request that its owner approves or rejects; an
-- approved request becomes a user_follow row, so user_follow keeps meaning "accepted" and the follower counters
-- (T05/BUG-070) are untouched. New table and indexes only; nothing that existed before Flyway is touched (BUG-049).

CREATE TABLE follow_requests (
    id          bigserial   PRIMARY KEY,
    follower_id bigint      NOT NULL,
    followed_id bigint      NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_follow_requests_follower FOREIGN KEY (follower_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_follow_requests_followed FOREIGN KEY (followed_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_follow_requests_pair UNIQUE (follower_id, followed_id),
    CONSTRAINT ck_follow_requests_not_self CHECK (follower_id <> followed_id)
);

-- The inbox of the owner, newest first
CREATE INDEX idx_follow_requests_followed_created ON follow_requests (followed_id, created_at DESC, id DESC);

-- Ask, cancel, ask again must not pile up unread notifications for the owner
CREATE UNIQUE INDEX uk_notifications_follow_request_unread ON notifications (recipient_id, actor_id)
    WHERE type = 'FOLLOW_REQUEST' AND read_at IS NULL;
