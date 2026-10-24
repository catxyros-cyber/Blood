package dev.bloom.client.mixin;

import dev.bloom.client.BloomClient;
import dev.bloom.client.core.settings.BloomSettings;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "Visual time of day": the client shows the sky at a fixed time. This only changes what this
 * client draws; the server's time, mob spawning and crop growth are unaffected.
 */
//#if MC >= 26
// 26.1 replaced the level's day time with world clocks; the client reads the Overworld clock.
@Mixin(net.minecraft.world.level.Level.class)
public abstract class ClientLevelDataMixin {
    @Inject(method = "getOverworldClockTime", at = @At("RETURN"), cancellable = true)
    private void bloom$visualTime(CallbackInfoReturnable<Long> cir) {
        dev.bloom.client.Hooks.fired("timeOfDay");
        if (!((Object) this instanceof ClientLevel)) return;
//#else
@Mixin(ClientLevel.ClientLevelData.class)
public abstract class ClientLevelDataMixin {
    @Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true)
    private void bloom$visualTime(CallbackInfoReturnable<Long> cir) {
        dev.bloom.client.Hooks.fired("timeOfDay");
//#endif
        BloomClient bc = BloomClient.get();
        if (bc == null || !bc.skyActive() || !BloomSettings.TIME_LOCK.on()) return;
        long day = Math.floorDiv(cir.getReturnValueJ(), 24000L) * 24000L;
        cir.setReturnValue(day + (long) BloomSettings.TIME_VALUE.intValue());
    }
}
