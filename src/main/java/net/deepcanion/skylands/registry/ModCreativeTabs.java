package net.deepcanion.skylands.registry;

import net.deepcanion.skylands.SkyLands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.Comparator;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    // Retain registrations for existing worlds but hide dimension-only, unfinished content.
    // The matching c:hidden_from_recipe_viewers item tag keeps JEI/EMI listings tidy.
    private static final java.util.Set<String> HIDDEN_UNFINISHED_BLOCKS = java.util.Set.of(
            "abyss_grass", "blue_grimstone", "celestial_button", "celestial_door", "celestial_fence",
            "celestial_log", "celestial_planks", "celestial_pressure_plate", "celestial_slab", "celestial_stairs",
            "celestial_trapdoor", "celestial_tree_leaves", "celestial_wood", "chiseled_grimstone", "glowing_grimstone",
            "grimstone", "grimstone_bricks", "grimstone_bricks_slab", "grimstone_bricks_stairs", "grimstone_diamond_ore",
            "grimstone_gold_ore", "grimstone_iron_ore", "grimstone_lapis_ore", "grimstone_mycelium", "grimstone_pillar",
            "grimstone_redstone_ore", "grimstone_slab", "grimstone_stairs", "grimstone_tiles", "grimstone_tiles_slab",
            "grimstone_tiles_stairs", "grimstone_wall", "polished_grimstone", "polished_grimstone_slab", "polished_grimstone_stairs",
            "short_abyss_grass", "shrine_floor", "shrine_floor_slab", "shrine_floor_staircase", "shrine_plate",
            "shrine_sand", "shrine_tiles", "shrine_tiles_slab", "shrine_tiles_staircase", "shrine_wall",
            "shrine_wall_base", "shrine_wall_slab", "shrine_wall_staircase",
            // Assets retained for the as-yet-unported Underground dimension.
            "sulfur_block", "sulfur_sand", "sulfur_spike", "cloudsiumm", "celestine_block",
            "polished_bedrock", "metallic_silk_block", "celestine_spike", "small_celestine_spike");
    private static final java.util.Set<String> HIDDEN_UNFINISHED_ITEMS = java.util.Set.of(
            "celestine_crystal", "ingot_of_the_sky", "smithing_template_upgrade_of_the_sky",
            "sword_of_the_sky", "pickaxe_of_the_sky", "axe_of_the_sky", "shovel_of_the_sky",
            "verdant_sword_of_the_sky", "pearlecent_sword_of_the_sky",
            "inky_sword_of_the_sky", "pale_sword_of_the_sky", "eye_of_the_sky");
    // Deliberate vanilla-like browsing order, without altering registry IDs or saved worlds.
    // Individual group orders use familiar progression: source material -> building -> decor -> utilities.
    private static int itemGroup(String id) {
        if (id.equals("skylands_starter_guide")) return 0;
        if (id.equals("glider") || id.endsWith("_glider_design")) return 4;
        if (id.endsWith("_spawn_egg")) return 10;
        if (id.endsWith("_music_disc") || id.equals("tears_of_moogcity")) return 9;
        if (id.endsWith("_smithing_template")) return 8;
        if (id.endsWith("_key") || id.equals("master_key")) return 7;
        if (id.contains("sword") || id.contains("axe") || id.contains("pickaxe") || id.contains("shovel")
                || id.contains("staff") || id.contains("bow") || id.contains("blade")) return 2;
        if (id.endsWith("_helmet")) return 3;
        if (id.contains("flower") || id.contains("grape") || id.contains("berry") || id.equals("frost_berries") || id.contains("radish")
                || id.contains("potion") || id.contains("tuc_tuc_wing")) return 5;
        if (id.endsWith("_bucket") || id.equals("eye_of_the_sky")) return 6;
        return 1; // ingots, Celestium, cores and other crafting ingredients
    }

    private static int itemOrder(String id) {
        if (id.equals("skylands_starter_guide")) return 0;
        if (id.equals("glider")) return 0;
        // All four Celestium materials together, immediately followed by their four cores.
        String[] celestium = {"celestium", "pearlecent_celestium", "pale_celestium", "inky_celestium"};
        for (int i = 0; i < celestium.length; i++) {
            if (id.equals(celestium[i])) return i;
            if (id.equals(celestium[i] + "_core")) return i + 4;
        }
        if (id.equals("broken_sky_automaton_sword")) return 0;
        if (id.equals("automaton_sword")) return 1;
        if (id.equals("igneus_war_axe")) return 2;
        if (id.equals("igneus_laser_blade")) return 3;
        if (id.equals("ice_bow")) return 4;
        if (id.equals("cloud_staff")) return 5;
        if (id.equals("ice_staff")) return 6;
        if (id.equals("pharaoh_staff")) return 7;
        // Foods -> potions -> throwable / placeable ingredients.
        String[] consumables = {"raw_tuc_tuc_wing", "cooked_tuc_tuc_wing",
                "frost_berries", "hot_radish", "cooling_potion", "warmth_potion",
                "smoke_flower", "float_grape"};
        for (int i = 0; i < consumables.length; i++) if (id.equals(consumables[i])) return i;
        if (id.equals("master_key")) return 99;
        return 20;
    }

    private static final Comparator<net.neoforged.neoforge.registries.DeferredHolder<Item, ? extends Item>> ITEM_ORDER =
            Comparator.<net.neoforged.neoforge.registries.DeferredHolder<Item, ? extends Item>>comparingInt(i -> itemGroup(i.getId().getPath()))
                    .thenComparingInt(i -> itemOrder(i.getId().getPath()))
                    .thenComparing(i -> i.getId().getPath());

    // The primary building families come first, each followed by its slabs/stairs/walls.
    private static final String[] MASONRY_FAMILIES = {
            "skyrock", "cobbled_skyrock", "tiled_skyrock", "polished_skyrock",
            "golden_mossy_skyrock", "golden_mossy_tiled_skyrock", "chiseled_skyrock", "skyrock_column",
            "glaciated_skyrock", "cobbled_glaciated_skyrock", "polished_glaciated_skyrock",
            "glaciated_skyrock_bricks", "glaciated_mossy_skyrock", "glaciated_mossy_skyrock_bricks",
            "chiseled_glaciated_skyrock", "glaciated_skyrock_pillar",
            "lavic_skymetal", "polished_lavic_skymetal", "lavic_skymetal_bricks",
            "lavic_skymetal_tiles", "lavic_skymetal_grates", "lavic_skymetal_pipe",
            "sky_sandstone", "polished_sky_sandstone", "sky_sandstone_bricks",
            "mossy_sky_sandstone", "mossy_sky_sandstone_bricks", "chiseled_sky_sandstone", "sky_sandstone_pillar",
            "cherry_mossy_skyrock", "cherry_mossy_tiled_skyrock", "swamp_mossy_skyrock", "swamp_mossy_skyrock_bricks"
    };

    private static int blockGroup(String id) {
        if (id.endsWith("_chest") || id.startsWith("open_") || id.equals("proto_chest")) return 8;
        if (id.contains("reward_dispenser") || id.equals("sky_launcher") || id.equals("storm_block")
                || id.equals("celestium_anvil") || id.equals("mechanical_geyser")) return 9;
        if (id.equals("cristalizedice") || id.equals("ice_spikes")) return 6;
        if (id.contains("celestium")) return 5;
        if (id.contains("_log") || id.contains("_wood") || id.contains("_planks") || id.contains("_mosaic")
                || id.endsWith("_door") || id.endsWith("_trapdoor") || id.endsWith("_fence")
                || id.endsWith("_button") || id.endsWith("_pressure_plate")) return 3;
        if (id.contains("_grass_block") || id.equals("sky_sand") || id.equals("lavic_skymetal_grass")) return 0;
        if (id.contains("grass") || id.contains("fern") || id.contains("bush") || id.contains("mushroom")
                || id.contains("leaves") || id.contains("sapling") || id.contains("cactus")
                || id.contains("flower") || id.contains("water_plant") || id.contains("float_grape")
                || id.contains("radish_plant")) return 6;
        if (id.equals("cloud_block")) return 7;
        if (id.contains("_ore")) return 4;
        return 1; // stone, masonry and other building blocks
    }

    private static int blockOrder(String id) {
        if (id.contains("_grass_block")) {
            String[] themes = {"golden", "glaciated", "lavic", "arid", "cherry", "swamp"};
            for (int i = 0; i < themes.length; i++) if (id.startsWith(themes[i])) return i;
        }
        if (id.equals("sky_sand")) return 7;
        if (id.equals("lavic_skymetal_grass")) return 8;
        if (blockGroup(id) == 1) {
            for (int i = 0; i < MASONRY_FAMILIES.length; i++) {
                String f = MASONRY_FAMILIES[i];
                if (id.equals(f) || id.startsWith(f + "_")) return i * 10 + shapeOrder(id);
            }
        }
        if (blockGroup(id) == 5) {
            String[] types = {"celestium", "pearlecent_celestium", "pale_celestium", "inky_celestium"};
            for (int i = 0; i < types.length; i++) if (id.startsWith(types[i])) return i * 10 +
                    (id.endsWith("_ore") ? 0 : id.endsWith("_block") ? 1 : id.endsWith("_spike") ? 2 : 3);
        }
        if (blockGroup(id) == 6) {
            // Each sky biome's vegetation stays together. Keep glacial crystals and
            // spikes alongside the glacial grass, ahead of the glaciated fern.
            String[] plants = {
                    "golden_grass", "golden_short_grass", "blow_flower", "golden_fern",
                    "golden_leaves", "golden_tree_sapling", "float_grape_plant",
                    "short_glaciated_grass", "tall_glaciated_grass", "cristalizedice",
                    "ice_spikes", "glaciated_fern", "glaciated_leaves",
                    "glaciated_tree_sapling", "large_frost_berry_bush", "frost_berry_bush",
                    "short_lavic_grass", "metallic_shorter_grass", "lavic_mushrooms",
                    "lavic_burnt_leaves", "lavic_tree_sapling",
                    "arid_grass", "small_arid_bush", "small_sky_cactus",
                    "solero_cactus", "lunero_cactus", "hot_radish_plant",
                    "cherry_grass", "cherry_short_grass",
                    "swamp_grass", "swamp_bush", "swamp_water_plant",
                    "smoke_flower_plant"
            };
            for (int i = 0; i < plants.length; i++) if (id.equals(plants[i])) return i;
            return 150;
        }
        if (id.equals("storm_block")) return 0;
        if (id.equals("sky_launcher")) return 1;
        if (id.equals("celestium_anvil")) return 2;
        return 200;
    }

    private static int shapeOrder(String id) {
        if (id.endsWith("_slab")) return 1;
        if (id.endsWith("_stairs") || id.endsWith("_staircase")) return 2;
        if (id.endsWith("_wall") || id.endsWith("_walls")) return 3;
        return 0;
    }

    private static final Comparator<net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem>> BLOCK_ORDER =
            Comparator.<net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem>>comparingInt(i -> blockGroup(i.getId().getPath()))
                    .thenComparingInt(i -> blockOrder(i.getId().getPath()))
                    .thenComparing(i -> i.getId().getPath());

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SkyLands.MOD_ID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SKY_LANDS_ITEMS = TABS.register("sky_lands_items",
            () -> CreativeModeTab.builder().title(Component.translatable("item_group.skylands.sky_lands_items"))
                    .icon(() -> ModItems.CELESTIUM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> ModItems.ITEMS.getEntries().stream()
                            .filter(i -> !(i.get() instanceof BlockItem) && !HIDDEN_UNFINISHED_ITEMS.contains(i.getId().getPath()))
                            .sorted(ITEM_ORDER).forEach(i -> output.accept(i.get()))).build());
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FLOATY_STUFF = TABS.register("floaty_stuff",
            () -> CreativeModeTab.builder().title(Component.translatable("item_group.skylands.floaty_stuff"))
                    .icon(() -> ModBlocks.SKYROCK.get().asItem().getDefaultInstance())
                    .displayItems((parameters, output) -> ModItems.BLOCK_ITEMS.stream()
                            .filter(i -> {
                                String id = i.getId().getPath();
                                return !id.equals("sky_sand") && !id.equals("sky_cactus")
                                        && !id.equals("sky_cactus_branch") && !id.equals("sky_cactus_top")
                                        && !id.equals("smoke_flower_plant_grow_stage")
                                        && !HIDDEN_UNFINISHED_BLOCKS.contains(id);
                            })
                            .sorted(BLOCK_ORDER).forEach(i -> output.accept(i.get()))).build());
    private ModCreativeTabs() { }
}
