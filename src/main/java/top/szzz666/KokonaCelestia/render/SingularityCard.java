package top.szzz666.KokonaCelestia.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.szzz666.KokonaCelestia.api.DakanuClient;
import top.szzz666.KokonaCelestia.api.SingularityClient;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 奇点分排行榜出图。
 * <p>
 * 与普通排行榜不同：这里把**第 500 名**（奇点分界线）放在视觉中心高亮，
 * 上方标注「奇点区间」、下方标注「未达奇点」，让用户一眼看出分界在哪。
 */
public final class SingularityCard extends CardRenderer {

    private static final int ROW_HEIGHT = 46;
    /** 分界线上方展示的排名数。 */
    private static final int ABOVE = 4;
    /** 分界线下方展示的排名数。 */
    private static final int BELOW = 5;

    /** 奇点分界线的名次（榜尾）。 */
    private static final int BOUNDARY_POSITION = 500;

    private final SingularityClient.Result result;
    private final List<JsonObject> entries;

    public SingularityCard(SingularityClient.Result result, JsonObject rankData) {
        this.result = result;
        this.entries = new ArrayList<>();
        for (JsonElement element : DakanuClient.optArray(rankData, "entries")) {
            if (element.isJsonObject()) {
                entries.add(element.getAsJsonObject());
            }
        }
    }

    @Override
    protected String subtitleTitle() {
        return "奇点分";
    }

    @Override
    protected String footerNote() {
        return "奇点分 = 第 500 名超弦分（不足 200 按 200 计）。"
                + "进入奇点需同时满足：超弦分达到 200 且排名前 500。数据来自 dakanu.com。";
    }

    @Override
    protected void prepare() {
        List<Integer> avatarIds = new ArrayList<>();
        for (JsonObject entry : window()) {
            avatarIds.add(DakanuClient.optInt(entry, "avatarId", 0));
        }
        Assets.prefetchAvatars(avatarIds);
    }

    /** 取窗口内的条目：第 496-500 名。 */
    private List<JsonObject> window() {
        int from = Math.max(0, entries.size() - (ABOVE + BELOW + 1));
        return entries.subList(from, entries.size());
    }

    @Override
    protected int layoutHeight() {
        int rows = Math.max(1, window().size());
        int y = HEADER_HEIGHT;
        y += 14;
        y += 132;
        y += 14;
        y += BAR_HEIGHT + 12 + rows * ROW_HEIGHT + 20;
        y += FOOTER_HEIGHT;
        return y;
    }

    @Override
    protected int drawBody(Canvas canvas, int top) {
        int x = Theme.MARGIN;
        int w = Theme.WIDTH - Theme.MARGIN * 2;
        int y = top + 14;

        y = headline(canvas, x, y, w);
        y += 14;
        y = board(canvas, x, y, w);
        return y;
    }

    /** 顶部：奇点分大字 + 判定说明。 */
    private int headline(Canvas canvas, int x, int y, int w) {
        int h = 132;
        Graphics2D g = canvas.raw();
        Shape shape = Canvas.roundRect(x, y, w, h, 16);
        g.setColor(new Color(255, 255, 255, 205));
        g.fill(shape);
        g.setPaint(new GradientPaint(x + w - 340, y, new Color(216, 74, 74, 0),
                x + w, y + h, new Color(216, 74, 74, 30)));
        g.fill(shape);
        g.setColor(Theme.CARD_EDGE);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(shape);

        // 左侧：奇点分
        canvas.text("当前奇点分", Theme.font(12, false),
                new Color(100, 116, 139, 220), x + 26, y + 34);
        canvas.text(String.valueOf(result.floor()), Theme.num(52, true),
                new Color(216, 74, 74), x + 26, y + 92);
        canvas.text("分", Theme.font(14, false), new Color(100, 116, 139, 210),
                x + 26 + canvas.width(String.valueOf(result.floor()), Theme.num(52, true)) + 8,
                y + 92);

        // 中间：进入奇点的两个条件
        float midX = x + 205;
        canvas.text("进入奇点的两个条件", Theme.font(11, false),
                new Color(100, 116, 139, 220), midX, y + 30);

        boolean scoreOk = result.floor() >= SingularityClient.MIN_SCORE;
        canvas.text("① 超弦分达到 " + SingularityClient.MIN_SCORE, Theme.font(12, true),
                scoreOk ? Theme.WIN : Theme.LOSE, midX, y + 54);

        canvas.text("② 全服排名前 " + SingularityClient.RANK_LIMIT, Theme.font(12, true),
                Theme.INK, midX, y + 76);
        canvas.text("守门：" + (result.playerName().isBlank() ? "—" : result.playerName()),
                Theme.font(11, false), new Color(100, 116, 139, 210), midX, y + 98);

        // 右侧：第 500 名的实际超弦分
        canvas.textRight("第 500 名", Theme.font(11, false),
                new Color(100, 116, 139, 220), x + w - 26, y + 30);
        String rawText = result.rawScore() > 0 ? result.rawScore() + " 分" : "未进超弦";
        canvas.textRight(rawText, Theme.num(17, true),
                result.rawScore() > 0 ? Theme.ACCENT : new Color(148, 163, 184),
                x + w - 26, y + 58);
        canvas.textRight(result.superstring() ? "无限超弦" : "未达超弦段位",
                Theme.font(11, false), new Color(100, 116, 139, 200), x + w - 26, y + 80);

        return y + h;
    }

    /** 排行榜：第 500 名为分界线，上下着色区分。 */
    private int board(Canvas canvas, int x, int y, int w) {
        List<JsonObject> rows = window();
        int cardH = Math.max(1, rows.size()) * ROW_HEIGHT + 20;
        int innerTop = sectionBar(canvas, x, y, w, "奇点分界线",
                "第 500 名") + 12;
        canvas.glassCard(x, innerTop, w, cardH, 12);

        float rowY = innerTop + 10;
        for (JsonObject entry : rows) {
            int position = DakanuClient.optInt(entry, "position", 0);
            boolean isBoundary = position == BOUNDARY_POSITION;

            // 分界线整行高亮
            if (isBoundary) {
                Graphics2D g = canvas.raw();
                g.setPaint(new GradientPaint(x + 10, rowY,
                        new Color(216, 74, 74, 38), x + w - 10, rowY,
                        new Color(216, 74, 74, 12)));
                g.fill(Canvas.roundRect(x + 10, rowY, w - 20, ROW_HEIGHT - 4, 8));
                g.setColor(new Color(216, 74, 74));
                g.fill(Canvas.roundRect(x + 10, rowY, 4, ROW_HEIGHT - 4, 2));
            }

            drawRow(canvas, entry, x, rowY, w, isBoundary);
            rowY += ROW_HEIGHT;
        }
        return innerTop + cardH;
    }

    private void drawRow(Canvas canvas, JsonObject entry, float x, float rowY,
                         float w, boolean boundary) {
        int position = DakanuClient.optInt(entry, "position", 0);
        float baseY = rowY + 27;

        // 名次
        Color posColor = boundary ? new Color(216, 74, 74) : new Color(148, 163, 184);
        canvas.text(String.valueOf(position), Theme.num(14, boundary), posColor, x + 24, baseY);

        // 头像 + 昵称
        BufferedImage avatar = Assets.avatar(DakanuClient.optInt(entry, "avatarId", 0));
        canvas.avatar(avatar, x + 58, rowY + 8, 30, 8);
        String name = Theme.ellipsis(DakanuClient.optString(entry, "nickname", "未知"),
                canvas.fm(Theme.font(13, boundary)), 150);
        canvas.text(name, Theme.font(13, boundary),
                boundary ? new Color(216, 74, 74) : Theme.INK, x + 96, baseY);

        // 段位
        int segment = DakanuClient.optInt(entry, "segment", 0);
        var badge = Assets.tierBadge(segment);
        if (badge != null) {
            canvas.raw().drawImage(badge, (int) (x + 270), (int) rowY + 7, 30, 30, null);
        }
        canvas.text(Theme.tierLabel(segment), Theme.font(12, false),
                new Color(71, 85, 105), x + 306, baseY);

        // 分数：未进入无限超弦的玩家榜单不给分数，显示「—」避免误解为 0 分
        int score = DakanuClient.optInt(entry, "score", 0);
        String scoreText = score > 0 ? score + " 分" : "—";
        canvas.text(scoreText, Theme.num(14, boundary),
                score > 0 ? (boundary ? new Color(216, 74, 74) : Theme.ACCENT)
                        : new Color(148, 163, 184),
                x + w - 130, baseY);

        // 分界线标注
        if (boundary) {
            canvas.chip("奇点线", Theme.font(10, true), new Color(216, 74, 74),
                    new Color(255, 255, 255, 220), new Color(216, 74, 74, 120),
                    x + w - 72, rowY + 7, 8, 18);
        }
    }
}
