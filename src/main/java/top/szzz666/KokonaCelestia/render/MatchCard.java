package top.szzz666.KokonaCelestia.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.szzz666.KokonaCelestia.api.DakanuClient;
import top.szzz666.KokonaCelestia.model.PlayerProfile;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** “看看对局”单场详情出图。 */
public final class MatchCard extends CardRenderer {

    private static final DateTimeFormatter MATCH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JsonObject detail;
    private final String playerName;
    private final PlayerProfile.Match summary;

    /**
     * @param detail     对局详情响应
     * @param playerName 被查询玩家昵称
     * @param summary    对应对局摘要（提供比分与地图，可能为 null）
     */
    public MatchCard(JsonObject detail, String playerName, PlayerProfile.Match summary) {
        this.detail = detail;
        this.playerName = playerName;
        this.summary = summary;
    }

    @Override
    protected String subtitleTitle() {
        return "对局详情";
    }

    private static final int HEADER_HEIGHT_BOX = 136;
    private static final int SECTION_GAP = 16;
    private static final int TEAM_GAP = 14;
    private static final int TEAM_HEADER_HEIGHT = 32;
    private static final int TEAM_ROW_HEIGHT = 54;

    @Override
    protected void prepare() {
        List<Integer> avatarIds = new java.util.ArrayList<>();
        List<Integer> roleIds = new java.util.ArrayList<>();
        for (Team team : teams()) {
            for (JsonObject player : team.players()) {
                avatarIds.add(DakanuClient.optInt(player, "avatarId", 0));
                roleIds.add(DakanuClient.optInt(player, "roleId", 0));
            }
        }
        List<Integer> mapIds = new java.util.ArrayList<>();
        if (summary != null) {
            mapIds.add(summary.mapId());
        }

        Assets.prefetchAll(
                Assets.avatarPaths(avatarIds),
                Assets.rolePaths(roleIds),
                Assets.mapPaths(mapIds));
    }

    @Override
    protected int layoutHeight() {
        List<Team> teams = displayTeams();
        int y = HEADER_HEIGHT;
        y += HEADER_HEIGHT_BOX + SECTION_GAP;
        for (Team team : teams) {
            y += BAR_HEIGHT + 12
                    + TEAM_HEADER_HEIGHT + team.players().size() * TEAM_ROW_HEIGHT + 12;
            y += TEAM_GAP;
        }
        y += FOOTER_HEIGHT;
        return y;
    }

    @Override
    protected int drawBody(Canvas canvas, int top) {
        int x = Theme.MARGIN;
        int w = Theme.WIDTH - Theme.MARGIN * 2;
        int y = top;

        List<Team> teams = displayTeams();
        y = header(canvas, x, y, w, teams);
        y += SECTION_GAP;

        // 双方胜负是否真的不同；生化模式里全部玩家都是 win，标「胜方」会误导
        long winCount = teams.stream().filter(Team::win).count();
        boolean definite = winCount > 0 && winCount < teams.size();

        for (Team team : teams) {
            y = teamTable(canvas, x, y, w, team, definite);
            y += TEAM_GAP;
        }
        return y;
    }

    private record Team(String label, List<JsonObject> players, boolean win) {
    }

    /** 顶部概要：结果、比分、地图、对局编号。 */
    private int header(Canvas canvas, int x, int y, int w, List<Team> teams) {
        int h = HEADER_HEIGHT_BOX;
        Graphics2D g = canvas.raw();
        Shape shape = Canvas.roundRect(x, y, w, h, 16);

        g.setColor(new Color(255, 255, 255, 200));
        g.fill(shape);

        // 地图缩略图作为右侧底纹
        int mapId = summary == null ? 0 : summary.mapId();
        var map = Assets.mapImage(mapId);
        if (map != null) {
            canvas.clipped(shape, () -> canvas.withAlpha(0.30f, () -> {
                var cover = cover(map, w, h);
                g.drawImage(cover, x, y, null);
            }));
            g.setPaint(new GradientPaint(x + w * 0.45f, 0, new Color(255, 255, 255, 240),
                    x + w * 0.8f, 0, new Color(255, 255, 255, 60)));
            canvas.clipped(shape, () -> g.fillRect(x, y, w, h));
        } else {
            g.setPaint(new GradientPaint(x, y, new Color(255, 255, 255, 130),
                    x, y + h, new Color(255, 255, 255, 40)));
            g.fill(shape);
        }

        g.setColor(Theme.CARD_EDGE);
        g.setStroke(new BasicStroke(1.1f));
        g.draw(shape);

        // 胜负以「被查询玩家」为准，而不是「这场有没有赢家」。
        // 早先按队伍里是否存在 win 来判定，导致查谁都显示「胜利」——
        // 输的那一方明明在败方阵营里，标题却是胜利。
        String viewerResult = summary == null ? "" : summary.result();
        boolean viewerWin = "win".equalsIgnoreCase(viewerResult);
        boolean viewerLose = "lose".equalsIgnoreCase(viewerResult);
        boolean viewerDraw = "draw".equalsIgnoreCase(viewerResult)
                || "tie".equalsIgnoreCase(viewerResult);

        // 摘要缺失时退回到「在队伍里按昵称找这个玩家」
        if (!viewerWin && !viewerLose && !viewerDraw) {
            for (Team team : teams) {
                for (JsonObject player : team.players()) {
                    if (playerName != null && playerName.equals(
                            DakanuClient.optString(player, "nickname", ""))) {
                        viewerWin = team.win();
                        viewerLose = !team.win();
                        break;
                    }
                }
            }
        }

        String headline;
        Color accent;
        if (viewerWin) {
            headline = "胜利";
            accent = Theme.WIN;
        } else if (viewerLose) {
            headline = "失败";
            accent = Theme.LOSE;
        } else if (viewerDraw) {
            headline = "平局";
            accent = Theme.MUTED;
        } else {
            headline = "对局详情";
            accent = Theme.MUTED;
        }

        canvas.text(playerName == null || playerName.isBlank() ? "对局" : playerName,
                Theme.font(12, false), new Color(100, 116, 139, 220), x + 26, y + 34);
        canvas.text(headline, Theme.font(27, true), accent, x + 26, y + 68);

        // 比分
        if (summary != null) {
            canvas.text(summary.myScore() + " : " + summary.opponentScore(), Theme.num(32, true),
                    Theme.INK, x + 26, y + 112);
        }

        // 中间信息块
        float midX = x + 226;
        canvas.text("地图", Theme.font(12, false), new Color(100, 116, 139, 220), midX, y + 34);
        canvas.text(Theme.mapName(mapId), Theme.font(16, true), Theme.INK, midX, y + 58);

        canvas.text("对局模式", Theme.font(12, false), new Color(100, 116, 139, 220), midX, y + 86);
        canvas.text(modeLabel(), Theme.font(13, true), new Color(71, 85, 105), midX, y + 108);

        // 右侧：对局编号与时间
        float rightX = x + w - 26;
        canvas.textRight("对局编号", Theme.font(12, false), new Color(100, 116, 139, 220),
                rightX, y + 34);
        canvas.textRight(DakanuClient.optString(detail, "roomId", "—"),
                Theme.num(15, true), new Color(71, 85, 105), rightX, y + 58);
        canvas.textRight("对局时间", Theme.font(12, false), new Color(100, 116, 139, 220),
                rightX, y + 86);
        canvas.textRight(summary == null || summary.playedAt() <= 0 ? "—"
                        : MATCH_TIME.format(Instant.ofEpochSecond(summary.playedAt()).atZone(ZONE)),
                Theme.num(13, false), new Color(71, 85, 105), rightX, y + 108);

        return y + h;
    }

    /**
     * 单支队伍的战绩表。
     *
     * @param definite 双方胜负是否可区分。生化等模式里 API 会把所有玩家都标成 win，
     *                 此时两个阵营都会是「胜方」，标注反而误导，因此干脆不标
     */
    private int teamTable(Canvas canvas, int x, int y, int w, Team team, boolean definite) {
        String note = team.players().size()
                + (team.label().startsWith("全部") ? " 名玩家" : " 名队员");
        String title = definite
                ? team.label() + "（" + (team.win() ? "胜方" : "败方") + "）"
                : team.label();
        int innerTop = sectionBar(canvas, x, y, w, title, note) + 12;

        int headerH = TEAM_HEADER_HEIGHT;
        int rowH = TEAM_ROW_HEIGHT;
        int cardH = headerH + team.players().size() * rowH + 12;
        canvas.glassCard(x, innerTop, w, cardH, 12);

        String[] heads = {"引航者", "超弦体", "击败", "击倒", "阵亡", "助攻", "伤害", "评分"};
        float[] ratios = {0.27f, 0.16f, 0.08f, 0.08f, 0.08f, 0.08f, 0.13f, 0.12f};
        float[] colX = columns(x + 16, w - 32, ratios);

        float headY = innerTop + 22;
        for (int i = 0; i < heads.length; i++) {
            canvas.text(heads[i], Theme.font(12, false), new Color(100, 116, 139, 215), colX[i], headY);
        }
        canvas.divider(x + 16, headY + 9, w - 32, Theme.LINE);

        float rowY = headY + 30;
        Color teamAccent = definite ? (team.win() ? Theme.WIN : Theme.LOSE) : Theme.ACCENT;

        for (int i = 0; i < team.players().size(); i++) {
            JsonObject player = team.players().get(i);
            if (i % 2 == 1) {
                canvas.fillRound(x + 10, rowY - 16, w - 20, rowH - 2, 8, Theme.ROW_ALT);
            }
            float baseY = rowY + 16;
            boolean isTarget = DakanuClient.optBool(player, "isTarget", false);

            int avatarId = DakanuClient.optInt(player, "avatarId", 0);
            canvas.avatar(Assets.avatar(avatarId), colX[0] - 2, rowY - 9, 36, 9);

            String nickname = Theme.ellipsis(DakanuClient.optString(player, "nickname", "未知玩家"),
                    canvas.fm(Theme.font(13, true)), 125);
            Color nameColor = isTarget ? Theme.ACCENT : Theme.INK;
            canvas.text(nickname, Theme.font(13, true), nameColor, colX[0] + 46, baseY);

            if (DakanuClient.optBool(player, "mvp", false)) {
                canvas.chip("MVP", Theme.num(9, true), new Color(176, 126, 18),
                        new Color(255, 228, 154, 225), new Color(232, 182, 62, 205),
                        colX[0] + 46, baseY + 5, 6, 16);
            } else if (DakanuClient.optBool(player, "svp", false)) {
                canvas.chip("SVP", Theme.num(9, true), new Color(88, 99, 118),
                        new Color(228, 234, 244, 225), new Color(180, 192, 210, 205),
                        colX[0] + 46, baseY + 5, 6, 16);
            }

            int roleId = DakanuClient.optInt(player, "roleId", 0);
            canvas.avatar(Assets.rolePortrait(roleId), colX[1] - 2, rowY - 7, 34, 9);
            canvas.text(DakanuClient.optString(player, "roleName", "未知"),
                    Theme.font(12, true), Theme.INK, colX[1] + 40, baseY);

            canvas.text(String.valueOf(DakanuClient.optInt(player, "kills", 0)),
                    Theme.num(14, true), Theme.INK, colX[2], baseY);
            canvas.text(String.valueOf(DakanuClient.optInt(player, "knockdowns", 0)),
                    Theme.num(13, false), new Color(100, 116, 139, 235), colX[3], baseY);
            canvas.text(String.valueOf(DakanuClient.optInt(player, "deaths", 0)),
                    Theme.num(13, false), new Color(100, 116, 139, 235), colX[4], baseY);
            canvas.text(String.valueOf(DakanuClient.optInt(player, "assists", 0)),
                    Theme.num(13, false), new Color(100, 116, 139, 235), colX[5], baseY);
            canvas.text(String.valueOf(DakanuClient.optInt(player, "damage", 0)),
                    Theme.num(13, false), Theme.INK, colX[6], baseY);
            canvas.text(String.valueOf(DakanuClient.optInt(player, "score", 0)),
                    Theme.num(14, true), teamAccent, colX[7], baseY);

            rowY += rowH;
        }

        return innerTop + cardH;
    }

    /**
     * 是否属于「没有阵营之分」的模式。
     * <p>
     * 生化模式（{@code RoomMode_ZOMBIE}）里所有玩家共享同一个结果，没有对抗双方；
     * 站点接口仍按「阵营 1 / 阵营 2」返回，直接照搬会让人误以为是一般的对抗局。
     */
    private boolean noCamps(List<Team> raw) {
        String mode = DakanuClient.optString(detail, "matchMode", "");
        if (mode.toUpperCase().contains("ZOMBIE")) {
            return true;
        }
        if (raw.size() < 2) {
            return false;
        }
        boolean same = raw.stream().allMatch(t -> t.win() == raw.get(0).win());
        String label = modeLabel();
        return same && (label == null || !label.contains("爆破"));
    }

    /** 实际展示用的分组：无阵营模式合并成一张总表。 */
    private List<Team> displayTeams() {
        List<Team> raw = teams();
        if (!noCamps(raw)) {
            return raw;
        }
        List<JsonObject> all = new ArrayList<>();
        for (Team team : raw) {
            all.addAll(team.players());
        }
        all.sort(Comparator.comparingInt((JsonObject p) ->
                DakanuClient.optInt(p, "score", 0)).reversed());
        return List.of(new Team("全部玩家", all, false));
    }

    private List<Team> teams() {
        List<Team> out = new ArrayList<>();
        for (JsonElement element : DakanuClient.optArray(detail, "teams")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject team = element.getAsJsonObject();
            List<JsonObject> players = new ArrayList<>();
            boolean win = false;
            for (JsonElement playerElement : DakanuClient.optArray(team, "players")) {
                if (!playerElement.isJsonObject()) {
                    continue;
                }
                JsonObject player = playerElement.getAsJsonObject();
                players.add(player);
                if ("win".equals(DakanuClient.optString(player, "result", ""))) {
                    win = true;
                }
            }
            players.sort(Comparator.comparingInt((JsonObject p) ->
                    DakanuClient.optInt(p, "score", 0)).reversed());
            out.add(new Team(DakanuClient.optString(team, "label", "阵营"), players, win));
        }
        out.sort(Comparator.comparing(Team::win).reversed());
        return out;
    }

    private String modeLabel() {
        String mode = DakanuClient.optString(detail, "matchMode", "");
        try {
            String label = DakanuClient.optString(
                    DakanuClient.optObject(DakanuClient.meta(), "modes"), mode, "");
            if (!label.isBlank()) {
                return label;
            }
        } catch (Exception ignored) {
        }
        return switch (mode) {
            case "RoomMode_RANK_BOMB" -> "排位爆破";
            case "RoomMode_BOMB" -> "爆破模式";
            case "RoomMode_TEAM" -> "团队竞技";
            default -> "未知模式";
        };
    }

    /** 等比缩放并居中裁剪，铺满指定区域。 */
    private static java.awt.image.BufferedImage cover(java.awt.image.BufferedImage source,
                                                      int width, int height) {
        float ratio = Math.max(width / (float) source.getWidth(), height / (float) source.getHeight());
        int w = Math.max(width, Math.round(source.getWidth() * ratio));
        int h = Math.max(height, Math.round(source.getHeight() * ratio));
        var scaled = Theme.scale(source, w, h);
        return scaled.getSubimage((w - width) / 2, (h - height) / 2, width, height);
    }
}
