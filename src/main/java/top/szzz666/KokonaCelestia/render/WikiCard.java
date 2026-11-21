package top.szzz666.KokonaCelestia.render;

import top.szzz666.KokonaCelestia.api.WikiClient;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

/**
 * WIKI 通用内容出图：把任意 WIKI 页面渲染成与其他功能统一风格的图片。
 * <p>
 * 用于覆盖站点上的全部页面 —— 需要精细排版的（兑换码、地图、信用分等）由专用卡片负责，
 * 其余页面统一走这里，保证「WIKI 上有的，机器人都能查」。
 */
public final class WikiCard extends CardRenderer {

    /**
     * 单页最多渲染的行数。
     * <p>
     * WIKI 里图鉴类页面（表情包 1431 行、基板 2295 行）远超合理图片高度，
     * 这里取 160 行 —— 约等于一张 4500px 高的图，既能覆盖多数页面，
     * 又不至于生成几十 MB 的 PNG。超出时明确提示剩余条数与原文链接。
     */
    private static final int MAX_LINES = 160;
    /** 单行最大字符数，超出截断。 */
    private static final int MAX_CHARS = 34;
    /** 自动折行的行高。 */
    private static final int LINE_HEIGHT = 27;
    /** 段落间距。 */
    private static final int PARAGRAPH_GAP = 10;

    private final String heading;
    private final WikiClient.Article article;
    private final List<Row> rows;
    /** 被省略的行数，用于提示用户查看原文。 */
    private final int omittedLines;

    /** 排版后的行。 */
    private record Row(String text, boolean isHeading, boolean isLink) {
    }

    public WikiCard(String heading, WikiClient.Article article) {
        this.heading = heading;
        this.article = article;
        this.rows = layout(article.lines());
        int total = article.lines() == null ? 0 : article.lines().size();
        this.omittedLines = Math.max(0, total - rows.size());
    }

    @Override
    protected String subtitleTitle() {
        return "WIKI · " + heading;
    }

    @Override
    protected String footerNote() {
        return "内容来自 biligame 卡拉彼丘 WIKI（wiki.biligame.com/klbq），以游戏内为准。";
    }

    @Override
    protected void prepare() {
        // 通用 WIKI 页面不含远程图片，无需预取
    }

    @Override
    protected int layoutHeight() {
        int y = HEADER_HEIGHT;
        y += BODY_TOP_GAP;
        y += rows.size() * LINE_HEIGHT;
        y += BODY_BOTTOM_PADDING;
        y += FOOTER_HEIGHT;
        return y;
    }

    private static final int BODY_TOP_GAP = 14;
    private static final int BODY_BOTTOM_PADDING = 26;

    @Override
    protected int drawBody(Canvas canvas, int top) {
        int x = Theme.MARGIN;
        int w = Theme.WIDTH - Theme.MARGIN * 2;
        int y = top + BODY_TOP_GAP;

        int bodyHeight = rows.size() * LINE_HEIGHT + 24;
        canvas.glassCard(x, y, w, bodyHeight, 14);

        float textX = x + 22;
        float rowY = y + 24;

        for (Row row : rows) {
            if (row.isHeading()) {
                // 小标题：深色小条 + 橙色色标
                Graphics2D g = canvas.raw();
                float barW = Math.min(canvas.width(row.text(), Theme.font(14, true)) + 28, w - 44);
                g.setPaint(Theme.barPaint((int) textX, (int) (textX + barW)));
                g.fill(Canvas.roundRect(textX, rowY - 14, barW, 24, 7));
                g.setColor(Theme.AMBER);
                g.fill(Canvas.roundRect(textX + 9, rowY - 9, 3, 14, 1.5f));
                canvas.text(row.text(), Theme.font(14, true), Color.WHITE, textX + 19, rowY + 3);
            } else {
                canvas.text(row.text(), Theme.font(13, false), Theme.INK, textX, rowY);
            }
            rowY += LINE_HEIGHT;
        }

        // 空页面提示
        if (rows.isEmpty()) {
            canvas.text("该页面暂无可提取的文字内容，可点击下方链接查看原页。",
                    Theme.font(13, false), Theme.FAINT, textX, rowY);
        } else if (omittedLines > 0) {
            // 明确告知被省略的规模，而不是悄悄截断
            canvas.text("… 还有 " + omittedLines + " 行未显示，完整内容见下方原文链接",
                    Theme.font(12, false), Theme.AMBER, textX, rowY - 4);
        }

        // 原文链接
        float linkY = y + bodyHeight - 14;
        canvas.text("原文：" + article.url(), Theme.num(11, false),
                new Color(100, 116, 139, 190), textX, linkY);

        return (int) (y + bodyHeight);
    }

    /**
     * 把原始文本行整理为可绘制的行。
     * <p>
     * 表格行（用竖线分隔的单元格）单独处理：按两列排布，避免整行挤在一起。
     */
    private static List<Row> layout(List<String> lines) {
        List<Row> rows = new ArrayList<>();
        if (lines == null) {
            return rows;
        }
        for (String raw : lines) {
            if (rows.size() >= MAX_LINES) {
                break;
            }
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            // “2026年11月 编辑”这类带编辑标记的行，去掉尾巴
            line = line.replaceAll("\\s*编辑$", "");

            // 表格行：拆出单元格，两列并排展示
            List<String> cells = splitCells(line);
            if (cells.size() >= 2) {
                String joined = String.join("   ", cells);
                List<String> wrapped = wrap(joined, MAX_CHARS + 8);
                for (String part : wrapped) {
                    rows.add(new Row(part, false, true));
                }
                continue;
            }

            boolean heading = looksLikeHeading(line);
            List<String> wrapped = wrap(line, heading ? MAX_CHARS - 4 : MAX_CHARS);
            for (int i = 0; i < wrapped.size(); i++) {
                rows.add(new Row(wrapped.get(i), heading && i == 0, false));
            }
        }
        return rows;
    }

    /** 按竖线拆分表格单元格。 */
    private static List<String> splitCells(String line) {
        List<String> cells = new ArrayList<>();
        for (String cell : line.split("\\s*\\|\\s*")) {
            String trimmed = cell.trim();
            if (!trimmed.isEmpty()) {
                cells.add(trimmed);
            }
        }
        return cells.size() >= 2 ? cells : List.of();
    }

    /**
     * 判断是否像小标题。
     * <p>
     * 判据刻意保守：WIKI 里大量条目本身就很短（地图名、道具名，如「404基地」「88区」），
     * 若把“短”直接当标题，整页会变成一堆黑条。因此只认三类：
     * <ul>
     *   <li>含年月，如「2026年11月」；</li>
     *   <li>含模式/分类等结构性后缀，如「一般爆破/团队乱斗模式」；</li>
     *   <li>较长且以「表 / 说明 / 简介 / 一览」结尾。</li>
     * </ul>
     */
    private static boolean looksLikeHeading(String line) {
        if (line.length() > 24) {
            return false;
        }
        // 键值行（含冒号）是正文数据，不是标题：「预售日期: 2026年1月27日」
        if (line.matches(".*[:：].*")) {
            return false;
        }
        // 日期开头的完整时间短语属于正文，只有「2026年11月」这种纯月份分组才算标题
        if (line.matches(".*\\d{4}年\\d{1,2}月.*")) {
            return line.matches("^\\d{4}年\\d{1,2}月$");
        }
        if (line.matches(".*(模式|简介|说明|一览|列表|评级|分类|图鉴|数据表|规则)$")
                && line.length() >= 4) {
            return true;
        }
        return false;
    }

    /** 按字符数折行（中文按等宽近似处理）。 */
    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        if (text.length() <= width) {
            out.add(text);
            return out;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + width);
            out.add(text.substring(start, end));
            start = end;
        }
        return out;
    }
}
