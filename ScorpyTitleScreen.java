package com.sefixz.scorpysafio;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ScorpyTitleScreen extends Screen {

    // ====== TEXTOS ======
    private static final String SIGNATURE = "by sefixz";
    // ====================

    // Tu logo (768x768 con transparencia)
    private static final Identifier LOGO = Identifier.of("scorpysafio", "textures/gui/logo.png");
    private static final int LOGO_TEX = 768;

    // Fondo opcional propio (1920x1080). Si existe, reemplaza al fondo animado generado.
    private static final Identifier BACKGROUND = Identifier.of("scorpysafio", "textures/gui/background.png");
    private static final int BG_W = 1920;
    private static final int BG_H = 1080;

    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float HALF_PI = (float) (Math.PI / 2.0);

    private static final int STAR_COUNT = 220;
    private static final int LON_BUCKETS = 150;
    private static final int LAT_BUCKETS = 75;

    // Nebulosas: x, y, radio (fracciones), velocidad, fase
    private static final float[][] NEB = {
            {0.12f, 0.30f, 0.42f, 0.8f, 0.0f},
            {0.38f, 0.70f, 0.45f, 1.1f, 1.7f},
            {0.55f, 0.25f, 0.38f, 0.7f, 3.1f},
            {0.78f, 0.60f, 0.50f, 0.9f, 4.4f},
            {0.30f, 0.05f, 0.34f, 1.3f, 5.5f},
            {0.92f, 0.15f, 0.36f, 1.0f, 2.2f}
    };
    private static final int[] NEB_COL = {0xB02A8C, 0x5A2AC8, 0xE0382A, 0xFF7A28, 0x7A2AA8, 0xC8283C};

    private final Batch batch = new Batch();
    private final long startNs = System.nanoTime();

    private boolean hasBackground;

    // estrellas
    private float[] starX, starY, starPhase;
    private boolean[] starBig;

    // planeta de bloques brillantes
    private float planetCx, planetCy, planetR, cellSize;
    private int cellCount;
    private float[] cX, cY, cLon, cLat, cZ;

    // geometría del logo (la usan los rayos)
    private float logoS, logoCx, logoY;

    public ScorpyTitleScreen() {
        super(Text.literal("Scorpy Safio"));
    }

    private float time() {
        return (System.nanoTime() - startNs) / 1_000_000_000f;
    }

    @Override
    protected void init() {
        hasBackground = client.getResourceManager().getResource(BACKGROUND).isPresent();

        Random rnd = new Random(42);
        starX = new float[STAR_COUNT];
        starY = new float[STAR_COUNT];
        starPhase = new float[STAR_COUNT];
        starBig = new boolean[STAR_COUNT];
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = rnd.nextFloat();
            starY[i] = rnd.nextFloat();
            starPhase[i] = rnd.nextFloat() * TAU;
            starBig[i] = rnd.nextInt(9) == 0;
        }

        buildPlanet();

        // Botones (panel inclinado estilo gamer, se dibujan solos)
        int bw = 170;
        int bh = 26;
        int cx = (int) (width * 0.27f);
        int x = cx - bw / 2;
        int y1 = (int) (height * 0.60f);
        int sp = (int) Math.max(bh + 6, height * 0.125f);

        addDrawableChild(new MenuButton(x, y1, bw, bh,
                Text.literal("JUGAR"), 0xFF4D4D,
                b -> client.setScreen(new MultiplayerScreen(this))));
        addDrawableChild(new MenuButton(x, y1 + sp, bw, bh,
                Text.literal("OPCIONES"), 0xFFE94D,
                b -> client.setScreen(new OptionsScreen(this, client.options))));
        addDrawableChild(new MenuButton(x, y1 + sp * 2, bw, bh,
                Text.literal("SALIR"), 0x4DFF6A,
                b -> client.scheduleStop()));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // ------------------------------------------------------------------
    // Planeta
    // ------------------------------------------------------------------

    private void buildPlanet() {
        planetCx = width * 0.84f;
        planetCy = height * 0.52f;
        planetR = height * 0.46f;
        cellSize = Math.max(2.2f, height / 110f);

        int n = (int) Math.ceil(planetR / cellSize);
        List<float[]> cells = new ArrayList<>();
        for (int gy = -n; gy <= n; gy++) {
            for (int gx = -n; gx <= n; gx++) {
                float dx = gx * cellSize;
                float dy = gy * cellSize;
                float d2 = (dx * dx + dy * dy) / (planetR * planetR);
                if (d2 >= 1f) continue;
                float x = planetCx + dx;
                float y = planetCy + dy;
                if (x > width + cellSize || x < -cellSize || y < -cellSize || y > height + cellSize) continue;
                float z = (float) Math.sqrt(1f - d2);
                float lon = (float) Math.atan2(dx / planetR, z);
                float lat = (float) Math.asin(dy / planetR);
                cells.add(new float[]{x, y, lon, lat, z});
            }
        }
        cellCount = cells.size();
        cX = new float[cellCount];
        cY = new float[cellCount];
        cLon = new float[cellCount];
        cLat = new float[cellCount];
        cZ = new float[cellCount];
        for (int i = 0; i < cellCount; i++) {
            float[] c = cells.get(i);
            cX[i] = c[0];
            cY[i] = c[1];
            cLon[i] = c[2];
            cLat[i] = c[3];
            cZ[i] = c[4];
        }
    }

    private static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 16);
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private void drawPlanet(float t) {
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 1.3f);

        // aura
        batch.disc(planetCx, planetCy, planetR * 1.6f,
                Batch.argb((int) (85 * pulse), 255, 110, 40), Batch.argb(0, 255, 60, 20), 48);
        batch.disc(planetCx, planetCy, planetR * 1.2f,
                Batch.argb((int) (110 * pulse), 255, 140, 50), Batch.argb(0, 255, 80, 20), 48);

        // cuerpo
        batch.disc(planetCx, planetCy, planetR,
                Batch.argb(255, 52, 18, 36), Batch.argb(255, 125, 46, 34), 56);

        // bloques encendidos que giran con el planeta
        float rot = t * 0.10f;
        float size = cellSize * 0.70f;
        float lonScale = LON_BUCKETS / TAU;
        float latScale = LAT_BUCKETS / (float) Math.PI;
        for (int i = 0; i < cellCount; i++) {
            int u = (int) Math.floor((cLon[i] + rot) * lonScale);
            int v = (int) Math.floor((cLat[i] + HALF_PI) * latScale);
            float h = hash(u, v);
            if (h < 0.52f) continue;
            float flick = 0.65f + 0.35f * (float) Math.sin(t * (1.5f + h * 3f) + h * 40f);
            float lum = (0.35f + 0.65f * cZ[i]) * flick;
            float hue = (h - 0.52f) / 0.48f;
            int rgb = Batch.lerpColor(0xFFFF7A2A, 0xFFFFD27A, hue) & 0xFFFFFF;
            batch.rect(cX[i], cY[i], cX[i] + size, cY[i] + size, Batch.withAlpha(rgb, Math.min(1f, lum * 1.1f)));
        }
    }

    // ------------------------------------------------------------------
    // Fondo
    // ------------------------------------------------------------------

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float t = time();

        if (hasBackground) {
            ctx.drawTexture(BACKGROUND, 0, 0, width, height, 0f, 0f, BG_W, BG_H, BG_W, BG_H);
        }

        batch.begin(ctx);

        if (!hasBackground) {
            // cielo
            batch.quad(0, 0, width, height, 0xFF140A36, 0xFF5A1830, 0xFF9A3A30, 0xFF3A1A5A);

            // nebulosas a la deriva
            float big = Math.max(width, height);
            for (int i = 0; i < NEB.length; i++) {
                float[] n = NEB[i];
                float cx = (n[0] + 0.04f * (float) Math.sin(t * 0.12f * n[3] + n[4])) * width;
                float cy = (n[1] + 0.03f * (float) Math.cos(t * 0.10f * n[3] + n[4])) * height;
                float r = n[2] * big * (1f + 0.05f * (float) Math.sin(t * 0.3f + n[4]));
                batch.disc(cx, cy, r, Batch.withAlpha(NEB_COL[i], 0.34f), Batch.withAlpha(NEB_COL[i], 0f), 28);
            }

            // estrellas
            for (int i = 0; i < STAR_COUNT; i++) {
                float layer = starBig[i] ? 1.6f : 1f;
                float x = ((starX[i] * width - t * 2.5f * layer) % width + width) % width;
                float y = starY[i] * height;
                float tw = 0.5f + 0.5f * (float) Math.sin(t * 1.5f + starPhase[i]);
                int a = 80 + (int) (175 * tw);
                float s = starBig[i] ? 1.3f : 0.7f;
                batch.rect(x, y, x + s, y + s, Batch.argb(a, 255, 255, 255));
            }

            // estrella fugaz
            float cycle = 6f;
            int bucket = (int) Math.floor(t / cycle);
            float ph = (t % cycle) / 1.1f;
            if (ph < 1f) {
                float sx = (0.45f + 0.5f * hash(bucket, 1)) * width;
                float sy = (0.04f + 0.3f * hash(bucket, 2)) * height;
                float dirX = -0.87f;
                float dirY = 0.49f;
                float dist = width * 0.35f * ph;
                float hx = sx + dirX * dist;
                float hy = sy + dirY * dist;
                for (int k = 0; k < 16; k++) {
                    float px = hx - dirX * k * 3f;
                    float py = hy - dirY * k * 3f;
                    int a = (int) ((1f - k / 16f) * (1f - ph * 0.6f) * 255f);
                    batch.rect(px, py, px + 1.5f, py + 1.5f, Batch.argb(a, 255, 235, 210));
                }
            }

            drawPlanet(t);
        }

        // viñeta
        int dark = 0xA0000000;
        int clear = 0x00000000;
        float vw = width * 0.18f;
        float vh = height * 0.18f;
        batch.quad(0, 0, vw, height, dark, clear, clear, dark);
        batch.quad(width - vw, 0, width, height, clear, dark, dark, clear);
        batch.quad(0, 0, width, vh, dark, dark, clear, clear);
        batch.quad(0, height - vh, width, height, clear, clear, dark, dark);

        batch.flush();
    }

    // ------------------------------------------------------------------
    // Primer plano
    // ------------------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta); // fondo + botones
        float t = time();
        drawLogo(ctx, t);
        drawLightning(ctx, t);
        drawSignature(ctx, t);
    }

    private float glitch(float t) {
        int bucket = (int) (t * 8f);
        float h = hash(bucket, 77);
        return h > 0.93f ? (h - 0.93f) / 0.07f : 0f;
    }

    private void drawLogo(DrawContext ctx, float t) {
        float size = Math.min(height * 0.54f, width * 0.46f);
        float bob = (float) Math.sin(t * 1.6f) * (height * 0.008f);
        float breathe = 1f + 0.012f * (float) Math.sin(t * 2.2f);
        float s = size * breathe;

        logoS = s;
        logoCx = width * 0.27f;
        logoY = height * 0.02f + bob;
        float x = logoCx - s / 2f;
        float y = logoY;

        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3.1f);
        float gl = glitch(t);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // halo rojo que late
        drawLogoTex(ctx, x, y, s, 1.07f, 1f, 0.08f, 0.08f, 0.30f + 0.25f * pulse);
        drawLogoTex(ctx, x, y, s, 1.15f, 1f, 0.25f, 0.05f, 0.12f + 0.12f * pulse);

        // glitch (aberración cromática)
        if (gl > 0f) {
            float off = gl * s * 0.015f;
            drawLogoTex(ctx, x - off, y, s, 1f, 1f, 0.1f, 0.1f, 0.55f);
            drawLogoTex(ctx, x + off, y, s, 1f, 0.1f, 0.9f, 1f, 0.45f);
        }

        // logo normal
        drawLogoTex(ctx, x, y, s, 1f, 1f, 1f, 1f, 1f);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void drawLogoTex(DrawContext ctx, float x, float y, float s, float mul,
                             float r, float g, float b, float a) {
        float ss = s * mul;
        float xx = x - (ss - s) / 2f;
        float yy = y - (ss - s) / 2f;
        float k = ss / LOGO_TEX;

        RenderSystem.setShaderColor(r, g, b, a);
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(xx, yy, 0f);
        m.scale(k, k, 1f);
        ctx.drawTexture(LOGO, 0, 0, LOGO_TEX, LOGO_TEX, 0f, 0f, LOGO_TEX, LOGO_TEX, LOGO_TEX, LOGO_TEX);
        m.pop();
    }

    /** Rayos rojos que saltan alrededor del logo, a ráfagas. */
    private void drawLightning(DrawContext ctx, float t) {
        int bucket = (int) (t * 9f);
        if (hash(bucket, 5) <= 0.72f || logoS <= 0f) return;

        float cx = logoCx;
        float cy = logoY + logoS * 0.45f;
        Random r = new Random((bucket / 2) * 7919L + 13L);
        int bolts = 2 + r.nextInt(3);

        batch.begin(ctx);
        for (int b = 0; b < bolts; b++) {
            float ang = r.nextFloat() * TAU;
            float rad = logoS * 0.30f;
            float x = cx + (float) Math.cos(ang) * rad;
            float y = cy + (float) Math.sin(ang) * rad;
            float segLen = logoS * 0.06f;
            for (int seg = 0; seg < 9; seg++) {
                ang += (r.nextFloat() - 0.5f) * 1.4f;
                float nx = x + (float) Math.cos(ang) * segLen;
                float ny = y + (float) Math.sin(ang) * segLen;
                int steps = Math.max(1, (int) segLen);
                for (int s = 0; s < steps; s++) {
                    float f = s / (float) steps;
                    float px = x + (nx - x) * f;
                    float py = y + (ny - y) * f;
                    batch.rect(px - 1.6f, py - 1.6f, px + 1.6f, py + 1.6f, 0x44FF2020);
                    batch.rect(px - 0.5f, py - 0.5f, px + 0.5f, py + 0.5f, 0xEEFFE0E0);
                }
                x = nx;
                y = ny;
            }
        }
        batch.flush();
    }

    /** "by sefixz" abajo a la derecha, con ola, brillo que barre y subrayado. */
    private void drawSignature(DrawContext ctx, float t) {
        float scale = 2f;
        int n = SIGNATURE.length();
        Text[] chars = new Text[n];
        float[] w = new float[n];
        float total = 0f;
        for (int i = 0; i < n; i++) {
            chars[i] = Text.literal(String.valueOf(SIGNATURE.charAt(i))).formatted(Formatting.BOLD, Formatting.ITALIC);
            w[i] = textRenderer.getWidth(chars[i]);
            total += w[i];
        }

        float x0 = width - 16f - total * scale;
        float baseY = height - 18f - 9f * scale;
        float textH = 9f * scale;

        // fondo oscuro suave + subrayado que se desvanece
        batch.begin(ctx);
        batch.quad(x0 - 60f, baseY - 8f, width, baseY + textH + 10f,
                0x00000000, 0x99000000, 0x99000000, 0x00000000);
        float lineY = baseY + textH + 3f;
        batch.quad(x0, lineY, x0 + total * scale, lineY + 1.6f,
                0xFFFF3B3B, 0x00FFC94D, 0x00FFC94D, 0xFFFF3B3B);
        float dot = x0 + ((t * 0.5f) % 1f) * total * scale;
        batch.rect(dot, lineY - 0.5f, dot + 5f, lineY + 2.1f, 0xFFFFFFFF);
        batch.flush();

        float cx = x0;
        float sweep = (t * 7f) % (n + 8);
        MatrixStack m = ctx.getMatrices();
        for (int i = 0; i < n; i++) {
            float wave = (float) Math.sin(t * 3f + i * 0.7f) * 1.2f;
            float mix = 0.5f + 0.5f * (float) Math.sin(t * 2f - i * 0.5f);
            int col = Batch.lerpColor(0xFFFF3B3B, 0xFFFFC94D, mix);
            float d = Math.abs(i - sweep);
            if (d < 1.5f) {
                col = Batch.lerpColor(col, 0xFFFFFFFF, 1f - d / 1.5f);
            }
            m.push();
            m.translate(cx, baseY + wave, 0f);
            m.scale(scale, scale, 1f);
            ctx.drawText(textRenderer, chars[i], 0, 0, col, true);
            m.pop();
            cx += w[i] * scale;
        }
    }

    // ------------------------------------------------------------------
    // Botón de texto con panel inclinado
    // ------------------------------------------------------------------

    private static class MenuButton extends ButtonWidget {
        private final int rgb;
        private float hover;

        MenuButton(int x, int y, int w, int h, Text label, int rgb, PressAction action) {
            super(x, y, w, h, label, action, DEFAULT_NARRATION_SUPPLIER);
            this.rgb = rgb;
        }

        @Override
        public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            boolean hot = isHovered() || isFocused();
            hover += ((hot ? 1f : 0f) - hover) * 0.25f;

            float t = (System.nanoTime() % 1_000_000_000_000L) / 1_000_000_000f;
            int w = getWidth();
            int h = getHeight();
            int x = (int) (getX() + hover * 8f);
            int y = getY();
            int accent = 0xFF000000 | rgb;
            int panel = Batch.lerpColor(0xA0080410, Batch.withAlpha(rgb, 0.60f), hover);

            for (int r = 0; r < h; r++) {
                int off = (int) ((h - 1 - r) * 0.38f);
                int left = x + off;
                ctx.fill(left, y + r, left + w, y + r + 1, panel);
                ctx.fill(left, y + r, left + 3, y + r + 1, accent);
                if (hover > 0.05f) {
                    int sx = (int) (((t * 1.2f) % 1f) * (w + 20)) - 10;
                    int a = Math.max(left + 3, left + sx);
                    int b = Math.min(left + w, left + sx + 10);
                    if (a < b) {
                        ctx.fill(a, y + r, b, y + r + 1, Batch.withAlpha(0xFFFFFF, 0.22f * hover));
                    }
                }
            }

            Text text = getMessage().copy().formatted(Formatting.BOLD, Formatting.ITALIC);
            int color = Batch.lerpColor(accent, 0xFFFFFFFF, hover);
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(x + 17f, y + h / 2f, 0f);
            m.scale(1.5f, 1.5f, 1f);
            ctx.drawText(MinecraftClient.getInstance().textRenderer, text, 0, -4, color, true);
            m.pop();
        }
    }
}
