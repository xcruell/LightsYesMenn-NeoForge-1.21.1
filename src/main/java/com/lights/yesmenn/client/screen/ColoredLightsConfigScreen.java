package com.lights.yesmenn.client.screen;

import com.lights.yesmenn.client.ColoredLightRenderConfig;
import com.lights.yesmenn.compat.vilc.VilcConfigBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public final class ColoredLightsConfigScreen extends Screen {
    private static final int COLUMN_WIDTH = 205;
    private static final int GAP = 10;
    private static final int PANEL_HEIGHT = 246;
    private static final float FOOTER_SCALE = 0.75F;
    private static final Component FOOTER = Component.literal(
            "Made by xcruell - v" + ModList.get()
                    .getModContainerById("lightsyesmenn")
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown"));

    private final Screen parent;
    private final boolean vilcAvailable;
    private final Object vilcConfig;

    public ColoredLightsConfigScreen(Screen parent) {
        super(Component.translatable("screen.lightsyesmenn.colored_lights_config"));
        this.parent = parent;
        this.vilcAvailable = VilcConfigBridge.available();
        this.vilcConfig = vilcAvailable ? VilcConfigBridge.config() : null;
    }

    public static void openFromVilc(Screen parent) {
        Minecraft.getInstance().setScreen(new ColoredLightsConfigScreen(parent));
    }

    @Override
    protected void init() {
        int columns = vilcConfig != null ? 3 : 1;
        int panelWidth = columns * COLUMN_WIDTH + (columns - 1) * GAP + 24;
        int left = (width - panelWidth) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int columnLeft = left + 12;
        int controlsTop = top + 44;

        if (vilcConfig != null) {
            addVilcQualityColumn(columnLeft, controlsTop);
            columnLeft += COLUMN_WIDTH + GAP;
        }

        addLightsYesMennColorColumn(columnLeft, controlsTop);

        if (vilcConfig != null) {
            columnLeft += COLUMN_WIDTH + GAP;
            addVilcColorColumn(columnLeft, controlsTop);
        }

        int buttonY = top + PANEL_HEIGHT - 32;
        addRenderableWidget(Button.builder(Component.translatable("controls.reset"), button -> {
                    ColoredLightRenderConfig.get().reset();
                    if (vilcConfig != null) {
                        VilcConfigBridge.resetColorSettings(vilcConfig);
                    }
                    rebuildConfigWidgets();
                })
                .bounds(left + 12, buttonY, COLUMN_WIDTH, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + panelWidth - 12 - COLUMN_WIDTH, buttonY, COLUMN_WIDTH, 20)
                .build());
    }

    private void addVilcQualityColumn(int left, int top) {
        addRenderableWidget(Button.builder(
                        Component.literal("Quality: " + VilcConfigBridge.qualityLabel(vilcConfig)),
                        button -> {
                            VilcConfigBridge.nextQualityPreset(vilcConfig);
                            rebuildConfigWidgets();
                        })
                .bounds(left, top, COLUMN_WIDTH, 20)
                .build());
        addRenderableWidget(new ConfigSlider(left, top + 26, COLUMN_WIDTH,
                "Point lights: %s", 1, 96,
                () -> VilcConfigBridge.getInt(vilcConfig, "pointLightLimit"),
                value -> VilcConfigBridge.setInt(vilcConfig, "pointLightLimit", (int) Math.round(value)),
                true));
        addRenderableWidget(new ConfigSlider(left, top + 52, COLUMN_WIDTH,
                "Area lights: %s", 1, 96,
                () -> VilcConfigBridge.getInt(vilcConfig, "areaLightLimit"),
                value -> VilcConfigBridge.setInt(vilcConfig, "areaLightLimit", (int) Math.round(value)),
                true));
        addRenderableWidget(Button.builder(
                        toggleLabel("Detailed normals", VilcConfigBridge.getBoolean(vilcConfig, "detailedNormals")),
                        button -> {
                            VilcConfigBridge.setBoolean(
                                    vilcConfig,
                                    "detailedNormals",
                                    !VilcConfigBridge.getBoolean(vilcConfig, "detailedNormals"));
                            rebuildConfigWidgets();
                        })
                .bounds(left, top + 78, COLUMN_WIDTH, 20)
                .build());
        addRenderableWidget(Button.builder(
                        toggleLabel("Voxel shadows", VilcConfigBridge.getBoolean(vilcConfig, "voxelShadows")),
                        button -> {
                            VilcConfigBridge.setBoolean(
                                    vilcConfig,
                                    "voxelShadows",
                                    !VilcConfigBridge.getBoolean(vilcConfig, "voxelShadows"));
                            rebuildConfigWidgets();
                        })
                .bounds(left, top + 104, COLUMN_WIDTH, 20)
                .build());
    }

    private void addLightsYesMennColorColumn(int left, int top) {
        ColoredLightRenderConfig config = ColoredLightRenderConfig.get();
        addRenderableWidget(new ConfigSlider(left, top, COLUMN_WIDTH,
                "Exposure: %s", 0.01, ColoredLightRenderConfig.MAX_EXPOSURE,
                () -> config.exposure, value -> config.exposure = value));
        addRenderableWidget(new ConfigSlider(left, top + 26, COLUMN_WIDTH,
                "Color strength: %s", 0.0, ColoredLightRenderConfig.MAX_COLOR_STRENGTH,
                () -> config.colorStrength, value -> config.colorStrength = value));
        addRenderableWidget(new ConfigSlider(left, top + 52, COLUMN_WIDTH,
                "Color saturation: %s", 0.5, 2.0,
                () -> config.colorSaturation, value -> config.colorSaturation = value));
        addRenderableWidget(new ConfigSlider(left, top + 78, COLUMN_WIDTH,
                "Neutral brightness: %s", 0.0, 2.0,
                () -> config.neutralLift, value -> config.neutralLift = value));
    }

    private void addVilcColorColumn(int left, int top) {
        addRenderableWidget(new ConfigSlider(left, top, COLUMN_WIDTH,
                "Exposure: %s", 0.01, 2.0,
                () -> VilcConfigBridge.getDouble(vilcConfig, "exposure"),
                value -> VilcConfigBridge.setDouble(vilcConfig, "exposure", value)));
        addRenderableWidget(new ConfigSlider(left, top + 26, COLUMN_WIDTH,
                "Color strength: %s", 0.0, 12.0,
                () -> VilcConfigBridge.getDouble(vilcConfig, "colorStrength"),
                value -> VilcConfigBridge.setDouble(vilcConfig, "colorStrength", value)));
        addRenderableWidget(new ConfigSlider(left, top + 52, COLUMN_WIDTH,
                "Color saturation: %s", 0.5, 2.0,
                () -> VilcConfigBridge.getDouble(vilcConfig, "colorSaturation"),
                value -> VilcConfigBridge.setDouble(vilcConfig, "colorSaturation", value)));
        addRenderableWidget(new ConfigSlider(left, top + 78, COLUMN_WIDTH,
                "Neutral brightness: %s", 0.0, 2.0,
                () -> VilcConfigBridge.getDouble(vilcConfig, "neutralLift"),
                value -> VilcConfigBridge.setDouble(vilcConfig, "neutralLift", value)));
        addRenderableWidget(new ConfigSlider(left, top + 104, COLUMN_WIDTH,
                "Luminance limit: %s", 1.0, 1.6,
                () -> VilcConfigBridge.getDouble(vilcConfig, "luminanceBoostLimit"),
                value -> VilcConfigBridge.setDouble(vilcConfig, "luminanceBoostLimit", value)));
    }

    @Override
    public void onClose() {
        ColoredLightRenderConfig.save();
        if (vilcConfig != null) {
            VilcConfigBridge.save();
        }
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int columns = vilcConfig != null ? 3 : 1;
        int panelWidth = columns * COLUMN_WIDTH + (columns - 1) * GAP + 24;
        int left = (width - panelWidth) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        graphics.fill(left, top, left + panelWidth, top + PANEL_HEIGHT, 0xD0000000);

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.lightsyesmenn.colored_lights_config.live_hint"),
                width / 2,
                top + 24,
                0xA0A0A0);

        int columnLeft = left + 12;
        if (vilcConfig != null) {
            drawColumnTitle(graphics, "VILC / Shader Limits", columnLeft, top + 34);
            columnLeft += COLUMN_WIDTH + GAP;
        }
        drawColumnTitle(graphics, "LightsYesMenn! / Veil", columnLeft, top + 34);
        if (vilcConfig != null) {
            columnLeft += COLUMN_WIDTH + GAP;
            drawColumnTitle(graphics, "VILC / Shader Colors", columnLeft, top + 34);
        }

        graphics.pose().pushPose();
        graphics.pose().scale(FOOTER_SCALE, FOOTER_SCALE, 1.0F);
        graphics.drawString(
                font,
                FOOTER,
                Math.round(6 / FOOTER_SCALE),
                Math.round((height - 8) / FOOTER_SCALE),
                0x70FFFFFF,
                false);
        graphics.pose().popPose();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    private void drawColumnTitle(GuiGraphics graphics, String text, int left, int y) {
        graphics.drawCenteredString(font, Component.literal(text), left + COLUMN_WIDTH / 2, y, 0xE0E0E0);
    }

    private void rebuildConfigWidgets() {
        clearWidgets();
        init();
    }

    private static Component toggleLabel(String label, boolean enabled) {
        return Component.literal(label + ": " + (enabled ? "ON" : "OFF"));
    }

    private static final class ConfigSlider extends AbstractSliderButton {
        private final String labelFormat;
        private final double min;
        private final double max;
        private final DoubleConsumer setter;
        private final boolean integer;

        private ConfigSlider(int x, int y, int width, String labelFormat, double min, double max,
                             DoubleSupplier getter, DoubleConsumer setter) {
            this(x, y, width, labelFormat, min, max, getter, setter, false);
        }

        private ConfigSlider(int x, int y, int width, String labelFormat, double min, double max,
                             DoubleSupplier getter, DoubleConsumer setter, boolean integer) {
            super(x, y, width, 20, Component.empty(), normalize(getter.getAsDouble(), min, max));
            this.labelFormat = labelFormat;
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.integer = integer;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            String valueText = integer
                    ? Integer.toString((int) Math.round(currentValue()))
                    : String.format(Locale.ROOT, "%.2f", currentValue());
            setMessage(Component.literal(String.format(Locale.ROOT, labelFormat, valueText)));
        }

        @Override
        protected void applyValue() {
            setter.accept(currentValue());
        }

        private double currentValue() {
            return min + value * (max - min);
        }

        private static double normalize(double value, double min, double max) {
            if (max <= min) {
                return 0.0;
            }
            return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
        }
    }
}
