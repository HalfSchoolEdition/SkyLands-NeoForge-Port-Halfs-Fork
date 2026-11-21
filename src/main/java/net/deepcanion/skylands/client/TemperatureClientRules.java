package net.deepcanion.skylands.client;

import net.deepcanion.skylands.config.GameplayConfig;
import net.deepcanion.skylands.effect.TemperatureSettingsPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Client-only lookup of server-authoritative helmet rules for inventory tooltips. */
@OnlyIn(Dist.CLIENT)
public final class TemperatureClientRules {
    private TemperatureClientRules() { }

    public static boolean helmetsEnabled() {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getPersistentData().contains(TemperatureSettingsPayload.CLIENT_HELMETS_ENABLED)) {
            return player.getPersistentData().getBoolean(TemperatureSettingsPayload.CLIENT_HELMETS_ENABLED);
        }
        return !GameplayConfig.SPEC.isLoaded() || GameplayConfig.TEMPERATURE_HELMETS_ENABLED.get();
    }
}
