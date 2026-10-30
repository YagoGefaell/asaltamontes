-- T23c2: suspension of accounts. A temporary one ends by itself when suspended_until passes; a permanent one
-- is lifted only by the staff. Existing rows are not suspended.
ALTER TABLE users ADD COLUMN suspended_until timestamp(6);
ALTER TABLE users ADD COLUMN suspended_permanently boolean NOT NULL DEFAULT false;
