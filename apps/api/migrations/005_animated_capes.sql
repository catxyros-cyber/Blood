-- Animated capes: the PNG is a vertical strip of frames (each frame width x width/2), shown
-- frame_ms apart. Static capes have one frame.
ALTER TABLE capes ADD COLUMN frame_ms integer NOT NULL DEFAULT 100;
