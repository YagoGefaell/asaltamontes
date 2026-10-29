-- T22a2: POST_COMMENT notifications and the explore grid. No existing data is changed.

-- Notifications can point to a post (target_ref = the post's public id). The constraint comes from V12 (Flyway), so its name is known.
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_target_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_target_type
    CHECK (target_type IS NULL OR target_type IN ('EVENT', 'COMMUNITY', 'CONVERSATION', 'USER', 'POST'));

-- Cooldown: one unread "X commented on your post" per commenter and post, however many comments she writes
CREATE UNIQUE INDEX uk_notifications_post_comment_unread ON notifications (recipient_id, actor_id, target_ref)
    WHERE type = 'POST_COMMENT' AND read_at IS NULL;

-- Explore (all live posts, newest first, across authors and communities)
CREATE INDEX idx_posts_created ON posts (created_at DESC, public_id DESC) WHERE deleted_at IS NULL;
