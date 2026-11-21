package top.szzz666.KokonaCelestia.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 壁纸条目解析。
 * <p>
 * WIKI 的壁纸页把每张图放在 {@code gallerygrid} 之外，用普通 {@code <img>} 展示，
 * 命名规律是 {@code 壁纸-<标题>.<ext>}（写在 alt 属性里）。
 * 页面给出的是 889px 缩略图，原图 1920px，可去掉 {@code /thumb/} 与尺寸后缀还原。
 */
public final class WikiWallpaper {

    private WikiWallpaper() {
    }

    /**
     * 一张壁纸。
     *
     * @param title    去掉前缀的标题
     * @param category 所属章节
     * @param thumbUrl 889px 缩略图
     * @param fullUrl  原图（1920px）
     */
    public record Wallpaper(String title, String category, String thumbUrl, String fullUrl) {
    }

    /** 壁纸图片标签。 */
    private static final Pattern IMG = Pattern.compile("<img[^>]*>", Pattern.CASE_INSENSITIVE);

    /** 章节标题。 */
    private static final Pattern HEADING = Pattern.compile(
            "<h([23])[^>]*>(.*?)</h\\1>", Pattern.DOTALL);

    private static final long CACHE_TTL_MS = 30 * 60_000L;
    private static volatile List<Wallpaper> cache;
    private static volatile long cachedAt;

    /** 全部壁纸（带缓存）。 */
    public static List<Wallpaper> all() throws Exception {
        List<Wallpaper> snapshot = cache;
        if (snapshot != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
            return snapshot;
        }
        synchronized (WikiWallpaper.class) {
            if (cache != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                return cache;
            }
            List<Wallpaper> list = parse(WikiClient.html("壁纸"));
            cache = list;
            cachedAt = System.currentTimeMillis();
            return list;
        }
    }

    /**
     * 解析 HTML。
     * <p>
     * 按 h2/h3 顺序扫描，记录当前章节，再把该章节下的壁纸图收进来。
     * 壁纸的识别依据是 alt 以「壁纸-」开头，其余图片（表情、图标）忽略。
     */
    static List<Wallpaper> parse(String html) {
        Map<String, Wallpaper> unique = new LinkedHashMap<>();

        // 把文档按标题切成片段，逐段处理。
        // 标题有 h2/h3 两级（h3 是 h2 下的年份细分），分类应取最近的 h2，
        // 否则所有壁纸都会被归到最后那个 h3（年份）名下。
        List<int[]> headingPositions = new ArrayList<>();
        List<String> headingTitles = new ArrayList<>();
        List<Integer> headingLevels = new ArrayList<>();
        Matcher headingMatcher = HEADING.matcher(html);
        while (headingMatcher.find()) {
            String title = headingMatcher.group(2)
                    .replaceAll("<[^>]+>", "")
                    .replace("&nbsp;", " ")
                    .replaceAll("\\s+", " ")
                    .replaceAll("\\s*\\[?编辑\\]?$", "")
                    .trim();
            headingPositions.add(new int[]{headingMatcher.start(), headingMatcher.end()});
            headingTitles.add(title);
            headingLevels.add(Integer.parseInt(headingMatcher.group(1)));
        }

        String currentH2 = "";
        for (int i = 0; i < headingPositions.size(); i++) {
            String title = headingTitles.get(i);
            int level = headingLevels.get(i);
            if (level == 2) {
                currentH2 = title;
            }
            // h3 只是年份细分，不改变大类；取当前的 h2 作为分类
            String category = level == 2 ? title : currentH2;
            if (category.isEmpty()) {
                continue;
            }
            int from = headingPositions.get(i)[1];
            int to = i + 1 < headingPositions.size()
                    ? headingPositions.get(i + 1)[0] : html.length();
            String segment = html.substring(from, to);

            Matcher imgMatcher = IMG.matcher(segment);
            while (imgMatcher.find()) {
                String tag = imgMatcher.group();
                String alt = attr(tag, "alt");
                if (!alt.startsWith("壁纸-")) {
                    continue;
                }
                String thumb = attr(tag, "src");
                if (thumb.isEmpty()) {
                    continue;
                }
                String name = alt.replaceFirst("^壁纸-", "")
                        .replaceAll("(?i)\\.(png|jpg|jpeg|webp)$", "")
                        .trim();
                unique.putIfAbsent(name, new Wallpaper(name, category, thumb, toFull(thumb)));
            }
        }
        return new ArrayList<>(unique.values());
    }

    /** 缩略图地址还原为原图地址。 */
    static String toFull(String thumbUrl) {
        if (thumbUrl == null || !thumbUrl.contains("/thumb/")) {
            return thumbUrl;
        }
        String full = thumbUrl.replace("/thumb/", "/");
        // 去掉末尾的 /<宽>px-<文件名>
        int slash = full.lastIndexOf('/');
        int imagesIdx = full.indexOf("/images/");
        if (slash > imagesIdx) {
            String tail = full.substring(slash + 1);
            if (tail.matches("\\d+px-.*")) {
                full = full.substring(0, slash);
            }
        }
        return full;
    }

    /** 取标签属性值。 */
    private static String attr(String tag, String name) {
        Matcher m = Pattern.compile(name + "=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(tag);
        return m.find() ? m.group(1) : "";
    }

    /** 按标题查找。 */
    public static Wallpaper find(List<Wallpaper> list, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String q = keyword.trim();
        for (Wallpaper w : list) {
            if (w.title().equalsIgnoreCase(q)) {
                return w;
            }
        }
        for (Wallpaper w : list) {
            if (w.title().contains(q)) {
                return w;
            }
        }
        return null;
    }

    /** 清空缓存（测试用）。 */
    public static void clearCache() {
        cache = null;
        cachedAt = 0;
    }
}
