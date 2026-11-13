package net.deepcanion.skylands.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;

/** Retains native config handling, with one combined island-density page. */
public final class SkyLandsConfigurationSection extends ConfigurationSectionScreen {
    public SkyLandsConfigurationSection(Screen parent, ModConfig.Type type, ModConfig config, Component title) {
        super(parent, type, config, title);
    }

    @Override protected Element createSection(String key, UnmodifiableConfig subconfig, UnmodifiableConfig subsection) {
        if (key.equals("islandDensity")) {
            Button button = Button.builder(Component.translatable("skylands.configuration.editDensity"),
                    ignored -> minecraft.setScreen(new IslandDensityScreen(this))).bounds(0, 0, 150, 20).build();
            return new Element(Component.translatable("skylands.configuration.islandDensity").append("..."),
                    Component.translatable("skylands.configuration.densityHelp"), button, false);
        }
        if (key.equals("biomeSelection")) {
            Button button = Button.builder(Component.translatable("skylands.configuration.editBiomeWeights"),
                    ignored -> minecraft.setScreen(new SkyBiomeWeightsScreen(this))).bounds(0, 0, 150, 20).build();
            return new Element(Component.translatable("skylands.configuration.biomeSelection").append("..."),
                    Component.translatable("skylands.configuration.biomeSelectionHelp"), button, false);
        }
        return super.createSection(key, subconfig, subsection);
    }
}
