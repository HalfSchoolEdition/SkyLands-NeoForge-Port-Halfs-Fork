package net.deepcanion.skylands.client.model;

import net.deepcanion.skylands.SkyLands;
import net.deepcanion.skylands.item.TemperatureHelmetItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Asset locations are intentionally independent of the registered item names. */
public final class TemperatureHelmetModel extends GeoModel<TemperatureHelmetItem> {
    private final ResourceLocation geometry;
    private final ResourceLocation texture;
    private final ResourceLocation animation;

    public TemperatureHelmetModel(TemperatureHelmetItem.Style style) {
        String file = style.asset();
        geometry = SkyLands.id("geo/" + file + ".geo.json");
        texture = SkyLands.id("textures/armor/" + file + ".png");
        animation = SkyLands.id("animations/" + file + ".animation.json");
    }

    @Override
    public ResourceLocation getModelResource(TemperatureHelmetItem item) {
        return geometry;
    }

    @Override
    public ResourceLocation getTextureResource(TemperatureHelmetItem item) {
        return texture;
    }

    @Override
    public ResourceLocation getAnimationResource(TemperatureHelmetItem item) {
        return animation;
    }
}
