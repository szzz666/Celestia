package top.szzz666.KokonaCelestia.render;

import top.szzz666.KokonaCelestia.model.PlayerProfile;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** “看看”玩家概览出图。 */
public final class PlayerCard extends CardRenderer {

    private static final DateTimeFormatter MATCH_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /** 最近战绩展示条数。 */
    private static final int RECENT_LIMIT = 10;

    private final PlayerProfile profile;

    public PlayerCard(PlayerProfile profile) {
        this.profile = profile;
    }

    @Override
    protected String subtitleTitle() {
        return "玩家概览";
    }

    @Override
    protected String subtitle() {
        String season = profile.meta() == null ? "" : profile.meta().currentSeason();
        String stamp = STAMP.format(Instant.now().atZone(ZONE));

        StringBuilder sb = new StringBuilder();
        if (season != null && !season.isBlank()) {
            sb.append(season).append(" · ");
        }

        // 站点并非实时抓取：实测有玩家数据滞后十几天。
        // 把抓取时间写在这里，避免用户拿过期数据与站点最新页面比对后误以为出错。
        double age = profile.dataAgeHours();
        if (age >= 0) {
            sb.append("数据抓取于 ").append(relativeAge(age));
            if (profile.dataStale()) {
                sb.append("（已过期，非实时）");
            }
        } else {
            sb.append(stamp);
        }
        return sb.toString();
    }

    /** 把小时数说成更好懂的说法。 */
    private static String relativeAge(double hours) {
        if (hours < 1) {
            return "刚刚";
        }
        if (hours < 24) {
            return (int) hours + " 小时前";
        }
        long days = (long) (hours / 24);
        return days + " 天前";
    }

    // ==================== 布局常量（绘制与高度计算共用） ====================

    private static final int HERO_HEIGHT = 276;
    private static final int HERO_GAP = 16;
    private static final int PANEL_GAP = 16;
    private static final int LEFT_PANEL_WIDTH = 320;
    private static final int SECTION_GAP = 18;

    private static final int RANK_CARD_HEIGHT = 116;
    private static final int RANK_LIST_ROW = 30;
    private static final int RANK_LIST_TOP_GAP = 30;

    private static final int MODE_ROW_HEIGHT = 34;
    private static final int MODE_HEADER_HEIGHT = 32;
    private static final int MODE_MAX_ROWS = 4;
    private static final int ROLE_SECTION_HEIGHT_PER_ROW = 52;
    private static final int ROLE_SECTION_PADDING = 44;
    private static final int ROLE_MAX_ROWS = 4;

    private static final int MATCH_CARD_HEIGHT = 116;
    private static final int MATCH_ROW_GAP = 12;

    @Override
    protected void prepare() {
        // 收集本页所有图片，一次性并发预取
        java.util.List<Integer> avatarIds = new java.util.ArrayList<>();
        avatarIds.add(profile.avatarId());

        java.util.List<Integer> roleIds = new java.util.ArrayList<>();
        for (PlayerProfile.Role role : profile.roles()) {
            roleIds.add(role.roleId());
        }
        for (PlayerProfile.Match match : profile.recentMatches()) {
            roleIds.add(match.roleId());
        }

        java.util.List<Integer> mapIds = new java.util.ArrayList<>();
        for (PlayerProfile.Match match : profile.recentMatches()) {
            mapIds.add(match.mapId());
        }

        java.util.List<Integer> segments = new java.util.ArrayList<>();
        segments.add(profile.rank().segment());
        segments.add(profile.rank().historicalSegment());

        // 四类资源一次性全部发起，统一等待，总耗时约等于最慢的一类
        Assets.prefetchAll(
                Assets.avatarPaths(avatarIds),
                Assets.rolePaths(roleIds),
                Assets.mapPaths(mapIds),
                Assets.tierPaths(segments));
    }

    @Override
    protected int layoutHeight() {
        int y = HEADER_HEIGHT;

        y += HERO_HEIGHT;
        y += HERO_GAP;

        y += Math.max(rankPanelHeight(), modePanelHeight());
        y += SECTION_GAP;

        y += recentSectionHeight();
        y += FOOTER_HEIGHT;
        return y;
    }

    /** 左栏高度：段位卡片 + 键值行 + 可选曾用名/隐私提示。 */
    private int rankPanelHeight() {
        int height = BAR_HEIGHT + 12;
        height += RANK_CARD_HEIGHT;
        height += RANK_LIST_TOP_GAP;
        height += RANK_LIST_ROW * 5;
        if (!profile.previousNames().isEmpty()) {
            height += 34;
        }
        if (profile.privacyHidden()) {
            height += 20;
        }
        return height + 6;
    }

    /** 右栏高度：模式表 + 常用角色网格。 */
    private int modePanelHeight() {
        int rows = Math.min(profile.modes().size(), MODE_MAX_ROWS);
        int height = BAR_HEIGHT + 12;
        height += MODE_HEADER_HEIGHT + Math.max(1, rows) * MODE_ROW_HEIGHT + 12;

        int roleRows = Math.min(profile.roles().size(), ROLE_MAX_ROWS);
        if (roleRows > 0) {
            height += 12;
            height += ROLE_SECTION_PADDING
                    + (int) Math.ceil(roleRows / 2.0) * ROLE_SECTION_HEIGHT_PER_ROW;
        }
        return height;
    }

    /** 最近战绩区块高度。 */
    private int recentSectionHeight() {
        int total = Math.min(profile.recentMatches().size(), RECENT_LIMIT);
        int height = BAR_HEIGHT + 12;
        if (total == 0) {
            return height + 64;
        }
        int rows = (total + 1) / 2;
        height += rows * MATCH_CARD_HEIGHT + (rows - 1) * MATCH_ROW_GAP;
        return height;
    }

    @Override
    protected int drawBody(Canvas canvas, int top) {
        int x = Theme.MARGIN;
        int w = Theme.WIDTH - Theme.MARGIN * 2;
        int y = top;

        y = hero(canvas, x, y, w);
        y += HERO_GAP;

        int leftW = LEFT_PANEL_WIDTH;
        int rightW = w - leftW - PANEL_GAP;
        int leftBottom = rankPanel(canvas, x, y, leftW);
        int rightBottom = modePanel(canvas, x + leftW + PANEL_GAP, y, rightW);
        y = Math.max(leftBottom, rightBottom) + SECTION_GAP;

        y = recentMatches(canvas, x, y, w);
        return y;
    }

    // ==================== 头部：立绘 + 身份 + 关键数据 ====================

    private int hero(Canvas canvas, int x, int y, int w) {
        int h = HERO_HEIGHT;
        Graphics2D g = canvas.raw();
        Shape shape = Canvas.roundRect(x, y, w, h, 16);

        g.setColor(new Color(255, 255, 255, 208));
        g.fill(shape);
        g.setPaint(new GradientPaint(x, y, new Color(255, 255, 255, 120),
                x, y + h, new Color(255, 255, 255, 40)));
        g.fill(shape);

        // 右侧立绘（取最常用角色），左侧渐隐融入卡片
        BufferedImage art = null;
        List<PlayerProfile.Role> roles = profile.roles();
        if (!roles.isEmpty()) {
            art = Assets.rolePortrait(roles.get(0).roleId());
        }
        if (art != null) {
            BufferedImage heroArt = art;
            int heroH = h;
            canvas.clipped(shape, () -> {
                BufferedImage scaled = fitHeight(heroArt, heroH);
                g.drawImage(scaled, x + w - scaled.getWidth() - 10, y, null);
            });
            g.setPaint(new GradientPaint(x + w - 360, 0, new Color(255, 255, 255, 255),
                    x + w - 150, 0, new Color(255, 255, 255, 0)));
            canvas.clipped(shape, () -> g.fillRect(x + w - 360, y, 360, h));
        }

        g.setColor(Theme.CARD_EDGE);
        g.setStroke(new BasicStroke(1.1f));
        g.draw(shape);

        // 头像
        int avatarSize = 82;
        BufferedImage avatar = Assets.avatar(profile.avatarId());
        canvas.avatar(avatar, x + 24, y + 24, avatarSize, 14);

        float textX = x + 24 + avatarSize + 20;
        canvas.text("引航者 · 战绩档案", Theme.font(12, false), new Color(100, 116, 139, 210),
                textX, y + 38);

        Font nameFont = Theme.font(30, true);
        canvas.text(Theme.ellipsis(profile.nickname(), canvas.fm(nameFont), 260),
                nameFont, Theme.INK, textX, y + 76);

        // ID / 等级 / 在线
        float metaY = y + 102;
        Font idFont = Theme.num(13, false);
        String idText = "ID " + profile.playerId();
        canvas.text(idText, idFont, new Color(71, 85, 105), textX, metaY);
        float cursor = textX + canvas.width(idText, idFont) + 16;
        String lvText = "LV. " + profile.level();
        Font lvFont = Theme.num(13, true);
        canvas.text(lvText, lvFont, new Color(71, 85, 105), cursor, metaY);
        cursor += canvas.width(lvText, lvFont) + 14;

        String presence = Theme.presenceLabel(profile.presence());
        Color presenceColor = "online".equals(profile.presence()) ? Theme.WIN : Theme.FAINT;
        Font presenceFont = Theme.font(11, true);
        canvas.chip(presence, presenceFont, presenceColor,
                Theme.alpha(presenceColor, 24), Theme.alpha(presenceColor, 72),
                cursor, metaY - 13, 10, 20);

        // 隐私标记：紧跟在线状态，与「在线 / 离线」同一视觉层级。
        // 隐私玩家的数据是残缺的（对局数可能只有 1 场、胜率 0%），
        // 若不显眼标注，用户会误判成真实战绩。
        if (profile.privacyHidden()) {
            cursor += canvas.chipWidth(presence, presenceFont, 10) + 8;
            Color privacyColor = new Color(113, 113, 122);
            canvas.chip("隐私保护", Theme.font(11, true), privacyColor,
                    Theme.alpha(privacyColor, 28), Theme.alpha(privacyColor, 90),
                    cursor, metaY - 13, 10, 20);
        }

        // 统计栅格：两行四列
        PlayerProfile.Summary s = profile.summary();
        float cellW = 138;
        float row1 = y + 148;
        float row2 = y + 212;
        statCell(canvas, textX, row1, "对局数", String.valueOf(s.rounds()), Theme.INK, false);
        statCell(canvas, textX + cellW, row1, "胜率", percent(s.winRate()), Theme.ACCENT, true);
        statCell(canvas, textX + cellW * 2, row1, "胜场", String.valueOf(s.wins()), Theme.INK, false);
        statCell(canvas, textX + cellW * 3, row1, "MVP 数", String.valueOf(s.mvpCount()), Theme.INK, false);

        statCell(canvas, textX, row2, "场均击杀", number(s.avgKill()), Theme.ACCENT, true);
        statCell(canvas, textX + cellW, row2, "回合均伤", number(s.roundAveDamage()), Theme.ACCENT, true);
        statCell(canvas, textX + cellW * 2, row2, "K/D", number(s.kd()), Theme.ACCENT, true);
        statCell(canvas, textX + cellW * 3, row2, "爆头率", percent(s.headshotRate()), Theme.ACCENT, true);

        return y + h;
    }

    // ==================== 段位与资料 ====================

    private int rankPanel(Canvas canvas, int x, int y, int w) {
        int innerTop = sectionBar(canvas, x, y, w, "段位与资料", "RANK FILE") + 12;

        int cardH = 116;
        canvas.glassCard(x, innerTop, w, cardH, 12);

        PlayerProfile.Rank rank = profile.rank();
        BufferedImage badge = Assets.tierBadge(rank.segment());
        float badgeSize = 60;
        if (badge != null) {
            canvas.raw().drawImage(badge, (int) (x + 20), (int) (innerTop + 28),
                    (int) badgeSize, (int) badgeSize, null);
        } else {
            canvas.fillRound(x + 20, innerTop + 28, badgeSize, badgeSize, 10,
                    Theme.alpha(Theme.ACCENT, 30));
        }

        float tx = x + 20 + badgeSize + 16;
        canvas.text("排位段位", Theme.font(12, false), new Color(100, 116, 139, 210), tx, innerTop + 34);
        canvas.text(Theme.tierLabel(rank.segment()), Theme.font(19, true), Theme.INK, tx, innerTop + 60);

        Font scoreFont = Theme.num(22, true);
        String scoreText = String.valueOf(rank.gradeScore());
        canvas.text(scoreText, scoreFont, Theme.ACCENT, tx, innerTop + 92);
        canvas.text("段位积分", Theme.font(11, false), new Color(100, 116, 139, 200),
                tx + canvas.width(scoreText, scoreFont) + 8, innerTop + 92);

        float ky = innerTop + cardH + 30;
        int lineH = 30;
        keyValue(canvas, x + 16, ky, w - 32, "历史最高段位",
                Theme.tierLabel(rank.historicalSegment()), Theme.INK);
        ky += lineH;
        keyValue(canvas, x + 16, ky, w - 32, "最高段位赛季",
                rank.historicalSeason() > 0 ? "S" + rank.historicalSeason() : "—", Theme.INK);
        ky += lineH;
        keyValue(canvas, x + 16, ky, w - 32, "全服排名",
                rank.rankPos() > 0 ? "第 " + rank.rankPos() + " 名" : "—", Theme.INK);
        ky += lineH;
        keyValue(canvas, x + 16, ky, w - 32, "获赞", String.valueOf(profile.likeCount()), Theme.INK);
        ky += lineH;
        keyValue(canvas, x + 16, ky, w - 32, "常用角色", profile.roles().size() + " 个", Theme.INK);
        ky += lineH;

        if (!profile.previousNames().isEmpty()) {
            canvas.text("曾用名", Theme.font(12, false), new Color(100, 116, 139, 200), x + 16, ky);
            String names = String.join("、", profile.previousNames());
            canvas.text(Theme.ellipsis(names, canvas.fm(Theme.font(12, false)), w - 32),
                    Theme.font(12, false), new Color(71, 85, 105), x + 16, ky + 17);
            ky += 34;
        }

        if (profile.privacyHidden()) {
            canvas.text("该玩家已开启生涯隐私，详细战绩不可见",
                    Theme.font(12, false), Theme.LOSE, x + 16, ky + 4);
            ky += 20;
        }

        return (int) ky + 6;
    }

    // ==================== 模式统计 + 常用角色 ====================

    private int modePanel(Canvas canvas, int x, int y, int w) {
        int innerTop = sectionBar(canvas, x, y, w, "生涯模式统计", "按模式分别计算") + 12;

        List<PlayerProfile.Mode> modes = profile.modes();
        int rowH = 34;
        int headerH = 32;
        int rows = Math.min(modes.size(), 4);
        int tableH = headerH + Math.max(1, rows) * rowH + 12;

        canvas.glassCard(x, innerTop, w, tableH, 12);

        String[] heads = {"模式", "对局", "胜率", "K/D", "击杀/场", "伤害/场"};
        float[] ratios = {0.25f, 0.13f, 0.15f, 0.13f, 0.16f, 0.18f};
        float[] colX = columns(x + 16, w - 32, ratios);

        float headY = innerTop + 22;
        for (int i = 0; i < heads.length; i++) {
            canvas.text(heads[i], Theme.font(12, false), new Color(100, 116, 139, 210), colX[i], headY);
        }
        canvas.divider(x + 16, headY + 9, w - 32, Theme.LINE);

        float rowY = headY + 28;
        if (modes.isEmpty()) {
            canvas.text("暂无模式数据", Theme.font(13, false), Theme.FAINT, x + 16, rowY + 8);
            rowY += 26;
        } else {
            for (int i = 0; i < rows; i++) {
                PlayerProfile.Mode mode = modes.get(i);
                if (i % 2 == 1) {
                    canvas.fillRound(x + 10, rowY - 15, w - 20, rowH - 4, 8, Theme.ROW_ALT);
                }
                canvas.text(Theme.ellipsis(mode.label(), canvas.fm(Theme.font(13, true)), 100),
                        Theme.font(13, true), Theme.INK, colX[0], rowY);
                canvas.text(String.valueOf(mode.rounds()), Theme.num(13, false), Theme.INK, colX[1], rowY);
                canvas.text(percent(mode.winRate()), Theme.num(13, true), Theme.ACCENT, colX[2], rowY);
                canvas.text(number(mode.kd()), Theme.num(13, false), Theme.INK, colX[3], rowY);
                canvas.text(number(mode.avgKill()), Theme.num(13, false), Theme.INK, colX[4], rowY);
                canvas.text(number(mode.avgDamage()), Theme.num(13, false), Theme.INK, colX[5], rowY);
                rowY += rowH;
            }
        }

        int tableBottom = innerTop + tableH;
        int cursor = tableBottom + 12;

        // 常用超弦体：两列网格
        List<PlayerProfile.Role> roles = profile.roles();
        int shown = Math.min(roles.size(), 4);
        if (shown > 0) {
            canvas.glassCard(x, cursor, w, 32 + (int) Math.ceil(shown / 2.0) * 52 + 12, 12);
            canvas.text("常用超弦体", Theme.font(12, false),
                    new Color(100, 116, 139, 210), x + 16, cursor + 21);

            float gridTop = cursor + 34;
            float cellW = (w - 32 - 10) / 2f;
            for (int i = 0; i < shown; i++) {
                PlayerProfile.Role role = roles.get(i);
                float cx = x + 16 + (i % 2) * (cellW + 10);
                float cy = gridTop + (i / 2) * 52;

                canvas.fillRound(cx, cy, cellW, 46, 10, new Color(255, 255, 255, 130));
                canvas.avatar(Assets.rolePortrait(role.roleId()), cx + 5, cy + 4, 38, 9);
                float nx = cx + 50;
                canvas.text(Theme.ellipsis(role.name(), canvas.fm(Theme.font(13, true)),
                                (int) cellW - 110), Theme.font(13, true), Theme.INK, nx, cy + 20);
                canvas.text(role.wins() + " 胜 / " + role.count() + " 场",
                        Theme.font(11, false), new Color(100, 116, 139, 210), nx, cy + 37);
                canvas.textRight(percent(role.winRate()), Theme.num(12, true), Theme.ACCENT,
                        cx + cellW - 12, cy + 27);
            }
            cursor += 32 + (int) Math.ceil(shown / 2.0) * 52 + 12;
        }

        return cursor;
    }

    // ==================== 最近战绩 ====================

    private int recentMatches(Canvas canvas, int x, int y, int w) {
        List<PlayerProfile.Match> matches = profile.recentMatches();
        int total = Math.min(matches.size(), RECENT_LIMIT);

        int wins = 0;
        int losses = 0;
        int draws = 0;
        for (int i = 0; i < total; i++) {
            switch (matches.get(i).result()) {
                case "win" -> wins++;
                case "lose" -> losses++;
                default -> draws++;
            }
        }
        String note = total == 0
                ? "暂无记录"
                : wins + " 胜 " + losses + " 负" + (draws > 0 ? " " + draws + " 平" : "")
                + " · 本页 " + total + " 场 · 全部模式";

        int innerTop = sectionBar(canvas, x, y, w, "最近战绩", note) + 12;

        if (total == 0) {
            canvas.glassCard(x, innerTop, w, 64, 12);
            canvas.text("没有符合条件的对局记录", Theme.font(13, false), Theme.FAINT,
                    x + 18, innerTop + 36);
            return innerTop + 64;
        }

        int gap = 14;
        int cardW = (w - gap) / 2;
        int cardH = 116;
        int rowGap = 12;
        int rows = (total + 1) / 2;

        for (int i = 0; i < total; i++) {
            float cx = x + (i % 2) * (cardW + gap);
            float cy = innerTop + (i / 2) * (cardH + rowGap);
            matchCard(canvas, matches.get(i), cx, cy, cardW, cardH);
        }
        return innerTop + rows * cardH + (rows - 1) * rowGap;
    }

    private void matchCard(Canvas canvas, PlayerProfile.Match match, float x, float y,
                           float w, float h) {
        boolean win = "win".equals(match.result());
        boolean draw = !win && !"lose".equals(match.result());
        Color accent = draw ? Theme.MUTED : (win ? Theme.WIN : Theme.LOSE);

        Graphics2D g = canvas.raw();
        Shape shape = Canvas.roundRect(x, y, w, h, 12);

        g.setColor(new Color(255, 255, 255, 216));
        g.fill(shape);

        // 地图缩略图作为底纹
        BufferedImage map = Assets.mapImage(match.mapId());
        if (map != null) {
            BufferedImage mapArt = fitCover(map, (int) w, (int) h);
            canvas.clipped(shape, () -> canvas.withAlpha(0.42f,
                    () -> g.drawImage(mapArt, (int) x, (int) y, null)));
            g.setColor(new Color(255, 255, 255, 138));
            g.fill(shape);
        }

        // 左侧结果色条
        canvas.clipped(shape, () -> {
            g.setColor(accent);
            g.fill(new java.awt.geom.RoundRectangle2D.Float(x, y, 5, h, 3, 3));
        });

        g.setColor(Theme.alpha(accent, 55));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(shape);

        // 第一行：结果 + MVP/SVP + 段位分变化
        float topY = y + 26;
        Font resultFont = Theme.font(13, true);
        String resultLabel = draw ? "平局" : (win ? "胜利" : "失败");
        canvas.text(resultLabel, resultFont, accent, x + 16, topY);

        float tagX = x + 16 + canvas.width(resultLabel, resultFont) + 8;
        if (match.mvp()) {
            canvas.chip("MVP", Theme.num(9, true), new Color(176, 126, 18),
                    new Color(255, 228, 154, 220), new Color(232, 182, 62, 200), tagX, topY - 12, 6, 17);
        } else if (match.svp()) {
            canvas.chip("SVP", Theme.num(9, true), new Color(88, 99, 118),
                    new Color(228, 234, 244, 220), new Color(180, 192, 210, 200), tagX, topY - 12, 6, 17);
        }

        if (match.divisionScoreDelta() != 0) {
            String delta = (match.divisionScoreDelta() > 0 ? "+" : "") + match.divisionScoreDelta();
            canvas.textRight(delta, Theme.num(19, true), accent, x + w - 16, topY + 2);
        }

        // 第二行：比分 + 地图 / 模式时间
        canvas.text(match.myScore() + " : " + match.opponentScore(), Theme.num(21, true),
                Theme.INK, x + 16, y + 56);

        canvas.textRight(Theme.mapName(match.mapId(), match.modeLabel()), Theme.font(13, true), Theme.INK,
                x + w - 16, y + 52);
        canvas.textRight(match.modeLabel() + " · " + formatTime(match.playedAt()),
                Theme.font(11, false), new Color(100, 116, 139, 220), x + w - 16, y + 70);

        // 第三行：角色 + KDA
        canvas.circleAvatar(Assets.rolePortrait(match.roleId()), x + 32, y + h - 32, 32);
        float nx = x + 54;
        canvas.text(Theme.ellipsis(roleName(match.roleId()),
                        canvas.fm(Theme.font(12, true)), 70),
                Theme.font(12, true), Theme.INK, nx, y + h - 28);

        Font kdaFont = Theme.num(14, true);
        String kda = match.kills() + " / " + match.deaths() + " / " + match.assists();
        float kdaX = nx + 80;
        canvas.text(kda, kdaFont, Theme.INK, kdaX, y + h - 27);
        canvas.text("K/D/A", Theme.font(9, false), new Color(100, 116, 139, 180),
                kdaX, y + h - 15);

        // 第四行：时长 + 对局号
        canvas.text("时长 " + duration(match.durationSeconds()), Theme.font(10, false),
                new Color(100, 116, 139, 190), x + 16, y + h - 8);
        canvas.textRight("ROOM " + match.roomId(), Theme.num(9, false),
                new Color(100, 116, 139, 175), x + w - 16, y + h - 8);
    }

    // ==================== 工具 ====================

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100);
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%,.2f", value);
    }

    private static String formatTime(long epochSeconds) {
        if (epochSeconds <= 0) {
            return "—";
        }
        return MATCH_TIME.format(Instant.ofEpochSecond(epochSeconds).atZone(ZONE));
    }

    private static String duration(int seconds) {
        if (seconds <= 0) {
            return "—";
        }
        return (seconds / 60) + "分" + (seconds % 60) + "秒";
    }

    private static String roleName(int roleId) {
        return switch (roleId) {
            case 101 -> "米雪儿";
            case 105 -> "奥黛丽";
            case 107 -> "玛德蕾娜";
            case 108 -> "信";
            case 109 -> "令";
            case 110 -> "白墨";
            case 112 -> "绯莎";
            case 115 -> "芙拉薇娅";
            case 119 -> "艾卡";
            case 120 -> "珐格兰丝";
            case 121 -> "忧雾";
            case 122 -> "玛拉";
            case 123 -> "蕾欧娜";
            case 124 -> "心夏";
            case 125 -> "千代";
            case 126 -> "诺诺";
            case 128 -> "拉薇";
            case 130 -> "汐";
            case 131 -> "伊薇特";
            case 132 -> "明";
            case 133 -> "梅瑞狄斯";
            case 137 -> "香奈美";
            case 146 -> "星绘";
            default -> "未知";
        };
    }

    /** 按高度等比缩放。 */
    private static BufferedImage fitHeight(BufferedImage source, int height) {
        float ratio = height / (float) source.getHeight();
        return Theme.scale(source, Math.max(1, Math.round(source.getWidth() * ratio)), height);
    }

    /** 按宽度等比缩放后居中裁剪，用于底纹铺满。 */
    private static BufferedImage fitCover(BufferedImage source, int width, int height) {
        float ratio = Math.max(width / (float) source.getWidth(), height / (float) source.getHeight());
        int w = Math.max(1, Math.round(source.getWidth() * ratio));
        int h = Math.max(1, Math.round(source.getHeight() * ratio));
        BufferedImage scaled = Theme.scale(source, w, h);
        return scaled.getSubimage((w - width) / 2, (h - height) / 2, width, height);
    }
}
