package net.deepcanion.skylands.worldgen;

import net.deepcanion.skylands.SkyLands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Keep block palettes centralized: no vanilla terrain or foliage substitutions. */
public final class SkyTerrainPalette {
    private final String topId, coreId, soilId, plantId, alternatePlantId, spikeId, oreId;
    // Constructed during feature placement, after block registration. Resolve
    // states once per palette instead of repeating registry lookups per voxel.
    private final BlockState topState, coreState, soilState, plantState,
            alternatePlantState, spikeState, oreState;
    private SkyTerrainPalette(String top, String core, String soil, String plant, String alternatePlant,
                              String spike, String ore) {
        topId=top; coreId=core; soilId=soil; plantId=plant; alternatePlantId=alternatePlant;
        spikeId=spike; oreId=ore;
        topState = state(topId);
        coreState = state(coreId);
        soilState = state(soilId);
        plantState = state(plantId);
        alternatePlantState = state(alternatePlantId);
        spikeState = spikeId == null ? null : state(spikeId).setValue(
                net.deepcanion.skylands.block.AttachedBlock.FACE,
                net.minecraft.world.level.block.state.properties.AttachFace.CEILING);
        oreState = oreId == null ? null : state(oreId);
    }
    public static SkyTerrainPalette forTheme(String theme) {
        return switch (theme) {
            case "golden" -> new SkyTerrainPalette("golden_grass_block","skyrock","skyrock","golden_short_grass","golden_fern","celestium_spike","celestium_ore");
            case "glaciated" -> new SkyTerrainPalette("glaciated_grass_block","glaciated_skyrock","glaciated_skyrock","short_glaciated_grass","glaciated_fern","pearlecent_celestium_spike","pearlecent_celestium_ore");
            case "arid" -> new SkyTerrainPalette("arid_grass_block","sky_sandstone","sky_sandstone","arid_grass","small_arid_bush","inky_celestium_spike","inky_celestium_ore");
            case "lavic" -> new SkyTerrainPalette("lavic_skymetal_grass","lavic_skymetal","lavic_skymetal","short_lavic_grass","metallic_shorter_grass","pale_celestium_spike","pale_celestium_ore");
            case "swamp" -> new SkyTerrainPalette("swamp_grass_block","skyrock","skyrock","swamp_grass","swamp_bush","pearlecent_celestium_spike","pearlecent_celestium_ore");
            case "cherry" -> new SkyTerrainPalette("cherry_grass_block","skyrock","skyrock","cherry_short_grass","cherry_grass","celestium_spike","celestium_ore");
            default -> throw new IllegalArgumentException("Unknown SkyLands island theme: " + theme);
        };
    }
    private static BlockState state(String id) { return BuiltInRegistries.BLOCK.get(SkyLands.id(id)).defaultBlockState(); }
    public boolean isGlaciated() { return "glaciated_grass_block".equals(topId); }
    public boolean isSwamp() { return "swamp_grass_block".equals(topId); }
    public boolean isCherry() { return "cherry_grass_block".equals(topId); }
    public boolean isGolden() { return "golden_grass_block".equals(topId); }
    public boolean isArid() { return "arid_grass_block".equals(topId); }
    public boolean isLavic() { return "lavic_skymetal_grass".equals(topId); }
    public BlockState top() { return topState; }
    public BlockState core() { return coreState; }
    public BlockState soil() { return soilState; }
    public BlockState plant(boolean alternate) { return alternate ? alternatePlantState : plantState; }
    public boolean hasUndersideCrystals() { return spikeState != null; }
    public BlockState spike() { return spikeState; }
    public BlockState ore() { return oreState; }
    public boolean isTop(BlockState state) { return state.is(topState.getBlock()); }
}
