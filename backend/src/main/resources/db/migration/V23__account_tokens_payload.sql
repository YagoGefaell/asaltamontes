-- T24b: a token can carry data. CHANGE_EMAIL keeps the new address here until the link sent to it is used.
-- Additive and nullable; account_tokens is a table of V21, nothing from before Flyway is touched (BUG-049).
-- Numbered V23 because V22 belongs to the open PR #161 (reports): merge #161 first.
ALTER TABLE account_tokens ADD COLUMN payload varchar(255);
