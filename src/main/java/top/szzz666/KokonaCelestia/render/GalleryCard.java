package top.szzz666.KokonaCelestia.render;

import top.szzz666.KokonaCelestia.api.WikiGallery;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 图鉴条目网格出图。
 * <p>
 * 与纯文字列表不同，这里直接把缩略图画出来，用户看得到实物样子，
 * 再按序号点开查看大图。这是「真正需要的内容」与「一坨文字」的区别。
 */
public final class GalleryCard extends CardRenderer {

    private static final int COLUMNS = 6;
    private static final int CELL_WIDTH = 148;
    private static final int IMAGE_SIZE = 96;
    private static final int CELL_HEIGHT = 148;
    private static final int ROW_GAP = 12;

    private final String pageTitle;
    private final List<WikiGallery.Entry> entries;
    private final int total;

    /**
     * @param pageTitle 页面名称
     * @param entries   当前页条目
     * @param total     条目总数
     */
    public GalleryCard(String pageTitle, List<WikiGallery.Entry> entries, int total) {
        this.pageTitle = pageTitle;
        this.entries = entries;
        this.total = total;
    }

    @Override
    protected String subtitleTitle() {
        return "图鉴 · " + pageTitle;
    }

    @Override
    protected String footerNote() {
        // 条目本身不可点选（图鉴数量太多，逐个编号没有意义），
        // 分页通过上一层的菜单序号切换，这里只说明当前页范围
        return "共 " + total + " 个条目 · 翻页请回复「返回」后选择其他页码";
    }

    @Override
    protected void prepare() {
        // 并行预取本页缩略图，避免逐张串行下载
        List<String> urls = new ArrayList<>();
        for (WikiGallery.Entry entry : entries) {
            urls.add(entry.imageUrl());
        }
        Assets.prefetch(urls);
    }

    private int rows() {
        return Math.max(1, (int) Math.ceil(entries.size() / (double) COLUMNS));
    }

    @Override
    protected int layoutHeight() {
        int y = HEADER_HEIGHT;
        y += 14;
        y += 66;
        y += 14;
        y += rows() * (CELL_HEIGHT + ROW_GAP) + 20;
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

        // 网格按等分列宽居中
        float cellW = (w - 32f) / COLUMNS;
        int rowCount = rows();
        int cardH = rowCount * (CELL_HEIGHT + ROW_GAP) + 20;
        canvas.glassCard(x, y, w, cardH, 14);

        for (int i = 0; i < entries.size(); i++) {
            WikiGallery.Entry entry = entries.get(i);
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            float cx = x + 16 + col * cellW;
            float cy = y + 16 + row * (CELL_HEIGHT + ROW_GAP);

            drawCell(canvas, entry, cx, cy, cellW);
        }

        return y + cardH;
    }

    /** 单个条目：缩略图 + 名称。 */
    private void drawCell(Canvas canvas, WikiGallery.Entry entry,
                          float x, float y, float cellW) {
        // 页面里给的地址可能是原图也可能是缩略图，直接用它最稳；
        // 下载后按显示尺寸缩小，避免把 636px 大图原样绘制
        BufferedImage raw = Assets.remote(entry.imageUrl());
        BufferedImage image = raw != null && raw.getWidth() > IMAGE_SIZE * 2
                ? Theme.scale(raw, IMAGE_SIZE * 2, IMAGE_SIZE * 2)
                : raw;
        float imageX = x + (cellW - IMAGE_SIZE) / 2f;

        canvas.avatar(image, imageX, y, IMAGE_SIZE, 12);

        // 名称（图鉴条目不可回复点选，因此不标序号，避免误导）
        String name = Theme.ellipsis(entry.name(), canvas.fm(Theme.font(12, false)),
                (int) cellW - 8);
        canvas.textCenter(name, Theme.font(12, false), Theme.INK,
                x + cellW / 2f, y + IMAGE_SIZE + 22);
    }

    /**
     * 把原图地址转成缩略图地址。
     * <p>
     * WIKI（MediaWiki）的缩略图规则是在目录中插入 {@code thumb/}，
     * 再在 hash 文件名后追加 {@code /<宽>px-<原始文件名>}：
     * <pre>
     * /images/klbq/0/0d/&lt;hash&gt;.png
     *   → /images/klbq/thumb/0/0d/&lt;hash&gt;.png/180px-&lt;原始文件名&gt;
     * </pre>
     * 原始文件名取自 img 的 alt 属性，缺失时退回 hash 本身。
     * 条目原图动辄 600px 以上，一页 36 张直接下载会非常慢，必须走缩略图。
     */
    static String thumbUrl(String url, String fileName) {
        if (url == null || url.isBlank() || url.contains("/thumb/")) {
            return url;
        }
        int imagesIdx = url.indexOf("/images/");
        if (imagesIdx < 0) {
            return url;
        }
        String prefix = url.substring(0, imagesIdx);
        String rest = url.substring(imagesIdx + "/images/".length());

        int firstSlash = rest.indexOf('/');
        int lastSlash = rest.lastIndexOf('/');
        if (firstSlash < 0 || lastSlash <= firstSlash) {
            return url;
        }
        String wiki = rest.substring(0, firstSlash + 1);
        String sub = rest.substring(firstSlash + 1, lastSlash + 1);
        String hashName = rest.substring(lastSlash + 1);

        String suffix = fileName == null || fileName.isBlank() ? hashName : fileName;
        // MediaWiki 的缩略图地址要求文件名整体做 URL 编码（中文、空格都要转义）
        suffix = encodePathSegment(suffix);

        return prefix + "/images/" + wiki + "thumb/" + sub + hashName + "/180px-" + suffix;
    }

    /** 按 URL 路径段规则编码：保留 -_.~，其余全部转义。 */
    private static String encodePathSegment(String value) {
        StringBuilder sb = new StringBuilder(value.length() * 3);
        for (byte b : value.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append((char) c);
            } else {
                sb.append('%').append(String.format("%02X", c));
            }
        }
        return sb.toString();
    }

    private int header(Canvas canvas, int x, int y, int w) {
        int h = 66;
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

        canvas.text(pageTitle, Theme.font(20, true), Theme.INK, x + 22, y + 40);
        canvas.textRight("共 " + total + " 个 · 本页 " + entries.size() + " 个",
                Theme.font(12, false), new Color(100, 116, 139, 210), x + w - 22, y + 40);
        return y + h;
    }
}
