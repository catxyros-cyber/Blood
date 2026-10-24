CREATE TABLE community_skins (
  id          uuid PRIMARY KEY,
  name        text NOT NULL CHECK (char_length(name) BETWEEN 1 AND 40),
  model       text NOT NULL CHECK (model IN ('classic', 'slim')),
  png         bytea NOT NULL CHECK (octet_length(png) <= 65536),
  enabled     boolean NOT NULL DEFAULT true,
  created_at  timestamptz NOT NULL DEFAULT now()
);
