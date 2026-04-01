package dev.dasuro.customnickname.gui;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.config.StorageConfig;
import dev.dasuro.customnickname.gui.widget.FramedButtonWidget;
import dev.dasuro.customnickname.util.ColorParser;
import dev.dasuro.customnickname.util.MojangLookup;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

public class CustomNickConfigScreen extends Screen {
    private enum Tab {
        ADD,
        ENTRIES,
        OPTIONS
    }

    private final Screen parent;
    private Tab tab = Tab.ADD;

    private TextFieldWidget addUsername;
    private TextFieldWidget addNickname;

    private boolean addShowPrefix = true;
    private boolean addShowSuffix = true;
    private boolean addRainbowWave = false;
    private float addRainbowSpeed = 1.0f;


    private SliderWidget rainbowSpeedSlider;
    private int addPreviewY = -1;


    private EntriesList entriesList;
    private TextFieldWidget searchField;

    private int optionsLabelX;
    private int optionsLabelY;
    private int indicatorLabelX;
    private int indicatorLabelY;
    private ErrorModal errorModal;
    private ButtonWidget addTabBtn;
    private ButtonWidget entriesTabBtn;
    private ButtonWidget optionsTabBtn;

    private static final String I18N_BASE = "gui.customnickname.config.";

    public CustomNickConfigScreen(Screen parent) {
        super(Text.translatable("gui.customnickname.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (this.errorModal == null) {
            this.errorModal = new ErrorModal(this.textRenderer);
        }
        this.errorModal.setScreenSize(this.width, this.height);

        this.clearChildren();

        int tabHeight = 24;
        int tabGap = 4;
        int tabWidth = (this.width - 20 - tabGap * 2) / 3;
        int tabX = 10;
        int tabY = 10;

        this.addTabBtn = ButtonWidget.builder(t("tab.add"), b -> {
                    tab = Tab.ADD;
                    init();
                })
                .dimensions(tabX, tabY, tabWidth, tabHeight)
                .build();
        this.addTabBtn.active = true;
        this.addTabBtn.setAlpha(0.0f);
        this.addDrawableChild(this.addTabBtn);

        this.entriesTabBtn = ButtonWidget.builder(t("tab.entries"), b -> {
                    tab = Tab.ENTRIES;
                    init();
                })
                .dimensions(tabX + tabWidth + tabGap, tabY, tabWidth, tabHeight)
                .build();
        this.entriesTabBtn.active = true;
        this.entriesTabBtn.setAlpha(0.0f);
        this.addDrawableChild(this.entriesTabBtn);

        this.optionsTabBtn = ButtonWidget.builder(t("tab.options"), b -> {
                    tab = Tab.OPTIONS;
                    init();
                })
                .dimensions(tabX + (tabWidth + tabGap) * 2, tabY, tabWidth, tabHeight)
                .build();
        this.optionsTabBtn.active = true;
        this.optionsTabBtn.setAlpha(0.0f);
        this.addDrawableChild(this.optionsTabBtn);

        int contentTop = tabY + tabHeight + 8;

        if (tab == Tab.ADD) {
            initAdd(contentTop);
        } else if (tab == Tab.ENTRIES) {
            initEntries(contentTop);
        } else {
            initOptions(contentTop);
        }

        // Done button for Entries and Options tabs
        if (tab == Tab.ENTRIES || tab == Tab.OPTIONS) {
            int btnWidth = 100;
            int btnX = (this.width - btnWidth) / 2;

            this.addDrawableChild(
                    ButtonWidget.builder(t("button.done"), b -> {
                                NickConfig.save();
                                this.close();
                            })
                            .dimensions(btnX, this.height - 30, btnWidth, 20)
                            .build()
            );
        }
    }

    private void initAdd(int top) {
        int x = 20;
        int w = this.width - 40;

        addUsername = new TextFieldWidget(
                this.textRenderer,
                x,
                top,
                w,
                20,
                t("field.username")
        );
        addUsername.setPlaceholder(t("placeholder.username"));
        // Keep username length reasonable (matches typical MC constraints)
        addUsername.setMaxLength(16);
        this.addDrawableChild(addUsername);

        addNickname = new TextFieldWidget(
                this.textRenderer,
                x,
                top + 30,
                w,
                20,
                t("field.nickname")
        );
        addNickname.setPlaceholder(t("placeholder.nickname"));
        addNickname.setMaxLength(4096);
        this.addDrawableChild(addNickname);

        int btnY = top + 60;
        int btnW = (w - 10) / 2;

        this.addDrawableChild(
                ButtonWidget.builder(
                                toggleLabel("toggle.show_prefix", addShowPrefix),
                                b -> {
                                    addShowPrefix = !addShowPrefix;
                                    b.setMessage(toggleLabel("toggle.show_prefix", addShowPrefix));
                                }
                        )
                        .dimensions(x, btnY, btnW, 20)
                        .build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(
                                toggleLabel("toggle.show_suffix", addShowSuffix),
                                b -> {
                                    addShowSuffix = !addShowSuffix;
                                    b.setMessage(toggleLabel("toggle.show_suffix", addShowSuffix));
                                }
                        )
                        .dimensions(x + btnW + 10, btnY, btnW, 20)
                        .build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(
                                toggleLabel("toggle.rainbow_wave", addRainbowWave),
                                b -> {
                                    addRainbowWave = !addRainbowWave;
                                    b.setMessage(toggleLabel("toggle.rainbow_wave", addRainbowWave));
                                    if (rainbowSpeedSlider != null) {
                                        rainbowSpeedSlider.active = addRainbowWave;
                                    }
                                }
                        )
                        .dimensions(x, btnY + 30, btnW, 20)
                        .build()
        );

        this.rainbowSpeedSlider = new SliderWidget(
                x + btnW + 10,
                btnY + 30,
                btnW,
                20,
                Text.empty(),
                (addRainbowSpeed - 0.1f) / 4.9f
        ) {
            {
                this.updateMessage();
            }

            @Override
            protected void updateMessage() {
                addRainbowSpeed = (float) (0.1f + this.value * 4.9f);
                String speed = String.format(Locale.ROOT, "%.1f", addRainbowSpeed);
                this.setMessage(t("rainbow_speed", speed));
            }

            @Override
            protected void applyValue() {}
        };

        this.rainbowSpeedSlider.active = addRainbowWave;
        this.addDrawableChild(this.rainbowSpeedSlider);

        int btnW2 = 100;
        int gap2 = 10;
        int totalW2 = btnW2 * 2 + gap2;
        int startX2 = (w - totalW2) / 2 + x;
        int yBottom2 = this.height - 30;

        // Preview directly under the rainbow row
        // Rainbow bottom = btnY+30+20. Box top = previewY-5, so previewY-5 should be 10px below rainbow.
        // => previewY = btnY + 30 + 20 + 10 + 5 = btnY + 65
        int rawPreviewY = btnY + 65;
        this.addPreviewY = Math.min(rawPreviewY, yBottom2 - 30);

        this.addDrawableChild(
                ButtonWidget.builder(t("button.save"), b -> saveAdd())
                        .dimensions(startX2, yBottom2, btnW2, 20)
                        .build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(t("button.done"), b -> {
                            NickConfig.save();
                            this.close();
                        })
                        .dimensions(startX2 + btnW2 + gap2, yBottom2, btnW2, 20)
                        .build()
        );
    }

    private void saveAdd() {
        String name = addUsername.getText().trim();
        String nick = addNickname.getText();

        if (name.isBlank()) {
            showError("gui.customnickname.error.username_required");
            return;
        }

        if (nick == null || nick.isBlank()) {
            showError("gui.customnickname.error.nickname_required");
            return;
        }

        String visibleNick = ColorParser.strip(nick);
        if (visibleNick.isBlank()) {
            showError("gui.customnickname.error.nickname_required_formatting_only");
            return;
        }

        NickEntry entry = new NickEntry();
        entry.username = name;
        entry.nickname = nick;
        entry.showPrefix = addShowPrefix;
        entry.showSuffix = addShowSuffix;
        entry.rainbow = addRainbowWave;
        entry.rainbowSpeed = addRainbowSpeed;

        UUID online = findOnlineUuid(name);
        if (online != null) {
            entry.username = findOnlineExactName(name);
            NickConfig.set(online, entry);

            // Clear inputs after save (new entry workflow)
            resetAddForm();
            return;
        }

        MojangLookup.resolveByName(name).thenAccept(profile -> {
            MinecraftClient.getInstance().execute(() -> {
                if (MinecraftClient.getInstance().currentScreen != this) {
                    return;
                }
                if (profile == null) {
                    showError("gui.customnickname.error.uuid_not_found");
                    return;
                }
                entry.username = profile.name();
                NickConfig.set(profile.uuid(), entry);

                // Clear inputs after save (new entry workflow)
                resetAddForm();
            });
        });
    }

    private void resetAddForm() {
        // Reset form state
        addShowPrefix = true;
        addShowSuffix = true;
        addRainbowWave = false;
        addRainbowSpeed = 1.0f;

        if (addUsername != null) addUsername.setText("");
        if (addNickname != null) addNickname.setText("");

        // Rebuild widgets so buttons/sliders reflect the default state
        init();
    }

    private void initEntries(int top) {
        int searchLabelWidth = this.textRenderer.getWidth(t("search_label"));
        int searchFieldX = 20 + searchLabelWidth;
        int searchFieldW = this.width - 40 - searchLabelWidth;

        searchField = new TextFieldWidget(
                this.textRenderer,
                searchFieldX,
                top,
                searchFieldW,
                20,
                t("field.search")
        );
        searchField.setMaxLength(256);
        searchField.setChangedListener(query -> refreshEntries());
        // Preserve search text across tab switches / resizes
        this.addDrawableChild(searchField);

        int listTop = top + 26;
        int listHeight = this.height - listTop - 40;

        entriesList = new EntriesList(
                MinecraftClient.getInstance(),
                this.width,
                listHeight,
                listTop,
                28
        );

        refreshEntries();
        this.addDrawableChild(entriesList);
    }

    private void refreshEntries() {
        if (entriesList == null) return;

        String query = (searchField != null ? searchField.getText() : "").trim().toLowerCase();

        List<EntryRow> rows = new ArrayList<>();

        List<Map.Entry<String, NickEntry>> list =
                new ArrayList<>(NickConfig.getAll().entrySet());

        list.sort(Comparator.comparing(e -> {
            NickEntry ne = e.getValue();
            return ne.username != null ? ne.username.toLowerCase() : e.getKey();
        }));

        for (Map.Entry<String, NickEntry> e : list) {
            UUID uuid;
            try {
                uuid = UUID.fromString(e.getKey());
            } catch (IllegalArgumentException ex) {
                continue;
            }

            if (!query.isEmpty()) {
                NickEntry ne = e.getValue();
                String username = (ne.username != null ? ne.username : "").toLowerCase();
                String strippedNick = (ne.nickname != null ? ColorParser.strip(ne.nickname) : "").toLowerCase();
                if (!username.contains(query) && !strippedNick.contains(query)) {
                    continue;
                }
            }

            rows.add(new EntryRow(uuid, e.getValue()));
        }

        entriesList.replaceEntries(rows);
        entriesList.setScrollY(0);
    }

    private void initOptions(int top) {
        boolean isGlobal = StorageConfig.getMode() == StorageConfig.StorageMode.GLOBAL;

        int labelWidth = this.textRenderer.getWidth(t("storage_mode_label"));
        int btnW = 80;
        int btnH = 20;
        int gap = 4;

        // Center the whole row (label + 2 buttons) horizontally
        int totalWidth = labelWidth + btnW + gap + btnW;
        int startX = (this.width - totalWidth) / 2;
        int btnX = startX + labelWidth;

        // "Global" button with tooltip
        ButtonWidget globalBtn = ButtonWidget.builder(t("button.global"), b -> {
                    NickConfig.switchMode(StorageConfig.StorageMode.GLOBAL);
                    init();
                })
                .dimensions(btnX, top, btnW, btnH)
                .tooltip(Tooltip.of(t("tooltip.global")))
                .build();
        globalBtn.active = !isGlobal;
        this.addDrawableChild(globalBtn);

        // "Local" button with tooltip
        ButtonWidget localBtn = ButtonWidget.builder(t("button.local"), b -> {
                    NickConfig.switchMode(StorageConfig.StorageMode.LOCAL);
                    init();
                })
                .dimensions(btnX + btnW + gap, top, btnW, btnH)
                .tooltip(Tooltip.of(t("tooltip.local")))
                .build();
        localBtn.active = isGlobal;
        this.addDrawableChild(localBtn);

        // Store positions for render() label drawing
        this.optionsLabelX = startX;
        this.optionsLabelY = top + (btnH - this.textRenderer.fontHeight) / 2;

        // --- Indicator toggle ---
        int indicatorY = top + btnH + 10;
        int indicatorLabelWidth = this.textRenderer.getWidth(t("indicator_label"));
        int indicatorBtnW = 60;
        int indicatorTotalW = indicatorLabelWidth + indicatorBtnW;
        int indicatorStartX = (this.width - indicatorTotalW) / 2;

        ButtonWidget indicatorBtn = ButtonWidget.builder(
                        StorageConfig.isShowIndicator() ? t("state.on") : t("state.off"),
                        b -> {
                            StorageConfig.setShowIndicator(!StorageConfig.isShowIndicator());
                            b.setMessage(StorageConfig.isShowIndicator() ? t("state.on") : t("state.off"));
                        })
                .dimensions(indicatorStartX + indicatorLabelWidth, indicatorY, indicatorBtnW, btnH)
                .tooltip(Tooltip.of(
                        Text.empty()
                                .append(t("tooltip.indicator_prefix"))
                                .append(Text.literal("\u270E").styled(s -> s.withColor(0xFFFF00)))
                                .append(t("tooltip.indicator_suffix"))))
                .build();
        this.addDrawableChild(indicatorBtn);

        this.indicatorLabelX = indicatorStartX;
        this.indicatorLabelY = indicatorY + (btnH - this.textRenderer.fontHeight) / 2;
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

        // IMPORTANT: super.render already calls renderBackground (blur).
        super.render(context, uiMouseX, uiMouseY, delta);

        context.drawTextWithShadow(
                this.textRenderer,
                Text.translatable("gui.customnickname.title"),
                10,
                2,
                0xFFFFFF
        );

        if (this.addTabBtn != null) {
            FramedButtonWidget.renderTab(context, this.addTabBtn, this.addTabBtn.getMessage(), uiMouseX, uiMouseY, tab == Tab.ADD);
        }
        if (this.entriesTabBtn != null) {
            FramedButtonWidget.renderTab(context, this.entriesTabBtn, this.entriesTabBtn.getMessage(), uiMouseX, uiMouseY, tab == Tab.ENTRIES);
        }
        if (this.optionsTabBtn != null) {
            FramedButtonWidget.renderTab(context, this.optionsTabBtn, this.optionsTabBtn.getMessage(), uiMouseX, uiMouseY, tab == Tab.OPTIONS);
        }

        // Draw the top edge of the content area but leave a gap under the active tab.
        ButtonWidget activeTabButton = switch (tab) {
            case ADD -> this.addTabBtn;
            case ENTRIES -> this.entriesTabBtn;
            case OPTIONS -> this.optionsTabBtn;
        };
        if (activeTabButton != null) {
            int lineY = activeTabButton.getY() + activeTabButton.getHeight() - 1;
            int left = 0;
            int right = this.width;
            int gapLeft = activeTabButton.getX() + 1;
            int gapRight = activeTabButton.getX() + activeTabButton.getWidth() - 1;
            int lineColor = 0xFFA0A0A0;

            if (left < gapLeft) {
                context.fill(left, lineY, gapLeft, lineY + 1, lineColor);
            }
            if (gapRight < right) {
                context.fill(gapRight, lineY, right, lineY + 1, lineColor);
            }
        }

        if (tab == Tab.ENTRIES && searchField != null) {
            context.drawTextWithShadow(
                    this.textRenderer,
                    t("search_label"),
                    20,
                    searchField.getY() + (searchField.getHeight() - this.textRenderer.fontHeight) / 2 + 1,
                    0xFFAAAAAA
            );
        }

        if (tab == Tab.OPTIONS) {
            context.drawTextWithShadow(
                    this.textRenderer,
                    t("storage_mode_label"),
                    optionsLabelX,
                    optionsLabelY,
                    0xFFFFFFFF
            );
            context.drawTextWithShadow(
                    this.textRenderer,
                    t("indicator_label"),
                    indicatorLabelX,
                    indicatorLabelY,
                    0xFFFFFFFF
            );
        }

        if (tab == Tab.ADD && addPreviewY != -1) {
            NickEntry tmp = new NickEntry();
            tmp.username = addUsername != null ? addUsername.getText() : "";
            tmp.nickname = addNickname != null ? addNickname.getText() : "";
            tmp.showPrefix = addShowPrefix;
            tmp.showSuffix = addShowSuffix;
            tmp.rainbow = addRainbowWave;
            tmp.rainbowSpeed = addRainbowSpeed;

            String username = tmp.username == null ? "" : tmp.username;
            String rawNick = tmp.nickname;

            // If no nickname is set or only formatting codes, show nothing in the preview
            String visiblePreviewNick = ColorParser.strip(rawNick);
            if (rawNick != null && !rawNick.isEmpty() && !visiblePreviewNick.isBlank()) {
                Text nickText = ColorParser.buildNick(tmp, Text.literal(username));


                int y = addPreviewY;
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

    private void showError(String translationKey) {
        if (errorModal == null) {
            errorModal = new ErrorModal(this.textRenderer);
            errorModal.setScreenSize(this.width, this.height);
        }
        errorModal.show(Text.translatable(translationKey));
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

    private static class EntriesList extends ElementListWidget<EntryRow> {
        public EntriesList(
                MinecraftClient client,
                int width,
                int height,
                int y,
                int itemHeight
        ) {
            super(client, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return this.width - 40;
        }

        @Override
        protected int getScrollbarX() {
            return this.width - 12;
        }

        public int rowLeft() {
            return this.getRowLeft();
        }
    }

    private class EntryRow extends ElementListWidget.Entry<EntryRow> {
        private final UUID uuid;
        private final NickEntry entry;

        private final ButtonWidget editButton;
        private final ButtonWidget deleteButton;

        public EntryRow(UUID uuid, NickEntry entry) {
            this.uuid = uuid;
            this.entry = entry;

            this.editButton = ButtonWidget.builder(t("button.edit"), b -> {
                        MinecraftClient.getInstance().setScreen(
                                new EditEntryScreen(CustomNickConfigScreen.this, uuid)
                        );
                    })
                    .dimensions(0, 0, 50, 20)
                    .build();

            this.deleteButton = ButtonWidget.builder(t("button.delete"), b -> {
                        NickConfig.remove(uuid);
                        refreshEntries();
                    })
                    .dimensions(0, 0, 60, 20)
                    .build();
        }

        @Override
        public List<? extends Element> children() {
            return List.of(editButton, deleteButton);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of(editButton, deleteButton);
        }

        @Override
        public void render(
                DrawContext context,
                int mouseX,
                int mouseY,
                boolean hovered,
                float delta
        ) {
            int x = entriesList != null ? entriesList.rowLeft() : 20;
            int y = this.getY();
            int entryWidth = entriesList != null ? entriesList.getRowWidth() : (CustomNickConfigScreen.this.width - 40);

            String username = resolveDisplayName(uuid, entry);

            // Vertically center text (9px) and buttons (20px) within usable row height (26px)
            int textY = y + (26 - textRenderer.fontHeight) / 2;
            int buttonY = y + (26 - 20) / 2;

            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(username),
                    x,
                    textY,
                    hovered ? 0xFFFFA0 : 0xFFFFFF
            );

            boolean hasNick = entry != null && entry.nickname != null && !entry.nickname.isEmpty();

            // Buttons should hug the right edge a bit more.
            int buttonAreaW = 50 + 60 + 4; // edit + delete + gap
            int bx = x + entryWidth - buttonAreaW;

            int textAreaRight = bx - 8;

            // Symmetric padding: use the same left padding as we use next to the buttons on the right.
            int leftPadding = 8;
            int previewX = x + leftPadding;

            if (hasNick) {
                Text nickBase = ColorParser.buildNick(entry, Text.literal(username));
                Text preview = Text.empty()
                        .append(nickBase)
                        .append(Text.literal(" "))
                        .append(Text.literal("(" + username + ")").styled(s -> s.withColor(0xFFAAAAAA)));

                if (textAreaRight > previewX) {
                    context.enableScissor(previewX, y, textAreaRight, y + 26);
                }

                context.drawTextWithShadow(
                        textRenderer,
                        preview,
                        previewX,
                        textY,
                        0xFFFFFFFF
                );

                if (textAreaRight > previewX) {
                    context.disableScissor();
                }
            }

            editButton.setPosition(bx, buttonY);
            deleteButton.setPosition(bx + 50 + 4, buttonY);

            editButton.render(context, mouseX, mouseY, delta);
            deleteButton.render(context, mouseX, mouseY, delta);

            // Separator line at the bottom of this row (skip for the last entry)
            int index = entriesList != null ? entriesList.children().indexOf(this) : -1;
            int total = entriesList != null ? entriesList.children().size() : 0;
            if (index < total - 1) {
                int lineY = y + 26; // itemHeight (28) - 2px padding
                context.fill(x, lineY, x + entryWidth, lineY + 1, 0xFF444444);
            }
        }
    }

    private String resolveDisplayName(UUID uuid, NickEntry entry) {
        if (entry != null && entry.username != null && !entry.username.isBlank()) {
            return entry.username;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null) {
            PlayerListEntry ple = mc.getNetworkHandler().getPlayerListEntry(uuid);
            if (ple != null && ple.getProfile().name() != null) {
                return ple.getProfile().name();
            }
        }
        return uuid.toString();
    }
}

