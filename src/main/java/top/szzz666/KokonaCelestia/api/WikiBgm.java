package top.szzz666.KokonaCelestia.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 WIKI 的 BGM 页面提取曲目与其音频直链。
 * <p>
 * 页面里每首曲子都是一个指向 {@code patchwiki.biligame.com} 的 mp3 直链，
 * 形如 {@code <a href="...mp3" title="Bgm-曲名.mp3">}。
 * 这些链接可直接被 OneBot 的 record 消息消费，无需中转下载。
 */
public final class WikiBgm {

    private WikiBgm() {
    }

    /** 一首曲目。 */
    public record Track(String name, String url) {
    }

    private static final Pattern LINK = Pattern.compile(
            "<a href=\"(https://patchwiki[^\"]+\\.mp3)\"[^>]*title=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    private static final long CACHE_TTL_MS = 30 * 60_000L;
    private static volatile List<Track> cache;
    private static volatile long cachedAt;

    /** 曲目列表（带缓存，避免每次翻页都重新抓取）。 */
    public static List<Track> tracks() throws Exception {
        List<Track> snapshot = cache;
        long now = System.currentTimeMillis();
        if (snapshot != null && now - cachedAt < CACHE_TTL_MS) {
            return snapshot;
        }
        synchronized (WikiBgm.class) {
            if (cache != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                return cache;
            }
            List<Track> list = parse(WikiClient.html("BGM"));
            cache = list;
            cachedAt = System.currentTimeMillis();
            return list;
        }
    }

    /** 解析页面 HTML，保持出现顺序并去重。 */
    static List<Track> parse(String html) {
        Map<String, Track> unique = new LinkedHashMap<>();
        Matcher matcher = LINK.matcher(html);
        while (matcher.find()) {
            String url = matcher.group(1);
            String name = clean(matcher.group(2));
            if (name.isEmpty() || url.isEmpty()) {
                continue;
            }
            unique.putIfAbsent(name, new Track(name, url));
        }
        return new ArrayList<>(unique.values());
    }

    /** 去掉文件名前后缀与扩展名。 */
    private static String clean(String raw) {
        String s = raw.replace(".mp3", "").replace(".MP3", "");
        s = s.replaceFirst("(?i)^Bgm[-－_ ]?", "");
        return s.trim();
    }

    /**
     * 按序号或名称查找曲目。
     *
     * @param query 序号（从 1 开始）或曲名片段
     * @return 命中的曲目；找不到返回 null
     */
    public static Track find(List<Track> tracks, String query) {
        if (query == null) {
            return null;
        }
        String q = query.trim();
        if (q.isEmpty()) {
            return null;
        }
        // 纯数字按序号
        if (q.matches("\\d{1,3}")) {
            int index = Integer.parseInt(q);
            return index >= 1 && index <= tracks.size() ? tracks.get(index - 1) : null;
        }
        // 名称精确匹配优先
        for (Track track : tracks) {
            if (track.name().equalsIgnoreCase(q)) {
                return track;
            }
        }
        // 其次包含匹配
        for (Track track : tracks) {
            if (track.name().contains(q)) {
                return track;
            }
        }
        return null;
    }

    /** 按名称模糊查找的候选（用于提示）。 */
    public static List<Track> search(List<Track> tracks, String query) {
        List<Track> hits = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return hits;
        }
        String q = query.trim();
        for (Track track : tracks) {
            if (track.name().contains(q)) {
                hits.add(track);
                if (hits.size() >= 8) {
                    break;
                }
            }
        }
        return hits;
    }
}
