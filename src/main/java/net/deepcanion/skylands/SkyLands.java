package net.deepcanion.skylands;

import com.mojang.logging.LogUtils;
import net.deepcanion.skylands.registry.ModBlocks;
import net.deepcanion.skylands.registry.ModEntities;
import net.deepcanion.skylands.registry.ModEffects;
import net.deepcanion.skylands.registry.ModParticles;
import net.deepcanion.skylands.entity.CombatEvents;
import net.deepcanion.skylands.registry.ModWorldgen;
import net.deepcanion.skylands.registry.ModSounds;
import net.deepcanion.skylands.registry.ContentSetup;
import net.deepcanion.skylands.registry.ModCreativeTabs;
import net.deepcanion.skylands.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/** Common entry point. Never references a client class or an inactive subsystem. */
@Mod(SkyLands.MOD_ID)
public final class SkyLands {
    public static final String MOD_ID = "skylands";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SkyLands(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,
                net.deepcanion.skylands.config.CloudConfig.SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON,
                net.deepcanion.skylands.config.GameplayConfig.SPEC);
        // Worldgen helpers are plain Java; sync their cache-keyed settings once
        // when NeoForge loads/reloads the COMMON config, before generation.
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading event)
                -> syncSurfaceCaves(event));
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading event)
                -> syncSurfaceCaves(event));
        net.deepcanion.skylands.registry.ModFluids.TYPES.register(modBus);
        net.deepcanion.skylands.registry.ModFluids.FLUIDS.register(modBus);
        // Preserve existing v0.9.3 test worlds after the Lavic tree ID cleanup.
        // Both block palettes and inventory BlockItems need aliases, not just assets.
        // These must be registered before the respective registries' RegisterEvent.
        for (String[] ids : new String[][] {
                {"lavic_stem", "lavic_burnt_log"},
                {"stripped_lavic_stem", "stripped_lavic_log"},
                {"lavic_fungus_block", "lavic_burnt_leaves"}
        }) {
            ModBlocks.BLOCKS.addAlias(id(ids[0]), id(ids[1]));
            ModItems.ITEMS.addAlias(id(ids[0]), id(ids[1]));
        }
        // Dev43 -> Dev44 aliases for items/blocks saved under the temporary names.
        for (String[] ids : new String[][] {
                {"glaciated_helmet", "frost_helmet"},
                {"lavic_helmet", "flame_helmet"},
                {"frost_potion", "cooling_potion"},
                {"warmth_radish", "hot_radish"},
                {"warmth_radish_plant", "hot_radish_plant"}
        }) {
            ModItems.ITEMS.addAlias(id(ids[0]), id(ids[1]));
        }
        ModBlocks.BLOCKS.addAlias(id("warmth_radish_plant"), id("hot_radish_plant"));
        // Old dev armor material keys may survive in item serialization.
        net.deepcanion.skylands.registry.ModArmorMaterials.MATERIALS.addAlias(id("glaciated"), id("frost"));
        net.deepcanion.skylands.registry.ModArmorMaterials.MATERIALS.addAlias(id("lavic"), id("flame"));
        ModBlocks.BLOCKS.register(modBus);
        net.deepcanion.skylands.registry.ModBlockEntities.TYPES.register(modBus);
        net.deepcanion.skylands.registry.ModMenus.MENUS.register(modBus);
        modBus.addListener(net.deepcanion.skylands.registry.ModBlockEntities::capabilities);
        ModEntities.register(modBus);
        net.deepcanion.skylands.registry.ModArmorMaterials.MATERIALS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.item.WarmthBrewing::register);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.item.CoolingBrewing::register);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.effect.GlaciatedBiomeCold::tick);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.effect.SkyTemperatures::tick);
        modBus.addListener(net.deepcanion.skylands.effect.TemperatureSettingsPayload::register);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.effect.TemperatureSettingsPayload::login);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.effect.TemperatureSettingsPayload::respawn);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.effect.TemperatureSettingsPayload::changedDimension);
        NeoForge.EVENT_BUS.addListener(CombatEvents::target);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.item.SkySwordRecovery::broken);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.item.SkySwordRecovery::tick);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.item.SkySwordRecovery::logout);
        ModItems.ITEMS.register(modBus);
        net.deepcanion.skylands.registry.ModVillagers.POI_TYPES.register(modBus);
        net.deepcanion.skylands.registry.ModVillagers.PROFESSIONS.register(modBus);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.registry.ModVillagers::trades);
        ModCreativeTabs.TABS.register(modBus);
        ModWorldgen.FEATURES.register(modBus);
        ModWorldgen.STRUCTURE_TYPES.register(modBus);
        ModWorldgen.PROCESSOR_TYPES.register(modBus);
        ModWorldgen.PLACEMENT_TYPES.register(modBus);
        ModWorldgen.STRUCTURE_PLACEMENT_TYPES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        modBus.addListener(net.deepcanion.skylands.glider.FlightAnimationPayload::register);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.glider.FlightAnimationState::tracking);
        modBus.addListener(net.deepcanion.skylands.glider.FreefallPayload::register);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.glider.FreefallFlight::login);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.glider.FreefallFlight::respawn);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.glider.FreefallFlight::dimensionChange);
        modBus.addListener(net.deepcanion.skylands.glider.GliderPayload::register);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.glider.GliderFlight::tick);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.glider.GliderFlight::login);
        modBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::serverStarted);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.worldgen.IslandDensity::starting);
        NeoForge.EVENT_BUS.addListener(net.deepcanion.skylands.worldgen.IslandDensity::stopped);
    }

    private static void syncSurfaceCaves(net.neoforged.fml.event.config.ModConfigEvent event) {
        if (event.getConfig().getSpec() != net.deepcanion.skylands.config.GameplayConfig.SPEC) return;
        net.deepcanion.skylands.worldgen.SkyIslandShape.configureSurfaceCaves(
                net.deepcanion.skylands.config.GameplayConfig.GENERATE_SURFACE_CAVES.get(),
                net.deepcanion.skylands.config.GameplayConfig.SURFACE_CAVE_FREQUENCY.get());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ContentSetup.registerFireBehavior();
        });
        LOGGER.info("[Sky Lands] Unofficial Port v0.9.4 common setup complete: {} blocks, {} items. Lithostitched sky biome injectors active; dimension port pending.",
                ModBlocks.BLOCKS.getEntries().size(), ModItems.ITEMS.getEntries().size());
    }

    private void serverStarted(ServerStartedEvent event) {
        LOGGER.info("[Sky Lands] Unofficial Port v0.9.4 server started.");
    }
}
