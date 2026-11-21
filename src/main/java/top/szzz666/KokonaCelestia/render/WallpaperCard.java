package top.szzz666.KokonaCelestia.render;

import top.szzz666.KokonaCelestia.api.WikiWallpaper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 壁纸预览网格。
 * <p>
 * 用户发「壁纸」时先看到这一页缩略图，决定要哪一张再按序号取原图。
 * 与图鉴网格的区别：壁纸是 16:9 横图，格子更宽，每行放得少。
 */
public final class WallpaperCard extends CardRenderer {

    private static final int COLUMNS = 3;
    private static final int CELL_WIDTH_RATIO = 1;
    private static final int THUMB_HEIGHT = 108;
    private static final int CELL_HEIGHT = 148;
    private static final int ROW_GAP = 14;
    private static final int COLUMN_GAP = 14;

    private final List<WikiWallpaper.Wallpaper> wallpapers;
    private final int startIndex;
    private final int total;

    /**
     * @param wallpapers 当前页壁纸
     * @param startIndex 当前页第一张的全局序号（从 1 开始）
     * @param total      壁纸总数
     */
    public WallpaperCard(List<WikiWallpaper.Wallpaper> wallpapers, int startIndex, int total) {
        this.wallpapers = wallpapers;
        this.startIndex = startIndex;
        this.total = total;
    }

    @Override
    protected String subtitleTitle() {
        return "壁纸";
    }

    @Override
    protected String footerNote() {
        return "回复序号看原图 · 共 " + total + " 张（本页 " + startIndex + "-"
                + (startIndex + wallpapers.size() - 1) + "）· 回复「下一页」翻页";
    }

    @Override
    protected void prepare() {
        List<String> urls = new ArrayList<>();
        for (WikiWallpaper.Wallpaper w : wallpapers) {
            urls.add(w.thumbUrl());
        }
        Assets.prefetch(urls);
    }

    private int rows() {
        return Math.max(1, (int) Math.ceil(wallpapers.size() / (double) COLUMNS));
    }

    @Override
    protected int layoutHeight() {
        int y = HEADER_HEIGHT;
        y += 14;
        y += 64;
        y += 14;
        y += rows() * (CELL_HEIGHT + ROW_GAP) + 16;
        y += FOOTER_HEIGHT;
        return y;
    }

    @Override
    protected int drawBody(Canvas canvas, int top) {
        int x = Theme.MARGIN;
        int w = Theme.WIDTH - Theme.MARGIN * 2;
        int y = top + 14;

        y = header(canvas, x, y, w);
        y += 14;

        int rowCount = rows();
        int cardH = rowCount * (CELL_HEIGHT + ROW_GAP) + 16;
        canvas.glassCard(x, y, w, cardH, 14);

        float cellW = (w - 32f - COLUMN_GAP * (COLUMNS - 1)) / COLUMNS;
        for (int i = 0; i < wallpapers.size(); i++) {
            WikiWallpaper.Wallpaper wp = wallpapers.get(i);
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            float cx = x + 16 + col * (cellW + COLUMN_GAP);
            float cy = y + 16 + row * (CELL_HEIGHT + ROW_GAP);
            drawCell(canvas, wp, startIndex + i, cx, cy, cellW);
        }

        return y + cardH;
    }

    /** 单张壁纸：横向缩略图 + 序号 + 标题。 */
    private void drawCell(Canvas canvas, WikiWallpaper.Wallpaper wp, int number,
                          float x, float y, float cellW) {
        BufferedImage raw = Assets.remote(wp.thumbUrl());
        // 16:9 横向裁切，填满格子
        canvas.clipped(Canvas.roundRect(x, y, cellW, THUMB_HEIGHT, 10), () -> {
            if (raw != null) {
                BufferedImage cover = cover(raw, (int) cellW, THUMB_HEIGHT);
                canvas.raw().drawImage(cover, (int) x, (int) y, null);
            } else {
                canvas.fillRound(x, y, cellW, THUMB_HEIGHT, 10, new Color(226, 232, 240));
            }
        });
        canvas.strokeRound(x, y, cellW, THUMB_HEIGHT, 10, new Color(255, 255, 255, 180), 1.2f);

        // 序号徽标（壁纸可回复序号取原图，因此需要编号）
        String label = String.valueOf(number);
        float badgeW = Math.max(24, canvas.width(label, Theme.num(11, true)) + 14);
        canvas.fillRound(x + 8, y + THUMB_HEIGHT - 26, badgeW, 18, 9,
                new Color(30, 41, 59, 215));
        canvas.textCenter(label, Theme.num(11, true), Color.WHITE,
                x + 8 + badgeW / 2f, y + THUMB_HEIGHT - 13);

        // 标题
        String title = Theme.ellipsis(wp.title(), canvas.fm(Theme.font(12, false)),
                (int) cellW - 8);
        canvas.text(title, Theme.font(12, false), Theme.INK, x + 2, y + THUMB_HEIGHT + 20);
    }

    /** 等比缩放并居中裁剪，填满目标尺寸。 */
    static BufferedImage cover(BufferedImage source, int width, int height) {
        float ratio = Math.max(width / (float) source.getWidth(), height / (float) source.getHeight());
        int w = Math.max(width, Math.round(source.getWidth() * ratio));
        int h = Math.max(height, Math.round(source.getHeight() * ratio));
        BufferedImage scaled = Theme.scale(source, w, h);
        return scaled.getSubimage((w - width) / 2, (h - height) / 2, width, height);
    }

    private int header(Canvas canvas, int x, int y, int w) {
        int h = 64;
        Graphics2D g = canvas.raw();
        Shape shape = Canvas.roundRect(x, y, w, h, 14);
        g.setColor(new Color(255, 255, 255, 200));
        g.fill(shape);
        g.setPaint(new GradientPaint(x, y, new Color(2, 132, 199, 20),
                x + 240, y + h, new Color(2, 132, 199, 0)));
        g.fill(shape);
        g.setColor(Theme.CARD_EDGE);
        g.setStroke(new BasicStroke(1.1f));
        g.draw(shape);

        canvas.text("官方壁纸", Theme.font(20, true), Theme.INK, x + 22, y + 40);
        canvas.textRight("共 " + total + " 张 · 回复序号看原图",
                Theme.font(12, false), new Color(100, 116, 139, 210), x + w - 22, y + 40);
        return y + h;
    }
}
