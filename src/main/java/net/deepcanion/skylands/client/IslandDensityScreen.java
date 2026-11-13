package net.deepcanion.skylands.client;

import java.util.ArrayList;
import java.util.List;
import net.deepcanion.skylands.config.GameplayConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Six draft sliders, with centered section headings, scrolling and explicit save/cancel. */
public final class IslandDensityScreen extends Screen {
    private final Screen parent;
    private final ModConfigSpec.IntValue[] settings = {GameplayConfig.SKY_SMALL, GameplayConfig.SKY_MEDIUM,
            GameplayConfig.SKY_LARGE, GameplayConfig.OUTSIDE_SMALL, GameplayConfig.OUTSIDE_MEDIUM, GameplayConfig.OUTSIDE_LARGE};
    private final int[] draft = new int[6];
    private final List<DensitySlider> sliders = new ArrayList<>();
    private final List<Button> footer = new ArrayList<>();
    private int scroll;
    private static final int TOP = 42;
    private static final int CONTENT_HEIGHT = 210;

    public IslandDensityScreen(Screen parent) {
        super(Component.translatable("skylands.configuration.islandDensity"));
        this.parent = parent;
        for (int i = 0; i < settings.length; i++) draft[i] = settings[i].getRaw();
    }

    @Override protected void init() {
        sliders.clear(); footer.clear();
        int sliderWidth = Math.min(310, width - 24);
        for (int i = 0; i < draft.length; i++)
            sliders.add(addRenderableWidget(new DensitySlider((width - sliderWidth) / 2, sliderWidth, i)));
        int buttonWidth = Math.min(100, (width - 32) / 3);
        int start = (width - (buttonWidth * 3 + 8)) / 2;
        footer.add(addRenderableWidget(Button.builder(Component.translatable("skylands.configuration.resetDensity"), ignored -> {
            java.util.Arrays.fill(draft, 100);
            for (DensitySlider slider : sliders) slider.refresh();
        }).bounds(start, height - 27, buttonWidth, 20).build()));
        footer.add(addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(start + buttonWidth + 4, height - 27, buttonWidth, 20).build()));
        footer.add(addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> {
            for (int i = 0; i < settings.length; i++) if (settings[i].getRaw() != draft[i]) settings[i].set(draft[i]);
            GameplayConfig.SPEC.save();
            onClose();
        }).bounds(start + (buttonWidth + 4) * 2, height - 27, buttonWidth, 20).build()));
        updatePositions();
    }

    private int bottom() { return Math.max(TOP + 1, height - 60); }
    private void updatePositions() {
        scroll = Math.clamp(scroll, 0, Math.max(0, CONTENT_HEIGHT - (bottom() - TOP)));
        for (DensitySlider slider : sliders) {
            int offset = slider.index < 3 ? 20 + slider.index * 26 : 132 + (slider.index - 3) * 26;
            int y = TOP + offset - scroll;
            slider.setY(y);
            slider.visible = y >= TOP && y + 20 <= bottom();
        }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseY >= TOP && mouseY < bottom()) {
            scroll -= (int) Math.round(vertical * 26);
            updatePositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 13, 0xffffff);
        graphics.enableScissor(0, TOP, width, bottom());
        heading(graphics, "skyBiomes", TOP - scroll);
        heading(graphics, "otherBiomes", TOP + 112 - scroll);
        for (DensitySlider slider : sliders) slider.render(graphics, mouseX, mouseY, partialTick);
        int viewport = bottom() - TOP;
        if (CONTENT_HEIGHT > viewport) {
            int x = (width + Math.min(310, width - 24)) / 2 + 3;
            int thumbHeight = Math.max(10, viewport * viewport / CONTENT_HEIGHT);
            int thumbY = TOP + scroll * (viewport - thumbHeight) / (CONTENT_HEIGHT - viewport);
            graphics.fill(x, TOP, x + 3, bottom(), 0xff333333);
            graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, 0xffaaaaaa);
        }
        graphics.disableScissor();
        graphics.drawCenteredString(font, Component.translatable("skylands.configuration.densityRestart"), width / 2, height - 48, 0xaaaaaa);
        for (Button button : footer) button.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Same plain dark backdrop as the supplied Jukebox360 NeoForge 1.21.1 config screens. */
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0101010);
    }

    private void heading(GuiGraphics graphics, String key, int y) {
        graphics.drawCenteredString(font, Component.translatable("skylands.configuration." + key), width / 2, y, 0xffffff);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN || key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
            scroll += key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN ? 78 : -78;
            updatePositions();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private final class DensitySlider extends AbstractSliderButton {
        private final int index;
        private DensitySlider(int x, int sliderWidth, int index) {
            super(x, TOP, sliderWidth, 20, Component.empty(), draft[index] / 300.0);
            this.index = index;
            setTooltip(Tooltip.create(Component.translatable("skylands.configuration.densityHelp")));
            updateMessage();
        }
        private void refresh() { value = draft[index] / 300.0; updateMessage(); }
        @Override protected void updateMessage() {
            String key = switch (index % 3) { case 0 -> "smallDensityLabel"; case 1 -> "mediumDensityLabel"; default -> "largeDensityLabel"; };
            setMessage(Component.translatable("skylands.configuration." + key, draft[index]));
        }
        @Override protected void applyValue() { draft[index] = (int) Math.round(value * 300); updateMessage(); }
    }
}
