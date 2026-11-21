package top.szzz666.KokonaCelestia.render;

import top.szzz666.KokonaCelestia.api.RolesClient;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 角色数据总览（对应 dakanu.com/roles）。
 * <p>
 * 一张图分两部分：上半是角色胜率与登场率排行，下半是各地图胜率。
 * 站点还有「地图阵容」，信息量太大，单独放在 {@link RolesLineupCard}。
 */
public final class RolesCard extends CardRenderer {

    private static final int ROLE_ROW_HEIGHT = 32;
    private static final int MAP_HEADER_HEIGHT = 40;
    private static final int MAP_LINEUP_HEIGHT = 62;
    private static final int LINEUP_ROW = 30;
    private static final int MAP_ROW_HEIGHT = 34;
    private static final int MAP_BAR_WIDTH = 150;

    private final RolesClient.Snapshot data;

    public RolesCard(RolesClient.Snapshot data) {
        this.data = data;
    }

    @Override
    protected String subtitleTitle() {
        return "角色数据";
    }

    @Override
    protected String footerNote() {
        return "数据来自 dakanu.com 本地统计（约 "
                + String.format(Locale.ROOT, "%,d", data.coveredMatches())
                + " 场排位），仅保留样本量 1000 场以上的角色。";
    }

    @Override
    protected int layoutHeight() {
        int y = HEADER_HEIGHT;
        y += 14 + 90 + 14;
        y += BAR_HEIGHT + 12 + data.roles().size() * ROLE_ROW_HEIGHT + 20;
        y += 16;
        y += BAR_HEIGHT + 12 + data.maps().size() * (MAP_HEADER_HEIGHT + MAP_LINEUP_HEIGHT) + 16;
        y += FOOTER_HEIGHT;
        return y;
    }

    @Override
    protected int drawBody(Canvas canvas, int top) {
        int x = Theme.MARGIN;
        int w = Theme.WIDTH - Theme.MARGIN * 2;
        int y = top + 14;

        y = summary(canvas, x, y, w);
        y += 14;
        y = roleTable(canvas, x, y, w);
        y += 16;
        y = mapTable(canvas, x, y, w);
        return y;
    }

    /** 顶部概览：统计范围 + 数据日期。 */
    private int summary(Canvas canvas, int x, int y, int w) {
        int h = 90;
        Graphics2D g = canvas.raw();
        Shape shape = Canvas.roundRect(x, y, w, h, 14);
        g.setColor(new Color(255, 255, 255, 200));
        g.fill(shape);
        g.setPaint(new GradientPaint(x + w - 260, y, new Color(2, 132, 199, 0),
                x + w, y + h, new Color(2, 132, 199, 26)));
        g.fill(shape);
        g.setColor(Theme.CARD_EDGE);
        g.setStroke(new BasicStroke(1.1f));
        g.draw(shape);

        canvas.text("统计对局", Theme.font(11, false), new Color(100, 116, 139, 210), x + 24, y + 30);
        canvas.text(String.format(Locale.ROOT, "%,d", data.coveredMatches()),
                Theme.num(24, true), Theme.ACCENT, x + 24, y + 62);

        canvas.text("角色", Theme.font(11, false), new Color(100, 116, 139, 210), x + 190, y + 30);
        canvas.text(String.valueOf(data.roles().size()), Theme.num(24, true),
                Theme.INK, x + 190, y + 62);

        canvas.text("地图", Theme.font(11, false), new Color(100, 116, 139, 210), x + 280, y + 30);
        canvas.text(String.valueOf(data.maps().size()), Theme.num(24, true),
                Theme.INK, x + 280, y + 62);

        canvas.textRight("数据日期", Theme.font(11, false),
                new Color(100, 116, 139, 210), x + w - 24, y + 30);
        canvas.textRight(data.day().isBlank() ? "—" : data.day(),
                Theme.num(14, true), new Color(71, 85, 105), x + w - 24, y + 58);
        return y + h;
    }

    /** 角色胜率表。 */
    private int roleTable(Canvas canvas, int x, int y, int w) {
        List<RolesClient.RoleStat> roles = data.roles();
        int cardH = roles.size() * ROLE_ROW_HEIGHT + 20;
        int innerTop = sectionBar(canvas, x, y, w, "角色胜率排行",
                "按胜率排序 · 共 " + roles.size() + " 个") + 12;
        canvas.glassCard(x, innerTop, w, cardH, 12);

        String[] heads = {"", "角色", "场次", "胜率", "登场率", "较昨日"};
        float[] ratios = {0.06f, 0.26f, 0.16f, 0.16f, 0.18f, 0.18f};
        float[] colX = columns(x + 16, w - 32, ratios);

        float headY = innerTop + 18;
        for (int i = 0; i < heads.length; i++) {
            canvas.text(heads[i], Theme.font(11, false),
                    new Color(100, 116, 139, 215), colX[i], headY);
        }
        canvas.divider(x + 16, headY + 7, w - 32, Theme.LINE);

        float rowY = headY + 24;
        for (int i = 0; i < roles.size(); i++) {
            RolesClient.RoleStat r = roles.get(i);
            if (i % 2 == 1) {
                canvas.fillRound(x + 10, rowY - 15, w - 20, ROLE_ROW_HEIGHT - 4, 7, Theme.ROW_ALT);
            }
            float baseY = rowY;

            // 名次
            canvas.text(String.valueOf(i + 1), Theme.num(11, true),
                    i < 3 ? Theme.AMBER : new Color(148, 163, 184), colX[0], baseY);

            // 头像 + 名字
            BufferedImage portrait = Assets.rolePortrait(r.roleId());
            canvas.avatar(portrait, colX[1] - 2, rowY - 14, 22, 6);
            canvas.text(Theme.ellipsis(r.name(), canvas.fm(Theme.font(12, true)), 90),
                    Theme.font(12, true), Theme.INK, colX[1] + 26, baseY);

            canvas.text(String.format(Locale.ROOT, "%,d", r.games()),
                    Theme.num(12, false), new Color(100, 116, 139, 230), colX[2], baseY);
            canvas.text(percent(r.winRate()), Theme.num(12, true), Theme.ACCENT, colX[3], baseY);
            canvas.text(percent(r.appearanceRate()), Theme.num(12, false),
                    new Color(71, 85, 105), colX[4], baseY);

            // 日环比
            double delta = r.winRateDelta();
            String arrow = delta > 0.001 ? "▲" : (delta < -0.001 ? "▼" : "—");
            Color deltaColor = delta > 0.001 ? Theme.WIN : (delta < -0.001 ? Theme.LOSE : Theme.FAINT);
            String text = Math.abs(delta) < 0.001 ? "—"
                    : arrow + String.format(Locale.ROOT, "%.1f%%", Math.abs(delta) * 100);
            canvas.text(text, Theme.num(11, true), deltaColor, colX[5], baseY);

            rowY += ROLE_ROW_HEIGHT;
        }
        return innerTop + cardH;
    }

    /**
     * 地图阵容表。
     * <p>
     * 站点给的 {@code winRate} 实为「防守方获胜率」（各图都在 69% 左右，
     * 因为爆破模式防守天然占优），单独看没有区分度。
     * 真正有价值的是 {@code sides} 里的攻防阵容与角色胜率，因此只展示这部分。
     */
    private int mapTable(Canvas canvas, int x, int y, int w) {
        List<RolesClient.MapStat> maps = data.maps();
        int blockHeight = MAP_HEADER_HEIGHT + MAP_LINEUP_HEIGHT;
        int cardH = maps.size() * blockHeight + 16;
        int innerTop = sectionBar(canvas, x, y, w, "地图阵容",
                "攻防双方最常选的角色 · 按对局量排序") + 12;
        canvas.glassCard(x, innerTop, w, cardH, 12);

        float rowY = innerTop + 14;
        for (int i = 0; i < maps.size(); i++) {
            RolesClient.MapStat m = maps.get(i);
            if (i > 0) {
                canvas.divider(x + 16, rowY - 6, w - 32, Theme.LINE);
            }

            // 地图名 + 缩略图 + 对局量
            BufferedImage thumb = Assets.mapImage(m.mapId());
            canvas.avatar(thumb, x + 20, rowY, 26, 6);
            canvas.text(m.name(), Theme.font(13, true), Theme.INK, x + 54, rowY + 17);
            canvas.textRight(String.format(Locale.ROOT, "%,d 场", m.games()),
                    Theme.num(10, false), new Color(100, 116, 139, 200), x + w - 20, rowY + 17);

            // 攻防阵容
            lineup(canvas, x + 20, rowY + 32, w - 40, "进攻", m.attack());
            lineup(canvas, x + 20, rowY + 32 + LINEUP_ROW, w - 40, "防守", m.defense());

            rowY += blockHeight;
        }
        return innerTop + cardH;
    }

    /**
     * 一行阵容：标签 + 前 5 个角色头像 + 名称与占比。
     * <p>
     * 早先把名称叠在头像右侧、又把百分比画在下方，导致文字互相重叠；
     * 这里改为「头像靠左横排、名称与占比统一排在右侧」，
     * 每个头像下方只留极小间距，文字不再与图标混在一起。
     */
    private void lineup(Canvas canvas, float x, float y, float w, String label,
                        RolesClient.Lineup lineup) {
        if (lineup == null || lineup.roles().isEmpty()) {
            return;
        }
        Color sideColor = "进攻".equals(label) ? new Color(225, 29, 72) : new Color(2, 132, 199);

        // 左侧标签
        canvas.chip(label, Theme.font(10, true), sideColor,
                Theme.alpha(sideColor, 24), Theme.alpha(sideColor, 70), x, y, 8, 20);

        // 中间：5 个角色头像横排（间距 32，不重叠）
        float cx = x + 48;
        for (int i = 0; i < Math.min(5, lineup.roles().size()); i++) {
            RolesClient.RoleStat r = lineup.roles().get(i);
            canvas.avatar(Assets.rolePortrait(r.roleId()), cx, y - 1, 24, 7);
            cx += 30;
        }

        // 右侧：最常选角色的名称与登场率（与左标签同一基线，不重叠）
        RolesClient.RoleStat top = lineup.roles().get(0);
        float rightX = x + w;
        canvas.textRight(String.format(Locale.ROOT, "%.0f%%", top.appearanceRate() * 100),
                Theme.num(11, true), sideColor, rightX, y + 15);
        canvas.textRight(top.name(), Theme.font(10, false),
                new Color(71, 85, 105), rightX - 40, y + 15);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100);
    }
}
