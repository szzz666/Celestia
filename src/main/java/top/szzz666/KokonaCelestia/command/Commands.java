package top.szzz666.KokonaCelestia.command;

import com.google.gson.JsonObject;
import top.szzz666.KokonaCelestia.api.DakanuClient;
import top.szzz666.KokonaCelestia.config.MyConfig;
import top.szzz666.KokonaCelestia.model.PlayerProfile;
import top.szzz666.KokonaCelestia.render.HelpCard;
import top.szzz666.KokonaCelestia.render.MatchCard;
import top.szzz666.KokonaCelestia.render.PlayerCard;
import top.szzz666.KokonaCelestia.render.RankCard;
import top.szzz666.KokonaCelestia.render.Renderer;
import top.szzz666.KokonaCelestia.util.Reply;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 指令实现。指令均为“精确前缀”匹配，未命中即静默，避免误触。 */
public final class Commands {

    private Commands() {
    }

    // ==================== 帮助 ====================

    public static void help(Reply reply) {
        reply.image(Renderer.render(new HelpCard()));
    }

    // ==================== 看看 <昵称> ====================

    /**
     * 「看看」：读缓存出玩家档案，秒回。
     * <p>
     * 卡片右上角会标注数据抓取时间；需要最新数据请用 {@code 看看最新}。
     */
    public static void profile(Reply reply, String argument) {
        lookup(reply, argument, false);
    }

    /**
     * 「看看最新」：强制向站点请求最新数据后出图。
     * <p>
     * 站点数据由它自己定期抓取，缓存里可能躺着十几天前的存档，
     * 这个指令会走 {@code ?refresh=1} 让站点重新抓取。代价是 10-40 秒，
     * 且该接口实测成功率约 1/3，失败时回退到缓存并说明。
     */
    public static void profileFresh(Reply reply, String argument) {
        lookup(reply, argument, true);
    }

    /** 两个入口共用的查找与候选选择逻辑。 */
    private static void lookup(Reply reply, String argument, boolean refresh) {
        String keyword = argument == null ? "" : argument.trim();
        if (keyword.isEmpty()) {
            reply.text(refresh
                    ? "请在指令后加上玩家昵称或 UID，例如：看看最新米雪儿"
                    : "请在指令后加上玩家昵称或 UID，例如：看看米雪儿");
            return;
        }

        try {
            List<DakanuClient.PlayerSuggestion> list = DakanuClient.search(keyword);
            if (list.isEmpty()) {
                reply.text("没有找到「" + keyword + "」这个玩家，请检查昵称或 UID 是否正确。");
                return;
            }
            // 多个候选时让用户选择，避免静默取第一个导致查错人
            if (needsChoosing(list)) {
                PendingSelection.remember(reply.groupId(), reply.userId(), keyword, list,
                        PendingSelection.Kind.PROFILE, null);
                reply.text(PendingSelection.describe(
                        new PendingSelection.Entry(keyword, list,
                                PendingSelection.Kind.PROFILE, null, 0)));
                return;
            }
            renderProfile(reply, list.get(0), refresh);
        } catch (Exception e) {
            reply.text("查询失败：" + friendly(e));
        }
    }

    // ==================== 看看对局 <昵称> <对局号> ====================

    public static void match(Reply reply, String argument) {
        String rest = argument == null ? "" : argument.trim();
        if (rest.isEmpty()) {
            reply.text("用法：看看对局 <玩家昵称> <对局号>");
            return;
        }

        // 对局号固定在最后一段，昵称可以含空格
        String[] parts = rest.split("\\s+");
        if (parts.length < 2) {
            reply.text("用法：看看对局 <玩家昵称> <对局号>，例如：看看对局米雪儿114514");
            return;
        }
        String roomToken = parts[parts.length - 1];
        String nickname = rest.substring(0, rest.length() - roomToken.length()).trim();

        try {
            List<DakanuClient.PlayerSuggestion> list = DakanuClient.search(nickname);
            if (list.isEmpty()) {
                reply.text("没有找到「" + nickname + "」这个玩家。");
                return;
            }
            if (needsChoosing(list)) {
                PendingSelection.remember(reply.groupId(), reply.userId(), nickname, list,
                        PendingSelection.Kind.MATCH, roomToken);
                reply.text(PendingSelection.describe(
                        new PendingSelection.Entry(nickname, list,
                                PendingSelection.Kind.MATCH, roomToken, 0)));
                return;
            }
            renderMatch(reply, list.get(0), roomToken);
        } catch (Exception e) {
            reply.text("查询失败：" + friendly(e));
        }
    }

    // ==================== 排行 [页数] ====================

    public static void rank(Reply reply, String argument) {
        String arg = argument == null ? "" : argument.trim();
        int page = 1;
        if (!arg.isEmpty()) {
            try {
                page = Integer.parseInt(arg);
            } catch (NumberFormatException e) {
                reply.text("页数需要是数字，例如：排行2");
                return;
            }
        }
        int maxPage = Math.max(1, MyConfig.rankMaxPage);
        if (page < 1 || page > maxPage) {
            reply.text("页数范围是 1 - " + maxPage + "。");
            return;
        }

        try {
            int pageSize = Math.min(100, Math.max(1, MyConfig.rankPageSize));
            JsonObject ranks = DakanuClient.rankings(1, page, pageSize);
            JsonObject season = safeSeason();
            reply.image(Renderer.render(new RankCard(ranks, season, page, pageSize)));
        } catch (Exception e) {
            reply.text("查询失败：" + friendly(e));
        }
    }

    // ==================== 候选选择 ====================

    /**
     * 处理用户回复的序号。
     *
     * @return 是否消费了这条消息（true 表示不再走指令解析）
     */
    public static boolean handleSelection(Reply reply, String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        long groupId = reply.groupId();
        long userId = reply.userId();
        if (!PendingSelection.has(groupId, userId)) {
            return false;
        }

        if ("取消".equals(trimmed) || "cancel".equalsIgnoreCase(trimmed)) {
            PendingSelection.clear(groupId, userId);
            reply.text("已取消本次查询。");
            return true;
        }

        // 只接受纯数字，避免误吞正常聊天
        if (!trimmed.matches("\\d{1,2}")) {
            return false;
        }

        PendingSelection.Entry entry = PendingSelection.take(groupId, userId);
        if (entry == null) {
            return false;
        }

        int index = Integer.parseInt(trimmed);
        if (index < 1 || index > entry.candidates().size()) {
            reply.text("序号超出范围，请回复 1 - " + entry.candidates().size() + "。");
            return true;
        }

        DakanuClient.PlayerSuggestion target = entry.candidates().get(index - 1);
        try {
            if (entry.kind() == PendingSelection.Kind.MATCH && entry.extra() != null) {
                renderMatch(reply, target, entry.extra());
            } else {
                renderProfile(reply, target, true);
            }
        } catch (Exception e) {
            reply.text("查询失败：" + friendly(e));
        }
        return true;
    }

    /** 是否需要用户选择：多个候选且无法唯一确定。 */
    private static boolean needsChoosing(List<DakanuClient.PlayerSuggestion> list) {
        if (list.size() <= 1) {
            return false;
        }
        // 只有一个精确匹配时直接用它
        int exact = 0;
        for (DakanuClient.PlayerSuggestion item : list) {
            if (item.exact()) {
                exact++;
            }
        }
        return exact != 1;
    }

    // ==================== 出图 ====================

    /**
     * 出玩家档案。
     * <p>
     * 每次查询都向站点请求一次最新数据：站点是自行定期抓取的，缓存里可能躺着
     * 十几天前的存档，直接读缓存会让用户以为查错了人。代价是每次都要等
     * 20-40 秒，因此先发一条文本告知进度，避免用户以为机器人没反应。
     * <p>
     * 隐私玩家（{@code privacyStatus=hidden}）站点读不到新数据，刷新只会白等，
     * 直接说明情况并用存档出图。
     */
    private static void renderProfile(Reply reply, DakanuClient.PlayerSuggestion target,
                                      boolean refresh) throws Exception {
        JsonObject raw;

        if (!refresh) {
            // 实测首次查询要 8 秒左右（拉头像、立绘、地图缩略图），
            // 素材缓存后约 3-4 秒；先给一句提示，避免用户以为没收到指令
            reply.text("请稍等…");
            raw = DakanuClient.summoner(target.playerId());
        } else {
            // 先用缓存判断隐私状态：隐私玩家刷新也拿不到新数据，不必让用户空等
            PlayerProfile cached = PlayerProfile.from(DakanuClient.summoner(target.playerId()));

            if ("hidden".equalsIgnoreCase(cached.privacyStatus())) {
                reply.text("「" + cached.nickname() + "」已开启生涯隐私，站点无法读取最新数据，以下为 "
                        + describeAge(cached) + " 抓取的存档。");
                reply.image(Renderer.render(new PlayerCard(cached)));
                return;
            }

            // 先提示再等待：刷新耗时 20-40 秒，没有提示用户会以为消息丢了
            reply.text("正在获取「" + cached.nickname() + "」的最新数据，请稍候…");

            try {
                raw = DakanuClient.summonerRefresh(target.playerId());
            } catch (Exception refreshError) {
                // 站点刷新会真的去上游重新抓取，偶发 524 超时很正常。
                // 回退到已有缓存并说明情况，不让用户等了一分钟还看不到图。
                reply.text("刷新未成功（站点上游超时），以下为 " + describeAge(cached)
                        + " 的存档数据，稍后可再试。");
                raw = DakanuClient.summoner(target.playerId());
            }
        }

        PlayerProfile profile = PlayerProfile.from(raw);
        reply.image(Renderer.render(new PlayerCard(profile)));
    }

    /** 把数据年龄说成「N 天前」这类更好懂的说法。 */
    private static String describeAge(PlayerProfile profile) {
        double hours = profile.dataAgeHours();
        if (hours < 0) {
            return "未知时间";
        }
        if (hours < 1) {
            return "刚刚";
        }
        if (hours < 24) {
            return (int) hours + " 小时前";
        }
        return (long) (hours / 24) + " 天前";
    }

    private static void renderMatch(Reply reply, DakanuClient.PlayerSuggestion target,
                                    String roomToken) throws Exception {
        JsonObject summoner = DakanuClient.summoner(target.playerId());
        PlayerProfile profile = PlayerProfile.from(summoner);
        MatchRef matched = resolveMatch(profile, roomToken);
        if (matched == null) {
            reply.text("没有找到与「" + roomToken + "」匹配的对局，请检查对局号。");
            return;
        }
        JsonObject detail = DakanuClient.matchDetail(matched.roomId());
        reply.image(Renderer.render(new MatchCard(detail, profile.nickname(), matched.summary())));
    }

    // ==================== 解析辅助 ====================

    /** 命中的对局：房间号 + 摘要（摘要提供地图与比分）。 */
    private record MatchRef(String roomId, PlayerProfile.Match summary) {
    }

    /**
     * 对局号解析：完整对局号为 18 位数字，也接受短号（尾数匹配最近对局）。
     * 匹配顺序为 完整号 -> 尾数 -> 包含 -> 18 位直连。
     */
    private static MatchRef resolveMatch(PlayerProfile profile, String token) {
        String cleaned = token.replaceAll("[^0-9]", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        List<PlayerProfile.Match> all = new ArrayList<>(profile.recentMatches());
        all.addAll(profile.historyMatches());

        for (PlayerProfile.Match match : all) {
            if (match.roomId().equals(cleaned)) {
                return new MatchRef(match.roomId(), match);
            }
        }
        for (PlayerProfile.Match match : all) {
            if (match.roomId().endsWith(cleaned)) {
                return new MatchRef(match.roomId(), match);
            }
        }
        for (PlayerProfile.Match match : all) {
            if (match.roomId().contains(cleaned)) {
                return new MatchRef(match.roomId(), match);
            }
        }
        return cleaned.length() >= 15 ? new MatchRef(cleaned, null) : null;
    }

    private static JsonObject safeSeason() {
        try {
            return DakanuClient.currentSeason();
        } catch (Exception e) {
            return null;
        }
    }

    private static String friendly(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "接口暂时不可用，请稍后再试。";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("timeout") || message.contains("超时")) {
            return "接口响应超时，请稍后再试（高峰期可能需要多等几秒）。";
        }
        if (lower.contains("http 5")) {
            return "上游服务器繁忙，请稍后再试。";
        }
        if (lower.contains("http 429") || message.contains("频繁")) {
            return "请求过于频繁，请稍后再试。";
        }
        return message;
    }
}
