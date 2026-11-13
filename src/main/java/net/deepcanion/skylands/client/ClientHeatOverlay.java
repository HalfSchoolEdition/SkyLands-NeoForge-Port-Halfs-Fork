package net.deepcanion.skylands.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.deepcanion.skylands.SkyLands;
import net.deepcanion.skylands.effect.SkyTemperatures;
import net.deepcanion.skylands.effect.TemperatureHazards;
import net.deepcanion.skylands.effect.TemperatureEquipment;
import net.deepcanion.skylands.worldgen.SkyBiomes;
import net.deepcanion.skylands.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Low-cost local rendering of the gradual Too Hot exposure vignette.
 * Server remains authoritative for exposure, cactus protection and damage.
 * The client predicts the same rules so the screen warms before the status
 * effect starts at full exposure, without per-tick network packets.
 */
public final class ClientHeatOverlay {
    private static final ResourceLocation TEXTURE = SkyLands.id("textures/screens/too_hot_overlay.png");
    private static final int MAX_HEAT = SkyTemperatures.HEAT_THRESHOLD;
    private static int heatTicks;
    private static boolean exposed;
    private static int lastCheckTick = Integer.MIN_VALUE;
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;

    private ClientHeatOverlay() { }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var player = minecraft.player;
        if (level == null || player == null) {
            heatTicks = 0;
            exposed = false;
            lastLevel = null;
            return;
        }
        if (minecraft.isPaused()) return;
        if (level != lastLevel) {
            heatTicks = 0;
            exposed = false;
            lastCheckTick = Integer.MIN_VALUE;
            lastLevel = level;
        }
        if (player.isCreative() || player.isSpectator()) {
            heatTicks = 0;
            exposed = false;
            return;
        }
        if (lastCheckTick == Integer.MIN_VALUE || player.tickCount - lastCheckTick >= 20
                || player.tickCount < lastCheckTick) {
            lastCheckTick = player.tickCount;
            boolean cactusBiome = level.getBiome(player.blockPosition()).is(SkyBiomes.ARID)
                    || level.getBiome(player.blockPosition()).is(SkyBiomes.LAVIC);
            exposed = TemperatureHazards.hot(level, player.blockPosition(), level.getDayTime(), player)
                    && !player.hasEffect(ModEffects.COOLING)
                    && !TemperatureEquipment.preventsOverheating(player)
                    && (!cactusBiome || !SkyTemperatures.nearProtectiveCactus(level,
                            player.blockPosition(), false, level.getDayTime()));
        }
        heatTicks = exposed ? Math.min(MAX_HEAT, heatTicks
                + (level.getBiome(player.blockPosition()).is(SkyBiomes.LAVIC) ? 2 : 1))
                : Math.max(0, heatTicks - 3);
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || heatTicks <= 0 || minecraft.screen != null) return;
        // The vignette grows over the full 14-second heat ramp and fades
        // more quickly in shelter. Slightly less quadratic than before so
        // the amber border is easier to notice before full exposure.
        float strength = heatTicks / (float) MAX_HEAT;
        float alpha = Math.min(1.0F, (float) Math.pow(strength, 1.5) * 0.98F);
        if (alpha < 0.004F) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(TEXTURE, 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                0.0F, 0.0F, 256, 256, 256, 256);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
