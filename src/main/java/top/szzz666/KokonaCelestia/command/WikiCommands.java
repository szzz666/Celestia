package top.szzz666.KokonaCelestia.command;

import top.szzz666.KokonaCelestia.api.WikiBgm;
import top.szzz666.KokonaCelestia.api.WikiClient;
import top.szzz666.KokonaCelestia.config.MyConfig;
import top.szzz666.KokonaCelestia.render.BgmCard;
import top.szzz666.KokonaCelestia.render.RedeemCard;
import top.szzz666.KokonaCelestia.render.Renderer;
import top.szzz666.KokonaCelestia.render.WikiCard;
import top.szzz666.KokonaCelestia.util.Reply;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * WIKI 相关指令。
 * <p>
 * 设计目标是覆盖站点全部内容：
 * <ul>
 *   <li>高频且有固定结构的（兑换码）做专用排版；</li>
 *   <li>其余任意页面统一走 {@code wiki <关键词>}，按搜索结果取最匹配的页面出图。</li>
 * </ul>
 */
public final class WikiCommands {

    private WikiCommands() {
    }

    /**
     * WIKI「游戏延伸」栏目的全部条目。
     * <p>
     * 这些是站点侧边栏固定提供的独立栏目，逐条登记成别名，
     * 保证用户直接输入栏目名即可命中，不必依赖模糊搜索。
     */
    private static final String[][] EXTENSIONS = {
            {"兑换码", "兑换码", "cdk", "礼包码", "兑换"},
            {"BGM", "bgm", "音乐", "主题曲", "原声"},
            {"壁纸", "壁纸", "wallpaper"},
            {"表情包", "表情包", "表情", "贴纸"},
            // 四格漫画 / 联动 / 相关周边 不设直接用别名，
            // 这三项只在「菜单」里提供，避免与菜单入口重复。
    };

    /** 常见页面别名，方便用户少打字。 */
    private static final String[][] ALIASES = {
            {"兑换码", "兑换码", "cdk", "礼包码", "兑换"},
            {"地图", "地图", "图池"},
            {"信用分", "信用分", "信誉", "信用"},
            {"角色筛选", "角色", "超弦体", "角色列表"},
            {"武器筛选", "武器", "枪械"},
            {"勋章", "勋章", "印迹"},
            {"喷漆", "喷漆"},
            {"头像框", "头像框"},
            {"基板", "基板"},
            {"好友亲密度", "亲密度", "好友"},
            {"战斗模式/排位爆破", "排位", "排位爆破", "段位"},
            {"战斗模式/团队乱斗", "团队乱斗", "团竞"},
            {"核心", "核心"},
            {"剧情故事", "剧情", "故事"},
            {"活动", "活动"},
            {"赛季冲刺", "赛季", "赛季冲刺"},
            {"主武器理论数据表", "武器数据", "ttk", "dps"},
            {"信用分", "信用"},
    };

    // ==================== 兑换码 ====================

    public static void redeem(Reply reply) {
        try {
            List<RedeemCard.Code> codes = fetchRedeemCodes();
            if (codes.isEmpty()) {
                reply.text("暂时没能从 WIKI 取到兑换码，请稍后再试。\n"
                        + WikiClient.pageUrl("兑换码"));
                return;
            }
            reply.image(Renderer.render(new RedeemCard(codes)));
        } catch (Exception e) {
            reply.text("查询失败：" + e.getMessage() + "\n" + WikiClient.pageUrl("兑换码"));
        }
    }

    /** 从兑换码页面的正文里解析出「兑换码 / 奖励 / 有效期」。 */
    static List<RedeemCard.Code> fetchRedeemCodes() throws Exception {
        List<String> lines = WikiClient.article("兑换码").lines();
        List<RedeemCard.Code> codes = new ArrayList<>();

        // 页面结构：按月分组，每组依次是 兑换码 -> 奖励 -> 有效期至：xxx
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!looksLikeCode(line)) {
                continue;
            }
            String reward = i + 1 < lines.size() ? lines.get(i + 1).trim() : "";
            String expiry = "";
            for (int j = i + 1; j < Math.min(i + 4, lines.size()); j++) {
                String probe = lines.get(j).trim();
                if (probe.startsWith("有效期")) {
                    expiry = probe.replaceFirst("^有效期至[:：]?", "").trim();
                    break;
                }
            }
            codes.add(new RedeemCard.Code(line, reward, expiry));
        }
        return codes;
    }

    /** 兑换码：全大写字母数字，或全小写字母数字，长度 5-24，不含空格与中文。 */
    private static boolean looksLikeCode(String line) {
        if (line.length() < 5 || line.length() > 24) {
            return false;
        }
        if (!line.matches("[A-Za-z0-9]+")) {
            return false;
        }
        // 至少包含 3 个字母，排除纯数字
        long letters = line.chars().filter(Character::isLetter).count();
        return letters >= 3;
    }

    // ==================== 游戏延伸栏目 ====================

    /**
     * 渲染 WIKI 某个固定栏目的内容。
     *
     * @param reply 回复出口
     * @param page  WIKI 页面标题
     * @param label 出图时显示的名称
     */
    public static void extension(Reply reply, String page, String label) {
        try {
            WikiClient.Article article = WikiClient.article(page);
            if (article.isEmpty()) {
                reply.text("「" + label + "」页面暂无内容，可查看原页：\n" + article.url());
                return;
            }
            reply.image(Renderer.render(new WikiCard(label, article)));
        } catch (Exception e) {
            reply.text("查询失败：" + e.getMessage() + "\n" + WikiClient.pageUrl(page));
        }
    }

    // ==================== BGM 点播 ====================

    /** 每页展示的曲目数。 */
    private static final int BGM_PAGE_SIZE = 60;

    /**
     * BGM 指令：不带参数时列出曲目，带参数时点播。
     * <p>
     * 点播直接把 WIKI 上的 mp3 直链交给 OneBot 的 record 消息，
     * 由客户端自行拉取，不占用机器人带宽与磁盘。
     */
    public static void bgm(Reply reply, String argument) {
        String arg = argument == null ? "" : argument.trim();
        try {
            List<WikiBgm.Track> tracks = WikiBgm.tracks();
            if (tracks.isEmpty()) {
                reply.text("暂时没能从 WIKI 取到曲目。\n" + WikiClient.pageUrl("BGM"));
                return;
            }
            if (arg.isEmpty()) {
                reply.image(Renderer.render(new BgmCard(tracks, 1, BGM_PAGE_SIZE)));
                return;
            }

            // 纯数字：可能是页码或序号，先按序号点播（更常见）
            WikiBgm.Track track = WikiBgm.find(tracks, arg);
            if (track != null) {
                play(reply, track);
                return;
            }

            // 未命中：给出相近曲目，避免只回一句“找不到”
            List<WikiBgm.Track> hits = WikiBgm.search(tracks, arg);
            if (!hits.isEmpty()) {
                StringBuilder sb = new StringBuilder("没有完全匹配的曲目，你是不是想听：\n");
                for (WikiBgm.Track hit : hits) {
                    sb.append("· ").append(hit.name()).append('\n');
                }
                sb.append("发送「BGM 完整曲名」即可点播。");
                reply.text(sb.toString());
                return;
            }
            reply.text("没有找到「" + arg + "」这首曲子。\n"
                    + "发送「BGM」查看全部曲目与序号。");
        } catch (Exception e) {
            reply.text("查询失败：" + e.getMessage() + "\n" + WikiClient.pageUrl("BGM"));
        }
    }

    /**
     * 播放一首曲目。
     * <p>
     * QQ 的语音消息对时长与格式有限制（常见 60 秒内、部分客户端要求 silk），
     * 而 WIKI 上的曲目多在 2-5 分钟。因此这里给出两种模式：
     * {@code record} 直接发语音（短曲可用），{@code link} 发直链（任何长度都能放）。
     * 语音发送失败时也会自动补发直链，不让用户一无所获。
     */
    private static void play(Reply reply, WikiBgm.Track track) {
        String mode = MyConfig.bgmSendMode == null ? "record" : MyConfig.bgmSendMode.trim();
        if ("link".equalsIgnoreCase(mode)) {
            reply.text("♪ " + track.name() + "\n" + track.url());
            return;
        }
        try {
            reply.textWithAudio("♪ " + track.name(), track.url());
        } catch (Exception e) {
            // 语音受限（时长过长 / 格式不支持）时退回直链
            reply.text("♪ " + track.name() + "\n语音发送失败，请直接播放：\n" + track.url());
        }
    }

    // ==================== 通用 WIKI 查询 ====================

    /**
     * {@code wiki <关键词>}：搜索 WIKI 并渲染最匹配页面的内容。
     */
    public static void query(Reply reply, String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            reply.text("用法：wiki <关键词>，例如：wiki 信用分 / wiki 勋章\n"
                    + "也可以直接使用：兑换码、地图");
            return;
        }
        try {
            String title = resolveTitle(kw);
            if (title == null) {
                reply.text("WIKI 上没有找到「" + kw + "」相关的内容。\n"
                        + "可以试试用更短的关键词，或直接打开 " + WikiClient.BASE);
                return;
            }
            WikiClient.Article article = WikiClient.article(title);
            if (article.isEmpty()) {
                reply.text("「" + title + "」页面没有可展示的文字内容，请查看原页：\n"
                        + article.url());
                return;
            }
            reply.image(Renderer.render(new WikiCard(article.title(), article)));
        } catch (Exception e) {
            reply.text("查询失败：" + e.getMessage() + "\n" + WikiClient.BASE);
        }
    }

    /** 解析关键词到页面标题：先查别名表，再走 WIKI 搜索。 */
    static String resolveTitle(String keyword) throws Exception {
        String direct = fromAlias(keyword);
        if (direct != null) {
            return direct;
        }
        List<WikiClient.Page> pages = WikiClient.search(keyword, 5);
        if (pages.isEmpty()) {
            return null;
        }
        // 优先标题完全一致的
        for (WikiClient.Page page : pages) {
            if (page.title().equalsIgnoreCase(keyword)) {
                return page.title();
            }
        }
        // 其次标题包含关键词的
        for (WikiClient.Page page : pages) {
            if (page.title().contains(keyword)) {
                return page.title();
            }
        }
        return pages.get(0).title();
    }

    private static String fromAlias(String keyword) {
        // 游戏延伸栏目优先，避免被通用别名抢先
        for (String[] row : EXTENSIONS) {
            String page = row[0];
            for (int i = 1; i < row.length; i++) {
                if (row[i].equalsIgnoreCase(keyword)) {
                    return page;
                }
            }
        }
        for (String[] row : ALIASES) {
            String page = row[0];
            for (int i = 1; i < row.length; i++) {
                if (row[i].equalsIgnoreCase(keyword)) {
                    return page;
                }
            }
        }
        return null;
    }

    /** 游戏延伸栏目的页面标题列表，供帮助菜单展示。 */
    public static java.util.List<String> extensionPages() {
        java.util.List<String> pages = new ArrayList<>();
        for (String[] row : EXTENSIONS) {
            pages.add(row[0]);
        }
        return pages;
    }

    /** 判断用户输入是否命中了别名表（用于分派到专用功能）。 */
    public static String specialFor(String keyword) {
        if (keyword == null) {
            return null;
        }
        String kw = keyword.trim().toLowerCase();
        for (String alias : new String[]{"兑换码", "cdk", "礼包码", "兑换"}) {
            if (alias.equals(kw)) {
                return "redeem";
            }
        }
        return null;
    }

    private static final Pattern MONTH_LINE = Pattern.compile("\\d{4}年\\d{1,2}月");

    /** 首行是否为月份分组标题（兑换码页专用）。 */
    static boolean isMonthHeading(String line) {
        return MONTH_LINE.matcher(line).find() && line.length() <= 12;
    }
}
