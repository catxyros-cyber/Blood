package dev.bloom.client.hud;

import dev.bloom.client.BloomClient;
import dev.bloom.client.core.hud.HudWidgetType;
import dev.bloom.client.core.perf.FrameStats;
import dev.bloom.client.core.settings.BloomSettings;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import java.io.ByteArrayInputStream;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.time.LocalTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The built-in HUD widgets. Each one is independent: a failure in one never affects another. */
public final class Widgets {
    private Widgets() {
    }

    public static HudWidget create(HudWidgetType t) {
        return switch (t) {
            case FPS -> new Fps();
            case FRAME_TIME -> new FrameTime();
            case MEMORY -> new Memory();
            case COORDINATES -> new Coords();
            case DIRECTION -> new Direction();
            case KEYSTROKES -> new Keystrokes();
            case CPS -> new Cps();
            case ARMOR -> new Armor();
            case POTIONS -> new Potions();
            case CLOCK -> new Clock();
            case HELD -> new Held();
            case STATUS -> new Status();
            case INFO -> new Info();
            case TARGET_INFO -> new TargetInfo();
            case LOW_HEALTH -> new LowHealth();
            case MUSIC -> new Music();
        };
    }

    // ---------------------------------------------------------------- FPS

    static final class Fps extends HudWidget {
        Fps() {
            super(HudWidgetType.FPS);
        }

        private String label(Ctx c) {
            int fps = BloomClient.get().frames().fps(System.nanoTime());
            return c.state.option("label") ? fps + " FPS" : String.valueOf(fps);
        }

        @Override
        public int[] measure(Ctx c) {
            return new int[]{Math.max(c.font.width("000 FPS"), c.font.width(label(c))), c.font.lineHeight - 1};
        }

        @Override
        public void render(GuiGraphics g, Ctx c, int w, int h) {
            int fps = BloomClient.get().frames().fps(System.nanoTime());
            int color = c.text;
            if (c.state.option("colorCoded")) color = fps >= 60 ? 0xFF7BE38F : fps >= 30 ? 0xFFF2D46B : 0xFFFF7A7A;
            String s = label(c);
            text(g, c, s, (w - c.font.width(s)) / 2, 0, color);
        }
    }

    // ---------------------------------------------------------------- frame time

    static final class FrameTime extends HudWidget {
        private final float[] graph = new float[60];

        FrameTime() {
            super(HudWidgetType.FRAME_TIME);
        }

        private String line(Ctx c) {
            FrameStats f = BloomClient.get().frames();
            long now = System.nanoTime();
            String s = String.format(Locale.ROOT, "%.1f ms", f.averageMs(now, 1_000_000_000L));
            if (c.state.option("worst")) s += String.format(Locale.ROOT, "  max %.1f", f.worstMs(now, 2_000_000_000L));
            return s;
        }

        @Override
        public int[] measure(Ctx c) {
            int w = Math.max(c.font.width("00.0 ms  max 00.0"), c.font.width(line(c)));
            int h = c.font.lineHeight - 1 + (c.state.option("graph") ? 18 : 0);
            return new int[]{Math.max(w, 60), h};
        }

        @Override
        public void render(GuiGraphics g, Ctx c, int w, int h) {
            text(g, c, line(c), 0, 0);
            if (!c.state.option("graph")) return;
            int n = BloomClient.get().frames().recentMs(graph);
            int top = c.font.lineHeight + 1, gh = 15;
            g.fill(0, top + gh, w, top + gh + 1, withAlpha(c.text, 0.25f));
            // 16.7 ms line (60 FPS)
            int y60 = top + gh - Math.round(gh * Math.min(1f, 16.7f / 50f));
            g.fill(0, y60, w, y60 + 1, withAlpha(c.accent, 0.35f));
            float bw = w / (float) graph.length;
            for (int i = 0; i < n; i++) {
                float ms = graph[i];
                int bh = Math.max(1, Math.round(gh * Math.min(1f, ms / 50f)));
                int x0 = Math.round(i * bw), x1 = Math.max(x0 + 1, Math.round((i + 1) * bw) - 0);
                int col = ms > 33.4f ? 0xFFFF7A7A : ms > 16.8f ? 0xFFF2D46B : c.accent;
                g.fill(x0, top + gh - bh, x1, top + gh, col);
            }
        }
    }

    // ---------------------------------------------------------------- memory

    static final class Memory extends HudWidget {
        Memory() {
            super(HudWidgetType.MEMORY);
        }

        private String line(Ctx c) {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory(), max = rt.maxMemory();
            String s = String.format(Locale.ROOT, "Mem %s / %s", size(used), size(max));
            if (c.state.option("percent")) s += "  " + (used * 100 / Math.max(1, max)) + "%";
            return s;
        }

        private static String size(long bytes) {
            double gb = bytes / (1024.0 * 1024 * 1024);
            return gb >= 1 ? String.format(Locale.ROOT, "%.1f GB", gb) : (bytes / (1024 * 1024)) + " MB";
        }

        @Override
        public int[] measure(Ctx c) {
            return new int[]{c.font.width(line(c)), c.font.lineHeight - 1 + (c.state.option("bar") ? 5 : 0)};
        }

        @Override
        public void render(GuiGraphics g, Ctx c, int w, int h) {
            text(g, c, line(c), 0, 0);
            if (c.state.option("bar")) {
                Runtime rt = Runtime.getRuntime();
                float f = (rt.totalMemory() - rt.freeMemory()) / (float) rt.maxMemory();
                int y = c.font.lineHeight + 1;
                g.fill(0, y, w, y + 3, withAlpha(c.text, 0.2f));
                g.fill(0, y, Math.round(w * Math.min(1f, f)), y + 3, f > 0.85f ? 0xFFFF7A7A : c.accent);
            }
        }
    }

    // ---------------------------------------------------------------- coordinates

    static final class Coords extends HudWidget {
        Coords() {
            super(HudWidgetType.COORDINATES);
        }

        private List<String> lines(Ctx c) {
            List<String> out = new ArrayList<>();
            LocalPlayer p = c.mc.player;
            double x = p != null ? p.getX() : 128.5, y = p != null ? p.getY() : 64, z = p != null ? p.getZ() : -42.5;
            if (c.state.option("compact")) {
                out.add(String.format(Locale.ROOT, "%d, %d, %d", (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
            } else {
                out.add(String.format(Locale.ROOT, "X  %.1f", x));
                out.add(String.format(Locale.ROOT, "Y  %.1f", y));
                out.add(String.format(Locale.ROOT, "Z  %.1f", z));
            }
            if (c.state.option("facing")) {
                float yaw = p != null ? p.getViewYRot(c.partialTick) : 180f;
                out.add(facing(yaw));
            }
            if (c.state.option("dimension")) {
                String dim = c.mc.level != null ? pretty(c.mc.level.dimension().identifier().getPath()) : "Overworld";
                out.add(dim);
            }
            if (c.state.option("nether")) {
                boolean nether = c.mc.level != null && c.mc.level.dimension() == Level.NETHER;
                double f = nether ? 8 : 1 / 8.0;
                out.add(String.format(Locale.ROOT, "%s  %d, %d", nether ? "Overworld" : "Nether", (int) Math.floor(x * f), (int) Math.floor(z * f)));
            }
            if (c.state.option("biome")) {
                String name = "Plains";
                if (c.mc.level != null && p != null) {
                    BlockPos pos = p.blockPosition();
                    name = c.mc.level.getBiome(pos).unwrapKey().map(k -> pretty(k.identifier().getPath())).orElse("Unknown");
                }
                out.add(name);
            }
            return out;
        }

        @Override
        public int[] measure(Ctx c) {
            List<String> l = lines(c);
            int w = 0;
            for (String s : l) w = Math.max(w, c.font.width(s));
            return new int[]{Math.max(w, c.font.width("X  -00000.0")), l.size() * (c.font.lineHeight + 1) - 2};
        }

        @Override
        public void render(GuiGraphics g, Ctx c, int w, int h) {
            int y = 0;
            for (String s : lines(c)) {
                if (s.length() > 2 && (s.startsWith("X ") || s.startsWith("Y ") || s.startsWith("Z "))) {
                    text(g, c, s.substring(0, 1), 0, y, c.accent);
                    text(g, c, s.substring(1).trim(), 12, y);
                } else {
                    text(g, c, s, 0, y);
                }
                y += c.font.lineHeight + 1;
            }
        }
    }

    /** "North (-Z)" style label for a yaw (Minecraft: 0 = south, 90 = west). */
    static String facing(float yaw) {
        int i = Math.round(Math.floorMod((int) Math.floor(yaw), 360) / 90f) & 3;
        return switch (i) {
            case 0 -> "Facing South (+Z)";
            case 1 -> "Facing West (-X)";
            case 2 -> "Facing North (-Z)";
            default -> "Facing East (+X)";
        };
    }

    static String pretty(String path) {
        StringBuilder b = new StringBuilder();
        for (String part : path.split("[_/]")) {
            if (part.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- direction

    static final class Direction extends HudWidget {
        private static final String[] NAMES = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        private static final String[] LONG = {"South (+Z)", "South-west", "West (−X)", "North-west", "North (−Z)", "North-east", "East (+X)", "South-east"};

        Direction() {
            super(HudWidgetType.DIRECTION);
        }

        private float yaw(Ctx c) {
            return c.mc.player != null ? c.mc.player.getViewYRot(c.partialTick) : 180f;
        }

        @Override
        public int[] measure(Ctx c) {
            if (c.state.option("compass")) return new int[]{120, c.font.lineHeight + 4 + (c.state.option("degrees") ? c.font.lineHeight : 0)};
            String s = label(c);
            return new int[]{c.font.width(s), c.font.lineHeight - 1};
        }

        private String label(Ctx c) {
            float y = Math.floorMod((int) Math.floor(yaw(c)), 360);
            int i = Math.round(y / 45f) & 7;
            String s = LONG[i];
            if (c.state.option("degrees")) s += String.format(Locale.ROOT, "  %d°", (int) y);
            return s;
        }

        @Override
        public void render(GuiGraphics g, Ctx c, int w, int h) {
            if (!c.state.option("compass")) {
                text(g, c, label(c), 0, 0);
                return;
            }
            float yaw = yaw(c);
            float pxPerDeg = 1.4f;
            int mid = w / 2;
            g.enableScissor(0, 0, w, h);
            try {
                for (int d = -180; d <= 540; d += 15) {
                    float delta = d - Math.floorMod((int) Math.floor(yaw), 360) - (yaw - (float) Math.floor(yaw));
                    if (delta > 180) delta -= 360;
                    if (delta < -180) delta += 360;
                    int x = mid + Math.round(delta * pxPerDeg);
                    if (x < -10 || x > w + 10) continue;
                    if (d % 45 == 0) {
                        String n = NAMES[Math.floorMod(d / 45, 8)];
                        int col = n.equals("N") ? c.accent : c.text;
                        text(g, c, n, x - c.font.width(n) / 2, 1, col);
                    } else {
                        g.fill(x, 3, x + 1, 6, withAlpha(c.text, 0.5f));
                    }
                }
            } finally {
                g.disableScissor();
            }
            g.fill(mid, c.font.lineHeight + 1, mid + 1, c.font.lineHeight + 4, c.accent);
            if (c.state.option("degrees")) {
                String deg = Math.floorMod((int) Math.floor(yaw), 360) + "°";
                text(g, c, deg, mid - c.font.width(deg) / 2, c.font.lineHeight + 4);
            }
        }
    }

    // ---------------------------------------------------------------- keystrokes

    static final class Keystrokes extends HudWidget {
        private static final int K = 20, GAP = 2;

        Keystrokes() {
            super(HudWidgetType.KEYSTROKES);
        }

        @Override
        public int[] measure(Ctx c) {
            int rows = 2 + (c.state.option("mouse") ? 1 : 0);
            int h = rows * K + (rows - 1) * GAP + (c.state.option("space") ? GAP + 9 : 0);
            return new int[]{3 * K + 2 * GAP, h};
        }

        @Override
        public void render(GuiGraphics g, Ctx c, int w, int h) {
            var o = c.mc.options;
            key(g, c, K + GAP, 0, K, K, label(o.keyUp.getTranslatedKeyMessage().getString(), "W"), o.keyUp.isDown());
            int y = K + GAP;
            key(g, c, 0, y, K, K, label(o.keyLeft.getTranslatedKeyMessage().getString(), "A"), o.keyLeft.isDown());
            key(g, c, K + GAP, y, K, K, label(o.keyDown.getTranslatedKeyMessage().getString(), "S"), o.keyDown.isDown());
            key(g, c, 2 * (K + GAP), y, K, K, label(o.keyRight.getTranslatedKeyMessage().getString(), "D"), o.keyRight.isDown());
            y += K + GAP;
            if (c.state.option("mouse")) {
                int mw = (w - GAP) / 2;
                boolean cps = c.state.option("cps");
                long now = System.currentTimeMillis();
                String l = cps ? "LMB " + BloomClient.get().cps().cps(0, now) : "LMB";
                String r = cps ? "RMB " + BloomClient.get().cps().cps(1, now) : "RMB";
                key(g, c, 0, y, mw, K, l, o.keyAttack.isDown());
                key(g, c, mw + GAP, y, w - mw - GAP, K, r, o.keyUse.isDown());
                y += K + GAP;
            }
            if (c.state.option("space")) {
                boolean down = o.keyJump.isDown();
                g.fill(0, y, w, y + 9, down ? withAlpha(c.accent, 0.85f) : 0x55000000);
                g.fill(w / 2 - 10, y + 4, w / 2 + 10, y + 5, down ? 0xFF000000 : c.text);
            }
        }

        private static String label(String bound, String fallback) {
            if (bound == null || bound.isEmpty()) return fallback;
            return bound.length() <= 3 ? bound.toUpperCase(Locale.ROOT) : bound.substring(0, 3);
        }

        private static void key(GuiGraphics g, Ctx c, int x, int y, int w, int h, String label, boolean down) {
            g.fill(x, y, x + w, y + h, down ? withAlpha(c.accent, 0.85f) : 0x55000000);
            int col = down ? 0xFF140C0E : c.text;
            g.drawString(c.font, label, x + (w - c.font.width(label)) / 2, y + (h - 8) / 2, col, c.shadow && !down);
        }
    }

    // ---------------------------------------------------------------- CPS

    static final class Cps extends HudWidget {
        Cps() {
            super(HudWidgetType.CPS);
        }

        private String line(Ctx c) {
            long now = System.currentTimeMillis();
            int l = BloomClient.get().cps().cps(0, now);
            if (!c.state.option("right")) return l + " CPS";
            return l + " | " + BloomClient.get().cps().cps(1, now) + " CPS";
        }

        @Override
        public int[] measure(Ctx c) {
            return new int[]{Math.max(c.font.width("00 | 00 CPS"), c.font.width(line(c))), c.font.lineHeight - 1};
        }

        @Override
        public void render(GuiGraphics g, Ctx c, int w, int h) {
            String s = line(c);
            text(g, c, s, (w - c.font.width(s)) / 2, 0);
        }
    }

    // ---------------------------------------------------------------- armor

    static final class Armor extends HudWidget {
        private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        Armor() {
            super(HudWidgetType.ARMOR);
        }

        private List<ItemStack> items(Ctx c) {
            List<ItemStack> out = new ArrayList<>();
            LocalPlayer p = c.mc.player;
            if (p != null) {
                for (EquipmentSlot s : SLOTS) {
                    ItemStack st = p.getItemBySlot(s);
                    if (!st.isEmpty()) out.add(st);
                }
                if (c.state.option("held") && !p.getMainHandItem().isEmpty()) out.add(p.getMainHandItem());
            }
            if (out.isEmpty() && c.preview) {
                out.add(new ItemStack(Items.DIAMOND_HELMET));
                out.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
                out.add(new ItemStack(Items.IRON_LEGGINGS));
                out.add(new ItemStack(Items.IRON_BOOTS));
            }
            return out;
        }

        private String durability(Ctx c, ItemStack st) {
            if (!c.state.option("durability") || !st.isDamageableItem()) return st.getCount() > 1 ? String.valueOf(st.getCount()) : "";
            int left = st.getMaxDamage() - st.getDamageValue();
            return c.state.option("percent") ? (left * 100 / Math.max(1, st.getMaxDamage())) + "%" : String.valueOf(left);
        }

        @Override
        public int[] measure(Ctx c) {
            List<ItemStack> items = items(c);
            if (items.isEmpty()) return null;
            if (c.state.option("horizontal")) {
                int w = 0;
                for (ItemStack st : items) w += 18 + Math.max(0, c.font.width(durability(c, st)) - 4);
                return new int[]{w, 16 + (c.state.option("durability") ? 0 : 0)};
            }
            int tw = 0;
            for (ItemStack st : items) tw = Math.max(tw, c.font.width(durability(c, st)));
            return new int[]{18 + (tw > 0 ? tw : -2), items.size() * 17 - 1};
        }

        @Override
        public void render(GuiGraphics g, Ctx c, int w, int h) {
            int x = 0, y = 0;
            boolean horiz = c.state.option("horizontal");
            boolean bars = c.state.option("bars");
            for (ItemStack st : items(c)) {
                g.renderItem(st, x, y);
                if (bars) durabilityBar(g, st, x, y);
                String d = durability(c, st);
                int col = c.text;
                if (st.isDamageableItem() && st.getMaxDamage() > 0) {
                    float f = 1f - st.getDamageValue() / (float) st.getMaxDamage();
                    if (f < 0.25f) col = 0xFFFF7A7A;
                    else if (f < 0.5f) col = 0xFFF2D46B;
                }
                if (horiz) {
                    if (!d.isEmpty()) text(g, c, d, x + 17, y + 8, col);
                    x += 18 + Math.max(0, c.font.width(d) - 4);
                } else {
                    if (!d.isEmpty()) text(g, c, d, x + 18, y + 4, col);
                    y += 17;
                }
            }
        }
    }

    // ---------------------------------------------------------------- target player information

    static final class TargetInfo extends HudWidget {
        private static final EquipmentSlot[] ARMOR_SLOTS = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };

        TargetInfo() { super(HudWidgetType.TARGET_INFO); }

        private net.minecraft.world.entity.player.Player target(Ctx c) {
            if (c.preview || c.mc.hitResult == null) return null;
            if (c.mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                    && hit.getEntity() instanceof net.minecraft.world.entity.player.Player player
                    && player != c.mc.player) return player;
            return null;
        }

        private List<ItemStack> armor(Ctx c, net.minecraft.world.entity.player.Player player) {
            List<ItemStack> items = new ArrayList<>();
            if (player != null) {
                for (EquipmentSlot slot : ARMOR_SLOTS) {
                    ItemStack stack = player.getItemBySlot(slot);
                    if (!stack.isEmpty()) items.add(stack);
                }
            } else if (c.preview) {
                ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET);
                ItemStack chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
                ItemStack legs = new ItemStack(Items.NETHERITE_LEGGINGS);
                ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);
                helmet.setDamageValue(80);
                chest.setDamageValue(220);
                legs.setDamageValue(130);
                boots.setDamageValue(45);
                items.addAll(List.of(helmet, chest, legs, boots));
            }
            return items;
        }

        private String name(Ctx c, net.minecraft.world.entity.player.Player player) {
            String value = player == null ? "SamplePlayer" : player.getName().getString();
            if (c.state.option("distance") && player != null && c.mc.player != null) {
                value += String.format(Locale.ROOT, "  %.1fm", c.mc.player.distanceTo(player));
            }
            int max = 170;
            while (!value.isEmpty() && c.font.width(value) > max) value = value.substring(0, value.length() - 1);
            return value;
        }

        private String health(Ctx c, net.minecraft.world.entity.player.Player player) {
            float hp = player == null ? 16f : player.getHealth();
            float max = player == null ? 20f : player.getMaxHealth();
            return String.format(Locale.ROOT, "%.1f / %.0f HP", hp, max);
        }

        private String durability(Ctx c, ItemStack stack) {
            if (!c.state.option("durability") || !stack.isDamageableItem()) return "";
            int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
            return c.state.option("percent")
                    ? (remaining * 100 / Math.max(1, stack.getMaxDamage())) + "%"
                    : String.valueOf(remaining);
        }

        @Override
        public int[] measure(Ctx c) {
            net.minecraft.world.entity.player.Player player = target(c);
            if (player == null && !c.preview) return null;
            int w = 112;
            if (c.state.option("name")) w = Math.max(w, c.font.width(name(c, player)));
            if (c.state.option("health")) w = Math.max(w, c.font.width(health(c, player)) + 90);
            List<ItemStack> stacks = c.state.option("armor") ? armor(c, player) : List.of();
            if (!stacks.isEmpty()) w = Math.max(w, stacks.size() * 34);
            if (c.state.option("armor") && stacks.isEmpty()) w = Math.max(w, c.font.width("No armor equipped"));
            int h = (c.state.option("name") ? 10 : 0)
                    + (c.state.option("health") ? 13 : 0)
                    + (c.state.option("armor") ? 19 : 0);
            return new int[]{w, Math.max(c.font.lineHeight, h)};
        }

        @Override
        public void render(GuiGraphics g, Ctx c, int w, int h) {
            net.minecraft.world.entity.player.Player player = target(c);
            int y = 0;
            if (c.state.option("name")) {
                text(g, c, name(c, player), 0, y, c.accent);
                y += 10;
            }
            if (c.state.option("health")) {
                float hp = player == null ? 16f : player.getHealth();
                float max = player == null ? 20f : Math.max(1f, player.getMaxHealth());
                int barW = Math.min(78, Math.max(36, w - c.font.width(health(c, player)) - 8));
                g.fill(0, y + 1, barW, y + 7, 0x88202020);
                float ratio = Math.max(0f, Math.min(1f, hp / max));
                int color = ratio <= 0.25f ? 0xFFFF5964 : ratio <= 0.5f ? 0xFFF2C65E : 0xFF5DDB9A;
                g.fill(0, y + 1, Math.round(barW * ratio), y + 7, color);
                text(g, health(c, player), barW + 5, y, c.text);
                y += 13;
            }
            if (!c.state.option("armor")) return;
            List<ItemStack> stacks = armor(c, player);
            if (stacks.isEmpty()) {
                text(g, "No armor equipped", 0, y, c.text);
                return;
            }
            int x = 0;
            for (ItemStack stack : stacks) {
                g.renderItem(stack, x, y);
                if (c.state.option("bars")) durabilityBar(g, stack, x, y);
                String value = durability(c, stack);
                if (!value.isEmpty()) text(g, value, x + 17, y + 4, durabilityColor(c, stack));
                x += 34;
            }
        }
    }

    // ---------------------------------------------------------------- low health warning

    static final class LowHealth extends HudWidget {
        LowHealth() { super(HudWidgetType.LOW_HEALTH); }

        private float threshold(Ctx c) {
            if (c.state.option("early")) return 0.5f;
            if (c.state.option("critical")) return 0.2f;
            return 0.3f;
        }

        private float health(Ctx c) {
            return c.mc.player == null ? (c.preview ? 4.4f : 0f) : c.mc.player.getHealth();
        }

        private float maxHealth(Ctx c) {
            return c.mc.player == null ? 20f : Math.max(1f, c.mc.player.getMaxHealth());
        }

        private boolean active(Ctx c) {
            return c.preview || (c.mc.player != null && health(c) / maxHealth(c) <= threshold(c));
        }

        private String label(Ctx c) {
            String value = "LOW HEALTH";
            if (c.state.option("value")) {
                value += String.format(Locale.ROOT, "  %.1f / %.0f HP", health(c), maxHealth(c));
            }
            return value;
        }

        @Override
        public int[] measure(Ctx c) {
            return active(c) ? new int[]{Math.max(92, c.font.width(label(c))), c.font.lineHeight - 1 + (c.state.option("bar") ? 5 : 0)} : null;
        }

        @Override
        public void render(GuiGraphics g, Ctx c, int w, int h) {
            if (!active(c)) return;
            float ratio = c.preview ? 0.22f : Math.max(0f, Math.min(1f, health(c) / maxHealth(c)));
            float pulse = c.state.option("pulse") ? 0.72f + 0.28f * (0.5f + 0.5f * (float) Math.sin(c.nowMs * 0.012f)) : 1f;
            int color = c.state.textColor >= 0 ? c.text : withAlpha(0xFFFF5964, pulse);
            text(g, label(c), 0, 0, color);
            if (c.state.option("bar")) {
                int y = c.font.lineHeight + 1;
                g.fill(0, y, w, y + 3, withAlpha(0xFFFF5964, 0.25f));
                g.fill(0, y, Math.round(w * ratio), y + 3, withAlpha(0xFFFF5964, pulse));
            }
        }
    }
    // ---------------------------------------------------------------- potions

    static final class Potions extends HudWidget {
        Potions() {
            super(HudWidgetType.POTIONS);
        }

        private record Line(Holder<MobEffect> effect, String name, String time, boolean ending) {
        }

        private List<Line> lines(Ctx c) {
            List<Line> out = new ArrayList<>();
            LocalPlayer p = c.mc.player;
            if (p != null) {
                for (MobEffectInstance e : p.getActiveEffects()) {
                    if (!e.showIcon() || hidden(c, e)) continue;
                    String name = e.getEffect().value().getDisplayName().getString();
                    if (e.getAmplifier() > 0 && c.state.option("level")) name += " " + roman(e.getAmplifier() + 1);
                    String time = e.isInfiniteDuration() ? "∞" : formatTicks(e.getDuration());
                    out.add(new Line(e.getEffect(), name, time, !e.isInfiniteDuration() && e.getDuration() < 200));
                }
            }
            if (out.isEmpty() && c.preview) {
                out.add(new Line(MobEffects.SPEED, "Speed II", "1:30", false));
                out.add(new Line(MobEffects.NIGHT_VISION, "Night Vision", "0:08", true));
            }
            return out;
        }

        private static boolean hidden(Ctx c, MobEffectInstance e) {
            MobEffectCategory cat = e.getEffect().value().getCategory();
            if (c.state.option("hideBeneficial") && cat == MobEffectCategory.BENEFICIAL) return true;
            if (c.state.option("hideHarmful") && cat == MobEffectCategory.HARMFUL) return true;
            if (c.state.option("hideInfinite") && e.isInfiniteDuration()) return true;
            if (BloomSettings.HUD_POTIONS_HIDDEN.mask() == 0) return false;
            return e.getEffect().unwrapKey().map(k -> BloomSettings.HUD_POTIONS_HIDDEN.contains(k.identifier().getPath())).orElse(false);
        }

        static String formatTicks(int ticks) {
            int s = ticks / 20;
            return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
        }

        static String roman(int n) {
            String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
            return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
        }

        @Override
        public int[] measure(Ctx c) {
            List<Line> l = lines(c);
            if (l.isEmpty()) return null;
            boolean icons = c.state.option("icons"), time = c.state.option("time"), compact = c.state.option("compact");
            int w = 0;
            for (Line line : l) {
                int tw = c.font.width(line.name()) + (time ? (compact ? c.font.width(line.time()) + 6 : 0) : 0);
                if (time && !compact) tw = Math.max(tw, c.font.width(line.time()));
                w = Math.max(w, tw);
            }
            int rowH = compact ? Math.max(10, icons ? 12 : 10) : (icons ? 20 : 19);
            return new int[]{(icons ? (compact ? 13 : 21) : 0) + w, l.size() * rowH - 2};
        }

        @Override
        public void render(GuiGraphics g, Ctx c, int w, int h) {
            boolean icons = c.state.option("icons"), time = c.state.option("time"), compact = c.state.option("compact");
            int rowH = compact ? Math.max(10, icons ? 12 : 10) : (icons ? 20 : 19);
            int y = 0;
            for (Line line : lines(c)) {
                int x = 0;
                if (icons) {
                    int s = compact ? 10 : 18;
                    var key = line.effect().unwrapKey();
                    if (key.isPresent()) {
                        Identifier sprite = key.get().identifier().withPrefix("mob_effect/");
                        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 0, y, s, s);
                    }
                    x = s + 3;
                }
                text(g, c, line.name(), x, y + (compact ? 1 : 0));
                if (time) {
                    int col = line.ending() && (c.nowMs / 400) % 2 == 0 ? 0xFFFF7A7A : dim(c.text, 0.75f);
                    if (compact) text(g, c, line.time(), x + c.font.width(line.name()) + 6, y + 1, col);
                    else text(g, c, line.time(), x, y + 10, col);
                }
                y += rowH;
            }
        }
    }

    /** Vanilla-style durability bar under an item drawn at (x, y). */
    static void durabilityBar(GuiGraphics g, ItemStack st, int x, int y) {
        if (!st.isBarVisible()) return;
        int w = st.getBarWidth();
        g.fill(x + 2, y + 13, x + 15, y + 15, 0xFF000000);
        g.fill(x + 2, y + 13, x + 2 + w, y + 14, 0xFF000000 | st.getBarColor());
    }

    static String durabilityText(ItemStack st, boolean percent) {
        if (!st.isDamageableItem() || st.getMaxDamage() <= 0) return "";
        int left = st.getMaxDamage() - st.getDamageValue();
        return percent ? (left * 100 / st.getMaxDamage()) + "%" : String.valueOf(left);
    }

    static int durabilityColor(HudWidget.Ctx c, ItemStack st) {
        if (!st.isDamageableItem() || st.getMaxDamage() <= 0) return c.text;
        float f = 1f - st.getDamageValue() / (float) st.getMaxDamage();
        return f < 0.25f ? 0xFFFF7A7A : f < 0.5f ? 0xFFF2D46B : c.text;
    }

    // ---------------------------------------------------------------- clock & session

    static final class Clock extends HudWidget {
        /** When the player joined the current world (ms), or 0 while not in a world. */
        private long sessionStart;

        Clock() {
            super(HudWidgetType.CLOCK);
        }

        private List<String> lines(Ctx c) {
            List<String> out = new ArrayList<>(2);
            if (c.mc.level == null) sessionStart = 0;
            else if (sessionStart == 0) sessionStart = c.nowMs;
            if (c.state.option("clock")) {
                LocalTime t = LocalTime.now();
                boolean h24 = c.state.option("h24"), sec = c.state.option("seconds");
                int h = t.getHour();
                String hh = h24 ? String.format(Locale.ROOT, "%02d", h) : String.valueOf(h % 12 == 0 ? 12 : h % 12);
                String s = hh + ":" + String.format(Locale.ROOT, "%02d", t.getMinute());
                if (sec) s += ":" + String.format(Locale.ROOT, "%02d", t.getSecond());
                if (!h24) s += h < 12 ? " AM" : " PM";
                out.add(s);
            }
            if (c.state.option("session")) {
                long ms = sessionStart > 0 ? c.nowMs - sessionStart : (c.preview ? 754_000L : 0L);
                long secs = ms / 1000;
                String s = secs >= 3600
                        ? String.format(Locale.ROOT, "%d:%02d:%02d", secs / 3600, (secs / 60) % 60, secs % 60)
                        : String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
                out.add("Session " + s);
            }
            return out;
        }

        @Override
        public int[] measure(Ctx c) {
            List<String> l = lines(c);
            if (l.isEmpty()) return null;
            int w = c.font.width("Session 0:00:00");
            for (String s : l) w = Math.max(w, c.font.width(s));
            return new int[]{w, l.size() * (c.font.lineHeight + 1) - 2};
        }

        @Override
        public void render(GuiGraphics g, Ctx c, int w, int h) {
            int y = 0;
            for (String s : lines(c)) {
                boolean session = s.startsWith("Session ");
                if (session) {
                    text(g, c, "Session", w - c.font.width(s), y, c.accent);
                    text(g, c, s.substring(7), w - c.font.width(s.substring(7)), y);
                } else {
                    text(g, c, s, w - c.font.width(s), y);
                }
                y += c.font.lineHeight + 1;
            }
        }
    }

    // ---------------------------------------------------------------- held item

    static final class Held extends HudWidget {
        Held() {
            super(HudWidgetType.HELD);
        }

        private List<ItemStack> items(Ctx c) {
            List<ItemStack> out = new ArrayList<>(2);
            LocalPlayer p = c.mc.player;
            if (p != null) {
                if (!p.getMainHandItem().isEmpty()) out.add(p.getMainHandItem());
                if (c.state.option("offhand") && !p.getOffhandItem().isEmpty()) out.add(p.getOffhandItem());
            }
            if (out.isEmpty() && c.preview) {
                ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                sword.setDamageValue(sword.getMaxDamage() * 2 / 3);
                out.add(sword);
                out.add(new ItemStack(Items.TOTEM_OF_UNDYING));
            }
            return out;
        }

        private String label(Ctx c, ItemStack st) {
            String d = durabilityText(st, c.state.option("percent"));
            if (d.isEmpty() && c.state.option("count") && st.getCount() > 1) return "x" + st.getCount();
            return d;
        }

        @Override
        public int[] measure(Ctx c) {
            List<ItemStack> items = items(c);
            if (items.isEmpty()) return null;
            int w = 0;
            for (ItemStack st : items) {
                String l = label(c, st);
                w += 18 + (l.isEmpty() ? 0 : c.font.width(l) + 2);
            }
            return new int[]{w - 2, 16};
        }

        @Override
        public void render(GuiGraphics g, Ctx c, int w, int h) {
            int x = 0;
            for (ItemStack st : items(c)) {
                g.renderItem(st, x, 0);
                if (c.state.option("bar")) durabilityBar(g, st, x, 0);
                String l = label(c, st);
                x += 18;
                if (!l.isEmpty()) {
                    text(g, c, l, x, 4, l.startsWith("x") ? c.text : durabilityColor(c, st));
                    x += c.font.width(l) + 2;
                }
            }
        }
    }

    // ---------------------------------------------------------------- sprint & sneak

    static final class Status extends HudWidget {
        Status() {
            super(HudWidgetType.STATUS);
        }

        private List<String[]> lines(Ctx c) {
            List<String[]> out = new ArrayList<>(3);
            LocalPlayer p = c.mc.player;
            boolean mode = c.state.option("mode");
            if (p != null) {
                if (c.state.option("flying") && p.getAbilities().flying) out.add(new String[]{"Flying", ""});
                if (c.state.option("sprint") && p.isSprinting()) {
                    boolean toggled = c.mc.options.toggleSprint().get() && c.mc.options.keySprint.isDown();
                    out.add(new String[]{"Sprinting", mode ? (toggled ? "Toggled" : c.mc.options.keySprint.isDown() ? "Held" : "") : ""});
                }
                if (c.state.option("sneak") && p.isShiftKeyDown()) {
                    boolean toggled = c.mc.options.toggleCrouch().get();
                    out.add(new String[]{"Sneaking", mode ? (toggled ? "Toggled" : "Held") : ""});
                }
            }
            if (out.isEmpty() && c.preview) out.add(new String[]{"Sprinting", mode ? "Toggled" : ""});
            return out;
        }

        @Override
        public int[] measure(Ctx c) {
            List<String[]> l = lines(c);
            if (l.isEmpty()) return null;
            int w = 0;
            for (String[] s : l) w = Math.max(w, c.font.width(s[0]) + (s[1].isEmpty() ? 0 : 4 + c.font.width("(" + s[1] + ")")));
            return new int[]{w, l.size() * (c.font.lineHeight + 1) - 2};
        }

        @Override
        public void render(GuiGraphics g, Ctx c, int w, int h) {
            int y = 0;
            for (String[] s : lines(c)) {
                int x = text(g, c, s[0], 0, y);
                if (!s[1].isEmpty()) text(g, c, "(" + s[1] + ")", x + 4 - (c.shadow ? 1 : 0), y, c.accent);
                y += c.font.lineHeight + 1;
            }
        }
    }

    // ---------------------------------------------------------------- info panel

    static final class Info extends HudWidget {
        Info() {
            super(HudWidgetType.INFO);
        }

        private List<String[]> lines(Ctx c) {
            List<String[]> out = new ArrayList<>(4);
            LocalPlayer p = c.mc.player;
            if (c.state.option("ping")) {
                String ping;
                if (c.mc.hasSingleplayerServer()) ping = "Singleplayer";
                else {
                    PlayerInfo info = p != null && c.mc.getConnection() != null ? c.mc.getConnection().getPlayerInfo(p.getUUID()) : null;
                    ping = info != null ? info.getLatency() + " ms" : (c.preview ? "42 ms" : "-");
                }
                out.add(new String[]{"Ping", ping});
            }
            if (c.state.option("server")) {
                ServerData sd = c.mc.getCurrentServer();
                String addr = sd != null ? sd.ip : c.mc.hasSingleplayerServer() ? "Local world" : (c.preview ? "play.example.net" : "-");
                if (addr.length() > 28) addr = addr.substring(0, 25) + "...";
                out.add(new String[]{"Server", addr});
            }
            long dayTime = c.mc.level != null ? c.mc.level.getDayTime() : 6000L * 3 + 24000L * 41;
            if (c.state.option("day")) out.add(new String[]{"Day", String.valueOf(dayTime / 24000L + 1)});
            if (c.state.option("time")) {
                long t = Math.floorMod(dayTime, 24000L);
                int hours = (int) ((t / 1000L + 6) % 24), mins = (int) ((t % 1000L) * 60 / 1000L);
                out.add(new String[]{"Time", String.format(Locale.ROOT, "%02d:%02d", hours, mins)});
            }
            return out;
        }

        @Override
        public int[] measure(Ctx c) {
            List<String[]> l = lines(c);
            if (l.isEmpty()) return null;
            int lw = 0, vw = 0;
            for (String[] s : l) {
                lw = Math.max(lw, c.font.width(s[0]));
                vw = Math.max(vw, c.font.width(s[1]));
            }
            return new int[]{lw + 6 + vw, l.size() * (c.font.lineHeight + 1) - 2};
        }

        @Override
        public void render(GuiGraphics g, Ctx c, int w, int h) {
            int y = 0;
            for (String[] s : lines(c)) {
                text(g, c, s[0], 0, y, c.accent);
                text(g, c, s[1], w - c.font.width(s[1]), y);
                y += c.font.lineHeight + 1;
            }
        }
    }
    // ---------------------------------------------------------------- now playing

    static final class Music extends HudWidget {
        private String cachedKey = "";
        private long lyricsStamp = Long.MIN_VALUE;
        private long nextLyricsCheckMs;
        private List<LyricLine> lyrics = List.of();
        private Identifier albumTexture;
        private String albumTextureKey = "";
        private int albumW = 1, albumH = 1;
        private record LyricLine(long atMs, String text) {}

        Music() { super(HudWidgetType.MUSIC); }

        private dev.bloom.client.core.music.MusicSession.Track track(Ctx c) {
            return c.preview ? null : dev.bloom.client.core.music.MusicSession.current();
        }

        private void updateArtwork(Ctx c) {
            var t = track(c);
            byte[] bytes = t == null ? null : t.artwork();
            String key = t == null ? "" : t.artist() + " - " + t.title() + " - " + t.album() + (bytes == null ? ":no-art" : ":art");
            if (key.equals(albumTextureKey)) return;
            albumTextureKey = key;
            if (albumTexture != null) c.mc.getTextureManager().release(albumTexture);
            albumTexture = null;
            if (bytes == null || bytes.length == 0) return;
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                albumW = image.getWidth(); albumH = image.getHeight();
                albumTexture = Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "music/album_art");
                c.mc.getTextureManager().register(albumTexture, new DynamicTexture(() -> "MOTION album artwork", image));
            } catch (Exception ignored) { albumTexture = null; }
        }

        private List<String> lines(Ctx c) {
            var t = track(c);
            String title = c.preview ? "Midnight City" : t == null ? "Nothing playing" : clean(t.title(), "Unknown track");
            String artist = c.preview ? "M83" : t == null ? "Start music in another app" : clean(t.artist(), "Unknown artist");
            List<String> out = new ArrayList<>();
            out.add(title);
            out.add(artist);
            if (c.state.option("album") && t != null && !t.album().isBlank()) out.add(clean(t.album(), ""));
            if (c.state.option("lyrics")) {
                String lyric = c.preview ? "♪ Waiting for the right moment" : t == null ? "" : currentLyric(t);
                if (!lyric.isBlank()) out.add(lyric);
            }
            return out;
        }

        private static String clean(String s, String fallback) {
            String v = s == null ? "" : s.replaceAll("[\\r\\n\\t]", " ").trim();
            return v.isEmpty() ? fallback : v;
        }

        private String currentLyric(dev.bloom.client.core.music.MusicSession.Track t) {
            String key = clean(t.artist(), "") + " - " + clean(t.title(), "");
            Path file = Path.of(System.getProperty("user.home"), "Music", "Lyrics", safeName(key) + ".lrc");
            if (!key.equals(cachedKey) || System.currentTimeMillis() >= nextLyricsCheckMs) {
                nextLyricsCheckMs = System.currentTimeMillis() + 1000;
                long stamp;
                try { stamp = Files.getLastModifiedTime(file).toMillis() ^ Files.size(file); }
                catch (Exception e) { stamp = -1; }
                if (!key.equals(cachedKey) || stamp != lyricsStamp) {
                    cachedKey = key;
                    lyricsStamp = stamp;
                    lyrics = readLyrics(file);
                }
            }
            String current = "";
            for (LyricLine line : lyrics) {
                if (line.atMs() > t.positionMs()) break;
                if (!line.text().isBlank()) current = line.text();
            }
            return current.isBlank() ? (lyrics.isEmpty() ? "Add synced .lrc lyrics" : "♪ …") : current;
        }

        private static String safeName(String text) { return text.replaceAll("[<>:\"/\\\\|?*]", "_").trim(); }

        private static List<LyricLine> readLyrics(Path file) {
            if (!Files.isRegularFile(file)) return List.of();
            List<LyricLine> out = new ArrayList<>();
            try {
                for (String raw : Files.readAllLines(file)) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\](.*)").matcher(raw);
                    while (m.find()) {
                        long min = Long.parseLong(m.group(1)), sec = Long.parseLong(m.group(2));
                        String fraction = m.group(3) == null ? "0" : m.group(3);
                        long millis = fraction.length() == 1 ? Long.parseLong(fraction) * 100 : fraction.length() == 2 ? Long.parseLong(fraction) * 10 : Long.parseLong(fraction);
                        out.add(new LyricLine((min * 60 + sec) * 1000 + millis, m.group(4).trim()));
                    }
                }
            } catch (Exception ignored) { return List.of(); }
            out.sort(java.util.Comparator.comparingLong(LyricLine::atMs));
            return out;
        }

        private List<String> clipped(Ctx c) {
            List<String> out = new ArrayList<>();
            for (String line : lines(c)) {
                String s = line;
                while (!s.isEmpty() && c.font.width(s) > 168) s = s.substring(0, s.length() - 1);
                if (s.length() < line.length() && s.length() > 2) s = s.substring(0, s.length() - 2) + "…";
                out.add(s);
            }
            return out;
        }

        @Override public int[] measure(Ctx c) {
            if (!c.preview && track(c) == null) { updateArtwork(c); return null; }
            updateArtwork(c);
            List<String> l = clipped(c);
            int textW = 0;
            for (String s : l) textW = Math.max(textW, c.font.width(s));
            return new int[]{34 + Math.max(72, textW), Math.max(28, l.size() * (c.font.lineHeight + 1) - 1)};
        }

        @Override public void render(GuiGraphics g, Ctx c, int w, int h) {
            g.fill(0, 2, 27, 27, withAlpha(c.accent, 0.20f));
            g.fill(1, 3, 26, 26, 0xB8101512);
            if (albumTexture != null) g.blit(RenderPipelines.GUI_TEXTURED, albumTexture, 1, 3, 0f, 0f, 25, 23, albumW, albumH, albumW, albumH, 0xFFFFFFFF);
            else text(g, c, "♫", 5, 8, c.accent);
            List<String> l = clipped(c);
            int y = 0;
            for (int i = 0; i < l.size(); i++) {
                int color = i == 0 ? c.text : i == 1 ? c.accent : dim(c.text, 0.78f);
                text(g, c, l.get(i), 32, y, color);
                y += c.font.lineHeight + 1;
            }
        }
    }
}
