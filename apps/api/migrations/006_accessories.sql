-- MOTION accessories: wings (an image) and auras (a colour + particle style) the owner publishes,
-- and which ones each player wears (at most one of each kind).
CREATE TABLE accessories (
  id          uuid PRIMARY KEY,
  name        text NOT NULL,
  kind        text NOT NULL CHECK (kind IN ('wings', 'aura')),
  access      text NOT NULL DEFAULT 'everyone' CHECK (access IN ('everyone', 'premium')),
  png         bytea,
  width       integer NOT NULL DEFAULT 0,
  height      integer NOT NULL DEFAULT 0,
  color       text NOT NULL DEFAULT '#39ff88',
  style       text,
  enabled     boolean NOT NULL DEFAULT true,
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE player_accessories (
  minecraft_uuid text NOT NULL,
  kind           text NOT NULL CHECK (kind IN ('wings', 'aura')),
  accessory_id   uuid NOT NULL REFERENCES accessories(id) ON DELETE CASCADE,
  updated_at     timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (minecraft_uuid, kind)
);
CREATE INDEX player_accessories_acc_idx ON player_accessories(accessory_id);
