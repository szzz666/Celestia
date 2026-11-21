package top.szzz666.KokonaCelestia.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色数据与地图阵容（对应 dakanu.com/roles 页面）。
 * <p>
 * 数据来自 {@code /api/roles/ranking}，是站点基于本地缓存的对局统计
 * （{@code coveredMatches} 约 12 万场排位）。响应里除了角色排行，
 * 还带 {@code mapStats}：每张地图的胜率与攻防双方的常见阵容。
 */
public final class RolesClient {

    private RolesClient() {
    }

    /**
     * 噪音过滤阈值。
     * <p>
     * 接口会返回只有 3-4 场的新角色（胜率偶然很高），以及 roleId=0 的无效项。
     * 直接按胜率排序会让这些条目挤掉真正有参考价值的角色，
     * 因此只保留样本量足够的。
     */
    private static final int MIN_GAMES = 1000;

    /** 角色统计。 */
    public record RoleStat(int roleId, String name, int games, double winRate,
                           double appearanceRate, double winRateDelta) {
    }

    /** 某一方的阵容。 */
    public record Lineup(String side, double winRate, List<RoleStat> roles) {
    }

    /** 地图统计。 */
    public record MapStat(int mapId, String name, int games, double winRate,
                          Lineup attack, Lineup defense) {
    }

    /** 完整数据集。 */
    public record Snapshot(int coveredMatches, String day, List<RoleStat> roles,
                           List<MapStat> maps) {
    }

    /** 拉取并整理数据。 */
    public static Snapshot fetch() throws IOException {
        JsonObject json = DakanuClient.rankingsForRoles();
        JsonObject meta = DakanuClient.meta();
        JsonObject roleNames = DakanuClient.optObject(meta, "roles");

        int covered = DakanuClient.optInt(json, "coveredMatches", 0);
        String day = DakanuClient.optString(json, "day", "");

        // 角色排行：过滤小样本与无效 ID
        List<RoleStat> roles = new ArrayList<>();
        for (JsonElement element : DakanuClient.optArray(json, "entries")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject e = element.getAsJsonObject();
            int roleId = DakanuClient.optInt(e, "roleId", 0);
            int games = DakanuClient.optInt(e, "games", 0);
            if (roleId <= 0 || games < MIN_GAMES) {
                continue;
            }
            String name = DakanuClient.optString(roleNames, String.valueOf(roleId), "");
            if (name.isBlank()) {
                name = "角色 " + roleId;
            }
            roles.add(new RoleStat(
                    roleId, name, games,
                    DakanuClient.optDouble(e, "winRate", 0),
                    DakanuClient.optDouble(e, "appearanceRate", 0),
                    DakanuClient.optDouble(e, "winRateDelta", 0)));
        }
        roles.sort((a, b) -> Double.compare(b.winRate(), a.winRate()));

        // 地图统计
        List<MapStat> maps = new ArrayList<>();
        for (JsonElement element : DakanuClient.optArray(json, "mapStats")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject m = element.getAsJsonObject();
            int mapId = DakanuClient.optInt(m, "mapId", 0);
            // 站点地图 ID 从 201 开始（201=404基地 … 220=莱布伦城），
            // 200 及以下是「未知地图」等无效项，没有对应图片与名称
            if (mapId < 201 || mapId > 220) {
                continue;
            }
            Lineup attack = null;
            Lineup defense = null;
            for (JsonElement sideElement : DakanuClient.optArray(m, "sides")) {
                if (!sideElement.isJsonObject()) {
                    continue;
                }
                JsonObject side = sideElement.getAsJsonObject();
                String sideKey = DakanuClient.optString(side, "side", "");
                List<RoleStat> picks = new ArrayList<>();
                for (JsonElement pickElement : DakanuClient.optArray(side, "roles")) {
                    if (!pickElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject pick = pickElement.getAsJsonObject();
                    int roleId = DakanuClient.optInt(pick, "roleId", 0);
                    String name = DakanuClient.optString(roleNames, String.valueOf(roleId), "");
                    picks.add(new RoleStat(roleId,
                            name.isBlank() ? "角色 " + roleId : name,
                            DakanuClient.optInt(pick, "games", 0),
                            DakanuClient.optDouble(pick, "winRate", 0),
                            DakanuClient.optDouble(pick, "pickRate", 0), 0));
                }
                picks.sort((a, b) -> Double.compare(b.appearanceRate(), a.appearanceRate()));
                Lineup lineup = new Lineup(sideKey,
                        DakanuClient.optDouble(side, "winRate", 0), picks);
                if ("attack".equals(sideKey)) {
                    attack = lineup;
                } else if ("defense".equals(sideKey)) {
                    defense = lineup;
                }
            }
            maps.add(new MapStat(mapId, mapName(mapId),
                    DakanuClient.optInt(m, "games", 0),
                    DakanuClient.optDouble(m, "winRate", 0), attack, defense));
        }
        maps.sort((a, b) -> Integer.compare(b.games(), a.games()));

        return new Snapshot(covered, day, roles, maps);
    }

    /** 地图名（与站点 mapNames 表一致）。 */
    static String mapName(int mapId) {
        String[] names = {
                "404基地", "88区", "欧拉港口", "风曳镇", "空间实验室", "柯西街区", "科斯迷特",
                "2号仓库", "鹿鹿潮玩区", "水下发电厂", "码头小镇", "花明弓道馆", "离境区",
                "亚克萨工厂", "簌雪东区", "洛希街区", "迦纳古城", "极地研究所", "奥卡努斯", "莱布伦城"
        };
        int index = mapId - 201;
        return index >= 0 && index < names.length ? names[index] : "地图 " + mapId;
    }
}
