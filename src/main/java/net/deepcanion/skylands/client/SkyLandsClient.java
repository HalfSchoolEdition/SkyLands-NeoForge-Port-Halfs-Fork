package net.deepcanion.skylands.client;

import net.deepcanion.skylands.SkyLands;
import net.deepcanion.skylands.registry.ModEntities;
import net.deepcanion.skylands.registry.ModParticles;
import net.deepcanion.skylands.client.particle.*;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.deepcanion.skylands.client.model.*;
import net.deepcanion.skylands.client.renderer.*;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Client lifecycle listeners are isolated from common/server class loading. */
@EventBusSubscriber(modid = SkyLands.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SkyLandsClient {
    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModelAutomaton_boss.LAYER_LOCATION, ModelAutomaton_boss::createBodyLayer);
        event.registerLayerDefinition(Modelfvex.LAYER_LOCATION, Modelfvex::createBodyLayer);
        event.registerLayerDefinition(Modelhovering_automaton.LAYER_LOCATION, Modelhovering_automaton::createBodyLayer);
        event.registerLayerDefinition(Modelsky_boss0.LAYER_LOCATION, Modelsky_boss0::createBodyLayer);
        event.registerLayerDefinition(ModelAutomaton.LAYER_LOCATION, ModelAutomaton::createBodyLayer);
        event.registerLayerDefinition(ModelAutomatondesert.LAYER_LOCATION, ModelAutomatondesert::createBodyLayer);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(net.deepcanion.skylands.registry.ModBlockEntities.REWARD_DISPENSER.get(), RewardDispenserRenderer::new);
        event.registerBlockEntityRenderer(net.deepcanion.skylands.registry.ModBlockEntities.SKY_CHEST.get(), SkyChestRenderer::new);
        event.registerEntityRenderer(ModEntities.RISING_CELESTIUM_SPIKE.get(), risingSpikeRenderer());
        event.registerEntityRenderer(ModEntities.SKY_AUTOMATON_BOSS.get(), SkyAutomatonBossRenderer::new);
        event.registerEntityRenderer(ModEntities.AUTOMATON_DRONE.get(), AutomatonDroneRenderer::new);
        event.registerEntityRenderer(ModEntities.GLACIATED_SKY_AUTOMATON.get(), GlaciatedSkyAutomatonRenderer::new);
        event.registerEntityRenderer(ModEntities.GLACIATED_DRAGON.get(), GlaciatedDragonRenderer::new);
        event.registerEntityRenderer(ModEntities.SKY_AUTOMATON_BRUTE.get(), SkyAutomatonBruteRenderer::new);
        event.registerEntityRenderer(ModEntities.ICE_SLOBERT.get(), IceBlobRenderer::new);
        event.registerEntityRenderer(ModEntities.MAGMA_SLOBERT.get(), MagmaSlobertRenderer::new);
        event.registerEntityRenderer(ModEntities.IGNEO_ROBO_FELINE.get(), IgneoRoboFelineRenderer::new);
        event.registerEntityRenderer(ModEntities.GOLDEN_TUC_TUC.get(), SkyBirdRenderer::new);
        event.registerEntityRenderer(ModEntities.SKY_PHARAOH.get(), SkyPharaohRenderer::new);
        event.registerEntityRenderer(ModEntities.FRIENDLY_VEX.get(), FriendlyVexRenderer::new);
        event.registerEntityRenderer(ModEntities.HAPPY_SLOBERT.get(), HappySlobertRenderer::new);
        event.registerEntityRenderer(ModEntities.ARTIFICIAL_ARACNID.get(), ArtificialAracnidRenderer::new);
        event.registerEntityRenderer(ModEntities.SMOKE_FLOWER_PROJ.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.FLOAT_GRAPE_PROJ.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.ICE_ARROW.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.ICEBOMB.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.FIRE_WAVE.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.EYE_OFTS_PROJECTILE.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.SKY_AUTOMATON.get(), SkyAutomatonRenderer::new);
        event.registerEntityRenderer(ModEntities.ARID_SKY_AUTOMATON.get(), AridSkyAutomatonRenderer::new);
        event.registerEntityRenderer(ModEntities.LAVIC_SKY_AUTOMATON.get(), LavicSkyAutomatonRenderer::new);
    }

    /**
     * FallingBlockRenderer handles FallingBlockEntity and every subclass.
     * NeoForge's renderer factory is invariant in T, so the type bridge is
     * intentionally local and safe (RisingCelestiumSpikeEntity is a subtype).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static net.minecraft.client.renderer.entity.EntityRendererProvider<net.deepcanion.skylands.entity.RisingCelestiumSpikeEntity> risingSpikeRenderer() {
        return context -> (net.minecraft.client.renderer.entity.EntityRenderer)
                new net.minecraft.client.renderer.entity.FallingBlockRenderer(context);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.WIND.get(), WindParticle::provider);
        event.registerSpriteSet(ModParticles.FIRE_LEAF.get(), FireLeafParticle::provider);
        event.registerSpriteSet(ModParticles.FIREFLY.get(), FireflyParticle::provider);
        event.registerSpriteSet(ModParticles.CLOUD_STAFF_SPARKLE.get(), CloudStaffSparkleParticle::provider);
        event.registerSpriteSet(ModParticles.STORM_CLOUD.get(), net.deepcanion.skylands.client.particle.StormCloudParticle::provider);
        event.registerSpriteSet(ModParticles.STORM_CLOUD_1.get(), net.deepcanion.skylands.client.particle.StormCloudParticle::provider);
        event.registerSpriteSet(ModParticles.STORM_SNOWFLAKE.get(), net.deepcanion.skylands.client.particle.StormSnowflakeParticle::provider);
        event.registerSpriteSet(ModParticles.CACTUS_WARM_PIXEL.get(), sprites -> ThermalCactusPixelParticle.provider(sprites, true));
        event.registerSpriteSet(ModParticles.CACTUS_COLD_PIXEL.get(), sprites -> ThermalCactusPixelParticle.provider(sprites, false));
        event.registerSpriteSet(ModParticles.ITEM_BREAK.get(), ItemBreakParticle::provider);
        event.registerSpriteSet(ModParticles.CLOUD_BARRIER_3.get(), CloudBarrier3Particle::provider);
        event.registerSpriteSet(ModParticles.CLOUD_BARRIER_2.get(), CloudBarrier2Particle::provider);
        event.registerSpriteSet(ModParticles.CLOUD.get(), CloudParticle::provider);
        event.registerSpriteSet(ModParticles.CLOUD_SOURCE.get(), CloudParticle::sourceProvider);
        event.registerSpriteSet(ModParticles.CLOUD_SMOKE.get(), CloudSmokeParticle::provider);
        event.registerSpriteSet(ModParticles.CLOUD_SMOKE_1.get(), CloudSmoke1Particle::provider);
        event.registerSpriteSet(ModParticles.EMPTY.get(), EmptyParticle::provider);
        event.registerSpriteSet(ModParticles.FAST.get(), FastParticle::provider);
        event.registerSpriteSet(ModParticles.ICE.get(), IceParticle::provider);
        event.registerSpriteSet(ModParticles.TEMP_FIRE_PARTICLE.get(), TempFireParticleParticle::provider);
    }

    @SubscribeEvent
    public static void menus(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(net.deepcanion.skylands.registry.ModMenus.SKYLANDS_STARTER_GUIDE.get(), StarterGuideScreen::new);
        event.register(net.deepcanion.skylands.registry.ModMenus.SKY_CHEST.get(), SkyChestScreen::new);
        event.register(net.deepcanion.skylands.registry.ModMenus.PROTO_CHEST.get(), SkyChestScreen::new);
    }
    @SubscribeEvent
    public static void itemExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
            @Override public net.minecraft.resources.ResourceLocation getStillTexture() { return SkyLands.id("block/molten_metal_still"); }
            @Override public net.minecraft.resources.ResourceLocation getFlowingTexture() { return SkyLands.id("block/molten_metal_flow"); }
        }, net.deepcanion.skylands.registry.ModFluids.MOLTEN_METAL_TYPE.get());
        net.neoforged.neoforge.client.extensions.common.IClientItemExtensions extension =
                new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private RewardDispenserItemRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new RewardDispenserItemRenderer();
                return renderer;
            }
        };
        event.registerItem(extension,
            net.deepcanion.skylands.registry.ModBlocks.GOLDEN_REWARD_DISPENSER.get().asItem(),
            net.deepcanion.skylands.registry.ModBlocks.GLACIATED_REWARD_DISPENSER.get().asItem(),
            net.deepcanion.skylands.registry.ModBlocks.LAVIC_REWARD_DISPENSER.get().asItem(),
            net.deepcanion.skylands.registry.ModBlocks.ARID_REWARD_DISPENSER.get().asItem());
    }

    @SubscribeEvent
    public static void keyMappings(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(SkyFlightClient.FREEFALL);
    }
    @SubscribeEvent
    public static void guiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAboveAll(SkyLands.id("heat_vignette"), ClientHeatOverlay::render);
        event.registerAboveAll(SkyLands.id("flight_hud"), SkyFlightClient::render);
    }
    private SkyLandsClient() { }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(FlightAnimations::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientClouds::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientSwampFireflies::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientCloudStaff::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientAmbientWind::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientHeatOverlay::tick);
        event.enqueueWork(() -> net.neoforged.fml.ModList.get().getModContainerById(SkyLands.MOD_ID)
                .orElseThrow().registerExtensionPoint(
                        net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                        (mod, parent) -> new net.neoforged.neoforge.client.gui.ConfigurationScreen(mod, parent,
                                (configParent, type, config, title) -> new SkyLandsConfigurationSection(configParent, type, config, title))));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SkyFlightClient::tick);
        event.enqueueWork(() -> net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
            net.deepcanion.skylands.registry.ModFluids.MOLTEN_METAL.get(), net.minecraft.client.renderer.RenderType.translucent()));
        event.enqueueWork(() -> net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
            net.deepcanion.skylands.registry.ModFluids.FLOWING_MOLTEN_METAL.get(), net.minecraft.client.renderer.RenderType.translucent()));
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                net.deepcanion.skylands.registry.ModBlocks.STORM_BLOCK.get().asItem(), SkyLands.id("storm_unlocked"),
                (net.minecraft.client.renderer.item.ItemPropertyFunction) (stack, level, entity, seed) ->
                        net.deepcanion.skylands.item.StormBlockItem.isUnlocked(stack) ? 1F : 0F));
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                net.deepcanion.skylands.registry.ModItems.GLIDER.get(), SkyLands.id("glider_canvas"),
                (net.minecraft.client.renderer.item.ItemPropertyFunction) (stack, level, entity, seed) -> net.deepcanion.skylands.item.GliderItem.canvas(stack)));
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                net.deepcanion.skylands.registry.ModItems.GLIDER.get(), SkyLands.id("glider_grounded"),
                (net.minecraft.client.renderer.item.ItemPropertyFunction) (stack, level, entity, seed) ->
                        entity instanceof net.minecraft.world.entity.player.Player && entity.onGround() ? 1F : 0F));
        SkyLands.LOGGER.info("[Sky Lands] Unofficial Port v0.9.4 client setup complete.");
    }
}
