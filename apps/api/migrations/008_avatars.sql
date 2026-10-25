-- MOTION 3D avatars: a third accessory kind. The avatar's model id (sahur, tralalero, …) is kept in
-- the style column, like an aura's particle style.
ALTER TABLE accessories DROP CONSTRAINT IF EXISTS accessories_kind_check;
ALTER TABLE accessories ADD CONSTRAINT accessories_kind_check CHECK (kind IN ('wings', 'aura', 'avatar'));
ALTER TABLE player_accessories DROP CONSTRAINT IF EXISTS player_accessories_kind_check;
ALTER TABLE player_accessories ADD CONSTRAINT player_accessories_kind_check CHECK (kind IN ('wings', 'aura', 'avatar'));
