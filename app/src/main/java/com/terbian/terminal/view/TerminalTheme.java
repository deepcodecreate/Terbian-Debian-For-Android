/*
 * Copyright 2026 deepcodecreate
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.terbian.terminal.view;

public class TerminalTheme {
    public static final String ID_BLACK = "termux";
    public static final String ID_BURNT = "burnt";
    public static final String ID_GRUVBOX = "gruvbox";
    public static final String ID_SOLARIZED = "solarized";
    public static final String ID_DRACULA = "dracula";
    public static final String ID_NORD = "nord";
    public static final String ID_LIGHT = "light";
    public static final String ID_KALI = "kali";
    public static final String ID_TOKYO = "tokyo";
    public static final String ID_MOCHA = "mocha";
    public static final String ID_ONEDARK = "onedark";
    public static final String ID_MONOKAI = "monokai";
    public static final String ID_MATRIX = "matrix";
    public static final String ID_NEON = "neon";
    public static final String ID_ROSE = "rosepine";

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
        for (TerminalTheme t : all()) {
            if (t.id.equals(id)) return t;
        }
        return black();
    }

    public static TerminalTheme[] all() {
        return new TerminalTheme[]{
                black(), kaliDark(), burnt(), gruvbox(), solarizedDark(), dracula(), nord(), lightPaper(),
                tokyoNight(), catppuccinMocha(), oneDark(), monokai(), matrix(), neon(), rosePine()
        };
    }

    public static TerminalTheme kaliDark() {
        return builder().id(ID_KALI).name("Kali dark")
                .backgroundColor(0xFF14151E).foregroundColor(0xFFE0E4F0)
                .cursorColor(0xFF3FA9FF).cursorTextColor(0xFF14151E).selectionColor(0x66367BF0)
                .cursorBlinkEnabled(false)
                .standard(new int[]{0xFF1C1E2A, 0xFFE5484D, 0xFF46C48A, 0xFFE5C07B, 0xFF367BF0, 0xFFB57EDC, 0xFF2CC3D5, 0xFFD6DAE6})
                .bright(new int[]{0xFF50536B, 0xFFFF6B70, 0xFF7DF0B0, 0xFFFFE08A, 0xFF6FA3FF, 0xFFD3A6F5, 0xFF6FE6F2, 0xFFFFFFFF})
                .build();
    }

    public static TerminalTheme tokyoNight() {
        return builder().id(ID_TOKYO).name("Tokyo Night")
                .backgroundColor(0xFF1A1B26).foregroundColor(0xFFC0CAF5)
                .cursorColor(0xFF7AA2F7).cursorTextColor(0xFF1A1B26).selectionColor(0x66364A82)
                .cursorBlinkEnabled(false)
                .standard(new int[]{0xFF15161E, 0xFFF7768E, 0xFF9ECE6A, 0xFFE0AF68, 0xFF7AA2F7, 0xFFBB9AF7, 0xFF7DCFFF, 0xFFA9B1D6})
                .bright(new int[]{0xFF414868, 0xFFF7768E, 0xFF9ECE6A, 0xFFE0AF68, 0xFF7AA2F7, 0xFFBB9AF7, 0xFF7DCFFF, 0xFFC0CAF5})
                .build();
    }

    public static TerminalTheme catppuccinMocha() {
        return builder().id(ID_MOCHA).name("Catppuccin Mocha")
                .backgroundColor(0xFF1E1E2E).foregroundColor(0xFFCDD6F4)
                .cursorColor(0xFFF5C2E7).cursorTextColor(0xFF1E1E2E).selectionColor(0x66585B70)
                .cursorBlinkEnabled(false)
                .standard(new int[]{0xFF45475A, 0xFFF38BA8, 0xFFA6E3A1, 0xFFF9E2AF, 0xFF89B4FA, 0xFFF5C2E7, 0xFF94E2D5, 0xFFBAC2DE})
                .bright(new int[]{0xFF585B70, 0xFFF38BA8, 0xFFA6E3A1, 0xFFF9E2AF, 0xFF89B4FA, 0xFFF5C2E7, 0xFF94E2D5, 0xFFA6ADC8})
                .build();
    }

    public static TerminalTheme oneDark() {
        return builder().id(ID_ONEDARK).name("One Dark")
                .backgroundColor(0xFF282C34).foregroundColor(0xFFABB2BF)
                .cursorColor(0xFF61AFEF).cursorTextColor(0xFF282C34).selectionColor(0x663E4451)
                .cursorBlinkEnabled(false)
                .standard(new int[]{0xFF3F4451, 0xFFE06C75, 0xFF98C379, 0xFFE5C07B, 0xFF61AFEF, 0xFFC678DD, 0xFF56B6C2, 0xFFABB2BF})
                .bright(new int[]{0xFF5C6370, 0xFFFF7B86, 0xFFB1E18B, 0xFFEFD08A, 0xFF7DC1FF, 0xFFD78CF0, 0xFF6FCBD8, 0xFFDCDFE4})
                .build();
    }

    public static TerminalTheme monokai() {
        return builder().id(ID_MONOKAI).name("Monokai")
                .backgroundColor(0xFF272822).foregroundColor(0xFFF8F8F2)
                .cursorColor(0xFFA6E22E).cursorTextColor(0xFF272822).selectionColor(0x66494A41)
                .cursorBlinkEnabled(false)
                .standard(new int[]{0xFF272822, 0xFFF92672, 0xFFA6E22E, 0xFFF4BF75, 0xFF66D9EF, 0xFFAE81FF, 0xFFA1EFE4, 0xFFF8F8F2})
                .bright(new int[]{0xFF75715E, 0xFFF92672, 0xFFA6E22E, 0xFFF4BF75, 0xFF66D9EF, 0xFFAE81FF, 0xFFA1EFE4, 0xFFF9F8F5})
                .build();
    }

    public static TerminalTheme matrix() {
        return builder().id(ID_MATRIX).name("Matrix")
                .backgroundColor(0xFF020A04).foregroundColor(0xFF3DFF7A)
                .cursorColor(0xFF00FF41).cursorTextColor(0xFF020A04).selectionColor(0x6600C853)
                .cursorStyle(CursorStyle.BLOCK).cursorBlinkEnabled(false)
                .standard(new int[]{0xFF031A0A, 0xFFE5534B, 0xFF00C853, 0xFFC5E478, 0xFF2E9F6B, 0xFF6BCB9A, 0xFF00E5A8, 0xFF9DFFB5})
                .bright(new int[]{0xFF1E5E34, 0xFFFF7B72, 0xFF00FF41, 0xFFE6FF9E, 0xFF4FD08F, 0xFF9BF0C2, 0xFF5CFFD2, 0xFFD7FFE3})
                .build();
    }

    public static TerminalTheme neon() {
        return builder().id(ID_NEON).name("Neon night")
                .backgroundColor(0xFF0A0A14).foregroundColor(0xFFEAF2FF)
                .cursorColor(0xFFFF2BD6).cursorTextColor(0xFF0A0A14).selectionColor(0x66FF2BD6)
                .cursorStyle(CursorStyle.BAR).cursorBlinkEnabled(false)
                .standard(new int[]{0xFF14142B, 0xFFFF3B6B, 0xFF2BFFB0, 0xFFFFE600, 0xFF3B8BFF, 0xFFFF2BD6, 0xFF00E5FF, 0xFFD8E2FF})
                .bright(new int[]{0xFF3A3A66, 0xFFFF6F91, 0xFF7DFFD0, 0xFFFFF27A, 0xFF7BB0FF, 0xFFFF7BE8, 0xFF7BF3FF, 0xFFFFFFFF})
                .build();
    }

    public static TerminalTheme rosePine() {
        return builder().id(ID_ROSE).name("Rosé Pine")
                .backgroundColor(0xFF191724).foregroundColor(0xFFE0DEF4)
                .cursorColor(0xFFEBBCBA).cursorTextColor(0xFF191724).selectionColor(0x66403D52)
                .cursorBlinkEnabled(false)
                .standard(new int[]{0xFF26233A, 0xFFEB6F92, 0xFF3E8FB0, 0xFFF6C177, 0xFF9CCFD8, 0xFFC4A7E7, 0xFFEBBCBA, 0xFFE0DEF4})
                .bright(new int[]{0xFF6E6A86, 0xFFEB6F92, 0xFF5FB0CF, 0xFFF6C177, 0xFF9CCFD8, 0xFFC4A7E7, 0xFFEBBCBA, 0xFFE0DEF4})
                .build();
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

    public static TerminalTheme black() {
        return builder()
                .id(ID_BLACK)
                .name("Black")
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
                .selectionColor(0x55C9A98A)
                .standard(new int[]{0xFF3D2C22, 0xFFB3261E, 0xFF2E7D32, 0xFF8F5F00, 0xFF1F5FA8, 0xFF8E4585, 0xFF0B7285, 0xFF76665A})
                .bright(new int[]{0xFF6B5A4D, 0xFFC62828, 0xFF3A8A3E, 0xFFA66F00, 0xFF2B6CB8, 0xFFA2529A, 0xFF12879B, 0xFF4A3A30})
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
