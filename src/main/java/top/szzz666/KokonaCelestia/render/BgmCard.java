package top.szzz666.KokonaCelestia.render;

import top.szzz666.KokonaCelestia.api.WikiBgm;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.List;

/** BGM 曲目列表出图：带序号，方便用户按号点播。 */
public final class BgmCard extends CardRenderer {

    private static final int ROW_HEIGHT = 30;
    private static final int ROWS_PER_COLUMN = 34;
    private static final int COLUMN_GAP = 24;

    private final List<WikiBgm.Track> tracks;
    private final int page;
    private final int pageSize;

    public BgmCard(List<WikiBgm.Track> tracks, int page, int pageSize) {
        this.tracks = tracks;
        this.page = page;
        this.pageSize = pageSize;
    }

    @Override
    protected String subtitleTitle() {
        return "BGM · 第 " + page + " 页";
    }

    @Override
    protected String footerNote() {
        return "回复序号即可点播（也可发「BGM 5」）· 共 165 首 · 来源 biligame WIKI";
    }

    private int totalPages() {
        return Math.max(1, (int) Math.ceil(tracks.size() / (double) pageSize));
    }

    /** 当前页包含的曲目。 */
    private List<WikiBgm.Track> current() {
        int from = Math.max(0, (page - 1) * pageSize);
        int to = Math.min(tracks.size(), from + pageSize);
        return tracks.subList(from, to);
    }

    @Override
    protected int layoutHeight() {
        int rows = Math.min(current().size(), ROWS_PER_COLUMN);
        int y = HEADER_HEIGHT;
        y += 14;
        y += 88;
        y += 14;
        y += BAR_HEIGHT + 12 + Math.max(1, rows) * ROW_HEIGHT + 40;
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

        List<WikiBgm.Track> list = current();
        int rows = Math.max(1, Math.min(list.size(), ROWS_PER_COLUMN));
        int cardH = rows * ROW_HEIGHT + 34;
        int innerTop = sectionBar(canvas, x, y, w, "曲目列表",
                "共 " + tracks.size() + " 首 · 第 " + page + " / " + totalPages() + " 页") + 12;
        canvas.glassCard(x, innerTop, w, cardH, 12);

        if (list.isEmpty()) {
            canvas.text("暂无曲目", Theme.font(13, false), Theme.FAINT, x + 22, innerTop + 26);
            return innerTop + cardH;
        }

        // 双列排版，长列表也能一屏放下
        int columns = list.size() > ROWS_PER_COLUMN ? 2 : 1;
        int perColumn = columns == 1 ? list.size() : ROWS_PER_COLUMN;
        float columnWidth = columns == 1 ? w - 44 : (w - 44 - COLUMN_GAP) / 2f;
        int startIndex = (page - 1) * pageSize;

        for (int i = 0; i < list.size(); i++) {
            WikiBgm.Track track = list.get(i);
            int column = i / perColumn;
            int row = i % perColumn;
            float cx = x + 22 + column * (columnWidth + COLUMN_GAP);
            float cy = innerTop + 26 + row * ROW_HEIGHT;

            int number = startIndex + i + 1;
            canvas.text(String.format("%3d", number), Theme.num(12, true),
                    Theme.ACCENT, cx, cy);

            String name = Theme.ellipsis(track.name(),
                    canvas.fm(Theme.font(12, false)), (int) columnWidth - 40);
            canvas.text(name, Theme.font(12, false), Theme.INK, cx + 34, cy);
        }

        canvas.text("发送「BGM 序号」或「BGM 曲名」即可点播",
                Theme.font(11, false), new Color(100, 116, 139, 200),
                x + 22, innerTop + cardH - 12);

        return innerTop + cardH;
    }

    private int header(Canvas canvas, int x, int y, int w) {
        int h = 88;
        Graphics2D g = canvas.raw();
        Shape shape = Canvas.roundRect(x, y, w, h, 14);
        g.setColor(new Color(255, 255, 255, 200));
        g.fill(shape);
        g.setPaint(new GradientPaint(x + w - 240, y, new Color(2, 132, 199, 0),
                x + w, y + h, new Color(2, 132, 199, 28)));
        g.fill(shape);
        g.setColor(Theme.CARD_EDGE);
        g.setStroke(new BasicStroke(1.1f));
        g.draw(shape);

        canvas.text("曲目总数", Theme.font(12, false), new Color(100, 116, 139, 210),
                x + 24, y + 30);
        canvas.text(String.valueOf(tracks.size()), Theme.num(30, true),
                Theme.ACCENT, x + 24, y + 66);

        canvas.text("点播方式", Theme.font(12, false), new Color(100, 116, 139, 210),
                x + 170, y + 30);
        canvas.text("BGM 序号 / BGM 曲名", Theme.font(14, true),
                Theme.INK, x + 170, y + 58);

        canvas.textRight("来源", Theme.font(12, false), new Color(100, 116, 139, 210),
                x + w - 24, y + 30);
        canvas.textRight("biligame WIKI", Theme.font(13, true),
                new Color(71, 85, 105), x + w - 24, y + 56);
        return y + h;
    }
}
