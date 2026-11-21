package com.sefixz.scorpysafio;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/**
 * Junta miles de rectángulos/degradados de color en pocos draw calls
 * (mucho más rápido que llamar a DrawContext.fill miles de veces).
 */
public final class Batch {
    private static final int MAX_QUADS = 4000;

    private BufferBuilder buf;
    private int quads;
    private Matrix4f mat;

    public void begin(DrawContext ctx) {
        this.mat = ctx.getMatrices().peek().getPositionMatrix();
        this.buf = null;
        this.quads = 0;
    }

    /** Rectángulo con un color por esquina (ARGB): arriba-izq, arriba-der, abajo-der, abajo-izq. */
    public void quad(float x1, float y1, float x2, float y2, int cTL, int cTR, int cBR, int cBL) {
        if (buf == null) {
            buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        }
        buf.vertex(mat, x1, y1, 0f).color(cTL);
        buf.vertex(mat, x1, y2, 0f).color(cBL);
        buf.vertex(mat, x2, y2, 0f).color(cBR);
        buf.vertex(mat, x2, y1, 0f).color(cTR);
        if (++quads >= MAX_QUADS) {
            flush();
        }
    }

    public void rect(float x1, float y1, float x2, float y2, int color) {
        quad(x1, y1, x2, y2, color, color, color, color);
    }

    /** Disco con degradado radial (centro -> borde). */
    public void disc(float cx, float cy, float r, int centerColor, int edgeColor, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2.0 * i) / segments;
            double a1 = (Math.PI * 2.0 * (i + 1)) / segments;
            float x0 = cx + (float) Math.cos(a0) * r;
            float y0 = cy + (float) Math.sin(a0) * r;
            float x1 = cx + (float) Math.cos(a1) * r;
            float y1 = cy + (float) Math.sin(a1) * r;
            if (buf == null) {
                buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            }
            buf.vertex(mat, cx, cy, 0f).color(centerColor);
            buf.vertex(mat, x0, y0, 0f).color(edgeColor);
            buf.vertex(mat, x1, y1, 0f).color(edgeColor);
            buf.vertex(mat, cx, cy, 0f).color(centerColor);
            if (++quads >= MAX_QUADS) {
                flush();
            }
        }
    }

    public void flush() {
        if (buf != null) {
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            BufferRenderer.drawWithGlobalProgram(buf.end());
            RenderSystem.enableCull();
            buf = null;
            quads = 0;
        }
    }

    public static int argb(int a, int r, int g, int b) {
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }

    public static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static int lerpColor(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t);
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return argb(a, r, g, b);
    }
}
