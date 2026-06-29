package com.lights.yesmenn.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.lights.yesmenn.LightsYesMenn;
import com.lights.yesmenn.block.ColoredLightSpotlightBlock;
import com.lights.yesmenn.block.entity.ColoredLightBlockEntity;
import com.lights.yesmenn.client.ColoredLightPresetStore;
import com.lights.yesmenn.network.ColoredLightActionPayload;
import com.lights.yesmenn.network.ColoredLightSpotlightPayload;
import com.lights.yesmenn.network.OpenColoredLightScreenPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

public class ColoredLightScreen extends Screen {
    private static final int MIN_TEMPERATURE = 1000;
    private static final int MAX_TEMPERATURE = 12000;
    private static final int PICKER_SIZE = 86;
    private static final int HUE_WIDTH = 180;
    private static final int HUE_HEIGHT = 18;
    private static final int PICKER_Y_OFFSET = 108;
    private static final Preset[] BUILT_IN_PRESETS = {
            new Preset("gui.lightsyesmenn.light.preset.warm", 0xFFB05C),
            new Preset("gui.lightsyesmenn.light.preset.white", 0xFFFFFF),
            new Preset("gui.lightsyesmenn.light.preset.red", 0xFF3030),
            new Preset("gui.lightsyesmenn.light.preset.green", 0x38FF70),
            new Preset("gui.lightsyesmenn.light.preset.blue", 0x4070FF),
            new Preset("gui.lightsyesmenn.light.preset.purple", 0xC050FF)
    };

    private final BlockPos pos;
    private final List<Button> customPresetButtons = new ArrayList<>();
    private final boolean spotlight;
    private int radius;
    private int red;
    private int green;
    private int blue;
    private int brightness;
    private int temperature;
    private int spotlightAngle;
    private int spotlightYaw;
    private int spotlightPitch;
    private int spotlightSize;
    private int spotlightSoftness;
    private int spotlightFocus;
    private int pickerHue;
    private boolean redstoneInverted;
    private boolean savedRedstoneInverted;
    private boolean colorPickerMode;
    private int savedSpotlightAngle;
    private int savedSpotlightYaw;
    private int savedSpotlightPitch;
    private int savedSpotlightSize;
    private int savedSpotlightSoftness;
    private int savedSpotlightFocus;
    private boolean selectingCustomPreset;
    private boolean syncing;
    private boolean suppressLightPreview;
    private int colorDragMode;
    private boolean sliderDragging;
    private EditBox radiusBox;
    private EditBox brightnessBox;
    private EditBox redBox;
    private EditBox greenBox;
    private EditBox blueBox;
    private IntSlider radiusSlider;
    private IntSlider brightnessSlider;
    private IntSlider redSlider;
    private IntSlider greenSlider;
    private IntSlider blueSlider;
    private IntSlider temperatureSlider;
    private IntSlider spotlightAngleSlider;
    private IntSlider spotlightYawSlider;
    private IntSlider spotlightPitchSlider;
    private IntSlider spotlightSizeSlider;
    private IntSlider spotlightSoftnessSlider;
    private IntSlider spotlightFocusSlider;
    private Button colorModeButton;
    private Button redstoneModeButton;
    private DynamicTexture pickerTexture;
    private ResourceLocation pickerTextureId;
    private int pickerTextureHue = -1;
    private DynamicTexture hueTexture;
    private ResourceLocation hueTextureId;

    public ColoredLightScreen(OpenColoredLightScreenPayload payload) {
        super(Component.translatable("screen.lightsyesmenn.colored_light"));
        pos = payload.pos();
        spotlight = payload.spotlight();
        radius = payload.radius();
        red = payload.color() >> 16 & 0xFF;
        green = payload.color() >> 8 & 0xFF;
        blue = payload.color() & 0xFF;
        brightness = payload.brightness();
        temperature = nearestTemperature(payload.color());
        spotlightAngle = payload.spotlightAngle();
        spotlightYaw = payload.spotlightYaw();
        spotlightPitch = payload.spotlightPitch();
        spotlightSize = payload.spotlightSize();
        spotlightSoftness = payload.spotlightSoftness();
        spotlightFocus = payload.spotlightFocus();
        pickerHue = hueFromColor(payload.color());
        redstoneInverted = payload.redstoneInverted();
        savedRedstoneInverted = redstoneInverted;
        savedSpotlightAngle = spotlightAngle;
        savedSpotlightYaw = spotlightYaw;
        savedSpotlightPitch = spotlightPitch;
        savedSpotlightSize = spotlightSize;
        savedSpotlightSoftness = spotlightSoftness;
        savedSpotlightFocus = spotlightFocus;
    }

    @Override
    protected void init() {
        Layout layout = layout();
        int panelWidth = layout.mainWidth();
        int left = layout.left();
        int top = layout.top();
        int sliderX = left + 84;
        int sliderWidth = panelWidth - 152;
        int fieldX = left + panelWidth - 58;

        radiusBox = numberBox(fieldX, top + 52, 46, radius, 2);
        brightnessBox = numberBox(fieldX, top + 78, 46, brightness, 3);
        redBox = numberBox(fieldX, top + 104, 46, red, 3);
        greenBox = numberBox(fieldX, top + 130, 46, green, 3);
        blueBox = numberBox(fieldX, top + 156, 46, blue, 3);

        radiusSlider = slider(sliderX, top + 52, sliderWidth, 1, 32, radius, value -> {
            syncField(radiusBox, value);
            previewLightSettings(true);
        });
        brightnessSlider = slider(sliderX, top + 78, sliderWidth, 25, 800, brightness, value -> {
            syncField(brightnessBox, value);
            previewLightSettings(true);
        });
        redSlider = slider(sliderX, top + 104, sliderWidth, 0, 255, red, value -> {
            syncField(redBox, value);
            previewLightSettings(true);
        });
        greenSlider = slider(sliderX, top + 130, sliderWidth, 0, 255, green, value -> {
            syncField(greenBox, value);
            previewLightSettings(true);
        });
        blueSlider = slider(sliderX, top + 156, sliderWidth, 0, 255, blue, value -> {
            syncField(blueBox, value);
            previewLightSettings(true);
        });
        temperatureSlider = slider(sliderX, top + 190, panelWidth - 96, MIN_TEMPERATURE, MAX_TEMPERATURE,
                temperature, this::applyTemperature);

        bindField(radiusBox, radiusSlider, 1, 32);
        bindField(brightnessBox, brightnessSlider, 25, 800);
        bindField(redBox, redSlider, 0, 255);
        bindField(greenBox, greenSlider, 0, 255);
        bindField(blueBox, blueSlider, 0, 255);

        colorModeButton = addRenderableWidget(Button.builder(colorModeLabel(),
                        button -> {
                            colorPickerMode = !colorPickerMode;
                            button.setMessage(colorModeLabel());
                            updateColorInputVisibility();
                        })
                .bounds(left + 12, top + 10, 104, 18)
                .build());
        updateColorInputVisibility();

        int presetWidth = Math.max(42, (panelWidth - 34) / BUILT_IN_PRESETS.length);
        int presetX = left + 12;
        for (Preset preset : BUILT_IN_PRESETS) {
            addRenderableWidget(Button.builder(Component.translatable(preset.translationKey()),
                            button -> applyColor(preset.color()))
                    .bounds(presetX, top + 240, presetWidth - 4, 20)
                    .build());
            presetX += presetWidth;
        }

        customPresetButtons.clear();
        int customX = left + 12;
        int customWidth = 48;
        List<Integer> customColors = ColoredLightPresetStore.getPresets();
        for (int slot = 0; slot < ColoredLightPresetStore.SLOT_COUNT; slot++) {
            int presetSlot = slot;
            Button button = new PresetSlotButton(
                    customX,
                    top + 286,
                    customWidth,
                    20,
                    customPresetLabel(slot, customColors.get(slot)),
                    ignored -> handleCustomPreset(presetSlot),
                    () -> selectingCustomPreset);
            button.active = customColors.get(slot) >= 0;
            customPresetButtons.add(addRenderableWidget(button));
            customX += customWidth + 4;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.lightsyesmenn.light.preset.save"),
                        button -> beginPresetSelection())
                .bounds(left + panelWidth - 132, top + 286, 120, 20)
                .build());

        redstoneModeButton = addRenderableWidget(Button.builder(redstoneModeLabel(),
                        button -> {
                            redstoneInverted = !redstoneInverted;
                            button.setMessage(redstoneModeLabel());
                        })
                .bounds(left + panelWidth - 172, top + 332, 160, 20)
                .build());

        int buttonY = top + layout.height() - 28;
        if (spotlight) {
            int spotLeft = layout.spotlightLeft();
            int spotSliderX = spotLeft + 106;
            int spotSliderWidth = layout.spotlightWidth() - 118;
            spotlightAngleSlider = slider(spotSliderX, top + 44, spotSliderWidth, 5, 75,
                    spotlightAngle, value -> {
                        spotlightAngle = value;
                        previewSpotlightSettings();
                    });
            spotlightSizeSlider = slider(spotSliderX, top + 76, spotSliderWidth, 2, 60,
                    spotlightSize, value -> {
                        spotlightSize = value;
                        previewSpotlightSettings();
                    });
            spotlightSoftnessSlider = slider(spotSliderX, top + 108, spotSliderWidth, 1, 50,
                    spotlightSoftness, value -> {
                        spotlightSoftness = value;
                        previewSpotlightSettings();
                    });
            spotlightFocusSlider = slider(spotSliderX, top + 140, spotSliderWidth, 25, 500,
                    spotlightFocus, value -> {
                        spotlightFocus = value;
                        previewSpotlightSettings();
                    });
            spotlightYawSlider = snappingSlider(spotSliderX, top + 184, spotSliderWidth, -90, 90,
                    spotlightYaw, value -> {
                        spotlightYaw = value;
                        previewSpotlightSettings();
                    }, -90, -45, 0, 45, 90);
            spotlightPitchSlider = snappingSlider(spotSliderX, top + 216, spotSliderWidth, -90, 90,
                    spotlightPitch, value -> {
                        spotlightPitch = value;
                        previewSpotlightSettings();
                    }, -90, -45, 0, 45, 90);
            addRenderableWidget(Button.builder(Component.translatable("gui.lightsyesmenn.light.spotlight_reset"),
                            button -> resetSpotlightSettings())
                    .bounds(spotSliderX + spotSliderWidth - 92, top + 240, 92, 20)
                    .build());
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.lightsyesmenn.save"), button -> saveChanges())
                .bounds(left + panelWidth - 132, buttonY, 56, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.lightsyesmenn.close"), button -> onClose())
                .bounds(left + panelWidth - 68, buttonY, 56, 20)
                .build());
    }

    private EditBox numberBox(int x, int y, int boxWidth, int value, int digits) {
        EditBox box = new CenteredNumberBox(font, x, y, boxWidth, 20, Component.empty());
        box.setValue(Integer.toString(value));
        box.setFilter(text -> text.isEmpty() || text.matches("[0-9]{0," + digits + "}"));
        addRenderableWidget(box);
        return box;
    }

    private IntSlider slider(int x, int y, int sliderWidth, int min, int max, int value, IntConsumer consumer) {
        return addRenderableWidget(new IntSlider(x, y, sliderWidth, 20, min, max, value, consumer));
    }

    private IntSlider snappingSlider(int x, int y, int sliderWidth, int min, int max, int value, IntConsumer consumer,
            int... snapPoints) {
        return addRenderableWidget(new IntSlider(x, y, sliderWidth, 20, min, max, value, consumer, snapPoints));
    }

    private void bindField(EditBox box, IntSlider slider, int min, int max) {
        box.setResponder(value -> {
            if (syncing || value.isEmpty()) {
                return;
            }
            try {
                int parsed = Integer.parseInt(value);
                if (parsed >= min && parsed <= max) {
                    slider.setIntValue(parsed, false);
                    previewLightSettings(true);
                }
            } catch (NumberFormatException ignored) {
            }
        });
    }

    private void syncField(EditBox box, int value) {
        syncing = true;
        box.setValue(Integer.toString(value));
        syncing = false;
    }

    private void applyTemperature(int kelvin) {
        temperature = kelvin;
        applyColor(colorFromTemperature(kelvin));
    }

    private void applyColor(int color) {
        pickerHue = hueFromColor(color);
        applyColorKeepingHue(color);
    }

    private void applyColorKeepingHue(int color) {
        suppressLightPreview = true;
        try {
            setChannel(redBox, redSlider, color >> 16 & 0xFF);
            setChannel(greenBox, greenSlider, color >> 8 & 0xFF);
            setChannel(blueBox, blueSlider, color & 0xFF);
        } finally {
            suppressLightPreview = false;
        }
        previewLightSettings(true);
    }

    private void setChannel(EditBox box, IntSlider slider, int value) {
        slider.setIntValue(value, false);
        syncField(box, value);
        if (!suppressLightPreview) {
            previewLightSettings(true);
        }
    }

    private void handleCustomPreset(int slot) {
        if (selectingCustomPreset) {
            ColoredLightPresetStore.setPreset(slot, currentColor());
            selectingCustomPreset = false;
            refreshCustomPresetButtons();
            return;
        }

        int color = ColoredLightPresetStore.getPresets().get(slot);
        if (color >= 0) {
            applyColor(color);
        }
    }

    private void beginPresetSelection() {
        selectingCustomPreset = true;
        refreshCustomPresetButtons();
    }

    private void refreshCustomPresetButtons() {
        List<Integer> colors = ColoredLightPresetStore.getPresets();
        for (int slot = 0; slot < customPresetButtons.size(); slot++) {
            Button button = customPresetButtons.get(slot);
            button.setMessage(customPresetLabel(slot, colors.get(slot)));
            button.active = selectingCustomPreset || colors.get(slot) >= 0;
        }
    }

    private static Component customPresetLabel(int slot, int color) {
        return color < 0
                ? Component.literal((slot + 1) + ": -")
                : Component.literal((slot + 1) + ": #" + String.format("%06X", color));
    }

    private void saveChanges() {
        Integer nextRadius = parseIfValid(radiusBox, 1, 32);
        Integer nextBrightness = parseIfValid(brightnessBox, 25, 800);
        Integer nextRed = parseIfValid(redBox, 0, 255);
        Integer nextGreen = parseIfValid(greenBox, 0, 255);
        Integer nextBlue = parseIfValid(blueBox, 0, 255);
        if (nextRadius == null || nextBrightness == null || nextRed == null || nextGreen == null || nextBlue == null) {
            return;
        }

        int color = nextRed << 16 | nextGreen << 8 | nextBlue;
        int currentColor = red << 16 | green << 8 | blue;
        boolean lightChanged = nextRadius != radius
                || nextBrightness != brightness
                || color != currentColor
                || redstoneInverted != savedRedstoneInverted;
        boolean spotlightChanged = spotlight
                && (spotlightAngle != savedSpotlightAngle
                || spotlightYaw != savedSpotlightYaw
                || spotlightPitch != savedSpotlightPitch
                || spotlightSize != savedSpotlightSize
                || spotlightSoftness != savedSpotlightSoftness
                || spotlightFocus != savedSpotlightFocus);
        if (!lightChanged && !spotlightChanged) {
            return;
        }

        radius = nextRadius;
        brightness = nextBrightness;
        red = nextRed;
        green = nextGreen;
        blue = nextBlue;
        savedRedstoneInverted = redstoneInverted;
        savedSpotlightAngle = spotlightAngle;
        savedSpotlightYaw = spotlightYaw;
        savedSpotlightPitch = spotlightPitch;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
                && minecraft.level.getBlockEntity(pos) instanceof ColoredLightBlockEntity light) {
            light.setColor(color);
            light.setBrightness(brightness);
            if (spotlight) {
                light.setSpotlightConfig(
                        spotlightAngle,
                        brightness,
                        spotlightYaw,
                        spotlightPitch,
                        spotlightSize,
                        spotlightSoftness,
                        spotlightFocus);
            }
        }
        if (lightChanged) {
            PacketDistributor.sendToServer(new ColoredLightActionPayload(
                    pos,
                    ColoredLightActionPayload.SET_CONFIG,
                    ColoredLightActionPayload.packConfig(radius, color, redstoneInverted)));
            PacketDistributor.sendToServer(new ColoredLightActionPayload(
                    pos,
                    ColoredLightActionPayload.SET_BRIGHTNESS,
                    brightness));
        }
        if (spotlightChanged) {
            PacketDistributor.sendToServer(new ColoredLightSpotlightPayload(
                    pos,
                    spotlightAngle,
                    brightness,
                    spotlightYaw,
                    spotlightPitch,
                    spotlightSize,
                    spotlightSoftness,
                    spotlightFocus));
            savedSpotlightSize = spotlightSize;
            savedSpotlightSoftness = spotlightSoftness;
            savedSpotlightFocus = spotlightFocus;
        }
    }

    private void previewLightSettings(boolean sendToServer) {
        Integer nextRadius = parseIfValid(radiusBox, 1, 32);
        Integer nextBrightness = parseIfValid(brightnessBox, 25, 800);
        Integer nextRed = parseIfValid(redBox, 0, 255);
        Integer nextGreen = parseIfValid(greenBox, 0, 255);
        Integer nextBlue = parseIfValid(blueBox, 0, 255);
        if (nextRadius == null || nextBrightness == null || nextRed == null || nextGreen == null || nextBlue == null) {
            return;
        }

        int color = nextRed << 16 | nextGreen << 8 | nextBlue;
        radius = nextRadius;
        brightness = nextBrightness;
        red = nextRed;
        green = nextGreen;
        blue = nextBlue;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
                && minecraft.level.getBlockEntity(pos) instanceof ColoredLightBlockEntity light) {
            light.setConfig(radius, color, redstoneInverted);
            light.setBrightness(brightness);
        }
        if (sendToServer) {
            PacketDistributor.sendToServer(new ColoredLightActionPayload(
                    pos,
                    ColoredLightActionPayload.SET_CONFIG,
                    ColoredLightActionPayload.packConfig(radius, color, redstoneInverted)));
            PacketDistributor.sendToServer(new ColoredLightActionPayload(
                    pos,
                    ColoredLightActionPayload.SET_BRIGHTNESS,
                    brightness));
        }
    }

    private void previewSpotlightSettings() {
        if (!spotlight) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
                && minecraft.level.getBlockEntity(pos) instanceof ColoredLightBlockEntity light) {
            light.setSpotlightConfig(
                    spotlightAngle,
                    brightness,
                    spotlightYaw,
                    spotlightPitch,
                    spotlightSize,
                    spotlightSoftness,
                    spotlightFocus);
        }
    }

    private void resetSpotlightSettings() {
        spotlightAngle = ColoredLightBlockEntity.DEFAULT_SPOTLIGHT_ANGLE;
        spotlightYaw = ColoredLightBlockEntity.DEFAULT_SPOTLIGHT_YAW;
        spotlightPitch = defaultSpotlightPitch();
        spotlightSize = ColoredLightBlockEntity.DEFAULT_SPOTLIGHT_SIZE;
        spotlightSoftness = ColoredLightBlockEntity.DEFAULT_SPOTLIGHT_SOFTNESS;
        spotlightFocus = ColoredLightBlockEntity.DEFAULT_SPOTLIGHT_FOCUS;
        spotlightAngleSlider.setIntValue(spotlightAngle, false);
        spotlightYawSlider.setIntValue(spotlightYaw, false);
        spotlightPitchSlider.setIntValue(spotlightPitch, false);
        spotlightSizeSlider.setIntValue(spotlightSize, false);
        spotlightSoftnessSlider.setIntValue(spotlightSoftness, false);
        spotlightFocusSlider.setIntValue(spotlightFocus, false);
        previewSpotlightSettings();
    }

    private int defaultSpotlightPitch() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            BlockState state = minecraft.level.getBlockState(pos);
            if (state.getBlock() instanceof ColoredLightSpotlightBlock) {
                return ColoredLightSpotlightBlock.defaultDisplayPitch(state);
            }
        }
        return ColoredLightBlockEntity.DEFAULT_SPOTLIGHT_PITCH;
    }

    private Component redstoneModeLabel() {
        return Component.translatable(redstoneInverted
                ? "gui.lightsyesmenn.light.redstone.on"
                : "gui.lightsyesmenn.light.redstone.off");
    }

    private Component colorModeLabel() {
        return Component.translatable(colorPickerMode
                ? "gui.lightsyesmenn.light.color_mode.sliders"
                : "gui.lightsyesmenn.light.color_mode.picker");
    }

    private void updateColorInputVisibility() {
        redBox.visible = !colorPickerMode;
        greenBox.visible = !colorPickerMode;
        blueBox.visible = !colorPickerMode;
        redSlider.visible = !colorPickerMode;
        greenSlider.visible = !colorPickerMode;
        blueSlider.visible = !colorPickerMode;
        temperatureSlider.visible = !colorPickerMode;
    }

    private int currentColor() {
        return parse(redBox, red, 0, 255) << 16
                | parse(greenBox, green, 0, 255) << 8
                | parse(blueBox, blue, 0, 255);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int direction = scrollY > 0 ? 1 : scrollY < 0 ? -1 : 0;
        if (direction == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        boolean shifted = hasShiftDown();
        if (adjustHovered(mouseX, mouseY, radiusBox, radiusSlider, direction * (shifted ? 2 : 1))) {
            return true;
        }
        if (adjustHovered(mouseX, mouseY, brightnessBox, brightnessSlider, direction * (shifted ? 100 : 25))) {
            return true;
        }
        if (adjustHovered(mouseX, mouseY, redBox, redSlider, direction * (shifted ? 16 : 1))
                || adjustHovered(mouseX, mouseY, greenBox, greenSlider, direction * (shifted ? 16 : 1))
                || adjustHovered(mouseX, mouseY, blueBox, blueSlider, direction * (shifted ? 16 : 1))) {
            return true;
        }
        if (temperatureSlider.isMouseOver(mouseX, mouseY)) {
            temperatureSlider.step(direction * (shifted ? 500 : 100));
            return true;
        }
        if (spotlight
                && (adjustHovered(mouseX, mouseY, null, spotlightAngleSlider, direction * (shifted ? 5 : 1))
                || adjustHovered(mouseX, mouseY, null, spotlightSizeSlider, direction * (shifted ? 5 : 1))
                || adjustHovered(mouseX, mouseY, null, spotlightSoftnessSlider, direction * (shifted ? 5 : 1))
                || adjustHovered(mouseX, mouseY, null, spotlightFocusSlider, direction * (shifted ? 25 : 5))
                || adjustHovered(mouseX, mouseY, null, spotlightYawSlider, direction * (shifted ? 15 : 1))
                || adjustHovered(mouseX, mouseY, null, spotlightPitchSlider, direction * (shifted ? 15 : 1)))) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && startColorPickerDrag(mouseX, mouseY)) {
            return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && handled && isAnySliderMouseOver(mouseX, mouseY)) {
            sliderDragging = true;
            updateWidgetAlpha();
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && colorDragMode != 0) {
            updateColorPickerDrag(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            colorDragMode = 0;
            if (sliderDragging) {
                sliderDragging = false;
                updateWidgetAlpha();
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean startColorPickerDrag(double mouseX, double mouseY) {
        if (!colorPickerMode) {
            return false;
        }
        Layout layout = layout();
        int left = layout.left();
        int top = layout.top();
        int squareX = left + 84;
        int squareY = top + PICKER_Y_OFFSET;
        int hueX = squareX + PICKER_SIZE + 10;
        int hueY = squareY;
        if (mouseX >= squareX && mouseX < squareX + PICKER_SIZE && mouseY >= squareY && mouseY < squareY + PICKER_SIZE) {
            colorDragMode = 1;
            updateColorPickerDrag(mouseX, mouseY);
            return true;
        }
        if (mouseX >= hueX && mouseX < hueX + HUE_WIDTH && mouseY >= hueY && mouseY < hueY + HUE_HEIGHT) {
            colorDragMode = 2;
            updateColorPickerDrag(mouseX, mouseY);
            return true;
        }
        return false;
    }

    private void updateColorPickerDrag(double mouseX, double mouseY) {
        Layout layout = layout();
        int squareX = layout.left() + 84;
        int squareY = layout.top() + PICKER_Y_OFFSET;
        if (colorDragMode == 1) {
            float saturation = Mth.clamp((float) (mouseX - squareX) / (PICKER_SIZE - 1), 0.0F, 1.0F);
            float value = 1.0F - Mth.clamp((float) (mouseY - squareY) / (PICKER_SIZE - 1), 0.0F, 1.0F);
            applyColorKeepingHue(hsvToRgb(pickerHue, saturation, value));
        } else if (colorDragMode == 2) {
            int hueX = squareX + PICKER_SIZE + 10;
            pickerHue = Mth.clamp(Math.round((float) (mouseX - hueX) / (HUE_WIDTH - 1) * 360.0F), 0, 360);
            applyColorKeepingHue(hsvToRgb(pickerHue, saturationFromColor(currentColor()), valueFromColor(currentColor())));
        }
    }

    private boolean adjustHovered(double mouseX, double mouseY, EditBox box, IntSlider slider, int delta) {
        if ((box == null || !box.isMouseOver(mouseX, mouseY)) && !slider.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        slider.step(delta);
        return true;
    }

    private boolean isAnySliderMouseOver(double mouseX, double mouseY) {
        return radiusSlider.isMouseOver(mouseX, mouseY)
                || brightnessSlider.isMouseOver(mouseX, mouseY)
                || redSlider.isMouseOver(mouseX, mouseY)
                || greenSlider.isMouseOver(mouseX, mouseY)
                || blueSlider.isMouseOver(mouseX, mouseY)
                || temperatureSlider.isMouseOver(mouseX, mouseY)
                || spotlight && (spotlightAngleSlider.isMouseOver(mouseX, mouseY)
                || spotlightSizeSlider.isMouseOver(mouseX, mouseY)
                || spotlightSoftnessSlider.isMouseOver(mouseX, mouseY)
                || spotlightFocusSlider.isMouseOver(mouseX, mouseY)
                || spotlightYawSlider.isMouseOver(mouseX, mouseY)
                || spotlightPitchSlider.isMouseOver(mouseX, mouseY));
    }

    private void updateWidgetAlpha() {
        float alpha = sliderDragging ? 0.25F : 1.0F;
        for (var renderable : renderables) {
            if (renderable instanceof AbstractWidget widget) {
                widget.setAlpha(alpha);
            }
        }
    }

    private static int parse(EditBox box, int fallback, int min, int max) {
        try {
            return Mth.clamp(Integer.parseInt(box.getValue()), min, max);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static Integer parseIfValid(EditBox box, int min, int max) {
        try {
            int value = Integer.parseInt(box.getValue());
            return value >= min && value <= max ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Layout layout = layout();
        int panelWidth = layout.mainWidth();
        int left = layout.left();
        int top = layout.top();
        int alpha = sliderDragging ? 0x38 : 0xE0;
        int cardAlpha = sliderDragging ? 0x15 : 0x55;
        int accent = cardAlpha << 24 | currentColor();
        guiGraphics.fill(left, top, left + panelWidth, top + layout.height(), alpha << 24 | 0x101010);
        drawCard(guiGraphics, left + 8, top + 38, panelWidth - 16, 180, accent, cardAlpha);
        drawCard(guiGraphics, left + 8, top + 228, panelWidth - 16, 38, cardAlpha << 24 | 0x30C060, cardAlpha);
        drawCard(guiGraphics, left + 8, top + 272, panelWidth - 16, 38, cardAlpha << 24 | 0x30C060, cardAlpha);
        drawCard(guiGraphics, left + 8, top + 318, panelWidth - 16, 44, accent, cardAlpha);
        if (spotlight) {
            guiGraphics.fill(layout.spotlightLeft(), top, layout.spotlightLeft() + layout.spotlightWidth(), top + layout.height(), alpha << 24 | 0x101010);
            drawCard(guiGraphics, layout.spotlightLeft() + 8, top + 32, layout.spotlightWidth() - 16, 236, accent, cardAlpha);
        }
        if (sliderDragging) {
            setNumberBoxesVisible(false);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (sliderDragging) {
            setNumberBoxesVisible(true);
            renderDimmedNumberBoxes(guiGraphics);
        }

        int textColor = withAlpha(0xFFFFFF);
        int labelColor = withAlpha(0xE0E0E0);
        int mutedColor = withAlpha(0xC0C0C0);
        guiGraphics.drawCenteredString(font, title, left + panelWidth / 2, top + 14, textColor);
        guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.radius"), left + 12, top + 58, labelColor);
        guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.brightness"), left + 12, top + 84, labelColor);
        if (colorPickerMode) {
            renderColorPicker(guiGraphics, left, top, layout.mainWidth());
        } else {
            guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.red"), left + 12, top + 110, withAlpha(0xFF7777));
            guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.green"), left + 12, top + 136, withAlpha(0x77FF77));
            guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.blue"), left + 12, top + 162, withAlpha(0x7777FF));
            guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.temperature"), left + 12, top + 196, withAlpha(0xFFE3C0));
        }
        guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.presets"), left + 12, top + 228, mutedColor);
        guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.custom_presets"), left + 12, top + 272, mutedColor);

        int previewColor = (sliderDragging ? 0x40 : 0xFF) << 24 | currentColor();
        guiGraphics.fill(left + 12, top + 334, left + 172, top + 356, previewColor);
        guiGraphics.renderOutline(left + 11, top + 333, 162, 24, withAlpha(0xFFFFFF));
        guiGraphics.drawCenteredString(font, Component.translatable("gui.lightsyesmenn.light.preview"),
                left + 92, top + 320, mutedColor);

        if (spotlight) {
            int spotLeft = layout.spotlightLeft();
            guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.spotlight"),
                    spotLeft + 12, top + 14, mutedColor);
            guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.spotlight_angle"),
                    spotLeft + 12, top + 50, labelColor);
            guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.spotlight_size"),
                    spotLeft + 12, top + 82, labelColor);
            guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.spotlight_softness"),
                    spotLeft + 12, top + 114, labelColor);
            guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.spotlight_focus"),
                    spotLeft + 12, top + 146, labelColor);
            guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.spotlight_yaw"),
                    spotLeft + 12, top + 190, labelColor);
            guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.spotlight_pitch"),
                    spotLeft + 12, top + 222, labelColor);
            renderBeamPreview(guiGraphics, spotLeft + 18, top + 300, layout.spotlightWidth() - 36, 84);
        }
    }

    private void setNumberBoxesVisible(boolean visible) {
        radiusBox.visible = visible;
        brightnessBox.visible = visible;
        redBox.visible = visible && !colorPickerMode;
        greenBox.visible = visible && !colorPickerMode;
        blueBox.visible = visible && !colorPickerMode;
    }

    private void renderDimmedNumberBoxes(GuiGraphics guiGraphics) {
        renderDimmedNumberBox(guiGraphics, radiusBox);
        renderDimmedNumberBox(guiGraphics, brightnessBox);
        if (!colorPickerMode) {
            renderDimmedNumberBox(guiGraphics, redBox);
            renderDimmedNumberBox(guiGraphics, greenBox);
            renderDimmedNumberBox(guiGraphics, blueBox);
        }
    }

    private void renderDimmedNumberBox(GuiGraphics guiGraphics, EditBox box) {
        int fill = 0x22000000;
        int outline = 0x40FFFFFF;
        int textColor = 0x60FFFFFF;
        guiGraphics.fill(box.getX(), box.getY(), box.getX() + box.getWidth(), box.getY() + box.getHeight(), fill);
        guiGraphics.renderOutline(box.getX(), box.getY(), box.getWidth(), box.getHeight(), outline);
        String value = box.getValue();
        int textX = box.getX() + (box.getWidth() - font.width(value)) / 2;
        guiGraphics.drawString(font, value, textX, box.getY() + 6, textColor, false);
    }
    private void renderColorPicker(GuiGraphics guiGraphics, int left, int top, int panelWidth) {
        int squareX = left + 84;
        int squareY = top + PICKER_Y_OFFSET;
        int hueX = squareX + PICKER_SIZE + 10;
        int hueY = squareY;
        guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.color_picker"), left + 12, squareY + 4, withAlpha(0xE0E0E0));
        ensurePickerTextures();
        guiGraphics.blit(pickerTextureId, squareX, squareY, 0, 0, PICKER_SIZE, PICKER_SIZE, PICKER_SIZE, PICKER_SIZE);
        guiGraphics.renderOutline(squareX - 1, squareY - 1, PICKER_SIZE + 2, PICKER_SIZE + 2, 0xAAFFFFFF);

        guiGraphics.blit(hueTextureId, hueX, hueY, 0, 0, HUE_WIDTH, HUE_HEIGHT, HUE_WIDTH, HUE_HEIGHT);
        guiGraphics.renderOutline(hueX - 1, hueY - 1, HUE_WIDTH + 2, HUE_HEIGHT + 2, 0xAAFFFFFF);

        int color = currentColor();
        int markerX = squareX + Math.round(saturationFromColor(color) * (PICKER_SIZE - 1));
        int markerY = squareY + Math.round((1.0F - valueFromColor(color)) * (PICKER_SIZE - 1));
        guiGraphics.renderOutline(markerX - 3, markerY - 3, 7, 7, 0xFFFFFFFF);
        int hueMarkerX = hueX + Math.round((pickerHue / 360.0F) * (HUE_WIDTH - 1));
        guiGraphics.fill(hueMarkerX - 1, hueY - 3, hueMarkerX + 2, hueY + HUE_HEIGHT + 3, 0xFFFFFFFF);

        guiGraphics.drawString(font, Component.literal(String.format("#%06X", color)),
                hueX, hueY + 32, withAlpha(0xE0E0E0));
        guiGraphics.fill(hueX, hueY + 46, hueX + 72, hueY + 74, 0xFF000000 | color);
        guiGraphics.renderOutline(hueX - 1, hueY + 45, 74, 30, 0xAAFFFFFF);
    }

    private void ensurePickerTextures() {
        if (hueTexture == null) {
            hueTextureId = ResourceLocation.fromNamespaceAndPath(LightsYesMenn.MODID,
                    "dynamic/colored_light_hue_" + Integer.toHexString(System.identityHashCode(this)));
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, HUE_WIDTH, HUE_HEIGHT, false);
            for (int x = 0; x < HUE_WIDTH; x++) {
                int hue = Math.round(x / (float) (HUE_WIDTH - 1) * 360.0F);
                int color = nativeRgba(hsvToRgb(hue, 1.0F, 1.0F));
                for (int y = 0; y < HUE_HEIGHT; y++) {
                    image.setPixelRGBA(x, y, color);
                }
            }
            hueTexture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(hueTextureId, hueTexture);
        }

        if (pickerTexture == null) {
            pickerTextureId = ResourceLocation.fromNamespaceAndPath(LightsYesMenn.MODID,
                    "dynamic/colored_light_picker_" + Integer.toHexString(System.identityHashCode(this)));
            pickerTexture = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, PICKER_SIZE, PICKER_SIZE, false));
            Minecraft.getInstance().getTextureManager().register(pickerTextureId, pickerTexture);
        }
        if (pickerTextureHue != pickerHue) {
            NativeImage image = pickerTexture.getPixels();
            if (image != null) {
                for (int y = 0; y < PICKER_SIZE; y++) {
                    float value = 1.0F - y / (float) (PICKER_SIZE - 1);
                    for (int x = 0; x < PICKER_SIZE; x++) {
                        float saturation = x / (float) (PICKER_SIZE - 1);
                        image.setPixelRGBA(x, y, nativeRgba(hsvToRgb(pickerHue, saturation, value)));
                    }
                }
                pickerTexture.upload();
                pickerTextureHue = pickerHue;
            }
        }
    }

    private static int nativeRgba(int rgb) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        return 0xFF000000 | blue << 16 | green << 8 | red;
    }

    private void renderBeamPreview(GuiGraphics guiGraphics, int x, int y, int previewWidth, int previewHeight) {
        guiGraphics.fill(x, y, x + previewWidth, y + previewHeight, (sliderDragging ? 0x2A : 0xAA) << 24 | 0x080808);
        guiGraphics.renderOutline(x, y, previewWidth, previewHeight, withAlpha(0xFFFFFF, sliderDragging ? 0x19 : 0x66));
        guiGraphics.drawString(font, Component.translatable("gui.lightsyesmenn.light.spotlight_preview"),
                x + 8, y + 8, withAlpha(0xC0C0C0));
        int centerX = x + previewWidth / 2;
        int originY = y + previewHeight - 12;
        int beamLength = previewHeight - 32;
        int halfWidth = Math.max(8, spotlightAngle * (previewWidth - 42) / 150);
        int softness = Math.max(2, spotlightSoftness / 3);
        int rgb = currentColor();
        float focus = spotlightFocus / 100.0F;
        for (int row = 0; row < beamLength; row++) {
            float distance = row / (float) beamLength;
            float widthProgress = 1.0F - distance;
            int rowHalfWidth = Math.max(2, Math.round(halfWidth * widthProgress));
            int centerAlpha = Mth.clamp(Math.round(110.0F * (float) Math.pow(widthProgress, 1.0F / Math.max(0.2F, focus))), 12, 150);
            int edgeAlpha = Mth.clamp(centerAlpha / 3, 4, 60);
            int drawY = originY - row;
            guiGraphics.fill(centerX - rowHalfWidth, drawY, centerX + rowHalfWidth, drawY + 1, centerAlpha << 24 | rgb);
            guiGraphics.fill(centerX - rowHalfWidth - softness, drawY, centerX - rowHalfWidth, drawY + 1, edgeAlpha << 24 | rgb);
            guiGraphics.fill(centerX + rowHalfWidth, drawY, centerX + rowHalfWidth + softness, drawY + 1, edgeAlpha << 24 | rgb);
        }
        guiGraphics.fill(centerX - 3, originY - 3, centerX + 3, originY + 3, 0xFFFFFFFF);
    }

    private static void drawCard(GuiGraphics guiGraphics, int x, int y, int width, int height, int accent, int alpha) {
        guiGraphics.fill(x, y, x + width, y + height, alpha << 24 | 0x181818);
        guiGraphics.renderOutline(x, y, width, height, alpha << 24 | 0xFFFFFF);
        guiGraphics.fill(x, y, x + 2, y + height, accent);
    }

    private int withAlpha(int rgb) {
        return withAlpha(rgb, sliderDragging ? 0x40 : 0xFF);
    }

    private static int withAlpha(int rgb, int alpha) {
        return alpha << 24 | rgb;
    }

    private Layout layout() {
        int mainWidth = Math.min(420, width - 16);
        int spotlightWidth = spotlight ? 330 : 0;
        int gap = spotlight ? 10 : 0;
        int totalWidth = Math.min(width - 16, mainWidth + gap + spotlightWidth);
        if (spotlight && totalWidth < mainWidth + gap + spotlightWidth) {
            spotlightWidth = Math.max(260, totalWidth - mainWidth - gap);
        }
        int height = spotlight ? 430 : 410;
        int left = (width - (mainWidth + gap + spotlightWidth)) / 2;
        int top = Math.max(8, (this.height - height) / 2);
        return new Layout(left, top, height, mainWidth, gap, spotlightWidth);
    }

    private record Layout(int left, int top, int height, int mainWidth, int gap, int spotlightWidth) {
        private int spotlightLeft() {
            return left + mainWidth + gap;
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClose() {
        saveChanges();
        super.onClose();
    }

    @Override
    public void removed() {
        releasePickerTextures();
        super.removed();
    }

    private void releasePickerTextures() {
        if (pickerTexture != null) {
            Minecraft.getInstance().getTextureManager().release(pickerTextureId);
            pickerTexture = null;
            pickerTextureId = null;
            pickerTextureHue = -1;
        }
        if (hueTexture != null) {
            Minecraft.getInstance().getTextureManager().release(hueTextureId);
            hueTexture = null;
            hueTextureId = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int colorFromTemperature(int kelvin) {
        double temp = kelvin / 100.0;
        double r;
        double g;
        double b;
        if (temp <= 66) {
            r = 255;
            g = 99.4708025861 * Math.log(temp) - 161.1195681661;
            b = temp <= 19 ? 0 : 138.5177312231 * Math.log(temp - 10) - 305.0447927307;
        } else {
            r = 329.698727446 * Math.pow(temp - 60, -0.1332047592);
            g = 288.1221695283 * Math.pow(temp - 60, -0.0755148492);
            b = 255;
        }
        return Mth.clamp((int) Math.round(r), 0, 255) << 16
                | Mth.clamp((int) Math.round(g), 0, 255) << 8
                | Mth.clamp((int) Math.round(b), 0, 255);
    }

    private static int hueFromColor(int color) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        if (max == min) {
            return 0;
        }
        float hue;
        if (max == red) {
            hue = 60.0F * ((green - blue) / (float) (max - min));
        } else if (max == green) {
            hue = 60.0F * (2.0F + (blue - red) / (float) (max - min));
        } else {
            hue = 60.0F * (4.0F + (red - green) / (float) (max - min));
        }
        if (hue < 0.0F) {
            hue += 360.0F;
        }
        return Math.round(hue);
    }

    private static float saturationFromColor(int color) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        return max == 0 ? 0.0F : (max - min) / (float) max;
    }

    private static float valueFromColor(int color) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        return Math.max(red, Math.max(green, blue)) / 255.0F;
    }

    private static int hsvToRgb(int hue, float saturation, float value) {
        float normalizedHue = (hue % 360) / 60.0F;
        int sector = Mth.floor(normalizedHue);
        float fraction = normalizedHue - sector;
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - saturation * fraction);
        float t = value * (1.0F - saturation * (1.0F - fraction));
        float red;
        float green;
        float blue;
        switch (sector) {
            case 0 -> {
                red = value;
                green = t;
                blue = p;
            }
            case 1 -> {
                red = q;
                green = value;
                blue = p;
            }
            case 2 -> {
                red = p;
                green = value;
                blue = t;
            }
            case 3 -> {
                red = p;
                green = q;
                blue = value;
            }
            case 4 -> {
                red = t;
                green = p;
                blue = value;
            }
            default -> {
                red = value;
                green = p;
                blue = q;
            }
        }
        return Mth.clamp(Math.round(red * 255.0F), 0, 255) << 16
                | Mth.clamp(Math.round(green * 255.0F), 0, 255) << 8
                | Mth.clamp(Math.round(blue * 255.0F), 0, 255);
    }

    private static int nearestTemperature(int color) {
        int targetRed = color >> 16 & 0xFF;
        int targetGreen = color >> 8 & 0xFF;
        int targetBlue = color & 0xFF;
        int bestTemperature = 6500;
        long bestDistance = Long.MAX_VALUE;
        for (int kelvin = MIN_TEMPERATURE; kelvin <= MAX_TEMPERATURE; kelvin += 100) {
            int candidate = colorFromTemperature(kelvin);
            int dr = (candidate >> 16 & 0xFF) - targetRed;
            int dg = (candidate >> 8 & 0xFF) - targetGreen;
            int db = (candidate & 0xFF) - targetBlue;
            long distance = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestTemperature = kelvin;
            }
        }
        return bestTemperature;
    }

    private record Preset(String translationKey, int color) {
    }

    private static final class CenteredNumberBox extends EditBox {
        private final Font font;

        private CenteredNumberBox(Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
            this.font = font;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int borderColor = isFocused() ? 0xFFFFFFFF : 0xFFA0A0A0;
            int fillColor = isFocused() ? 0xFF000000 : 0xE0000000;
            guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), fillColor);
            guiGraphics.renderOutline(getX(), getY(), getWidth(), getHeight(), borderColor);

            String value = getValue();
            int textX = getX() + (getWidth() - font.width(value)) / 2;
            int textY = getY() + (getHeight() - 8) / 2;
            guiGraphics.drawString(font, value, textX, textY, 0xFFE0E0E0, false);
            if (isFocused() && Util.getMillis() / 300L % 2L == 0L) {
                int cursorX = textX + font.width(value);
                guiGraphics.fill(cursorX + 1, textY - 1, cursorX + 2, textY + 10, 0xFFFFFFFF);
            }
        }
    }
    private static final class PresetSlotButton extends Button {
        private final BooleanSupplier highlighted;

        private PresetSlotButton(int x, int y, int width, int height, Component message, OnPress onPress,
                                 BooleanSupplier highlighted) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.highlighted = highlighted;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            if (highlighted.getAsBoolean()) {
                guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x6630C060);
                guiGraphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF70E090);
            }
        }
    }

    private static final class IntSlider extends AbstractSliderButton {
        private static final int SNAP_THRESHOLD = 2;

        private final int min;
        private final int max;
        private final IntConsumer consumer;
        private final int[] snapPoints;
        private int intValue;

        private IntSlider(int x, int y, int width, int height, int min, int max, int value, IntConsumer consumer) {
            this(x, y, width, height, min, max, value, consumer, new int[0]);
        }

        private IntSlider(int x, int y, int width, int height, int min, int max, int value, IntConsumer consumer,
                int... snapPoints) {
            super(x, y, width, height, Component.empty(), normalized(value, min, max));
            this.min = min;
            this.max = max;
            this.consumer = consumer;
            this.snapPoints = snapPoints;
            this.intValue = snap(value);
            this.value = normalized(intValue, min, max);
            updateMessage();
        }

        private static double normalized(int value, int min, int max) {
            return (double) (Mth.clamp(value, min, max) - min) / (max - min);
        }

        private void setIntValue(int value, boolean notify) {
            intValue = snap(value);
            this.value = normalized(intValue, min, max);
            updateMessage();
            if (notify) {
                consumer.accept(intValue);
            }
        }

        private void step(int delta) {
            setIntValue(intValue + delta, true);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(Integer.toString(intValue)));
        }

        @Override
        protected void applyValue() {
            intValue = snap((int) Math.round(min + value * (max - min)));
            this.value = normalized(intValue, min, max);
            consumer.accept(intValue);
        }

        private int snap(int value) {
            int clamped = Mth.clamp(value, min, max);
            for (int snapPoint : snapPoints) {
                if (Math.abs(clamped - snapPoint) <= SNAP_THRESHOLD) {
                    return Mth.clamp(snapPoint, min, max);
                }
            }
            return clamped;
        }
    }
}
