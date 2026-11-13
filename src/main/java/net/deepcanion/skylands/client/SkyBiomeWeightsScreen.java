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

/** The region type weights are separate from small/medium/large structure density. */
public final class SkyBiomeWeightsScreen extends Screen {
    private final Screen parent;
    private final int[] draft = new int[6];
    private final List<WeightSlider> sliders = new ArrayList<>();
    private int scroll;
    private static final String[] BIOMES={"glaciated","lavic","swamp","arid","cherry","golden"};
    public SkyBiomeWeightsScreen(Screen parent) {
        super(Component.translatable("skylands.configuration.biomeSelection"));
        this.parent=parent;
        for(int i=0;i<6;i++)draft[i]=GameplayConfig.SKY_BIOME_WEIGHTS[i].getRaw();
    }
    @Override protected void init() {
        sliders.clear();
        int w=Math.min(320,width-24), x=(width-w)/2;
        for(int i=0;i<6;i++)sliders.add(addRenderableWidget(new WeightSlider(x,w,i)));
        int b=Math.min(100,(width-32)/3), start=(width-3*b-8)/2;
        addRenderableWidget(Button.builder(Component.translatable("skylands.configuration.resetDensity"), v->{
            java.util.Arrays.fill(draft,100);for(WeightSlider slider:sliders)slider.refresh();
        }).bounds(start,height-27,b,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),v->onClose())
                .bounds(start+b+4,height-27,b,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),v->{
            for(int i=0;i<6;i++)if(GameplayConfig.SKY_BIOME_WEIGHTS[i].getRaw()!=draft[i])
                GameplayConfig.SKY_BIOME_WEIGHTS[i].set(draft[i]);
            GameplayConfig.SPEC.save();onClose();
        }).bounds(start+2*(b+4),height-27,b,20).build());
        reposition();
    }
    private int bottom(){return Math.max(46,height-64);}
    private void reposition(){
        scroll=Math.clamp(scroll,0,Math.max(0,6*29-(bottom()-48)));
        for(WeightSlider slider:sliders){
            int y=52+slider.index*29-scroll;
            slider.setY(y);slider.visible=y>=47&&y+20<=bottom();
        }
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){
        if(y>=47&&y<bottom()){scroll-=(int)Math.round(v*29);reposition();return true;}
        return super.mouseScrolled(x,y,h,v);
    }
    @Override public void render(GuiGraphics gfx,int mx,int my,float pt){
        renderBackground(gfx,mx,my,pt);
        gfx.drawCenteredString(font,title,width/2,14,0xffffff);
        gfx.enableScissor(0,47,width,bottom());
        for(WeightSlider slider:sliders)slider.render(gfx,mx,my,pt);
        gfx.disableScissor();
        gfx.drawCenteredString(font,Component.translatable("skylands.configuration.biomeSelectionShortHelp"),
                width/2,height-47,0xaaaaaa);
        // Buttons render through the parent Screen's widget loop; sliders are manually scissored.
        for(var widget:renderables)if(!(widget instanceof WeightSlider))widget.render(gfx,mx,my,pt);
    }
    @Override public void renderBackground(GuiGraphics gfx,int mx,int my,float pt){
        gfx.fill(0,0,width,height,0xF0101010);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    private final class WeightSlider extends AbstractSliderButton {
        private final int index;
        private WeightSlider(int x,int w,int i){
            super(x,52,w,20,Component.empty(),draft[i]/300.0);
            index=i;
            setTooltip(Tooltip.create(Component.translatable("skylands.configuration.biomeSelectionHelp")));
            updateMessage();
        }
        private void refresh(){value=draft[index]/300.0;updateMessage();}
        @Override protected void updateMessage(){
            setMessage(Component.translatable("skylands.configuration.biomeWeight."+BIOMES[index]).append(": "+draft[index]+"%"));
        }
        @Override protected void applyValue(){draft[index]=(int)Math.round(value*300);updateMessage();}
    }
}
