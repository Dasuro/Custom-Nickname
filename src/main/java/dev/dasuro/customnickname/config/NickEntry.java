package dev.dasuro.customnickname.config;

public class NickEntry {
    public String username;
    public String nickname;
    public boolean showPrefix;
    public boolean showSuffix;
    public boolean rainbow;
    public float rainbowSpeed;

    public NickEntry() {
        this.showPrefix = true;
        this.showSuffix = true;
        this.rainbow = false;
        this.rainbowSpeed = 1.0f;
    }

    public NickEntry(
            String username,
            String nickname,
            boolean showPrefix,
            boolean showSuffix,
            boolean rainbow,
            float rainbowSpeed
    ) {
        this.username = username;
        this.nickname = nickname;
        this.showPrefix = showPrefix;
        this.showSuffix = showSuffix;
        this.rainbow = rainbow;
        this.rainbowSpeed = rainbowSpeed;
    }
}
