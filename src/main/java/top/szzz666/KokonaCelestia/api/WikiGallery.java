package top.szzz666.KokonaCelestia.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图鉴条目解析。
 * <p>
 * WIKI 的装饰图鉴（头像框 / 喷漆 / 勋章 / 基板）用 {@code gallerygrid} 布局，
 * 每个条目是一张缩略图加一个名称徽标，形如：
 * <pre>
 * &lt;div class="gallerygrid-item"&gt;
 *   &lt;div class="gallerygrid-imagebox"&gt;&lt;a href="..."&gt;&lt;img src="..."&gt;&lt;/a&gt;&lt;/div&gt;
 *   &lt;div class="gallerygrid-captionbox"&gt;
 *     &lt;span class="badge"&gt;闪耀鎏金&lt;/span&gt;
 *   &lt;/div&gt;
 * &lt;/div&gt;
 * </pre>
 * 这里把每个条目解析成「名称 + 缩略图 URL」，以便做成可点选的图标列表，
 * 而不是把上千行文字塞进一张长图。
 */
public final class WikiGallery {

    private WikiGallery() {
    }

    /**
     * 一个图鉴条目。
     *
     * @param name     显示名称（如「闪耀鎏金」）
     * @param imageUrl 原图地址
     * @param fileName 原始文件名（取自 alt，缩略图 URL 需要它）
     */
    public record Entry(String name, String imageUrl, String fileName) {
    }

    /** 条目块。 */
    private static final Pattern ITEM = Pattern.compile(
            "<div class=\"gallerygrid-item[^\"]*\"[^>]*>(.*?)</div>\\s*</div>\\s*</div>",
            Pattern.DOTALL);

    /** 图片地址。 */
    private static final Pattern IMG = Pattern.compile(
            "<img[^>]+src=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    /** 图片原始文件名。 */
    private static final Pattern ALT = Pattern.compile(
            "<img[^>]+alt=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    /** 名称徽标。 */
    private static final Pattern BADGE = Pattern.compile(
            "<span class=\"badge[^\"]*\"[^>]*>(.*?)</span>", Pattern.DOTALL);

    /**
     * 另一种图鉴结构：{@code div.thumb} + {@code div.gallerytext}。
     * <p>
     * 表情包页面用这种布局：缩略图在 {@code thumb} 里，
     * 名称在 {@code gallerytext} 内的 {@code <big>} 标签，描述在 {@code <small>}。
     */
    private static final Pattern THUMB_ITEM = Pattern.compile(
            "<div class=\"thumb\"[^>]*>.*?<img[^>]*src=\"([^\"]+)\""
                    + ".*?</div>\\s*</div>\\s*<div class=\"gallerytext\">(.*?)</div>",
            Pattern.DOTALL);

    /** gallerytext 里的名称。 */
    private static final Pattern BIG_NAME = Pattern.compile(
            "<big>(.*?)</big>", Pattern.DOTALL);

    /** 兜底：任何带文字的 span（部分页面没有 badge 类）。 */
    private static final Pattern CAPTION = Pattern.compile(
            "gallerygrid-captionbox[^>]*>(.*?)</div>", Pattern.DOTALL);

    /**
     * 解析页面里的图鉴条目。
     *
     * @param html 页面 HTML
     * @return 按出现顺序去重后的条目列表
     */
    public static List<Entry> parse(String html) {
        Map<String, Entry> unique = new LinkedHashMap<>();
        Matcher itemMatcher = ITEM.matcher(html);
        while (itemMatcher.find()) {
            String block = itemMatcher.group(1);

            Matcher imgMatcher = IMG.matcher(block);
            if (!imgMatcher.find()) {
                continue;
            }
            String imageUrl = imgMatcher.group(1);

            // alt 里是原始文件名，构造缩略图地址时需要
            String fileName = "";
            Matcher altMatcher = ALT.matcher(block);
            if (altMatcher.find()) {
                fileName = altMatcher.group(1).trim();
            }

            String name = firstText(BADGE, block);
            if (name == null) {
                name = firstText(CAPTION, block);
            }
            if (name == null || name.isEmpty()) {
                continue;
            }
            unique.putIfAbsent(name, new Entry(name, imageUrl, fileName));
        }
        // 再按 thumb 结构补充（表情包等页面用这种布局）
        Matcher thumbMatcher = THUMB_ITEM.matcher(html);
        while (thumbMatcher.find()) {
            String imageUrl = thumbMatcher.group(1);
            String text = thumbMatcher.group(2);
            Matcher nameMatcher = BIG_NAME.matcher(text);
            if (!nameMatcher.find()) {
                continue;
            }
            String name = nameMatcher.group(1)
                    .replaceAll("<[^>]+>", "")
                    .replace("&nbsp;", " ")
                    .replaceAll("\s+", " ")
                    .trim();
            if (name.isEmpty()) {
                continue;
            }
            unique.putIfAbsent(name, new Entry(name, imageUrl, ""));
        }

        return new ArrayList<>(unique.values());
    }

    /** 取第一个匹配组并清理 HTML 与实体。 */
    private static String firstText(Pattern pattern, String block) {
        Matcher matcher = pattern.matcher(block);
        if (!matcher.find()) {
            return null;
        }
        String text = matcher.group(1)
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&#95;", "_")
                .replaceAll("\\s+", " ")
                .trim();
        return text.isEmpty() ? null : text;
    }

    /** 页面是否像图鉴（含足够多的条目）。 */
    public static boolean looksLikeGallery(String html) {
        return count(html, "gallerygrid-item") >= 6 || count(html, "gallerytext") >= 6;
    }

    private static int count(String s, String sub) {
        int n = 0;
        int i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) {
            n++;
            i += sub.length();
        }
        return n;
    }
}
