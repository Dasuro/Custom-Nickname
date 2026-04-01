package dev.dasuro.customnickname.gui;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.config.StorageConfig;
import dev.dasuro.customnickname.gui.widget.FramedButtonWidget;
import dev.dasuro.customnickname.util.ColorParser;
import dev.dasuro.customnickname.util.MojangLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

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

    private EditBox addUsername;
    private EditBox addNickname;

    private boolean addShowPrefix = true;
    private boolean addShowSuffix = true;
    private boolean addRainbowWave = false;
    private float addRainbowSpeed = 1.0f;


    private AbstractSliderButton rainbowSpeedSlider;
    private int addPreviewY = -1;


    private EntriesList entriesList;
    private EditBox searchField;

    private int optionsLabelX;
    private int optionsLabelY;
    private int indicatorLabelX;
    private int indicatorLabelY;
    private ErrorModal errorModal;
    private Button addTabBtn;
    private Button entriesTabBtn;
    private Button optionsTabBtn;

    private static final String I18N_BASE = "gui.customnickname.config.";

    public CustomNickConfigScreen(Screen parent) {
        super(Component.translatable("gui.customnickname.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (this.errorModal == null) {
            this.errorModal = new ErrorModal(this.font);
        }
        this.errorModal.setScreenSize(this.width, this.height);

        this.clearWidgets();

        int tabHeight = 24;
        int tabGap = 4;
        int tabWidth = (this.width - 20 - tabGap * 2) / 3;
        int tabX = 10;
        int tabY = 10;

        this.addTabBtn = Button.builder(t("tab.add"), b -> {
                    tab = Tab.ADD;
                    init();
                })
                .bounds(tabX, tabY, tabWidth, tabHeight)
                .build();
        this.addTabBtn.active = true;
        this.addTabBtn.visible = false;
        this.addWidget(this.addTabBtn);

        this.entriesTabBtn = Button.builder(t("tab.entries"), b -> {
                    tab = Tab.ENTRIES;
                    init();
                })
                .bounds(tabX + tabWidth + tabGap, tabY, tabWidth, tabHeight)
                .build();
        this.entriesTabBtn.active = true;
        this.entriesTabBtn.visible = false;
        this.addWidget(this.entriesTabBtn);

        this.optionsTabBtn = Button.builder(t("tab.options"), b -> {
                    tab = Tab.OPTIONS;
                    init();
                })
                .bounds(tabX + (tabWidth + tabGap) * 2, tabY, tabWidth, tabHeight)
                .build();
        this.optionsTabBtn.active = true;
        this.optionsTabBtn.visible = false;
        this.addWidget(this.optionsTabBtn);

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

            this.addRenderableWidget(
                    Button.builder(t("button.done"), b -> {
                                NickConfig.save();
                                this.onClose();
                            })
                            .bounds(btnX, this.height - 30, btnWidth, 20)
                            .build()
            );
        }
    }

    private void initAdd(int top) {
        int x = 20;
        int w = this.width - 40;

        addUsername = new EditBox(
                this.font,
                x,
                top,
                w,
                20,
                t("field.username")
        );
        addUsername.setHint(t("placeholder.username"));
        // Keep username length reasonable (matches typical MC constraints)
        addUsername.setMaxLength(16);
        this.addRenderableWidget(addUsername);

        addNickname = new EditBox(
                this.font,
                x,
                top + 30,
                w,
                20,
                t("field.nickname")
        );
        addNickname.setHint(t("placeholder.nickname"));
        addNickname.setMaxLength(4096);
        this.addRenderableWidget(addNickname);

        int btnY = top + 60;
        int btnW = (w - 10) / 2;

        this.addRenderableWidget(
                Button.builder(
                                toggleLabel("toggle.show_prefix", addShowPrefix),
                                b -> {
                                    addShowPrefix = !addShowPrefix;
                                    b.setMessage(toggleLabel("toggle.show_prefix", addShowPrefix));
                                }
                        )
                        .bounds(x, btnY, btnW, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                toggleLabel("toggle.show_suffix", addShowSuffix),
                                b -> {
                                    addShowSuffix = !addShowSuffix;
                                    b.setMessage(toggleLabel("toggle.show_suffix", addShowSuffix));
                                }
                        )
                        .bounds(x + btnW + 10, btnY, btnW, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                toggleLabel("toggle.rainbow_wave", addRainbowWave),
                                b -> {
                                    addRainbowWave = !addRainbowWave;
                                    b.setMessage(toggleLabel("toggle.rainbow_wave", addRainbowWave));
                                    if (rainbowSpeedSlider != null) {
                                        rainbowSpeedSlider.active = addRainbowWave;
                                    }
                                }
                        )
                        .bounds(x, btnY + 30, btnW, 20)
                        .build()
        );

        this.rainbowSpeedSlider = new AbstractSliderButton(
                x + btnW + 10,
                btnY + 30,
                btnW,
                20,
                Component.empty(),
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
        this.addRenderableWidget(this.rainbowSpeedSlider);

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

        this.addRenderableWidget(
                Button.builder(t("button.save"), b -> saveAdd())
                        .bounds(startX2, yBottom2, btnW2, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(t("button.done"), b -> {
                            NickConfig.save();
                            this.onClose();
                        })
                        .bounds(startX2 + btnW2 + gap2, yBottom2, btnW2, 20)
                        .build()
        );
    }

    private void saveAdd() {
        String name = addUsername.getValue().trim();
        String nick = addNickname.getValue();

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
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().screen != this) {
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

        if (addUsername != null) addUsername.setValue("");
        if (addNickname != null) addNickname.setValue("");

        // Rebuild widgets so buttons/sliders reflect the default state
        init();
    }

    private void initEntries(int top) {
        int searchLabelWidth = this.font.width(t("search_label"));
        int searchFieldX = 20 + searchLabelWidth;
        int searchFieldW = this.width - 40 - searchLabelWidth;

        searchField = new EditBox(
                this.font,
                searchFieldX,
                top,
                searchFieldW,
                20,
                t("field.search")
        );
        searchField.setMaxLength(256);
        searchField.setResponder(query -> refreshEntries());
        // Preserve search text across tab switches / resizes
        this.addRenderableWidget(searchField);

        int listTop = top + 26;
        int listHeight = this.height - listTop - 40;

        entriesList = new EntriesList(
                Minecraft.getInstance(),
                this.width,
                listHeight,
                listTop,
                28
        );

        refreshEntries();
        this.addRenderableWidget(entriesList);
    }

    private void refreshEntries() {
        if (entriesList == null) return;

        String query = (searchField != null ? searchField.getValue() : "").trim().toLowerCase();

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
        entriesList.setScrollAmount(0);
    }

    private void initOptions(int top) {
        boolean isGlobal = StorageConfig.getMode() == StorageConfig.StorageMode.GLOBAL;

        int labelWidth = this.font.width(t("storage_mode_label"));
        int btnW = 80;
        int btnH = 20;
        int gap = 4;

        // Center the whole row (label + 2 buttons) horizontally
        int totalWidth = labelWidth + btnW + gap + btnW;
        int startX = (this.width - totalWidth) / 2;
        int btnX = startX + labelWidth;

        // "Global" button with tooltip
        Button globalBtn = Button.builder(t("button.global"), b -> {
                    NickConfig.switchMode(StorageConfig.StorageMode.GLOBAL);
                    init();
                })
                .bounds(btnX, top, btnW, btnH)
                .tooltip(Tooltip.create(t("tooltip.global")))
                .build();
        globalBtn.active = !isGlobal;
        this.addRenderableWidget(globalBtn);

        // "Local" button with tooltip
        Button localBtn = Button.builder(t("button.local"), b -> {
                    NickConfig.switchMode(StorageConfig.StorageMode.LOCAL);
                    init();
                })
                .bounds(btnX + btnW + gap, top, btnW, btnH)
                .tooltip(Tooltip.create(t("tooltip.local")))
                .build();
        localBtn.active = isGlobal;
        this.addRenderableWidget(localBtn);

        // Store positions for render() label drawing
        this.optionsLabelX = startX;
        this.optionsLabelY = top + (btnH - this.font.lineHeight) / 2;

        // --- Indicator toggle ---
        int indicatorY = top + btnH + 10;
        int indicatorLabelWidth = this.font.width(t("indicator_label"));
        int indicatorBtnW = 60;
        int indicatorTotalW = indicatorLabelWidth + indicatorBtnW;
        int indicatorStartX = (this.width - indicatorTotalW) / 2;

        Button indicatorBtn = Button.builder(
                        StorageConfig.isShowIndicator() ? t("state.on") : t("state.off"),
                        b -> {
                            StorageConfig.setShowIndicator(!StorageConfig.isShowIndicator());
                            b.setMessage(StorageConfig.isShowIndicator() ? t("state.on") : t("state.off"));
                        })
                .bounds(indicatorStartX + indicatorLabelWidth, indicatorY, indicatorBtnW, btnH)
                .tooltip(Tooltip.create(
                        Component.empty()
                                .append(t("tooltip.indicator_prefix"))
                                .append(Component.literal("\u270E").withColor(0xFFFF00))
                                .append(t("tooltip.indicator_suffix"))))
                .build();
        this.addRenderableWidget(indicatorBtn);

        this.indicatorLabelX = indicatorStartX;
        this.indicatorLabelY = indicatorY + (btnH - this.font.lineHeight) / 2;
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
                Component.translatable("gui.customnickname.title"),
                10,
                2,
                0xFFFFFF,
                true
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
        Button activeTabButton = switch (tab) {
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
            context.text(
                    this.font,
                    t("search_label"),
                    20,
                    searchField.getY() + (searchField.getHeight() - this.font.lineHeight) / 2 + 1,
                    0xFFAAAAAA,
                    true
            );
        }

        if (tab == Tab.OPTIONS) {
            context.text(
                    this.font,
                    t("storage_mode_label"),
                    optionsLabelX,
                    optionsLabelY,
                    0xFFFFFFFF,
                    true
            );
            context.text(
                    this.font,
                    t("indicator_label"),
                    indicatorLabelX,
                    indicatorLabelY,
                    0xFFFFFFFF,
                    true
            );
        }

        if (tab == Tab.ADD && addPreviewY != -1) {
            NickEntry tmp = new NickEntry();
            tmp.username = addUsername != null ? addUsername.getValue() : "";
            tmp.nickname = addNickname != null ? addNickname.getValue() : "";
            tmp.showPrefix = addShowPrefix;
            tmp.showSuffix = addShowSuffix;
            tmp.rainbow = addRainbowWave;
            tmp.rainbowSpeed = addRainbowSpeed;

            String username = tmp.username == null ? "" : tmp.username;
            String rawNick = tmp.nickname;

            // If no nickname is set or only formatting codes, show nothing in the preview
            String visiblePreviewNick = ColorParser.strip(rawNick);
            if (rawNick != null && !rawNick.isEmpty() && !visiblePreviewNick.isBlank()) {
                Component nickText = ColorParser.buildNick(tmp, Component.literal(username));


                int y = addPreviewY;
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

        // Handle tab button clicks manually
        double mouseX = click.x();
        double mouseY = click.y();

        if (isInsideTab(this.addTabBtn, mouseX, mouseY)) {
            return switchTabFromMouse(Tab.ADD, this.addTabBtn);
        }
        if (isInsideTab(this.entriesTabBtn, mouseX, mouseY)) {
            return switchTabFromMouse(Tab.ENTRIES, this.entriesTabBtn);
        }
        if (isInsideTab(this.optionsTabBtn, mouseX, mouseY)) {
            return switchTabFromMouse(Tab.OPTIONS, this.optionsTabBtn);
        }

        return super.mouseClicked(click, doubleClick);
    }

    private boolean switchTabFromMouse(Tab targetTab, Button tabButton) {
        if (this.tab == targetTab) {
            return true;
        }

        if (tabButton != null) {
            tabButton.playDownSound(Minecraft.getInstance().getSoundManager());
        }

        this.tab = targetTab;
        this.init();
        return true;
    }


    private static boolean isInsideTab(Button button, double mouseX, double mouseY) {
        if (button == null) return false;
        return mouseX >= button.getX()
                && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY()
                && mouseY < button.getY() + button.getHeight();
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

    private void showError(String translationKey) {
        if (errorModal == null) {
            errorModal = new ErrorModal(this.font);
            errorModal.setScreenSize(this.width, this.height);
        }
        errorModal.show(Component.translatable(translationKey));
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

    private static class EntriesList extends ContainerObjectSelectionList<EntryRow> {
        public EntriesList(
                Minecraft client,
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
        protected int scrollBarX() {
            return this.width - 12;
        }

        public int rowLeft() {
            return this.getRowLeft();
        }
    }

    private class EntryRow extends ContainerObjectSelectionList.Entry<EntryRow> {
        private final UUID uuid;
        private final NickEntry entry;

        private final Button editButton;
        private final Button deleteButton;

        public EntryRow(UUID uuid, NickEntry entry) {
            this.uuid = uuid;
            this.entry = entry;

            this.editButton = Button.builder(t("button.edit"), b -> {
                        Minecraft.getInstance().setScreen(
                                new EditEntryScreen(CustomNickConfigScreen.this, uuid)
                        );
                    })
                    .bounds(0, 0, 50, 20)
                    .build();

            this.deleteButton = Button.builder(t("button.delete"), b -> {
                        NickConfig.remove(uuid);
                        refreshEntries();
                    })
                    .bounds(0, 0, 60, 20)
                    .build();
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(editButton, deleteButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(editButton, deleteButton);
        }

        @Override
        public void extractContent(
                GuiGraphicsExtractor context,
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
            int textY = y + (26 - font.lineHeight) / 2;
            int buttonY = y + (26 - 20) / 2;

            context.text(
                    font,
                    Component.literal(username),
                    x,
                    textY,
                    hovered ? 0xFFFFA0 : 0xFFFFFF,
                    true
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
                Component nickBase = ColorParser.buildNick(entry, Component.literal(username));
                Component preview = Component.empty()
                        .append(nickBase)
                        .append(Component.literal(" "))
                        .append(Component.literal("(" + username + ")").withColor(0xFFAAAAAA));

                if (textAreaRight > previewX) {
                    context.enableScissor(previewX, y, textAreaRight, y + 26);
                }

                context.text(
                        font,
                        preview,
                        previewX,
                        textY,
                        0xFFFFFFFF,
                        true
                );

                if (textAreaRight > previewX) {
                    context.disableScissor();
                }
            }

            editButton.setX(bx);
            editButton.setY(buttonY);
            deleteButton.setX(bx + 50 + 4);
            deleteButton.setY(buttonY);

            editButton.extractRenderState(context, mouseX, mouseY, delta);
            deleteButton.extractRenderState(context, mouseX, mouseY, delta);

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

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo ple = mc.getConnection().getPlayerInfo(uuid);
            if (ple != null && ple.getProfile().name() != null) {
                return ple.getProfile().name();
            }
        }
        return uuid.toString();
    }
}