package top.szzz666.KokonaCelestia.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 卡拉彼丘 WIKI（biligame）读取客户端。
 * <p>
 * 该站启用了腾讯 EdgeOne 防护，缺少 {@code Referer} 时 API 会返回 HTTP 567
 * “请求已被拦截”；带上站点自身的 Referer 后即可正常访问。
 * <p>
 * 页面内容优先取 {@code prop=text}（渲染后的 HTML）：大量页面的数据来自
 * Lua 模块（{@code #invoke}）、Widget 或 SMW 查询，仅读 wikitext 拿不到实际内容。
 */
public final class WikiClient {

    public static final String HOST = "wiki.biligame.com";
    public static final String BASE = "https://wiki.biligame.com/klbq";
    private static final String API = BASE + "/api.php";
    private static final String REFERER = BASE + "/";
    private static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static final int CONNECT_TIMEOUT = 8_000;
    private static final int READ_TIMEOUT = 15_000;

    /** WIKI 内容变动很慢，缓存 30 分钟足够。 */
    private static final long CACHE_TTL_MS = 30 * 60_000L;
    private static final int CACHE_LIMIT = 120;

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private WikiClient() {
    }

    private record Cached(String body, long expiresAt) {
    }

    /** 页面条目。 */
    public record Page(String title, String url) {
    }

    /** 抓取结果：标题 + 纯文本行。 */
    public record Article(String title, String url, List<String> lines) {
        public boolean isEmpty() {
            return lines == null || lines.isEmpty();
        }
    }

    // ==================== 基础请求 ====================

    private static String get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", UA);
        // 缺少 Referer 会被 EdgeOne 拦截（HTTP 567）
        conn.setRequestProperty("Referer", REFERER);
        conn.setRequestProperty("Accept", "application/json,text/html,*/*");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("WIKI 返回 HTTP " + status);
        }
        try (InputStream in = conn.getInputStream()) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String cached(String key, long ttl, Loader loader) throws IOException {
        Cached entry = CACHE.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && entry.expiresAt() > now) {
            return entry.body();
        }
        String body = loader.load();
        if (CACHE.size() > CACHE_LIMIT) {
            CACHE.clear();
        }
        CACHE.put(key, new Cached(body, now + ttl));
        return body;
    }

    @FunctionalInterface
    private interface Loader {
        String load() throws IOException;
    }

    // ==================== 页面抓取 ====================

    /**
     * 取页面渲染后的 HTML。
     * <p>
     * 必须带 {@code redirects=1}：WIKI 里有大量「重定向页」（如「好友亲密度」指向「好友」），
     * 不带该参数只会拿到一段“重定向到：”的提示，取不到正文。
     */
    public static String html(String page) throws IOException {
        return cached("html:" + page, CACHE_TTL_MS, () -> {
            String url = API + "?action=parse&format=json&prop=text&redirects=1&page=" + encode(page);
            JsonObject json = JsonParser.parseString(get(url)).getAsJsonObject();
            if (!json.has("parse")) {
                throw new IOException("WIKI 上没有「" + page + "」这个页面");
            }
            return json.getAsJsonObject("parse").getAsJsonObject("text").get("*").getAsString();
        });
    }

    /** 页面重定向后的真实标题（无重定向时返回自身）。 */
    public static String resolvedTitle(String page) throws IOException {
        return cached("resolved:" + page, CACHE_TTL_MS, () -> {
            String url = API + "?action=parse&format=json&prop=text&redirects=1&page=" + encode(page);
            JsonObject json = JsonParser.parseString(get(url)).getAsJsonObject();
            if (!json.has("parse")) {
                return page;
            }
            return json.getAsJsonObject("parse").get("title").getAsString();
        });
    }

    /** 取页面标题（大小写/重定向归一化）。 */
    public static String canonicalTitle(String page) throws IOException {
        String url = API + "?action=parse&format=json&prop=displaytitle&redirects=1&page=" + encode(page);
        try {
            JsonObject json = JsonParser.parseString(get(url)).getAsJsonObject();
            if (json.has("parse")) {
                return json.getAsJsonObject("parse").get("title").getAsString();
            }
        } catch (RuntimeException ignored) {
        }
        return page;
    }

    // ==================== 搜索 ====================

    /** 在 WIKI 中搜索页面（使用 MediaWiki 全文检索）。 */
    public static List<Page> search(String keyword, int limit) throws IOException {
        String key = "search:" + keyword + ":" + limit;
        return cachedPages(key, () -> {
            String url = API + "?action=query&list=search&format=json&srlimit=" + limit
                    + "&srsearch=" + encode(keyword);
            JsonObject json = JsonParser.parseString(get(url)).getAsJsonObject();
            List<Page> pages = new ArrayList<>();
            JsonObject query = json.has("query") ? json.getAsJsonObject("query") : null;
            if (query == null) {
                return pages;
            }
            for (JsonElement element : query.getAsJsonArray("search")) {
                String title = element.getAsJsonObject().get("title").getAsString();
                pages.add(new Page(title, pageUrl(title)));
            }
            return pages;
        });
    }

    private static List<Page> cachedPages(String key, PageLoader loader) throws IOException {
        Cached entry = CACHE.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && entry.expiresAt() > now) {
            return decodePages(entry.body());
        }
        List<Page> pages = loader.load();
        if (CACHE.size() > CACHE_LIMIT) {
            CACHE.clear();
        }
        CACHE.put(key, new Cached(encodePages(pages), now + CACHE_TTL_MS));
        return pages;
    }

    @FunctionalInterface
    private interface PageLoader {
        List<Page> load() throws IOException;
    }

    private static String encodePages(List<Page> pages) {
        StringBuilder sb = new StringBuilder();
        for (Page page : pages) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(page.title());
        }
        return sb.toString();
    }

    private static List<Page> decodePages(String body) {
        List<Page> pages = new ArrayList<>();
        if (body.isEmpty()) {
            return pages;
        }
        for (String line : body.split("\n")) {
            if (!line.isBlank()) {
                pages.add(new Page(line, pageUrl(line)));
            }
        }
        return pages;
    }

    /** 站内页面链接。 */
    public static String pageUrl(String title) {
        return BASE + "/" + encode(title.replace(' ', '_'));
    }

    // ==================== 文本提取 ====================

    /**
     * 把渲染后的 HTML 转为纯文本行，去掉导航与脚本。
     * <p>
     * 表格单独处理：{@code <tr>} 内把 {@code <td>/<th>} 用 " | " 连接，
     * 这样一行表格在文本里仍然是一行，便于后续按列排版。
     */
    public static List<String> extractLines(String html) {
        String body = stripChrome(html);
        body = flattenTables(body);
        String text = body
                .replaceAll("(?is)<(script|style|noscript)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|li|tr|h[1-6]|table|section)>", "\n")
                .replaceAll("(?i)</t[dh]>", " | ")
                .replaceAll("<[^>]+>", "");

        text = unescape(text);
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw.replaceAll("[ \\t\\u00a0]+", " ").trim();
            line = line.replaceAll("(\\s*\\|\\s*)+$", "").trim();
            if (line.isEmpty()) {
                continue;
            }
            // 过滤导航与目录噪音
            if (line.length() <= 2 && !line.matches(".*[\\u4e00-\\u9fffA-Za-z0-9].*")) {
                continue;
            }
            if (isTocLine(line)) {
                continue;
            }
            lines.add(line);
        }
        return lines;
    }

    /** 去掉页面级导航、面包屑与目录。 */
    private static String stripChrome(String html) {
        String s = html;
        // 目录：MediaWiki 的 #toc 与其包裹容器，内容只是章节索引，对阅读无价值
        s = s.replaceAll("(?is)<div[^>]*id=\"toc\"[^>]*>.*?</div>\\s*</div>", " ");
        s = s.replaceAll("(?is)<div[^>]*id=\"toc\"[^>]*>.*?</div>", " ");
        s = s.replaceAll("(?is)<table[^>]*id=\"toc\"[^>]*>.*?</table>", " ");
        s = s.replaceAll("(?is)<div[^>]*class=\"[^\"]*toc[^\"]*\"[^>]*>.*?</div>", " ");
        // 目录在部分皮肤里是 ul.toc
        s = s.replaceAll("(?is)<ul[^>]*class=\"[^\"]*toc[^\"]*\"[^>]*>.*?</ul>", " ");
        s = s.replaceAll("(?is)<table[^>]*class=\"[^\"]*navbox[^\"]*\"[^>]*>.*?</table>", " ");
        s = s.replaceAll("(?is)<div[^>]*class=\"[^\"]*(navbox|nav-box|breadcrumb)[^\"]*\"[^>]*>.*?</div>", " ");
        s = s.replaceAll("(?is)<span[^>]*class=\"[^\"]*mw-editsection[^\"]*\"[^>]*>.*?</span>", " ");
        return s;
    }

    /** 过滤目录行：形如「1 信用评级」「1.1 信誉评级」的章节索引。 */
    private static boolean isTocLine(String line) {
        return line.matches("^\\d+(\\.\\d+)*\\s+\\S.*") && line.length() <= 30;
    }

    /**
     * 把表格整理成「每行一条、单元格用 | 分隔」的形式。
     * <p>
     * 网页里 {@code <td>} 之间常有换行，直接转文本会把一行表格拆散；
     * 这里先按 {@code <tr>} 切块，块内去掉换行，再用 " | " 连接单元格。
     */
    private static String flattenTables(String html) {
        java.util.regex.Matcher rowMatcher = java.util.regex.Pattern
                .compile("(?is)<tr[^>]*>(.*?)</tr>").matcher(html);
        StringBuilder out = new StringBuilder(html.length());
        int last = 0;
        while (rowMatcher.find()) {
            out.append(html, last, rowMatcher.start());
            String row = rowMatcher.group(1);
            java.util.regex.Matcher cellMatcher = java.util.regex.Pattern
                    .compile("(?is)<t[dh][^>]*>(.*?)</t[dh]>").matcher(row);
            StringBuilder cells = new StringBuilder();
            while (cellMatcher.find()) {
                String cell = cellMatcher.group(1)
                        .replaceAll("(?i)<br\\s*/?>", " ")
                        .replaceAll("<[^>]+>", "")
                        .replaceAll("\\s+", " ")
                        .trim();
                if (cell.isEmpty()) {
                    continue;
                }
                if (cells.length() > 0) {
                    cells.append(" | ");
                }
                cells.append(cell);
            }
            if (cells.length() > 0) {
                out.append("<tr>").append(cells).append("</tr>");
            }
            last = rowMatcher.end();
        }
        out.append(html.substring(last));
        return out.toString();
    }

    /** HTML 实体解码：既处理命名实体，也处理 {@code &#215;} 这类数字实体。 */
    private static String unescape(String text) {
        String s = text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&hellip;", "…")
                .replace("&times;", "×")
                .replace("&middot;", "·")
                .replace("&laquo;", "«")
                .replace("&raquo;", "»");

        // 数字实体：十进制与十六进制
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("&#(x?[0-9a-fA-F]+);").matcher(s);
        StringBuilder sb = new StringBuilder(s.length());
        while (m.find()) {
            String raw = m.group(1);
            int code;
            try {
                code = raw.startsWith("x") || raw.startsWith("X")
                        ? Integer.parseInt(raw.substring(1), 16)
                        : Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                continue;
            }
            // 过滤掉不可打印 / 代理区码点，避免出现乱码方块
            if (code < 32 || (code >= 0xD800 && code <= 0xDFFF) || code > 0x10FFFF) {
                m.appendReplacement(sb, "");
                continue;
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                    new String(Character.toChars(code))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 抓取页面并转为纯文本（自动跟随重定向）。 */
    public static Article article(String page) throws IOException {
        String html = html(page);
        String title = resolvedTitle(page);
        return new Article(title, pageUrl(title), extractLines(html));
    }

    /** 页面中的一个章节。 */
    public record Section(String title, int level, List<String> lines) {
    }

    /**
     * 按 h2/h3 标题把页面切成章节。
     * <p>
     * 这是多级菜单的基础：长页面（表情包 1431 行、基板 2295 行）一次性展示没有意义，
     * 拆成章节后用户可以先选分类再看内容。
     * 首个 h2 之前的内容作为「概览」章节返回。
     */
    public static List<Section> sections(String page) throws IOException {
        String html = html(page);
        String title = resolvedTitle(page);
        return splitSections(html, title);
    }

    /** 章节切分实现。 */
    private static List<Section> splitSections(String html, String pageTitle) {
        List<Section> sections = new ArrayList<>();

        // 在每个标题前插入标记，便于后续按段落切分
        String marked = html.replaceAll(
                "(?is)(<h([234])[^>]*>)(.*?)(</h\\2>)",
                "\u0001$2\u0002$3\u0003");

        String[] chunks = marked.split("\u0001", -1);
        int index = 0;
        for (String chunk : chunks) {
            if (chunk.isEmpty()) {
                index++;
                continue;
            }
            String sectionTitle;
            String body;
            int level = 3;
            if (chunk.startsWith("2\u0002") || chunk.startsWith("3\u0002")
                    || chunk.startsWith("4\u0002")) {
                level = chunk.charAt(0) - '0';
                int sep = chunk.indexOf('\u0003');
                String headHtml = sep > 0 ? chunk.substring(2, sep) : "";
                body = sep > 0 ? chunk.substring(sep + 1) : "";
                sectionTitle = headHtml.replaceAll("<[^>]+>", "")
                        .replace("&nbsp;", " ")
                        .replaceAll("\\s+", " ")
                        // 去掉 [编辑] 链接残留与其包裹的空格
                        .replaceAll("\\s*\\[?编辑\\]?$", "")
                        .replaceAll("\\s*\\[\\d+\\]$", "")
                        .trim();
            } else {
                body = chunk;
                sectionTitle = index == 0 ? "概览" : "";
            }
            index++;

            if (sectionTitle.isEmpty() || isNoiseTitle(sectionTitle)) {
                continue;
            }
            List<String> lines = extractLines(body);
            // 空章节（只有标题没有内容）不进入菜单，避免用户点进去看到空白
            if (lines.isEmpty()) {
                continue;
            }
            sections.add(new Section(sectionTitle, level, lines));
        }
        return sections;
    }

    /** 过滤「目录」这类无实际内容的标题。 */
    private static boolean isNoiseTitle(String title) {
        return title.equals("目录") || title.startsWith("导航") || title.isEmpty();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** 清空缓存（调试用）。 */
    public static void clearCache() {
        CACHE.clear();
    }
}
