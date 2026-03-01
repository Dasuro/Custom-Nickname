package dev.dasuro.customnickname.gui;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.util.ColorParser;
import dev.dasuro.customnickname.util.MojangLookup;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CustomNickConfigScreen extends Screen {
    private enum Tab {
        ADD,
        ENTRIES
    }

    private final Screen parent;
    private Tab tab = Tab.ADD;

    private TextFieldWidget addUsername;
    private TextFieldWidget addNickname;

    private boolean addShowPrefix = true;
    private boolean addShowSuffix = true;
    private boolean addRainbowWave = false;
    private float addRainbowSpeed = 1.0f;

    private ButtonWidget togglePrefix;
    private ButtonWidget toggleSuffix;
    private ButtonWidget toggleRainbow;
    private ButtonWidget speedMinus;
    private ButtonWidget speedPlus;
    private ButtonWidget saveButton;

    private SliderWidget rainbowSpeedSlider;
    private int addPreviewY = -1;


    private EntriesList entriesList;

    public CustomNickConfigScreen(Screen parent) {
        super(Text.literal("Custom Nickname"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearChildren();

        int tabHeight = 24;
        int tabWidth = (this.width - 20) / 2;
        int tabX = 10;
        int tabY = 10;

        ButtonWidget addTabBtn = ButtonWidget.builder(Text.literal("Add"), b -> {
                    tab = Tab.ADD;
                    init();
                })
                .dimensions(tabX, tabY, tabWidth, tabHeight)
                .build();
        addTabBtn.active = tab != Tab.ADD;
        this.addDrawableChild(addTabBtn);

        ButtonWidget entriesTabBtn = ButtonWidget.builder(Text.literal("Nicknames"), b -> {
                    tab = Tab.ENTRIES;
                    init();
                })
                .dimensions(tabX + tabWidth, tabY, tabWidth, tabHeight)
                .build();
        entriesTabBtn.active = tab != Tab.ENTRIES;
        this.addDrawableChild(entriesTabBtn);

        int contentTop = tabY + tabHeight + 10;

        if (tab == Tab.ADD) {
            initAdd(contentTop);
        } else {
            initEntries(contentTop);
        }

        // Done button only for Entries tab
        if (tab == Tab.ENTRIES) {
            int btnWidth = 100;
            int btnX = (this.width - btnWidth) / 2;

            this.addDrawableChild(
                    ButtonWidget.builder(Text.literal("Done"), b -> {
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
                Text.literal("Username")
        );
        addUsername.setPlaceholder(Text.literal("Username"));
        // Keep username length reasonable (matches typical MC constraints)
        addUsername.setMaxLength(16);
        this.addDrawableChild(addUsername);

        addNickname = new TextFieldWidget(
                this.textRenderer,
                x,
                top + 30,
                w,
                20,
                Text.literal("Nickname")
        );
        addNickname.setPlaceholder(
                Text.literal("Nickname (supports &a, &#FF0000, ...)")
        );
        addNickname.setMaxLength(4096);
        this.addDrawableChild(addNickname);

        int btnY = top + 60;
        int btnW = (w - 10) / 2;

        togglePrefix = this.addDrawableChild(
                ButtonWidget.builder(
                                Text.literal(toggleLabel("Show prefix", addShowPrefix)),
                                b -> {
                                    addShowPrefix = !addShowPrefix;
                                    b.setMessage(
                                            Text.literal(
                                                    toggleLabel("Show prefix", addShowPrefix)
                                            )
                                    );
                                }
                        )
                        .dimensions(x, btnY, btnW, 20)
                        .build()
        );

        toggleSuffix = this.addDrawableChild(
                ButtonWidget.builder(
                                Text.literal(toggleLabel("Show suffix", addShowSuffix)),
                                b -> {
                                    addShowSuffix = !addShowSuffix;
                                    b.setMessage(
                                            Text.literal(
                                                    toggleLabel("Show suffix", addShowSuffix)
                                            )
                                    );
                                }
                        )
                        .dimensions(x + btnW + 10, btnY, btnW, 20)
                        .build()
        );

        toggleRainbow = this.addDrawableChild(
                ButtonWidget.builder(
                                Text.literal(toggleLabel("Rainbow wave", addRainbowWave)),
                                b -> {
                                    addRainbowWave = !addRainbowWave;
                                    b.setMessage(
                                            Text.literal(
                                                    toggleLabel("Rainbow wave", addRainbowWave)
                                            )
                                    );
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
                Text.literal(""),
                (addRainbowSpeed - 0.1f) / 4.9f
        ) {
            {
                this.updateMessage();
            }

            @Override
            protected void updateMessage() {
                addRainbowSpeed = (float) (0.1f + this.value * 4.9f);
                this.setMessage(
                        Text.literal(String.format("Rainbow speed: %.1fx", addRainbowSpeed))
                );
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
                ButtonWidget.builder(Text.literal("Save"), b -> saveAdd())
                        .dimensions(startX2, yBottom2, btnW2, 20)
                        .build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), b -> {
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
            sendChat("Custom Nickname: username is required.");
            return;
        }

        if (nick == null || nick.isBlank()) {
            sendChat("Custom Nickname: nickname is required.");
            return;
        }

        String visibleNick = ColorParser.strip(nick);
        if (visibleNick.isBlank()) {
            sendChat("Custom Nickname: nickname is required (formatting codes alone are not enough).");
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
                if (profile == null) {
                    sendChat("Custom Nickname: could not resolve UUID.");
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
        int listHeight = this.height - top - 40;

        entriesList = new EntriesList(
                MinecraftClient.getInstance(),
                this.width,
                listHeight,
                top,
                28
        );

        refreshEntries();
        this.addDrawableChild(entriesList);
    }

    private void refreshEntries() {
        if (entriesList == null) return;

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

            rows.add(new EntryRow(uuid, e.getValue()));
        }

        entriesList.replaceEntries(rows);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        // Dark translucent overlay so widgets are readable
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // IMPORTANT: super.render already calls renderBackground (blur).
        super.render(context, mouseX, mouseY, delta);

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Custom Nickname"),
                10,
                2,
                0xFFFFFF
        );

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

                // In the add-preview we only show the resulting nick (no "old name" suffix).
                Text preview = nickText;

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
                        Text.literal("Preview: "),
                        boxX + 5,
                        y,
                        0xFFAAAAAA
                );

                int labelW = this.textRenderer.getWidth("Preview: ");
                int previewTextX = boxX + 5 + labelW;

                int scissorRight = boxRight - 5;
                if (scissorRight > previewTextX) {
                    context.enableScissor(previewTextX, boxY, scissorRight, boxBottom);
                }

                context.drawTextWithShadow(
                        this.textRenderer,
                        preview,
                        previewTextX,
                        y,
                        0xFFFFFFFF
                );

                if (scissorRight > previewTextX) {
                    context.disableScissor();
                }
            }
        }
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    private static String toggleLabel(String name, boolean value) {
        return name + ": " + (value ? "ON" : "OFF");
    }

    private void sendChat(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.sendMessage(Text.literal(msg), false);
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

            this.editButton = ButtonWidget.builder(Text.literal("Edit"), b -> {
                        MinecraftClient.getInstance().setScreen(
                                new EditEntryScreen(CustomNickConfigScreen.this, uuid)
                        );
                    })
                    .dimensions(0, 0, 50, 20)
                    .build();

            this.deleteButton = ButtonWidget.builder(Text.literal("Delete"), b -> {
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

            // Separator line at the bottom of this row
            int lineY = y + 26; // itemHeight (28) - 2px padding
            context.fill(x, lineY, x + entryWidth, lineY + 1, 0xFF444444);
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

