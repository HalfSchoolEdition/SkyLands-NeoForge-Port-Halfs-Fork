package net.deepcanion.skylands.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.deepcanion.skylands.SkyLands;
import net.deepcanion.skylands.registry.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * One deterministic instance-specific details pass for single-piece Fallen Island jigsaws.
 * World-coordinate vein sites are irregular clusters; no cached mutable state, no chunk lookups.
 * Structure-void markers authored above grass are replaced with sparse vegetation or removed.
 */
public final class FallenIslandProcessor extends StructureProcessor {
    public static final MapCodec<FallenIslandProcessor> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(com.mojang.serialization.Codec.STRING.fieldOf("theme")
                    .forGetter(value -> value.theme)).apply(instance, FallenIslandProcessor::new));

    private final String theme;
    private volatile SkyTerrainPalette palette;
    public FallenIslandProcessor(String theme) { this.theme = theme; }

    @Override
    public StructureTemplate.StructureBlockInfo process(LevelReader level, BlockPos origin, BlockPos reference,
            StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo placed,
            StructurePlaceSettings settings, StructureTemplate template) {
        BlockState block = placed.state();
        BlockPos pos = placed.pos();
        long salt = switch (theme) {
            case "lavic" -> 0x19f4ae1L;
            case "golden" -> 0x72a1b4eL;
            case "glaciated" -> 0x8c160f1L;
            case "arid" -> 0xd4e51a3L;
            case "swamp" -> 0x5ca660fL;
            case "cherry" -> 0x760cf53L;
            default -> 0x1863L;
        };
        // Handcrafted Lavic underside spikes: authored candidate sockets are
        // randomly enabled per structure instance, so the underside differs.
        if ("lavic".equals(theme) && block.is(net.deepcanion.skylands.registry.ModBlocks.PALE_CELESTIUM_SPIKE.get())) {
            long h = SkyTerrainPatterns.mix(salt, pos.getX(), pos.getY(), pos.getZ(), 0x6bb14L);
            return SkyTerrainPatterns.unit(h) < .72 ? placed : null;
        }
        if (block.is(Blocks.STRUCTURE_VOID)) {
            long h = SkyTerrainPatterns.mix(salt, pos.getX(), pos.getY(), pos.getZ(), 0x43967aL);
            if (SkyTerrainPatterns.unit(h) > 0.25) return null;
            String a = switch (theme) {
                case "lavic" -> "short_lavic_grass";
                case "glaciated" -> "short_glaciated_grass";
                case "arid" -> "arid_grass";
                case "swamp" -> "swamp_grass";
                case "cherry" -> "cherry_short_grass";
                default -> "golden_short_grass";
            };
            String b = switch (theme) {
                case "lavic" -> "metallic_shorter_grass";
                case "arid" -> "small_arid_bush";
                case "swamp" -> "swamp_bush";
                case "cherry" -> "cherry_grass";
                case "glaciated" -> "glaciated_fern";
                default -> "golden_fern";
            };
            String plant = (h & 3L) == 0L ? b : a;
            BlockState state = BuiltInRegistries.BLOCK.get(SkyLands.id(plant)).defaultBlockState();
            return new StructureTemplate.StructureBlockInfo(pos, state, null);
        }
        SkyTerrainPalette currentPalette = palette;
        if (currentPalette == null) palette = currentPalette = SkyTerrainPalette.forTheme(theme);
        if (!block.is(currentPalette.core().getBlock()) || !isRareOreVein(pos, salt)) return placed;
        return new StructureTemplate.StructureBlockInfo(pos, currentPalette.ore(), null);
    }

    /** Rare ellipsoidal mineral pockets, not independent random ore speckles. */
    static boolean isRareOreVein(BlockPos pos, long salt) {
        int scale = 5;
        int cx = Math.floorDiv(pos.getX(), scale);
        int cy = Math.floorDiv(pos.getY(), scale);
        int cz = Math.floorDiv(pos.getZ(), scale);
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int y = cy - 1; y <= cy + 1; y++) {
                for (int z = cz - 1; z <= cz + 1; z++) {
                    long h = SkyTerrainPatterns.mix(salt, x, y, z, 0x17ec451L);
                    if (SkyTerrainPatterns.unit(h) >= 0.019) continue;
                    double px = x * scale + 1 + ((h >>> 8) & 3);
                    double py = y * scale + 1 + ((h >>> 13) & 3);
                    double pz = z * scale + 1 + ((h >>> 18) & 3);
                    double rx = 1.8 + ((h >>> 23) & 3) * 0.25;
                    double ry = 1.5 + ((h >>> 29) & 3) * 0.20;
                    double rz = 1.8 + ((h >>> 35) & 3) * 0.25;
                    double dx = (pos.getX() - px) / rx;
                    double dy = (pos.getY() - py) / ry;
                    double dz = (pos.getZ() - pz) / rz;
                    if (dx * dx + dy * dy + dz * dz < 1) return true;
                }
            }
        }
        return false;
    }

    @Override protected StructureProcessorType<?> getType() { return ModWorldgen.FALLEN_DETAILS.get(); }
}
