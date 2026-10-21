-- BLOOD tags (e.g. [OWNER]) the owner gives to players, and when each player last had the launcher open.
CREATE TABLE player_tags (
  minecraft_uuid text PRIMARY KEY,
  text           text NOT NULL,
  color          text NOT NULL,
  updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE presence (
  minecraft_uuid text PRIMARY KEY,
  last_seen      timestamptz NOT NULL,
  playing        boolean NOT NULL DEFAULT false
);
