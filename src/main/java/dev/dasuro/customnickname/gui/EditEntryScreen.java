package dev.dasuro.customnickname.gui;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.util.ColorParser;
import dev.dasuro.customnickname.util.MojangLookup;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.UUID;
import java.util.Locale;

public class EditEntryScreen extends Screen {
    private final Screen parent;
    private final UUID uuid;

    private NickEntry entry;

    private TextFieldWidget usernameField;
    private TextFieldWidget nicknameField;

    private SliderWidget speedSlider;
    private int previewY = -1;
    private ErrorModal errorModal;

    private static final String I18N_BASE = "gui.customnickname.edit.";

    public EditEntryScreen(Screen parent, UUID uuid) {
        super(Text.translatable(I18N_BASE + "title"));
        this.parent = parent;
        this.uuid = uuid;
    }

    @Override
    protected void init() {
        if (this.errorModal == null) {
            this.errorModal = new ErrorModal(this.textRenderer);
        }
        this.errorModal.setScreenSize(this.width, this.height);

        this.clearChildren();

        // Work on a copy so "Back" discards changes
        if (entry == null) {
            NickEntry original = NickConfig.get(uuid);
            if (original == null) {
                entry = new NickEntry();
            } else {
                entry = new NickEntry(
                        original.username,
                        original.nickname,
                        original.showPrefix,
                        original.showSuffix,
                        original.rainbow,
                        original.rainbowSpeed
                );
            }
        }

        int x = 20;
        int w = this.width - 40;
        int y = 40;

        usernameField = new TextFieldWidget(
                this.textRenderer,
                x,
                y,
                w,
                20,
                t("field.username")
        );
        // Keep username length reasonable (matches typical MC constraints)
        usernameField.setMaxLength(16);
        usernameField.setText(entry.username != null ? entry.username : "");
        this.addDrawableChild(usernameField);

        nicknameField = new TextFieldWidget(
                this.textRenderer,
                x,
                y + 30,
                w,
                20,
                t("field.nickname")
        );
        // Allow long input (hex gradients can get very long)
        nicknameField.setMaxLength(4096);
        nicknameField.setText(entry.nickname != null ? entry.nickname : "");
        this.addDrawableChild(nicknameField);

        int btnY = y + 60;
        int btnW = (w - 10) / 2;

        this.addDrawableChild(
                ButtonWidget.builder(
                                toggleLabel("toggle.show_prefix", entry.showPrefix),
                                b -> {
                                    entry.showPrefix = !entry.showPrefix;
                                    b.setMessage(toggleLabel("toggle.show_prefix", entry.showPrefix));
                                }
                        )
                        .dimensions(x, btnY, btnW, 20)
                        .build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(
                                toggleLabel("toggle.show_suffix", entry.showSuffix),
                                b -> {
                                    entry.showSuffix = !entry.showSuffix;
                                    b.setMessage(toggleLabel("toggle.show_suffix", entry.showSuffix));
                                }
                        )
                        .dimensions(x + btnW + 10, btnY, btnW, 20)
                        .build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(
                                toggleLabel("toggle.rainbow_wave", entry.rainbow),
                                b -> {
                                    entry.rainbow = !entry.rainbow;
                                    b.setMessage(toggleLabel("toggle.rainbow_wave", entry.rainbow));
                                    if (speedSlider != null) {
                                        speedSlider.active = entry.rainbow;
                                    }
                                }
                        )
                        .dimensions(x, btnY + 30, btnW, 20)
                        .build()
        );

        speedSlider = new SliderWidget(
                x + btnW + 10,
                btnY + 30,
                btnW,
                20,
                Text.empty(),
                (entry.rainbowSpeed - 0.1f) / 4.9f
        ) {
            {
                this.updateMessage();
            }

            @Override
            protected void updateMessage() {
                entry.rainbowSpeed = (float) (0.1f + this.value * 4.9f);
                String speed = String.format(Locale.ROOT, "%.1f", entry.rainbowSpeed);
                this.setMessage(t("rainbow_speed", speed));
            }

            @Override
            protected void applyValue() {}
        };

        speedSlider.active = entry.rainbow;
        this.addDrawableChild(speedSlider);

        int btnW2 = 100;
        int gap2 = 10;
        int totalW2 = btnW2 * 2 + gap2;
        int startX2 = (this.width - totalW2) / 2;
        int yBottom2 = this.height - 30;

        // Preview directly under the rainbow row
        // Rainbow bottom = btnY+30+20. Box top = previewY-5, so previewY-5 should be 10px below rainbow.
        // => previewY = btnY + 30 + 20 + 10 + 5 = btnY + 65
        int rawPreviewY = btnY + 65;
        this.previewY = Math.min(rawPreviewY, yBottom2 - 30);

        this.addDrawableChild(
                ButtonWidget.builder(t("button.save"), b -> {
                            String name = usernameField.getText().trim();
                            String nick = nicknameField.getText();

                            if (name.isBlank()) {
                                showError("gui.customnickname.error.username_required");
                                return;
                            }

                            String visibleNick = ColorParser.strip(nick);
                            if (nick == null || nick.isBlank() || visibleNick.isBlank()) {
                                showError("gui.customnickname.error.nickname_required_formatting_only");
                                return;
                            }

                            entry.nickname = nick;

                            // Check if username was changed
                            NickEntry original = NickConfig.get(uuid);
                            String originalName = original != null ? original.username : null;
                            boolean usernameChanged = originalName != null && !name.equalsIgnoreCase(originalName);

                            if (!usernameChanged) {
                                // Username unchanged - just save under the same UUID
                                entry.username = name;
                                NickConfig.set(uuid, entry);
                                MinecraftClient.getInstance().setScreen(parent);
                                return;
                            }

                            // Username changed - resolve new UUID like saveAdd() does
                            UUID onlineUuid = findOnlineUuid(name);
                            if (onlineUuid != null) {
                                entry.username = findOnlineExactName(name);
                                NickConfig.remove(uuid);
                                NickConfig.set(onlineUuid, entry);
                                MinecraftClient.getInstance().setScreen(parent);
                                return;
                            }

                            // Not online - try Mojang API (async)
                            MojangLookup.resolveByName(name).thenAccept(profile -> {
                                MinecraftClient.getInstance().execute(() -> {
                                    if (MinecraftClient.getInstance().currentScreen != this) {
                                        return;
                                    }
                                    if (profile == null) {
                                        showError("gui.customnickname.error.uuid_for_name_not_found", name);
                                        return;
                                    }
                                    entry.username = profile.name();
                                    NickConfig.remove(uuid);
                                    NickConfig.set(profile.uuid(), entry);
                                    MinecraftClient.getInstance().setScreen(parent);
                                });
                            });
                        })
                        .dimensions(startX2, yBottom2, btnW2, 20)
                        .build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(t("button.back"), b -> {
                            MinecraftClient.getInstance().setScreen(parent);
                        })
                        .dimensions(startX2 + btnW2 + gap2, yBottom2, btnW2, 20)
                        .build()
        );
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        // Dark translucent overlay so widgets are readable
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean modalOpen = errorModal != null && errorModal.isOpen();
        int uiMouseX = modalOpen ? -10000 : mouseX;
        int uiMouseY = modalOpen ? -10000 : mouseY;

        super.render(context, uiMouseX, uiMouseY, delta);

        context.drawTextWithShadow(
                this.textRenderer,
                t("title"),
                10,
                10,
                0xFFFFFF
        );

        // Preview with rainbow support
        if (previewY != -1) {
            NickEntry tmp = new NickEntry();
            tmp.username = usernameField.getText();
            tmp.nickname = nicknameField.getText();
            tmp.showPrefix = entry.showPrefix;
            tmp.showSuffix = entry.showSuffix;
            tmp.rainbow = entry.rainbow;
            tmp.rainbowSpeed = entry.rainbowSpeed;

            String username = tmp.username == null ? "" : tmp.username;
            String rawNick = tmp.nickname;

            // If no nickname is set or only formatting codes, show nothing in the preview
            String visibleNick = ColorParser.strip(rawNick);
            if (rawNick != null && !rawNick.isEmpty() && !visibleNick.isBlank()) {
                Text nickText = ColorParser.buildNick(tmp, Text.literal(username));

                int y = previewY;
                int boxX = 20;
                int boxY = y - 5;
                int boxRight = this.width - 20;
                int boxBottom = y + this.textRenderer.fontHeight + 5;

                // Border (drawn as outer rect)
                context.fill(boxX, boxY, boxRight, boxBottom, 0xFF555555);
                // Background (inset by 1px on each side)
                context.fill(boxX + 1, boxY + 1, boxRight - 1, boxBottom - 1, 0xFF111111);

                context.drawTextWithShadow(
                        this.textRenderer,
                        t("preview_label"),
                        boxX + 5,
                        y,
                        0xFFAAAAAA
                );

                int labelW = this.textRenderer.getWidth(t("preview_label"));
                int previewTextX = boxX + 5 + labelW;

                int scissorRight = boxRight - 5;
                // Start scissor 2px earlier so italic text isn't clipped on the left
                int scissorLeft = previewTextX - 2;
                if (scissorRight > scissorLeft) {
                    context.enableScissor(scissorLeft, boxY, scissorRight, boxBottom);
                }

                context.drawTextWithShadow(
                        this.textRenderer,
                        nickText,
                        previewTextX,
                        y,
                        0xFFFFFFFF
                );

                if (scissorRight > scissorLeft) {
                    context.disableScissor();
                }
            }
        }

        if (errorModal != null) {
            errorModal.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (errorModal != null && errorModal.isOpen()) {
            return errorModal.mouseClicked(click, doubleClick);
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (errorModal != null && errorModal.isOpen()) {
            return errorModal.mouseReleased(click);
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (errorModal != null && errorModal.isOpen()) {
            return errorModal.mouseDragged(click, deltaX, deltaY);
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (errorModal != null && errorModal.isOpen()) {
            return errorModal.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (errorModal != null && errorModal.isOpen()) {
            return errorModal.keyPressed(keyInput);
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        if (errorModal != null && errorModal.isOpen()) {
            return errorModal.charTyped(charInput);
        }
        return super.charTyped(charInput);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    private static Text t(String key, Object... args) {
        return Text.translatable(I18N_BASE + key, args);
    }

    private static Text toggleLabel(String labelKey, boolean value) {
        return Text.translatable(
                I18N_BASE + "toggle",
                t(labelKey),
                value ? t("state.on") : t("state.off")
        );
    }

    private void showError(String translationKey, Object... args) {
        if (errorModal == null) {
            errorModal = new ErrorModal(this.textRenderer);
            errorModal.setScreenSize(this.width, this.height);
        }
        errorModal.show(Text.translatable(translationKey, args));
    }

    private UUID findOnlineUuid(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return null;

        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            String n = e.getProfile().name();
            if (n != null && n.equalsIgnoreCase(name)) return e.getProfile().id();
        }
        return null;
    }

    private String findOnlineExactName(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return name;

        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            String n = e.getProfile().name();
            if (n != null && n.equalsIgnoreCase(name)) return n;
        }
        return name;
    }
}

