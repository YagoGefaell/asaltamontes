-- T14: community names unique regardless of case and spacing (BUG-047), and a URL slug (BUG-013).

-- 1. Same normalization as CommunityService: trimmed, single spaces
UPDATE communities
SET name = regexp_replace(btrim(name), '\s+', ' ', 'g')
WHERE name <> regexp_replace(btrim(name), '\s+', ' ', 'g');

-- 2. Rename case-insensitive duplicates deterministically: the oldest community (lowest id) keeps the name
UPDATE communities c
SET name = left(c.name, 100 - length(' (' || c.id || ')')) || ' (' || c.id || ')'
WHERE EXISTS (
    SELECT 1 FROM communities older
    WHERE lower(older.name) = lower(c.name) AND older.id < c.id
);

ALTER TABLE communities DROP CONSTRAINT uk_communities_name;
CREATE UNIQUE INDEX ux_communities_name_lower ON communities (lower(name));

-- 3. Slug: lowercase ASCII letters, digits and single hyphens (SlugGenerator does the same for new ones)
ALTER TABLE communities ADD COLUMN slug varchar(120);

UPDATE communities
SET slug = coalesce(
    nullif(left(btrim(regexp_replace(
        translate(lower(name), 'áàäâãåéèëêíìïîóòöôõúùüûñçý', 'aaaaaaeeeeiiiiooooouuuuncy'),
        '[^a-z0-9]+', '-', 'g'), '-'), 100), ''),
    'comunidad');

-- Collisions: the oldest keeps the slug, the others get "-<id>"
UPDATE communities c
SET slug = c.slug || '-' || c.id
WHERE EXISTS (
    SELECT 1 FROM communities older
    WHERE older.slug = c.slug AND older.id < c.id
);

ALTER TABLE communities ALTER COLUMN slug SET NOT NULL;
ALTER TABLE communities ADD CONSTRAINT uk_communities_slug UNIQUE (slug);

-- 4. Search by name: lower(name) LIKE '%q%' (pg_trgm is enabled in V2)
CREATE INDEX idx_communities_name_trgm ON communities USING gin (lower(name) gin_trgm_ops);
