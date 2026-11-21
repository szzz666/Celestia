package top.szzz666.KokonaCelestia.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.szzz666.KokonaCelestia.api.DakanuClient;

import java.util.ArrayList;
import java.util.List;

import static top.szzz666.KokonaCelestia.api.DakanuClient.optArray;
import static top.szzz666.KokonaCelestia.api.DakanuClient.optBool;
import static top.szzz666.KokonaCelestia.api.DakanuClient.optDouble;
import static top.szzz666.KokonaCelestia.api.DakanuClient.optInt;
import static top.szzz666.KokonaCelestia.api.DakanuClient.optLong;
import static top.szzz666.KokonaCelestia.api.DakanuClient.optObject;
import static top.szzz666.KokonaCelestia.api.DakanuClient.optString;

/** 玩家档案模型，由 /api/summoner 响应投影而来。 */
public record PlayerProfile(
        String playerId,
        String nickname,
        List<String> previousNames,
        int level,
        int avatarId,
        String presence,
        String privacyStatus,
        int likeCount,
        Rank rank,
        Summary summary,
        List<Mode> modes,
        List<Role> roles,
        List<Match> recentMatches,
        List<Match> historyMatches,
        Meta meta,
        long queriedAt) {

    /**
     * 数据抓取时间距今多久（小时）。
     * <p>
     * 站点并非实时抓取：实测有的玩家数据滞后数天甚至十几天，
     * 卡片需要把这一点告诉用户，否则会被误认为「查询结果不对」。
     *
     * @return 小时数；未知时间返回 -1
     */
    public double dataAgeHours() {
        if (queriedAt <= 0) {
            return -1;
        }
        return (System.currentTimeMillis() - queriedAt) / 3_600_000.0;
    }

    /** 数据是否明显滞后（超过 24 小时）。 */
    public boolean dataStale() {
        double age = dataAgeHours();
        return age >= 24;
    }

    /** 段位信息。 */
    public record Rank(int segment, int topSegment, int historicalSegment, int gradeScore,
                       int topGradeScore, int rankPos, int historicalRankPos,
                       int historicalSeason, int divisionScore) {
    }

    /** 汇总统计。 */
    public record Summary(int rounds, int wins, int losses, double winRate, double kd, double kda,
                          int mvpCount, double mvpRate, double headshotRate, double avgDamage,
                          double roundAveDamage, double avgKill, int kills, int assists, int deaths) {
    }

    /** 模式统计。 */
    public record Mode(String label, int rounds, int wins, double winRate, double kd, double kda,
                       double avgDamage, double roundAveDamage, double avgKill, double headshotRate) {
    }

    /** 角色使用统计。 */
    public record Role(int roleId, String name, int count, int wins, double winRate,
                       double kd, double avgDamage, int maxDamage, int maxKill) {
    }

    /** 单场对局摘要。 */
    public record Match(String roomId, String modeLabel, int mapId, int roleId, String result,
                        int myScore, int opponentScore, int durationSeconds, long playedAt,
                        int kills, int deaths, int assists, int damage,
                        boolean mvp, boolean svp, int divisionScoreDelta, int myRanking) {
    }

    /** 抓取元信息。 */
    public record Meta(String currentSeason, int matchesTotal, boolean cached, String source) {
    }

    /** 从接口响应构建模型。 */
    public static PlayerProfile from(JsonObject root) {
        JsonObject profile = optObject(root, "profile");
        if (profile == null) {
            throw new IllegalStateException("接口未返回玩家档案");
        }
        JsonObject player = optObject(profile, "player");

        List<String> previousNames = new ArrayList<>();
        for (JsonElement element : optArray(player, "previousNames")) {
            if (!element.isJsonNull()) {
                previousNames.add(element.getAsString());
            }
        }

        JsonObject presence = optObject(profile, "presence");
        JsonObject rankJson = optObject(profile, "rank");
        JsonObject summaryJson = optObject(profile, "summary");
        JsonObject metaJson = optObject(profile, "meta");

        Rank rank = new Rank(
                optInt(rankJson, "segment", 0),
                optInt(rankJson, "topSegment", 0),
                optInt(rankJson, "historicalSegment", 0),
                optInt(rankJson, "gradeScore", 0),
                optInt(rankJson, "topGradeScore", 0),
                optInt(rankJson, "rankPos", 0),
                optInt(rankJson, "historicalSegmentPos", 0),
                optInt(rankJson, "historicalSegmentSeason", 0),
                optInt(rankJson, "divisionScore", 0));

        Summary summary = new Summary(
                optInt(summaryJson, "rounds", 0),
                optInt(summaryJson, "wins", 0),
                optInt(summaryJson, "losses", 0),
                optDouble(summaryJson, "winRate", 0),
                optDouble(summaryJson, "kd", 0),
                optDouble(summaryJson, "kda", 0),
                optInt(summaryJson, "mvpCount", 0),
                optDouble(summaryJson, "mvpRate", 0),
                optDouble(summaryJson, "headshotRate", 0),
                optDouble(summaryJson, "avgDamage", 0),
                optDouble(summaryJson, "roundAveDamage", 0),
                optDouble(summaryJson, "avgKill", 0),
                optInt(summaryJson, "kills", 0),
                optInt(summaryJson, "assists", 0),
                optInt(summaryJson, "deaths", 0));

        List<Mode> modes = new ArrayList<>();
        for (JsonElement element : optArray(profile, "modes")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject mode = element.getAsJsonObject();
            modes.add(new Mode(
                    optString(mode, "label", optString(mode, "mode", "未知模式")),
                    optInt(mode, "rounds", 0),
                    optInt(mode, "wins", 0),
                    optDouble(mode, "winRate", 0),
                    optDouble(mode, "kd", 0),
                    optDouble(mode, "kda", 0),
                    optDouble(mode, "avgDamage", 0),
                    optDouble(mode, "roundAveDamage", 0),
                    optDouble(mode, "avgKill", 0),
                    optDouble(mode, "headshotRate", 0)));
        }

        List<Role> roles = new ArrayList<>();
        for (JsonElement element : optArray(profile, "roles")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject role = element.getAsJsonObject();
            roles.add(new Role(
                    optInt(role, "roleId", 0),
                    optString(role, "name", "未知角色"),
                    optInt(role, "count", 0),
                    optInt(role, "wins", 0),
                    optDouble(role, "winRate", 0),
                    optDouble(role, "kd", 0),
                    optDouble(role, "avgDamage", 0),
                    optInt(role, "maxDamage", 0),
                    optInt(role, "maxKill", 0)));
        }

        List<Match> recent = parseMatches(optArray(profile, "matches"));
        List<Match> history = parseMatches(optArray(profile, "historyMatches"));

        Meta meta = new Meta(
                optString(metaJson, "currentSeason", ""),
                optInt(metaJson, "matchesTotal", 0),
                optBool(metaJson, "cached", false),
                optString(metaJson, "source", ""));

        return new PlayerProfile(
                optString(player, "id", ""),
                optString(player, "nickname", ""),
                previousNames,
                optInt(player, "level", 0),
                optInt(player, "avatarId", 0),
                optString(presence, "state", "offline"),
                optString(player, "privacyStatus", "unknown"),
                optInt(player, "laud", 0),
                rank, summary, modes, roles, recent, history, meta,
                optLong(player, "queriedAt", 0));
    }

    private static long optLong(JsonObject json, String key, long fallback) {
        try {
            return json.has(key) && !json.get(key).isJsonNull()
                    ? json.get(key).getAsLong() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static List<Match> parseMatches(JsonArray array) {
        List<Match> list = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject match = element.getAsJsonObject();
            list.add(new Match(
                    optString(match, "roomId", ""),
                    optString(match, "modeLabel", "未知模式"),
                    optInt(match, "mapId", 0),
                    optInt(match, "roleId", 0),
                    optString(match, "result", "unknown"),
                    optInt(match, "myScore", 0),
                    optInt(match, "opponentScore", 0),
                    optInt(match, "durationSeconds", 0),
                    optLong(match, "playedAt", 0),
                    optInt(match, "kills", 0),
                    optInt(match, "deaths", 0),
                    optInt(match, "assists", 0),
                    optInt(match, "damage", 0),
                    optBool(match, "mvp", false),
                    optBool(match, "svp", false),
                    optInt(match, "divisionScoreDelta", 0),
                    optInt(match, "myRanking", 0)));
        }
        return list;
    }

    /** 生涯是否被隐私保护。 */
    public boolean privacyHidden() {
        return "hidden".equalsIgnoreCase(privacyStatus) || "closed".equalsIgnoreCase(privacyStatus);
    }
}
