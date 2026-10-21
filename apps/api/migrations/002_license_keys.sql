-- Premium license keys. Only a SHA-256 of each key is stored; the full key is shown once
-- when it's created. A redeemed key is bound to one PC (a hash of a hash of its machine id)
-- and can be moved to another PC at most once every 24 hours (counting from activation).
CREATE TABLE license_keys (
  id            uuid PRIMARY KEY,
  key_hash      bytea NOT NULL UNIQUE,
  key_hint      text NOT NULL,
  label         text NOT NULL DEFAULT '',
  duration_days integer CHECK (duration_days IS NULL OR duration_days > 0),
  created_at    timestamptz NOT NULL DEFAULT now(),
  redeemed_at   timestamptz,
  expires_at    timestamptz,
  hwid_hash     bytea,
  last_move_at  timestamptz,
  revoked       boolean NOT NULL DEFAULT false
);
CREATE INDEX license_keys_created_idx ON license_keys(created_at DESC);
