package top.szzz666.KokonaCelestia.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.szzz666.KokonaCelestia.api.DakanuClient;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** “排行”排行榜出图。 */
public final class RankCard extends CardRenderer {

    private static final DateTimeFormatter SEASON_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JsonObject ranks;
    private final JsonObject season;
    private final int page;
    private final int pageSize;

    public RankCard(JsonObject ranks, JsonObject season, int page, int pageSize) {
        this.ranks = ranks;
        this.season = season;
        this.page = page;
        this.pageSize = pageSize;
    }

    @Override
    protected String subtitleTitle() {
        return "排行榜";
    }

    private static final int BANNER_HEIGHT = 132;
    private static final int SECTION_GAP = 16;
    private static final int TABLE_HEADER_HEIGHT = 34;
    private static final int TABLE_ROW_HEIGHT = 56;
    private static final int TABLE_FOOT_HEIGHT = 40;

    @Override
    protected void prepare() {
        java.util.List<Integer> avatarIds = new java.util.ArrayList<>();
        java.util.List<Integer> roleIds = new java.util.ArrayList<>();
        java.util.List<Integer> segments = new java.util.ArrayList<>();
        for (JsonObject entry : entries()) {
            avatarIds.add(DakanuClient.optInt(entry, "avatarId", 0));
            segments.add(DakanuClient.optInt(entry, "segment", 0));
            com.google.gson.JsonArray roles = DakanuClient.optArray(entry, "roles");
            for (JsonElement role : roles) {
                try {
                    roleIds.add(role.getAsInt());
                } catch (RuntimeException ignored) {
                }
            }
        }
        // 横幅用到的高段位立绘
        roleIds.add(146);

        Assets.prefetchAll(
                Assets.avatarPaths(avatarIds),
                Assets.rolePaths(roleIds),
                Assets.tierPaths(segments));
    }

    @Override
    protected int layoutHeight() {
        int y = HEADER_HEIGHT;
        y += BANNER_HEIGHT + SECTION_GAP;
        y += BAR_HEIGHT + 12
                + TABLE_HEADER_HEIGHT + entries().size() * TABLE_ROW_HEIGHT + TABLE_FOOT_HEIGHT;
        y += FOOTER_HEIGHT;
        return y;
    }

    @Override
    protected int drawBody(Canvas canvas, int top) {
        int x = Theme.MARGIN;
        int w = Theme.WIDTH - Theme.MARGIN * 2;
        int y = top;

        y = seasonBanner(canvas, x, y, w);
        y += SECTION_GAP;
        y = table(canvas, x, y, w);
        return y;
    }

    /** 顶部赛季横条：分数线、赛季起止、榜单总量。 */
    private int seasonBanner(Canvas canvas, int x, int y, int w) {
        int h = BANNER_HEIGHT;
        Graphics2D g = canvas.raw();
        Shape shape = Canvas.roundRect(x, y, w, h, 16);

        g.setColor(new Color(255, 255, 255, 196));
        g.fill(shape);

        // 左侧角色立绘作为极淡底纹
        var art = Assets.rolePortrait(146);
        if (art != null) {
            canvas.clipped(shape, () -> canvas.withAlpha(0.16f, () -> {
                int artH = h + 40;
                int artW = Math.round(art.getWidth() * (artH / (float) art.getHeight()));
                g.drawImage(Theme.scale(art, artW, artH), x + w - artW - 30, y - 20, null);
            }));
            g.setPaint(new GradientPaint(x + w - 300, 0, new Color(255, 255, 255, 240),
                    x + w - 140, 0, new Color(255, 255, 255, 40)));
            canvas.clipped(shape, () -> g.fillRect(x + w - 300, y, 300, h));
        }

        g.setColor(Theme.CARD_EDGE);
        g.setStroke(new BasicStroke(1.1f));
        g.draw(shape);

        // 分数概览：本页榜首 / 本页末位 / 上榜总人数
        List<JsonObject> entries = entries();
        String topScore = "—";
        String bottomScore = "—";
        String metric = DakanuClient.optString(ranks, "metricLabel", "段位分");
        if (!entries.isEmpty()) {
            topScore = String.valueOf(DakanuClient.optInt(entries.get(0), "score", 0));
            bottomScore = String.valueOf(
                    DakanuClient.optInt(entries.get(entries.size() - 1), "score", 0));
        }

        String typeLabel = DakanuClient.optString(
                DakanuClient.optObject(ranks, "rankType"), "label", "段位榜");
        int total = DakanuClient.optInt(ranks, "total", 0);

        bannerStat(canvas, x + 26, y + 32, "本页最高" + metric, topScore);
        bannerStat(canvas, x + 26 + 180, y + 32, "本页最低" + metric, bottomScore);
        bannerStat(canvas, x + 26 + 360, y + 32, "上榜人数",
                String.format(Locale.ROOT, "%,d", total));

        // 赛季起止
        String startText = "—";
        String endText = "—";
        String countdownText = "";
        if (season != null) {
            JsonObject battlepass = DakanuClient.optObject(season, "season");
            long startsAt = DakanuClient.optLong(battlepass, "starts_at_unix", 0);
            long endsAt = DakanuClient.optLong(battlepass, "ends_at_unix", 0);
            if (startsAt > 0) {
                startText = SEASON_TIME.format(Instant.ofEpochSecond(startsAt).atZone(ZONE));
            }
            if (endsAt > 0) {
                endText = SEASON_TIME.format(Instant.ofEpochSecond(endsAt).atZone(ZONE));
                countdownText = countdown(endsAt - System.currentTimeMillis() / 1000);
            }
        }

        float metaY = y + h - 36;
        canvas.text("赛季开始", Theme.font(11, false), new Color(100, 116, 139, 210), x + 26, metaY);
        canvas.text(startText, Theme.num(13, true), Theme.INK, x + 26, metaY + 18);
        canvas.text("赛季结束", Theme.font(11, false), new Color(100, 116, 139, 210),
                x + 26 + 175, metaY);
        canvas.text(endText, Theme.num(13, true), Theme.INK, x + 26 + 175, metaY + 18);

        if (!countdownText.isEmpty()) {
            canvas.text("剩余 " + countdownText, Theme.font(13, true), Theme.AMBER,
                    x + 26 + 370, metaY + 18);
        }

        // 右侧：赛季名与榜单类型
        String seasonLabel = "";
        if (season != null) {
            seasonLabel = DakanuClient.optString(
                    DakanuClient.optObject(season, "ranked_season"), "label", "");
        }
        canvas.textRight("当前赛季", Theme.font(11, false), new Color(100, 116, 139, 210),
                x + w - 26, y + 32);
        canvas.textRight(seasonLabel.isBlank() ? "—" : seasonLabel,
                Theme.num(23, true), Theme.ACCENT, x + w - 26, y + 60);
        canvas.textRight(typeLabel + " · " + metric, Theme.font(12, false),
                new Color(100, 116, 139, 210), x + w - 26, y + 88);

        String day = DakanuClient.optString(ranks, "day", "");
        canvas.textRight("数据日期 " + (day.isBlank() ? "—" : day), Theme.num(11, false),
                new Color(100, 116, 139, 180), x + w - 26, y + 110);

        return y + h;
    }

    private void bannerStat(Canvas canvas, float x, float y, String label, String value) {
        canvas.text(label, Theme.font(11, false), new Color(100, 116, 139, 210), x, y);
        canvas.text(value, Theme.num(23, true), Theme.ACCENT, x, y + 28);
    }

    /** 排行榜表格。 */
    private int table(Canvas canvas, int x, int y, int w) {
        List<JsonObject> entries = entries();
        int total = DakanuClient.optInt(ranks, "total", 0);
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        int start = DakanuClient.optInt(ranks, "start", 1);
        int end = DakanuClient.optInt(ranks, "end", entries.size());
        String note = "第 " + page + " / " + totalPages + " 页 · " + start + "-" + end
                + " 名 · 共 " + total + " 名引航者";

        int innerTop = sectionBar(canvas, x, y, w, "排行榜", note) + 12;

        int headerH = TABLE_HEADER_HEIGHT;
        int rowH = TABLE_ROW_HEIGHT;
        int footH = TABLE_FOOT_HEIGHT;
        int cardH = headerH + entries.size() * rowH + footH;
        canvas.glassCard(x, innerTop, w, cardH, 14);

        String[] heads = {"排名", "引航者", "常用超弦体", "段位", "段位积分", "胜场", "场次", "变化"};
        float[] ratios = {0.07f, 0.28f, 0.17f, 0.19f, 0.11f, 0.07f, 0.06f, 0.05f};
        float[] colX = columns(x + 16, w - 32, ratios);

        float headY = innerTop + 24;
        for (int i = 0; i < heads.length; i++) {
            canvas.text(heads[i], Theme.font(12, false), new Color(100, 116, 139, 215), colX[i], headY);
        }
        canvas.divider(x + 16, headY + 9, w - 32, Theme.LINE);

        float rowY = headY + 30;
        if (entries.isEmpty()) {
            canvas.text("该页暂无数据", Theme.font(13, false), Theme.FAINT, x + 20, rowY + 14);
            rowY += 40;
        }

        for (int i = 0; i < entries.size(); i++) {
            JsonObject entry = entries.get(i);
            if (i % 2 == 1) {
                canvas.fillRound(x + 10, rowY - 16, w - 20, rowH - 4, 8, Theme.ROW_ALT);
            }
            float baseY = rowY + 14;

            int position = DakanuClient.optInt(entry, "position", 0);
            canvas.text(String.valueOf(position), Theme.num(15, true),
                    position <= 3 ? Theme.AMBER : Theme.INK, colX[0], baseY);

            // 头像 + 昵称 + 等级
            canvas.avatar(Assets.avatar(DakanuClient.optInt(entry, "avatarId", 0)),
                    colX[1] - 2, rowY - 10, 38, 9);
            String nickname = Theme.ellipsis(DakanuClient.optString(entry, "nickname", "未知"),
                    canvas.fm(Theme.font(13, true)), 122);
            canvas.text(nickname, Theme.font(13, true), Theme.INK, colX[1] + 46, baseY);
            canvas.text("Lv." + DakanuClient.optInt(entry, "level", 0), Theme.num(10, false),
                    new Color(100, 116, 139, 205), colX[1] + 46, baseY + 16);

            // 常用超弦体
            JsonArray roles = DakanuClient.optArray(entry, "roles");
            for (int r = 0; r < Math.min(3, roles.size()); r++) {
                int roleId = 0;
                try {
                    roleId = roles.get(r).getAsInt();
                } catch (RuntimeException ignored) {
                }
                canvas.avatar(Assets.rolePortrait(roleId), colX[2] + r * 30, rowY - 3, 30, 8);
            }

            // 段位徽章
            int segment = DakanuClient.optInt(entry, "segment", 0);
            var badge = Assets.tierBadge(segment);
            if (badge != null) {
                g(canvas).drawImage(badge, (int) colX[3], (int) rowY - 5, 36, 36, null);
            }
            canvas.text(Theme.tierLabel(segment), Theme.font(12, true), Theme.INK,
                    colX[3] + 42, baseY);

            canvas.text(String.valueOf(DakanuClient.optInt(entry, "score", 0)),
                    Theme.num(14, true), Theme.ACCENT, colX[4], baseY);
            canvas.text(String.valueOf(DakanuClient.optInt(entry, "wins", 0)),
                    Theme.num(13, false), Theme.INK, colX[5], baseY);

            String games = entry.has("totalGames") && !entry.get("totalGames").isJsonNull()
                    ? String.valueOf(DakanuClient.optInt(entry, "totalGames", 0)) : "—";
            canvas.text(games, Theme.num(13, false), new Color(100, 116, 139, 230), colX[6], baseY);

            int delta = DakanuClient.optInt(entry, "scoreDelta", 0);
            String deltaText = delta > 0 ? "+" + delta : (delta < 0 ? String.valueOf(delta) : "—");
            Color deltaColor = delta > 0 ? Theme.WIN : (delta < 0 ? Theme.LOSE : Theme.FAINT);
            canvas.text(deltaText, Theme.num(12, true), deltaColor, colX[7], baseY);

            rowY += rowH;
        }

        canvas.textCenter("第 " + page + " / " + totalPages + " 页 · 每小时整点更新",
                Theme.font(11, false), new Color(100, 116, 139, 200), x + w / 2f,
                innerTop + cardH - 14);

        return innerTop + cardH;
    }

    private static Graphics2D g(Canvas canvas) {
        return canvas.raw();
    }

    private List<JsonObject> entries() {
        List<JsonObject> list = new ArrayList<>();
        for (JsonElement element : DakanuClient.optArray(ranks, "entries")) {
            if (element.isJsonObject()) {
                list.add(element.getAsJsonObject());
            }
        }
        return list;
    }

    private static String countdown(long seconds) {
        if (seconds <= 0) {
            return "已结束";
        }
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        return days > 0 ? days + "天" + hours + "小时" + minutes + "分"
                : hours + "小时" + minutes + "分";
    }
}
