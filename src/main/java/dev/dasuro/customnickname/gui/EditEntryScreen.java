package dev.dasuro.customnickname.gui;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.util.ColorParser;
import dev.dasuro.customnickname.util.MojangLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import java.util.Locale;

public class EditEntryScreen extends Screen {
    private final Screen parent;
    private final UUID uuid;

    private NickEntry entry;

    private EditBox usernameField;
    private EditBox nicknameField;

    private AbstractSliderButton speedSlider;
    private int previewY = -1;
    private ErrorModal errorModal;

    private static final String I18N_BASE = "gui.customnickname.edit.";

    public EditEntryScreen(Screen parent, UUID uuid) {
        super(Component.translatable(I18N_BASE + "title"));
        this.parent = parent;
        this.uuid = uuid;
    }

    @Override
    protected void init() {
        if (this.errorModal == null) {
            this.errorModal = new ErrorModal(this.font);
        }
        this.errorModal.setScreenSize(this.width, this.height);

        this.clearWidgets();

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

        usernameField = new EditBox(
                this.font,
                x,
                y,
                w,
                20,
                t("field.username")
        );
        // Keep username length reasonable (matches typical MC constraints)
        usernameField.setMaxLength(16);
        usernameField.setValue(entry.username != null ? entry.username : "");
        this.addRenderableWidget(usernameField);

        nicknameField = new EditBox(
                this.font,
                x,
                y + 30,
                w,
                20,
                t("field.nickname")
        );
        // Allow long input (hex gradients can get very long)
        nicknameField.setMaxLength(4096);
        nicknameField.setValue(entry.nickname != null ? entry.nickname : "");
        this.addRenderableWidget(nicknameField);

        int btnY = y + 60;
        int btnW = (w - 10) / 2;

        this.addRenderableWidget(
                Button.builder(
                                toggleLabel("toggle.show_prefix", entry.showPrefix),
                                b -> {
                                    entry.showPrefix = !entry.showPrefix;
                                    b.setMessage(toggleLabel("toggle.show_prefix", entry.showPrefix));
                                }
                        )
                        .bounds(x, btnY, btnW, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                toggleLabel("toggle.show_suffix", entry.showSuffix),
                                b -> {
                                    entry.showSuffix = !entry.showSuffix;
                                    b.setMessage(toggleLabel("toggle.show_suffix", entry.showSuffix));
                                }
                        )
                        .bounds(x + btnW + 10, btnY, btnW, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                toggleLabel("toggle.rainbow_wave", entry.rainbow),
                                b -> {
                                    entry.rainbow = !entry.rainbow;
                                    b.setMessage(toggleLabel("toggle.rainbow_wave", entry.rainbow));
                                    if (speedSlider != null) {
                                        speedSlider.active = entry.rainbow;
                                    }
                                }
                        )
                        .bounds(x, btnY + 30, btnW, 20)
                        .build()
        );

        speedSlider = new AbstractSliderButton(
                x + btnW + 10,
                btnY + 30,
                btnW,
                20,
                Component.empty(),
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
        this.addRenderableWidget(speedSlider);

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

        this.addRenderableWidget(
                Button.builder(t("button.save"), b -> {
                            String name = usernameField.getValue().trim();
                            String nick = nicknameField.getValue();

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
                                Minecraft.getInstance().setScreen(parent);
                                return;
                            }

                            // Username changed - resolve new UUID like saveAdd() does
                            UUID onlineUuid = findOnlineUuid(name);
                            if (onlineUuid != null) {
                                entry.username = findOnlineExactName(name);
                                NickConfig.remove(uuid);
                                NickConfig.set(onlineUuid, entry);
                                Minecraft.getInstance().setScreen(parent);
                                return;
                            }

                            // Not online - try Mojang API (async)
                            MojangLookup.resolveByName(name).thenAccept(profile -> {
                                Minecraft.getInstance().execute(() -> {
                                    if (Minecraft.getInstance().screen != this) {
                                        return;
                                    }
                                    if (profile == null) {
                                        showError("gui.customnickname.error.uuid_for_name_not_found", name);
                                        return;
                                    }
                                    entry.username = profile.name();
                                    NickConfig.remove(uuid);
                                    NickConfig.set(profile.uuid(), entry);
                                    Minecraft.getInstance().setScreen(parent);
                                });
                            });
                        })
                        .bounds(startX2, yBottom2, btnW2, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(t("button.back"), b -> {
                            Minecraft.getInstance().setScreen(parent);
                        })
                        .bounds(startX2 + btnW2 + gap2, yBottom2, btnW2, 20)
                        .build()
        );
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        boolean modalOpen = errorModal != null && errorModal.isOpen();
        int uiMouseX = modalOpen ? -10000 : mouseX;
        int uiMouseY = modalOpen ? -10000 : mouseY;

        super.extractRenderState(context, uiMouseX, uiMouseY, delta);

        context.text(
                this.font,
                t("title"),
                10,
                10,
                0xFFFFFF,
                true
        );

        // Preview with rainbow support
        if (previewY != -1) {
            NickEntry tmp = new NickEntry();
            tmp.username = usernameField.getValue();
            tmp.nickname = nicknameField.getValue();
            tmp.showPrefix = entry.showPrefix;
            tmp.showSuffix = entry.showSuffix;
            tmp.rainbow = entry.rainbow;
            tmp.rainbowSpeed = entry.rainbowSpeed;

            String username = tmp.username == null ? "" : tmp.username;
            String rawNick = tmp.nickname;

            // If no nickname is set or only formatting codes, show nothing in the preview
            String visibleNick = ColorParser.strip(rawNick);
            if (rawNick != null && !rawNick.isEmpty() && !visibleNick.isBlank()) {
                Component nickText = ColorParser.buildNick(tmp, Component.literal(username));

                int y = previewY;
                int boxX = 20;
                int boxY = y - 5;
                int boxRight = this.width - 20;
                int boxBottom = y + this.font.lineHeight + 5;

                // Border (drawn as outer rect)
                context.fill(boxX, boxY, boxRight, boxBottom, 0xFF555555);
                // Background (inset by 1px on each side)
                context.fill(boxX + 1, boxY + 1, boxRight - 1, boxBottom - 1, 0xFF111111);

                context.text(
                        this.font,
                        t("preview_label"),
                        boxX + 5,
                        y,
                        0xFFAAAAAA,
                        true
                );

                int labelW = this.font.width(t("preview_label"));
                int previewTextX = boxX + 5 + labelW;

                int scissorRight = boxRight - 5;
                // Start scissor 2px earlier so italic text isn't clipped on the left
                int scissorLeft = previewTextX - 2;
                if (scissorRight > scissorLeft) {
                    context.enableScissor(scissorLeft, boxY, scissorRight, boxBottom);
                }

                context.text(
                        this.font,
                        nickText,
                        previewTextX,
                        y,
                        0xFFFFFFFF,
                        true
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
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (errorModal != null && errorModal.isOpen()) {
            return errorModal.mouseClicked(click, doubleClick);
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (errorModal != null && errorModal.isOpen()) {
            return errorModal.mouseReleased(click);
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
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
    public boolean keyPressed(KeyEvent keyInput) {
        if (errorModal != null && errorModal.isOpen()) {
            return errorModal.keyPressed(keyInput);
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharacterEvent charInput) {
        if (errorModal != null && errorModal.isOpen()) {
            return errorModal.charTyped(charInput);
        }
        return super.charTyped(charInput);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private static Component t(String key, Object... args) {
        return Component.translatable(I18N_BASE + key, args);
    }

    private static Component toggleLabel(String labelKey, boolean value) {
        return Component.translatable(
                I18N_BASE + "toggle",
                t(labelKey),
                value ? t("state.on") : t("state.off")
        );
    }

    private void showError(String translationKey, Object... args) {
        if (errorModal == null) {
            errorModal = new ErrorModal(this.font);
            errorModal.setScreenSize(this.width, this.height);
        }
        errorModal.show(Component.translatable(translationKey, args));
    }

    private UUID findOnlineUuid(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;

        for (PlayerInfo e : mc.getConnection().getOnlinePlayers()) {
            String n = e.getProfile().name();
            if (n != null && n.equalsIgnoreCase(name)) return e.getProfile().id();
        }
        return null;
    }

    private String findOnlineExactName(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return name;

        for (PlayerInfo e : mc.getConnection().getOnlinePlayers()) {
            String n = e.getProfile().name();
            if (n != null && n.equalsIgnoreCase(name)) return n;
        }
        return name;
    }
}

