-- BLOOD capes: images the owner publishes, and which cape each player wears.
-- Capes are visible to BLOOD Client users only (Minecraft doesn't let a client change what other clients see).
CREATE TABLE capes (
  id          uuid PRIMARY KEY,
  name        text NOT NULL,
  access      text NOT NULL DEFAULT 'everyone' CHECK (access IN ('everyone', 'premium')),
  png         bytea NOT NULL,
  width       integer NOT NULL,
  height      integer NOT NULL,
  enabled     boolean NOT NULL DEFAULT true,
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE player_capes (
  minecraft_uuid text PRIMARY KEY,
  cape_id        uuid NOT NULL REFERENCES capes(id) ON DELETE CASCADE,
  updated_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX player_capes_cape_idx ON player_capes(cape_id);
