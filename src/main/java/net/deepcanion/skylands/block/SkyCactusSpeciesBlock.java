package net.deepcanion.skylands.block;

import net.deepcanion.skylands.effect.SkyTemperatures;
import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Two sky-cactus species with shared trunk/branch/top blockstates.
 * Closing and opening changes only the terminal block model. The old three
 * cactus IDs remain registered to preserve existing worlds and templates.
 */
public final class SkyCactusSpeciesBlock extends Block {
    public enum Part implements StringRepresentable {
        TRUNK("trunk"), BRANCH("branch"), TOP("top");
        private final String name;
        Part(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    private final boolean solero;

    public SkyCactusSpeciesBlock(boolean solero, Properties properties) {
        super(properties);
        this.solero = solero;
        registerDefaultState(stateDefinition.any().setValue(PART, Part.TOP)
                .setValue(FACING, Direction.NORTH).setValue(OPEN, true));
    }
    public boolean protectsCold() { return solero; }
    /** Both cacti switch on the same dawn/dusk threshold; Solero blooms
     * throughout the night, Lunero throughout the day. There is no dull
     * morning/evening window where both are closed. */
    public boolean opensAt(long time) {
        boolean night=Math.floorMod(time,24000L)>=12000L;
        return solero ? night : !night;
    }
    private boolean shouldOpen(long time) { return opensAt(time); }

    /** Shelter applies only if the nearby column has an actual open top.
     * Starting from any nearby segment lets taller cacti protect players
     * close to their bases without scanning wider areas for the top. */
    public boolean hasOpenTop(net.minecraft.world.level.LevelReader level, BlockPos segment) {
        for (int h = 0; h < 8; h++) {
            BlockState state = level.getBlockState(segment.above(h));
            if (!state.is(this)) return false;
            if (state.getValue(PART) == Part.TOP) return state.getValue(OPEN);
        }
        return false;
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING, OPEN);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(OPEN, shouldOpen(context.getLevel().getDayTime()));
    }
    @Override protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level,
                                            BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(this)) return true;
        return below.is(BlockTags.SAND) || below.is(Blocks.CACTUS)
                || below.is(ModBlocks.SKY_SAND.get()) || below.is(ModBlocks.ARID_GRASS_BLOCK.get());
    }
    @Override protected BlockState updateShape(BlockState state, Direction direction,
                BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos))
            return Blocks.AIR.defaultBlockState();
        // Player-stacked cacti also keep exactly one terminal top. When the
        // top is removed, the newly exposed last segment becomes a top.
        if (direction == Direction.UP) {
            if (neighbor.is(this) && state.getValue(PART) == Part.TOP)
                return state.setValue(PART, Part.TRUNK);
            if (neighbor.isAir() && state.getValue(PART) != Part.TOP) {
                level.scheduleTick(pos, this, 1);
                return state.setValue(PART, Part.TOP);
            }
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
    @Override public void onPlace(BlockState state, net.minecraft.world.level.Level level,
                                  BlockPos pos, BlockState previous, boolean moving) {
        super.onPlace(state, level, pos, previous, moving);
        if (!level.isClientSide() && state.getValue(PART) == Part.TOP)
            level.scheduleTick(pos, this, 4 + Math.floorMod(pos.hashCode(), 8));
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != Part.TOP) return;
        updateBloom(state, level, pos);
        boolean active = shouldOpen(level.getDayTime());
        // State updates run more frequently to follow dawn/dusk closely,
        // but particle packets retain approximately their previous cadence.
        if (active && random.nextInt(4)==0) level.sendParticles(solero ? net.minecraft.core.particles.ParticleTypes.FLAME
                    : net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                    pos.getX() + .5, pos.getY() + .35, pos.getZ() + .5,
                    3, 1.7, .75, 1.7, .005);
        if (active && random.nextInt(4)==0) {
            // Nine additional bulb-colored, fullbright glints for every three
            // original flame/snowflake particles. One packet per species, per
            // update; distributed through the area, not just on its boundary.
            level.sendParticles(solero ? net.deepcanion.skylands.registry.ModParticles.CACTUS_WARM_PIXEL.get()
                        : net.deepcanion.skylands.registry.ModParticles.CACTUS_COLD_PIXEL.get(),
                    pos.getX() + .5, pos.getY() + .35, pos.getZ() + .5,
                    9, 2.65, 1.35, 2.65, .005);
        }
        level.scheduleTick(pos, this, 8 + random.nextInt(4));
    }
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != Part.TOP) return;
        updateBloom(state, level, pos);
        if (random.nextFloat() < .22f) grow(level, pos, random);
    }
    private void updateBloom(BlockState state, ServerLevel level, BlockPos pos) {
        boolean open = shouldOpen(level.getDayTime());
        if (state.getValue(OPEN) != open)
            level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_CLIENTS);
    }
    private void grow(ServerLevel level, BlockPos pos, RandomSource random) {
        if (pos.getY() + 1 >= level.getMaxBuildHeight() || !level.isEmptyBlock(pos.above())) return;
        int height = 1;
        while (height < 8 && level.getBlockState(pos.below(height)).is(this)) height++;
        if (height >= 8 || !level.ensureCanWrite(pos.above())) return;
        BlockState oldTop = level.getBlockState(pos);
        if (!oldTop.is(this) || oldTop.getValue(PART) != Part.TOP) return;
        // 1 in 5 intermediate segments displays a branch. Its orientation is
        // stored on the segment instead of needing a separate block ID.
        level.setBlock(pos, oldTop.setValue(PART, random.nextInt(5) == 0 ? Part.BRANCH : Part.TRUNK)
                .setValue(FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)), Block.UPDATE_CLIENTS);
        BlockState top = defaultBlockState().setValue(PART, Part.TOP)
                .setValue(FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random))
                .setValue(OPEN, shouldOpen(level.getDayTime()));
        level.setBlock(pos.above(), top, Block.UPDATE_ALL);
    }
}
