-- T20c: the silence belongs to user + community, not to the membership row. A muted member can leave and, if she
-- joins again, she is still muted. New table only; nothing that existed before Flyway is altered (BUG-049).
CREATE TABLE community_mutes (
    community_id bigint NOT NULL,
    user_id      bigint NOT NULL,
    created_at   timestamp NOT NULL DEFAULT now(),
    PRIMARY KEY (community_id, user_id),
    CONSTRAINT fk_community_mutes_community FOREIGN KEY (community_id) REFERENCES communities (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_mutes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Members muted today keep being muted
INSERT INTO community_mutes (community_id, user_id)
SELECT community_id, user_account_id FROM community_members WHERE status = 'MUTED';
