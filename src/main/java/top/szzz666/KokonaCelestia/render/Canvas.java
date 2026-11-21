package top.szzz666.KokonaCelestia.render;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** 绘图上下文封装：统一圆角卡片、文本、渐变与栅格语义。 */
public final class Canvas {

    private final Graphics2D g;
    private final FontMetricsCache metrics = new FontMetricsCache();

    public Canvas(Graphics2D g) {
        this.g = g;
        Theme.quality(g);
    }

    public Graphics2D raw() {
        return g;
    }

    // ==================== 形状 ====================

    public static Shape roundRect(float x, float y, float w, float h, float r) {
        float radius = Math.min(r, Math.min(w, h) / 2f);
        return new RoundRectangle2D.Float(x, y, w, h, radius * 2, radius * 2);
    }

    /** 玻璃卡片：柔和投影 + 半透明白底 + 高光描边。 */
    public void glassCard(float x, float y, float w, float h, float radius) {
        Shape shape = roundRect(x, y, w, h, radius);
        // 投影
        for (int i = 4; i >= 1; i--) {
            g.setColor(new Color(120, 100, 140, 5 + i));
            g.fill(roundRect(x - i * 0.5f, y - i * 0.5f + 3, w + i, h + i, radius + i * 0.5f));
        }
        g.setColor(Theme.CARD);
        g.fill(shape);
        g.setPaint(new java.awt.GradientPaint(x, y, new Color(255, 255, 255, 150),
                x, y + h, new Color(255, 255, 255, 60)));
        g.fill(shape);
        g.setColor(Theme.CARD_EDGE);
        g.setStroke(new BasicStroke(1.1f));
        g.draw(shape);
    }

    /** 纯色填充圆角矩形。 */
    public void fillRound(float x, float y, float w, float h, float r, Color color) {
        g.setColor(color);
        g.fill(roundRect(x, y, w, h, r));
    }

    /** 渐变填充圆角矩形。 */
    public void fillRound(float x, float y, float w, float h, float r, Paint paint) {
        g.setPaint(paint);
        g.fill(roundRect(x, y, w, h, r));
    }

    public void strokeRound(float x, float y, float w, float h, float r, Color color, float width) {
        g.setColor(color);
        g.setStroke(new BasicStroke(width));
        g.draw(roundRect(x, y, w, h, r));
    }

    /** 圆形头像，带白色描边。 */
    public void avatar(BufferedImage image, float x, float y, float size, float radius) {
        Shape clip = roundRect(x, y, size, size, radius);
        Composite old = g.getComposite();
        if (image != null) {
            Shape oldClip = g.getClip();
            g.clip(clip);
            g.drawImage(image, (int) x, (int) y, (int) size, (int) size, null);
            g.setClip(oldClip);
        } else {
            g.setColor(new Color(226, 232, 240));
            g.fill(clip);
        }
        g.setComposite(old);
        g.setColor(new Color(255, 255, 255, 200));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(clip);
    }

    /** 圆形头像（真圆）。 */
    public void circleAvatar(BufferedImage image, float cx, float cy, float size) {
        Shape clip = new Ellipse2D.Float(cx - size / 2, cy - size / 2, size, size);
        if (image != null) {
            Shape oldClip = g.getClip();
            g.clip(clip);
            g.drawImage(image, (int) (cx - size / 2), (int) (cy - size / 2), (int) size, (int) size, null);
            g.setClip(oldClip);
        } else {
            g.setColor(new Color(226, 232, 240));
            g.fill(clip);
        }
        g.setColor(new Color(255, 255, 255, 210));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(clip);
    }

    // ==================== 文本 ====================

    public void text(String text, Font font, Color color, float x, float y) {
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    public void textCenter(String text, Font font, Color color, float centerX, float y) {
        FontMetrics fm = fm(font);
        text(text, font, color, centerX - fm.stringWidth(text) / 2f, y);
    }

    public void textRight(String text, Font font, Color color, float rightX, float y) {
        FontMetrics fm = fm(font);
        text(text, font, color, rightX - fm.stringWidth(text), y);
    }

    /** 渐变文字。 */
    public void textGradient(String text, Font font, Paint paint, float x, float y) {
        FontMetrics fm = fm(font);
        g.setFont(font);
        g.setPaint(paint);
        g.drawString(text, x, y);
    }

    public float width(String text, Font font) {
        return fm(font).stringWidth(text);
    }

    public FontMetrics fm(Font font) {
        return metrics.get(g, font);
    }

    /** 单个胶囊标签。 */
    public void chip(String text, Font font, Color fg, Color bg, Color border,
                     float x, float y, float padX, float height) {
        float w = width(text, font) + padX * 2;
        fillRound(x, y, w, height, height / 2f, bg);
        if (border != null) {
            strokeRound(x, y, w, height, height / 2f, border, 1f);
        }
        FontMetrics fm = fm(font);
        float baseline = y + (height - fm.getHeight()) / 2f + fm.getAscent();
        text(text, font, fg, x + padX, baseline);
    }

    public float chipWidth(String text, Font font, float padX) {
        return width(text, font) + padX * 2;
    }

    /** 分隔线。 */
    public void divider(float x, float y, float w, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(1f));
        g.draw(new java.awt.geom.Line2D.Float(x, y, x + w, y));
    }

    /** 在指定裁剪区域内绘制。 */
    public void clipped(Shape shape, Runnable body) {
        Shape old = g.getClip();
        g.clip(shape);
        body.run();
        g.setClip(old);
    }

    /** 以指定透明度绘制。 */
    public void withAlpha(float alpha, Runnable body) {
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        body.run();
        g.setComposite(old);
    }

    /** 字体度量缓存，避免高频 getFontMetrics 开销。 */
    private static final class FontMetricsCache {
        private Font lastFont;
        private FontMetrics lastMetrics;

        FontMetrics get(Graphics2D g, Font font) {
            if (lastFont == null || !lastFont.equals(font)) {
                lastFont = font;
                lastMetrics = g.getFontMetrics(font);
            }
            return lastMetrics;
        }
    }

    /** 恢复默认绘制状态。 */
    public void resetPaint() {
        g.setPaint(Color.BLACK);
        g.setStroke(new BasicStroke(1f));
        g.setComposite(AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
}
