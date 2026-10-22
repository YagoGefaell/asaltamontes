-- T15c: indexes for the event filters of GET /api/events.

-- City filter (case-insensitive: the query compares lower(city)), soonest first.
CREATE INDEX idx_events_city_starts_at ON events (lower(city), starts_at) WHERE city IS NOT NULL;

-- Bounding box over the map (D-005: no PostGIS yet). Only events with coordinates.
CREATE INDEX idx_events_coordinates ON events (latitude, longitude) WHERE latitude IS NOT NULL;
