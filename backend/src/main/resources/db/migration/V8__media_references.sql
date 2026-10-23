-- T17c: resources reference their image by media id. The URL text columns stay: they hold the URL of the
-- "medium" variant for references to our own uploads (so reads need no join) and the legacy external URLs
-- (e.g. the Google profile picture).
-- New objects only; nothing that existed before Flyway is touched (BUG-049).

ALTER TABLE user_profiles ADD COLUMN avatar_media_id uuid;
ALTER TABLE communities   ADD COLUMN cover_media_id  uuid;
ALTER TABLE conversations ADD COLUMN group_media_id  uuid;

ALTER TABLE user_profiles ADD CONSTRAINT fk_user_profiles_avatar_media
    FOREIGN KEY (avatar_media_id) REFERENCES media (id) ON DELETE SET NULL;
ALTER TABLE communities ADD CONSTRAINT fk_communities_cover_media
    FOREIGN KEY (cover_media_id) REFERENCES media (id) ON DELETE SET NULL;
ALTER TABLE conversations ADD CONSTRAINT fk_conversations_group_media
    FOREIGN KEY (group_media_id) REFERENCES media (id) ON DELETE SET NULL;

-- Partial indexes: the orphan cleanup and ON DELETE SET NULL look rows up by media id
CREATE INDEX idx_user_profiles_avatar_media ON user_profiles (avatar_media_id) WHERE avatar_media_id IS NOT NULL;
CREATE INDEX idx_communities_cover_media ON communities (cover_media_id) WHERE cover_media_id IS NOT NULL;
CREATE INDEX idx_conversations_group_media ON conversations (group_media_id) WHERE group_media_id IS NOT NULL;

-- Existing references: URLs that point to our own store become media ids when the media exists, belongs to the
-- user who owns the resource and has the right purpose. The text column is rewritten to the relative URL of the
-- medium variant, so a row that pointed to a foreign host with our path stops calling that host. Anything else
-- stays as it was (legacy external URL).
UPDATE user_profiles p
   SET avatar_media_id = m.id,
       profile_picture_url = '/media/avatar/' || m.id || '/medium.jpg'
  FROM media m
 WHERE p.profile_picture_url ~ '^(?:https?://[^/]+)?/media/avatar/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/(?:thumb|medium|large)\.jpg$'
   AND m.id = substring(p.profile_picture_url from '/media/avatar/([0-9a-f-]{36})/')::uuid
   AND m.purpose = 'AVATAR'
   AND m.owner_id = p.user_id;

UPDATE communities c
   SET cover_media_id = m.id,
       image_url = '/media/community-cover/' || m.id || '/medium.jpg'
  FROM media m
 WHERE c.image_url ~ '^(?:https?://[^/]+)?/media/community-cover/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/(?:thumb|medium|large)\.jpg$'
   AND m.id = substring(c.image_url from '/media/community-cover/([0-9a-f-]{36})/')::uuid
   AND m.purpose = 'COMMUNITY_COVER'
   AND m.owner_id = c.created_by_id;

UPDATE conversations v
   SET group_media_id = m.id,
       group_image = '/media/group-image/' || m.id || '/medium.jpg'
  FROM media m
 WHERE v.group_image ~ '^(?:https?://[^/]+)?/media/group-image/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/(?:thumb|medium|large)\.jpg$'
   AND m.id = substring(v.group_image from '/media/group-image/([0-9a-f-]{36})/')::uuid
   AND m.purpose = 'GROUP_IMAGE'
   -- conversations have no creator column: the image must belong to a member of the group
   AND EXISTS (SELECT 1 FROM conversation_participants cp
                WHERE cp.conversation_id = v.id AND cp.user_id = m.owner_id);
