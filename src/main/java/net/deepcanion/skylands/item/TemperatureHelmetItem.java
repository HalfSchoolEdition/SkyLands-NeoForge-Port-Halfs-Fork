package net.deepcanion.skylands.item;

import java.util.List;
import java.util.function.Consumer;
import net.deepcanion.skylands.client.renderer.TemperatureHelmetRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Temperature protection continues to be controlled by TemperatureEquipment.
 * GeckoLib is only used for rendering the wearable model; replacing the
 * models does not change the item's normal armor behavior or its registry ID.
 */
public final class TemperatureHelmetItem extends ArmorItem implements GeoItem {
    public enum Style {
        FROST("frost_helmet"), FLAME("flame_helmet");

        private final String asset;

        Style(String asset) { this.asset = asset; }
        public String asset() { return asset; }
    }

    private final Style style;
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    public TemperatureHelmetItem(Holder<ArmorMaterial> material, Style style, Properties properties) {
        super(material, ArmorItem.Type.HELMET, properties);
        this.style = style;
    }

    public Style style() { return style; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        if (!net.deepcanion.skylands.client.TemperatureClientRules.helmetsEnabled()) return;
        // Explicitly describe the protection granted by the equipped helmet.
        // The icy and warm colors distinguish the two temperatures at a glance.
        boolean frost = this.style == Style.FROST;
        lines.add(Component.translatable(frost
                        ? "item.skylands.frost_helmet.tooltip"
                        : "item.skylands.flame_helmet.tooltip")
                .withStyle(format -> format.withColor(frost ? 0x54CDDF : 0xFFA13D)));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // These simple vanilla-style helmets are not animated.
        // The separate badge / snowflake bones remain available for the future.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableCache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            // Lazy initialization: do not construct client renderer classes until required.
            private GeoArmorRenderer<TemperatureHelmetItem> renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(
                    T wearer, ItemStack stack, EquipmentSlot slot, HumanoidModel<T> original) {
                if (this.renderer == null) {
                    this.renderer = new TemperatureHelmetRenderer(TemperatureHelmetItem.this.style);
                }
                return this.renderer;
            }
        });
    }
}
