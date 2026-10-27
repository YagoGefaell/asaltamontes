-- T19: who expelled each banned member (and with which role at that moment), so a moderator cannot readmit
-- whoever the owner expelled; and the lookup of the last rejected request (cooldown before asking again).
-- Only new columns and indexes: nothing that existed before Flyway is altered (BUG-049).

ALTER TABLE community_members ADD COLUMN banned_by      bigint;
ALTER TABLE community_members ADD COLUMN banned_by_role varchar(20);
ALTER TABLE community_members ADD CONSTRAINT fk_community_members_banned_by
    FOREIGN KEY (banned_by) REFERENCES users (id) ON DELETE SET NULL;
ALTER TABLE community_members ADD CONSTRAINT ck_community_members_banned_by_role
    CHECK (banned_by_role IN ('OWNER', 'MODERATOR'));

-- Existing bans: the actor of the latest BAN in the log. Her role is OWNER if she is still the owner of that
-- community; otherwise (or without a log entry) MODERATOR, the permissive reading: any staff member can readmit.
UPDATE community_members m
SET banned_by = l.actor_id,
    banned_by_role = CASE WHEN a.role = 'OWNER' THEN 'OWNER' ELSE 'MODERATOR' END
FROM (SELECT DISTINCT ON (community_id, target_id) community_id, target_id, actor_id
      FROM community_moderation_log
      WHERE action = 'BAN'
      ORDER BY community_id, target_id, id DESC) l
LEFT JOIN community_members a ON a.community_id = l.community_id AND a.user_account_id = l.actor_id
WHERE m.status = 'BANNED' AND m.community_id = l.community_id AND m.user_account_id = l.target_id;

UPDATE community_members SET banned_by_role = 'MODERATOR' WHERE status = 'BANNED' AND banned_by_role IS NULL;

CREATE INDEX idx_moderation_log_rejections ON community_moderation_log (community_id, target_id, id DESC)
    WHERE action = 'REQUEST_REJECTED';
