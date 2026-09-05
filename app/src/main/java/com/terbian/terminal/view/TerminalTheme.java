package com.terbian.terminal.view;

public class TerminalTheme {

    public static final String ID_BURNT = "burnt";
    public static final String ID_TERMUX = "termux";
    public static final String ID_GRUVBOX = "gruvbox";
    public static final String ID_SOLARIZED = "solarized";
    public static final String ID_DRACULA = "dracula";
    public static final String ID_NORD = "nord";
    public static final String ID_LIGHT = "light";

    public final String id;
    public final String name;
    public final int backgroundColor;
    public final int foregroundColor;
    public final int cursorColor;
    public final int cursorTextColor;
    public final int selectionColor;
    public final CursorStyle cursorStyle;
    public final float fontSizeSp;
    public final boolean cursorBlinkEnabled;
    public final int cursorBlinkIntervalMs;
    public final boolean typingAnimationEnabled;
    public final int typingAnimationDurationMs;
    public final float lineSpacingExtraDp;
    public final int[] standard;
    public final int[] bright;

    private TerminalTheme(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.backgroundColor = b.backgroundColor;
        this.foregroundColor = b.foregroundColor;
        this.cursorColor = b.cursorColor;
        this.cursorTextColor = b.cursorTextColor;
        this.selectionColor = b.selectionColor;
        this.cursorStyle = b.cursorStyle;
        this.fontSizeSp = b.fontSizeSp;
        this.cursorBlinkEnabled = b.cursorBlinkEnabled;
        this.cursorBlinkIntervalMs = b.cursorBlinkIntervalMs;
        this.typingAnimationEnabled = b.typingAnimationEnabled;
        this.typingAnimationDurationMs = b.typingAnimationDurationMs;
        this.lineSpacingExtraDp = b.lineSpacingExtraDp;
        this.standard = b.standard;
        this.bright = b.bright;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder b = builder();
        b.id = id;
        b.name = name;
        b.backgroundColor = backgroundColor;
        b.foregroundColor = foregroundColor;
        b.cursorColor = cursorColor;
        b.cursorTextColor = cursorTextColor;
        b.selectionColor = selectionColor;
        b.cursorStyle = cursorStyle;
        b.fontSizeSp = fontSizeSp;
        b.cursorBlinkEnabled = cursorBlinkEnabled;
        b.cursorBlinkIntervalMs = cursorBlinkIntervalMs;
        b.typingAnimationEnabled = typingAnimationEnabled;
        b.typingAnimationDurationMs = typingAnimationDurationMs;
        b.lineSpacingExtraDp = lineSpacingExtraDp;
        b.standard = standard;
        b.bright = bright;
        return b;
    }

    public static TerminalTheme byId(String id) {
        if (ID_TERMUX.equals(id)) return termuxDark();
        if (ID_GRUVBOX.equals(id)) return gruvbox();
        if (ID_SOLARIZED.equals(id)) return solarizedDark();
        if (ID_DRACULA.equals(id)) return dracula();
        if (ID_NORD.equals(id)) return nord();
        if (ID_LIGHT.equals(id)) return lightPaper();
        return burnt();
    }

    public static TerminalTheme[] all() {
        return new TerminalTheme[]{
                burnt(), termuxDark(), gruvbox(), solarizedDark(), dracula(), nord(), lightPaper()
        };
    }

    public static TerminalTheme burnt() {
        return builder()
                .id(ID_BURNT)
                .name("Burnt espresso")
                .backgroundColor(0xFF1A120C)
                .foregroundColor(0xFFF3E6D8)
                .cursorColor(0xFFE8B089)
                .cursorTextColor(0xFF1A120C)
                .selectionColor(0x885C3A22)
                .cursorStyle(CursorStyle.BLOCK)
                .cursorBlinkEnabled(false)
                .standard(new int[]{0xFF2A1C14, 0xFFC45C48, 0xFF8FA36A, 0xFFD4A05A, 0xFFC48962, 0xFFB07A6A, 0xFF8AA090, 0xFFE4D4C4})
                .bright(new int[]{0xFF5A4030, 0xFFE89580, 0xFFB7C4A4, 0xFFE8C98A, 0xFFE8B089, 0xFFD4A090, 0xFFA8C4B0, 0xFFFFF6EC})
                .build();
    }

    public static TerminalTheme termuxDark() {
        return builder()
                .id(ID_TERMUX)
                .name("Termux dark")
                .backgroundColor(0xFF000000)
                .foregroundColor(0xFFE6E6E6)
                .cursorColor(0xFFD3D7CF)
                .cursorTextColor(0xFF000000)
                .selectionColor(0x33FFFFFF)
                .cursorBlinkEnabled(false)
                .build();
    }

    public static TerminalTheme gruvbox() {
        return builder()
                .id(ID_GRUVBOX)
                .name("Gruvbox")
                .backgroundColor(0xFF1D2021)
                .foregroundColor(0xFFEBDBB2)
                .cursorColor(0xFFFE8019)
                .cursorTextColor(0xFF1D2021)
                .selectionColor(0x88504945)
                .cursorBlinkEnabled(false)
                .build();
    }

    public static TerminalTheme solarizedDark() {
        return builder()
                .id(ID_SOLARIZED)
                .name("Solarized dark")
                .backgroundColor(0xFF002B36)
                .foregroundColor(0xFF839496)
                .cursorColor(0xFF93A1A1)
                .cursorTextColor(0xFF002B36)
                .selectionColor(0xAA073642)
                .cursorStyle(CursorStyle.BAR)
                .cursorBlinkEnabled(false)
                .build();
    }

    public static TerminalTheme dracula() {
        return builder()
                .id(ID_DRACULA)
                .name("Dracula")
                .backgroundColor(0xFF282A36)
                .foregroundColor(0xFFF8F8F2)
                .cursorColor(0xFFF8F8F2)
                .cursorTextColor(0xFF282A36)
                .cursorBlinkEnabled(false)
                .build();
    }

    public static TerminalTheme nord() {
        return builder()
                .id(ID_NORD)
                .name("Nord")
                .backgroundColor(0xFF2E3440)
                .foregroundColor(0xFFD8DEE9)
                .cursorColor(0xFF88C0D0)
                .cursorTextColor(0xFF2E3440)
                .cursorBlinkEnabled(false)
                .build();
    }

    public static TerminalTheme lightPaper() {
        return builder()
                .id(ID_LIGHT)
                .name("Light paper")
                .backgroundColor(0xFFFBF6EF)
                .foregroundColor(0xFF3D2C22)
                .cursorColor(0xFF8B5A32)
                .cursorTextColor(0xFFFBF6EF)
                .cursorStyle(CursorStyle.UNDERLINE)
                .cursorBlinkEnabled(false)
                .build();
    }

    public static class Builder {
        private String id = ID_BURNT;
        private String name = "Burnt espresso";
        private int backgroundColor = 0xFF1A120C;
        private int foregroundColor = 0xFFF3E6D8;
        private int cursorColor = 0xFFE8B089;
        private int cursorTextColor = 0xFF1A120C;
        private int selectionColor = 0x885C3A22;
        private CursorStyle cursorStyle = CursorStyle.BLOCK;
        private float fontSizeSp = 14f;
        private boolean cursorBlinkEnabled = false;
        private int cursorBlinkIntervalMs = 530;
        private boolean typingAnimationEnabled = false;
        private int typingAnimationDurationMs = 140;
        private float lineSpacingExtraDp = 2f;
        private int[] standard = new int[]{
                0xFF2E3436, 0xFFCC0000, 0xFF4E9A06, 0xFFC4A000,
                0xFF3465A4, 0xFF75507B, 0xFF06989A, 0xFFD3D7CF
        };
        private int[] bright = new int[]{
                0xFF555753, 0xFFEF2929, 0xFF8AE234, 0xFFFCE94F,
                0xFF729FCF, 0xFFAD7FA8, 0xFF34E2E2, 0xFFEEEEEC
        };

        public Builder id(String v) { this.id = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder backgroundColor(int c) { this.backgroundColor = c; return this; }
        public Builder foregroundColor(int c) { this.foregroundColor = c; return this; }
        public Builder cursorColor(int c) { this.cursorColor = c; return this; }
        public Builder cursorTextColor(int c) { this.cursorTextColor = c; return this; }
        public Builder selectionColor(int c) { this.selectionColor = c; return this; }
        public Builder cursorStyle(CursorStyle s) { this.cursorStyle = s; return this; }
        public Builder fontSizeSp(float sp) { this.fontSizeSp = sp; return this; }
        public Builder cursorBlinkEnabled(boolean v) { this.cursorBlinkEnabled = v; return this; }
        public Builder cursorBlinkIntervalMs(int ms) { this.cursorBlinkIntervalMs = ms; return this; }
        public Builder typingAnimationEnabled(boolean v) { this.typingAnimationEnabled = v; return this; }
        public Builder typingAnimationDurationMs(int ms) { this.typingAnimationDurationMs = ms; return this; }
        public Builder lineSpacingExtraDp(float dp) { this.lineSpacingExtraDp = dp; return this; }
        public Builder standard(int[] v) { this.standard = v; return this; }
        public Builder bright(int[] v) { this.bright = v; return this; }

        public TerminalTheme build() {
            return new TerminalTheme(this);
        }
    }
}
