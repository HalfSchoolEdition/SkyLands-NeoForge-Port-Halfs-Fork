package net.deepcanion.skylands.client.renderer;

import net.deepcanion.skylands.client.model.TemperatureHelmetModel;
import net.deepcanion.skylands.item.TemperatureHelmetItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * GeckoLib 4 humanoid armor renderer. Both imported Blockbench files already
 * use the expected bipedHead -> armorHead bone hierarchy, so the complete
 * helmet follows the wearer's head in first/third person and on armor stands.
 */
public final class TemperatureHelmetRenderer extends GeoArmorRenderer<TemperatureHelmetItem> {
    public TemperatureHelmetRenderer(TemperatureHelmetItem.Style style) {
        super(new TemperatureHelmetModel(style));
    }
}
