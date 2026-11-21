package top.szzz666.KokonaCelestia.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.szzz666.KokonaCelestia.util.Http;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Dakanu 公开接口封装，带短周期内存缓存以减轻上游压力。 */
public final class DakanuClient {

    public static final String HOST = "www.dakanu.com";
    public static final String SITE = "https://www.dakanu.com";

    /** 默认缓存时长，配置项未加载或取值非法时使用。 */
    private static final long DEFAULT_TTL_MS = 60_000L;
    private static final int CACHE_LIMIT = 200;

    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private DakanuClient() {
    }

    private record CacheEntry(String json, long expiresAt) {
    }

    /** 允许抛出 IOException 的加载器。 */
    @FunctionalInterface
    private interface Loader<T> {
        T load() throws IOException;
    }

    /**
     * 读取 cache.ttl-seconds 配置（单位秒），每次取用时读取静态字段，
     * 因此修改配置后无需重启即可生效。取值非法时回退到默认值。
     */
    private static long cacheTtlMs() {
        int seconds = top.szzz666.KokonaCelestia.config.MyConfig.cacheTtlSeconds;
        return seconds > 0 ? seconds * 1000L : DEFAULT_TTL_MS;
    }

    /** 清空缓存（供调试与配置变更后立即生效使用）。 */
    public static void clearCache() {
        CACHE.clear();
    }

    private static JsonObject cachedJson(String key, long ttl, Loader<JsonObject> loader) throws IOException {
        CacheEntry entry = CACHE.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && entry.expiresAt() > now) {
            return JsonParser.parseString(entry.json()).getAsJsonObject();
        }
        JsonObject value = loader.load();
        if (CACHE.size() > CACHE_LIMIT) {
            CACHE.clear();
        }
        // TTL 从“结果返回”时开始计算：上游一次请求可能需要数秒，
        // 若从发起时刻计算，慢请求会让缓存在写入瞬间就已过期。
        CACHE.put(key, new CacheEntry(value.toString(), System.currentTimeMillis() + ttl));
        return value;
    }

    /** 写入缓存（供强制刷新后覆写旧条目）。 */
    private static void putCache(String key, JsonObject value) {
        if (CACHE.size() > CACHE_LIMIT) {
            CACHE.clear();
        }
        CACHE.put(key, new CacheEntry(value.toString(), System.currentTimeMillis() + cacheTtlMs()));
    }

    private static JsonObject getJson(String path) throws IOException {
        return getJson(path, 0);
    }

    /** 带自定义读超时的请求；{@code readTimeoutMs <= 0} 用默认值。 */
    private static JsonObject getJson(String path, int readTimeoutMs) throws IOException {
        Http.Response response = readTimeoutMs > 0
                ? Http.get(HOST, path, readTimeoutMs)
                : Http.get(HOST, path);
        if (!response.ok()) {
            throw new IOException("接口返回 HTTP " + response.status() + "：" + truncate(response.text()));
        }
        String text = response.text();
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(text);
        } catch (RuntimeException e) {
            throw new IOException("接口返回内容无法解析：" + truncate(text));
        }
        if (!parsed.isJsonObject()) {
            throw new IOException("接口返回内容格式异常");
        }
        JsonObject json = parsed.getAsJsonObject();
        if (json.has("ok") && !json.get("ok").getAsBoolean()) {
            throw new IOException(optString(json, "error", "查询失败"));
        }
        return json;
    }

    /** 按昵称 / UID 搜索玩家候选。 */
    public static List<PlayerSuggestion> search(String query) throws IOException {
        JsonObject json = cachedJson("search:" + query, cacheTtlMs(),
                () -> getJson("/api/search/players?q=" + encode(query)));
        List<PlayerSuggestion> list = new ArrayList<>();
        for (JsonElement element : optArray(json, "suggestions")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            list.add(new PlayerSuggestion(
                    optString(item, "playerId", ""),
                    optString(item, "nickname", ""),
                    optString(item, "matchedName", ""),
                    optInt(item, "level", 0),
                    optInt(item, "icon", 0),
                    optString(item, "matchedBy", ""),
                    optBool(item, "exact", false)));
        }
        return list;
    }

    /** 拉取玩家完整档案（走缓存）。 */
    public static JsonObject summoner(String playerId) throws IOException {
        return cachedJson("summoner:" + playerId, cacheTtlMs(),
                () -> getJson("/api/summoner/" + encode(playerId) + "?region=cn"));
    }

    /**
     * 强制刷新玩家数据。
     * <p>
     * 站点并非实时同步：数据由站点定期抓取，实测有玩家滞后十几天。
     * 页面上的「更新数据」按钮走的就是 {@code ?refresh=1}，服务端会同步重新抓取，
     * 因此这个请求明显更慢（数秒到二十几秒）。
     * <p>
     * 该调用不进缓存，且会覆写同一玩家的缓存条目，避免刚刷完又读到旧数据。
     */
    public static JsonObject summonerRefresh(String playerId) throws IOException {
        // 实测该接口成功率仅约 1/3（另外 2/3 返回 524），重试会把等待翻倍，
        // 收益不抵代价：失败就快速回退缓存，由调用方向用户说明。
        JsonObject fresh = getJson(
                "/api/summoner/" + encode(playerId) + "?region=cn&refresh=1", 40_000);
        putCache("summoner:" + playerId, fresh);
        return fresh;
    }

    /** 拉取对局详情。 */
    public static JsonObject matchDetail(String roomId) throws IOException {
        return cachedJson("match:" + roomId, cacheTtlMs(),
                () -> getJson("/api/match-detail/" + encode(roomId) + "?v=20261119a"));
    }

    /** 拉取排行榜。 */
    public static JsonObject rankings(int type, int page, int pageSize) throws IOException {
        return cachedJson("rank:" + type + ":" + page + ":" + pageSize, cacheTtlMs(),
                () -> getJson("/api/rankings?type=" + type + "&page=" + page + "&page_size=" + pageSize));
    }

    /**
     * 角色数据与地图阵容（对应站点 /roles 页面）。
     * <p>
     * 返回体含 {@code entries}（角色排行）与 {@code mapStats}（地图阵容），
     * 数据集较大且更新频率低，缓存 10 分钟。
     */
    public static JsonObject rankingsForRoles() throws IOException {
        return cachedJson("rolesRanking", 600_000L,
                () -> getJson("/api/roles/ranking?min_games=3"));
    }

    /** 当前赛季信息（含结束时间）。 */
    public static JsonObject currentSeason() throws IOException {
        return cachedJson("season", 300_000L, () -> getJson("/api/seasons/current"));
    }

    // 以下几项是几乎不变的字典数据（赛季起止、角色/模式表、榜单类型），
    // 使用独立的固定长 TTL，不跟随 cache.ttl-seconds，避免频繁回源。

    /** 角色 / 模式字典。 */
    public static JsonObject meta() throws IOException {
        return cachedJson("meta", 600_000L, () -> getJson("/api/meta"));
    }

    /** 排行榜类型列表。 */
    public static JsonArray rankTypes() throws IOException {
        JsonObject json = cachedJson("rankTypes", 600_000L, () -> getJson("/api/rankings/types"));
        return optArray(json, "types");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() > 120 ? flat.substring(0, 120) + "…" : flat;
    }

    // ==================== Gson 便捷方法 ====================

    public static String optString(JsonObject json, String key, String fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(key).getAsString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static double optDouble(JsonObject json, String key, double fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(key).getAsDouble();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static int optInt(JsonObject json, String key, int fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(key).getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static long optLong(JsonObject json, String key, long fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(key).getAsLong();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static boolean optBool(JsonObject json, String key, boolean fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(key).getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static JsonObject optObject(JsonObject json, String key) {
        if (json == null || !json.has(key) || !json.get(key).isJsonObject()) {
            return null;
        }
        return json.getAsJsonObject(key);
    }

    public static JsonArray optArray(JsonObject json, String key) {
        if (json == null || !json.has(key) || !json.get(key).isJsonArray()) {
            return new JsonArray();
        }
        return json.getAsJsonArray(key);
    }

    /** 结论：搜索结果条目。 */
    public record PlayerSuggestion(String playerId, String nickname, String matchedName,
                                   int level, int icon, String matchedBy, boolean exact) {

        public boolean matchedByUid() {
            return "uid".equals(matchedBy);
        }
    }
}
