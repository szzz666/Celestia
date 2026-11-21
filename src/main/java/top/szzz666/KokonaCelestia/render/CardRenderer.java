package top.szzz666.KokonaCelestia.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 出图基类：负责画布尺寸、页面背景、标题栏、分组条与页脚。
 * 所有视图共用同一套骨架与色板，保证机器人出图风格统一。
 * <p>
 * 性能约定：布局用 {@link #layoutHeight()} 估算总高度，实际只绘制一次。
 * 早先的实现先在高画布上“测量”再正式绘制，等于整页画两遍，且测量阶段会触发
 * 所有远程图片的串行下载，单次出图可达 25 秒。
 */
public abstract class CardRenderer {

    protected static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    protected static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 页面副标题，例如「玩家概览」「排行榜」。
     * <p>
     * 最终标题为「{@code image.brand} - 副标题」，品牌名由配置决定，
     * 各视图只负责提供副标题，避免品牌名散落在多处。
     */
    protected abstract String subtitleTitle();

    /** 完整页面标题：品牌名 + 副标题。 */
    protected String title() {
        String suffix = subtitleTitle();
        String brand = Theme.brand();
        return suffix == null || suffix.isBlank() ? brand : brand + " - " + suffix;
    }

    /** 页面右侧副标题。 */
    protected String subtitle() {
        return STAMP.format(Instant.now().atZone(ZONE));
    }

    /** 页脚右侧署名，取自 {@code image.watermark} 配置。 */
    protected String watermark() {
        return Theme.watermark();
    }

    /** 页脚说明。 */
    protected String footerNote() {
        return "数据来源 dakanu.com，不同数据的缓存周期不同，以游戏内为准。— 表示缺失。";
    }

    /**
     * 内容区绘制。
     *
     * @param top 内容起始 Y
     * @return 内容区底部 Y
     */
    protected abstract int drawBody(Canvas canvas, int top);

    /**
     * 页面总高度。
     * <p>
     * 由各视图按自身数据量精确计算，不通过“画一遍再量”获得。
     * 实现方必须与 {@link #drawBody} 使用同一套行高常量，否则会出现留白或截断。
     */
    protected abstract int layoutHeight();

    /**
     * 绘制前的准备：并行预取本页需要的图片。
     * <p>
     * 绘制时 {@code Assets.avatar(...)} 是同步调用，若不预先拉齐，
     * 十几张图会退化成逐张串行的 TLS 请求，单页可能卡二十多秒。
     * 默认不做事，由各视图按需覆盖。
     */
    protected void prepare() {
    }

    /** 渲染为图片：预取图片 -> 计算高度 -> 只绘制一次。 */
    public BufferedImage render() {
        prepare();
        int height = Math.max(360, layoutHeight());
        BufferedImage out = new BufferedImage(Theme.WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        Canvas canvas = new Canvas(g);
        paint(canvas, height);
        g.dispose();
        return out;
    }

    /** 测量页面总高度（供排版调试）。 */
    public int measure() {
        return Math.max(360, layoutHeight());
    }

    /** 完整布局：背景 -> 标题 -> 内容 -> 页脚，返回总高度。 */
    private int paint(Canvas canvas, int canvasHeight) {
        Graphics2D g = canvas.raw();
        g.setPaint(Theme.pageBackground(Theme.WIDTH, canvasHeight));
        g.fillRect(0, 0, Theme.WIDTH, canvasHeight);

        int y = paintHeader(canvas);
        int bodyBottom = drawBody(canvas, y);
        return paintFooter(canvas, bodyBottom);
    }

    /** 页头：左侧标题 + 右侧时间戳，标题下方一条渐隐短线作为视觉锚点。 */
    protected int paintHeader(Canvas canvas) {
        int x = Theme.MARGIN;
        // 品牌名由用户配置，可能很长；右侧还要放时间戳，这里做宽度保护避免重叠
        int maxTitleWidth = Theme.WIDTH - Theme.MARGIN * 2 - 190;
        Font titleFont = Theme.font(24, true);
        String heading = Theme.ellipsis(title(), canvas.fm(titleFont), maxTitleWidth);
        canvas.text(heading, titleFont, Theme.INK, x, 46);

        String sub = subtitle();
        if (sub != null && !sub.isEmpty()) {
            canvas.textRight(sub, Theme.num(14, false), new Color(100, 116, 139, 190),
                    Theme.WIDTH - Theme.MARGIN, 44);
        }

        Graphics2D g = canvas.raw();
        int lineY = 60;
        g.setPaint(new GradientPaint(x, lineY, Theme.AMBER, x + 46, lineY,
                new Color(255, 114, 50, 0)));
        g.fillRect(x, lineY, 46, 2);

        return HEADER_HEIGHT;
    }

    /** 内容区起始 Y。 */
    protected static final int HEADER_HEIGHT = 82;

    /** 页脚占用高度（含上方留白）。 */
    protected static final int FOOTER_HEIGHT = 58;

    /** 页脚：左侧说明，右侧署名（署名为空时不绘制）。 */
    protected int paintFooter(Canvas canvas, int top) {
        int baseline = top + 40;
        canvas.text(footerNote(), Theme.font(12, false), new Color(100, 116, 139, 170),
                Theme.MARGIN, baseline);
        String mark = watermark();
        if (mark != null && !mark.isBlank()) {
            canvas.textRight(mark, Theme.num(13, true), new Color(100, 116, 139, 150),
                    Theme.WIDTH - Theme.MARGIN, baseline);
        }
        return baseline + 18;
    }

    /**
     * 深色分组条：左侧橙色色标 + 标题，右侧附注。
     *
     * @return 分组条底部 Y
     */
    protected int sectionBar(Canvas canvas, int x, int y, int width, String title, String note) {
        Graphics2D g = canvas.raw();
        g.setPaint(Theme.barPaint(x, x + width));
        g.fill(Canvas.roundRect(x, y, width, BAR_HEIGHT, 10));

        g.setColor(Theme.AMBER);
        g.fill(Canvas.roundRect(x + 14, y + 11, 4, BAR_HEIGHT - 22, 2));

        canvas.text(title, Theme.font(15, true), Color.WHITE, x + 28, y + 26);
        if (note != null && !note.isEmpty()) {
            canvas.textRight(note, Theme.font(12, false), new Color(255, 255, 255, 150),
                    x + width - 16, y + 26);
        }
        return y + BAR_HEIGHT;
    }

    /** 分组条高度。 */
    protected static final int BAR_HEIGHT = 40;

    /** 通用“标签 + 大数值”单元，用于统计栅格。 */
    protected void statCell(Canvas canvas, float x, float y, String label, String value,
                            Color valueColor, boolean accent) {
        canvas.text(label, Theme.font(13, false), new Color(100, 116, 139, 220), x, y);
        Font valueFont = Theme.num(26, true);
        canvas.text(value, valueFont, valueColor, x, y + 32);
        if (accent) {
            Graphics2D g = canvas.raw();
            float w = Math.max(1, Math.min(canvas.width(value, valueFont), 90));
            g.setPaint(new GradientPaint(x, 0, Theme.alpha(Theme.ACCENT, 150),
                    x + w, 0, new Color(56, 189, 248, 0)));
            g.fill(Canvas.roundRect(x, y + 38, w, 2, 1));
        }
    }

    /** 文本型键值对（左侧标签、右侧值，中间点线引导）。 */
    protected void keyValue(Canvas canvas, float x, float y, float width,
                            String label, String value, Color valueColor) {
        Font labelFont = Theme.font(12, false);
        Font valueFont = Theme.num(14, true);
        canvas.text(label, labelFont, new Color(100, 116, 139, 200), x, y);
        float labelWidth = canvas.width(label, labelFont);
        float valueWidth = canvas.width(value, valueFont);
        float start = x + labelWidth + 12;
        float end = x + width - valueWidth - 12;

        if (end > start) {
            Graphics2D g = canvas.raw();
            g.setColor(new Color(148, 163, 184, 70));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[]{2f, 4f}, 0f));
            g.draw(new Line2D.Float(start, y - 4, end, y - 4));
            g.setStroke(new BasicStroke(1f));
        }
        canvas.textRight(value, valueFont, valueColor, x + width, y);
    }

    /** 均分列坐标。 */
    protected static float[] columns(float start, float total, float[] ratios) {
        float[] out = new float[ratios.length];
        float cursor = start;
        for (int i = 0; i < ratios.length; i++) {
            out[i] = cursor;
            cursor += total * ratios[i];
        }
        return out;
    }
}
