-- T13: user blocks and the "who can start a private chat with me" policy (D-018: EVERYONE by default).
-- Only new objects are added; nothing that existed before Flyway is touched (BUG-049).

CREATE TABLE user_blocks (
    blocker_id bigint       NOT NULL,
    blocked_id bigint       NOT NULL,
    created_at timestamp(6) NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_blocks PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT fk_user_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_blocks_not_self CHECK (blocker_id <> blocked_id)
);

-- "Who blocked me" lookups (the PK already serves "whom did I block")
CREATE INDEX idx_user_blocks_blocked ON user_blocks (blocked_id);

ALTER TABLE user_profiles ADD COLUMN dm_policy varchar(10) NOT NULL DEFAULT 'EVERYONE';
ALTER TABLE user_profiles ADD CONSTRAINT ck_user_profiles_dm_policy CHECK (dm_policy IN ('EVERYONE', 'FOLLOWING', 'MUTUALS', 'NOBODY'));
