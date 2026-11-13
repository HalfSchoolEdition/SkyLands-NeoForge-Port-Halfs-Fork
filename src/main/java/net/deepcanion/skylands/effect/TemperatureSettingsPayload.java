package net.deepcanion.skylands.effect;

import net.deepcanion.skylands.SkyLands;
import net.deepcanion.skylands.config.GameplayConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Synchronizes server temperature rules so the client heat overlay matches gameplay. */
public record TemperatureSettingsPayload(boolean netherHot, boolean overworldCold,
                                         boolean glaciatedCold, boolean lavicHot,
                                         boolean aridDayHot, boolean aridNightCold)
        implements CustomPacketPayload {
    public static final String CLIENT_NETHER_HEAT = "skylands_nether_heat_enabled";
    public static final String CLIENT_OVERWORLD_COLD = "skylands_overworld_cold_enabled";
    public static final String CLIENT_GLACIATED_COLD = "skylands_glaciated_cold_enabled";
    public static final String CLIENT_LAVIC_HEAT = "skylands_lavic_heat_enabled";
    public static final String CLIENT_ARID_DAY_HEAT = "skylands_arid_day_heat_enabled";
    public static final String CLIENT_ARID_NIGHT_COLD = "skylands_arid_night_cold_enabled";

    public static final Type<TemperatureSettingsPayload> TYPE = new Type<>(SkyLands.id("temperature_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TemperatureSettingsPayload> CODEC = new StreamCodec<>() {
        public TemperatureSettingsPayload decode(RegistryFriendlyByteBuf buffer) {
            return new TemperatureSettingsPayload(buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
        }
        public void encode(RegistryFriendlyByteBuf buffer, TemperatureSettingsPayload settings) {
            buffer.writeBoolean(settings.netherHot());
            buffer.writeBoolean(settings.overworldCold());
            buffer.writeBoolean(settings.glaciatedCold());
            buffer.writeBoolean(settings.lavicHot());
            buffer.writeBoolean(settings.aridDayHot());
            buffer.writeBoolean(settings.aridNightCold());
        }
    };

    @Override public Type<TemperatureSettingsPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        // Bumped version because this payload contains four new booleans.
        event.registrar("2").playToClient(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> {
            var data = context.player().getPersistentData();
            data.putBoolean(CLIENT_NETHER_HEAT, payload.netherHot());
            data.putBoolean(CLIENT_OVERWORLD_COLD, payload.overworldCold());
            data.putBoolean(CLIENT_GLACIATED_COLD, payload.glaciatedCold());
            data.putBoolean(CLIENT_LAVIC_HEAT, payload.lavicHot());
            data.putBoolean(CLIENT_ARID_DAY_HEAT, payload.aridDayHot());
            data.putBoolean(CLIENT_ARID_NIGHT_COLD, payload.aridNightCold());
        }));
    }

    private static void send(ServerPlayer player) {
        if (!GameplayConfig.SPEC.isLoaded()) return;
        boolean nether = GameplayConfig.NETHER_TOO_HOT.get();
        boolean overworld = GameplayConfig.OVERWORLD_COLD_FREEZING.get();
        boolean glaciated = GameplayConfig.GLACIATED_TOO_COLD.get();
        boolean lavic = GameplayConfig.LAVIC_TOO_HOT.get();
        boolean aridDay = GameplayConfig.ARID_DAY_TOO_HOT.get();
        boolean aridNight = GameplayConfig.ARID_NIGHT_TOO_COLD.get();
        var data = player.getPersistentData();
        data.putBoolean(CLIENT_NETHER_HEAT, nether);
        data.putBoolean(CLIENT_OVERWORLD_COLD, overworld);
        data.putBoolean(CLIENT_GLACIATED_COLD, glaciated);
        data.putBoolean(CLIENT_LAVIC_HEAT, lavic);
        data.putBoolean(CLIENT_ARID_DAY_HEAT, aridDay);
        data.putBoolean(CLIENT_ARID_NIGHT_COLD, aridNight);
        PacketDistributor.sendToPlayer(player, new TemperatureSettingsPayload(
                nether, overworld, glaciated, lavic, aridDay, aridNight));
    }

    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }
    /** Also catches live Common-config reloads, checking once every five seconds. */
    public static void syncIfChanged(ServerPlayer player) {
        if (player.tickCount % 100 != 0 || !GameplayConfig.SPEC.isLoaded()) return;
        var data = player.getPersistentData();
        if (!data.contains(CLIENT_NETHER_HEAT) || !data.contains(CLIENT_OVERWORLD_COLD)
                || !data.contains(CLIENT_GLACIATED_COLD) || !data.contains(CLIENT_LAVIC_HEAT)
                || !data.contains(CLIENT_ARID_DAY_HEAT) || !data.contains(CLIENT_ARID_NIGHT_COLD)
                || data.getBoolean(CLIENT_NETHER_HEAT) != GameplayConfig.NETHER_TOO_HOT.get()
                || data.getBoolean(CLIENT_OVERWORLD_COLD) != GameplayConfig.OVERWORLD_COLD_FREEZING.get()
                || data.getBoolean(CLIENT_GLACIATED_COLD) != GameplayConfig.GLACIATED_TOO_COLD.get()
                || data.getBoolean(CLIENT_LAVIC_HEAT) != GameplayConfig.LAVIC_TOO_HOT.get()
                || data.getBoolean(CLIENT_ARID_DAY_HEAT) != GameplayConfig.ARID_DAY_TOO_HOT.get()
                || data.getBoolean(CLIENT_ARID_NIGHT_COLD) != GameplayConfig.ARID_NIGHT_TOO_COLD.get())
            send(player);
    }
}
