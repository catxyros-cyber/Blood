-- BLOOD API schema (PostgreSQL 14+).
-- Users are identified by their Minecraft profile UUID, verified through
-- Mojang's session server. No passwords or Minecraft/Microsoft tokens are stored.

CREATE TABLE users (
  id                 uuid PRIMARY KEY,
  minecraft_uuid     text NOT NULL UNIQUE CHECK (minecraft_uuid ~ '^[0-9a-f]{32}$'),
  username           text NOT NULL,
  stripe_customer_id text UNIQUE,
  created_at         timestamptz NOT NULL DEFAULT now(),
  updated_at         timestamptz NOT NULL DEFAULT now()
);

-- Opaque bearer tokens (launcher) and website cookies; only SHA-256 hashes are stored.
CREATE TABLE sessions (
  token_hash   bytea PRIMARY KEY,
  user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind         text NOT NULL CHECK (kind IN ('launcher', 'web')),
  device       text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  last_used_at timestamptz NOT NULL DEFAULT now(),
  expires_at   timestamptz NOT NULL
);
CREATE INDEX sessions_user_idx ON sessions(user_id);
CREATE INDEX sessions_expiry_idx ON sessions(expires_at);

-- Sign-in challenges (the serverId used for Mojang's join/hasJoined handshake).
CREATE TABLE auth_challenges (
  id         text PRIMARY KEY,
  server_id  text NOT NULL,
  expires_at timestamptz NOT NULL,
  used_at    timestamptz
);

-- One-time codes that sign a launcher user into the website.
CREATE TABLE web_link_codes (
  code_hash  bytea PRIMARY KEY,
  user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at timestamptz NOT NULL,
  used_at    timestamptz
);

-- Mirror of Stripe subscriptions, kept current by webhooks.
CREATE TABLE subscriptions (
  id                   text PRIMARY KEY,           -- sub_…
  user_id              uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status               text NOT NULL,
  price_id             text,
  plan                 text,
  current_period_end   timestamptz,
  cancel_at_period_end boolean NOT NULL DEFAULT false,
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX subscriptions_user_idx ON subscriptions(user_id);

-- One-time (lifetime) purchases.
CREATE TABLE purchases (
  id             text PRIMARY KEY,                 -- cs_… (Checkout Session)
  user_id        uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  payment_intent text UNIQUE,
  price_id       text,
  plan           text NOT NULL,
  status         text NOT NULL CHECK (status IN ('paid', 'refunded')),
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX purchases_user_idx ON purchases(user_id);

-- Manually granted Premium (support, giveaways). NULL expires_at = permanent.
CREATE TABLE grants (
  user_id    uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  reason     text NOT NULL,
  expires_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- Processed webhook events, so retries are idempotent.
CREATE TABLE stripe_events (
  id          text PRIMARY KEY,
  type        text NOT NULL,
  received_at timestamptz NOT NULL DEFAULT now()
);

-- Cloud sync: launcher settings and installation metadata (never game files or tokens).
CREATE TABLE sync_documents (
  user_id    uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  version    integer NOT NULL,
  data       jsonb NOT NULL,
  device     text,
  updated_at timestamptz NOT NULL DEFAULT now()
);
