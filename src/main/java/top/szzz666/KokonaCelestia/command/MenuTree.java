package top.szzz666.KokonaCelestia.command;

import top.szzz666.KokonaCelestia.api.WikiClient;
import top.szzz666.KokonaCelestia.api.WikiGallery;
import top.szzz666.KokonaCelestia.render.WikiCard;

import java.util.ArrayList;
import java.util.List;

import static top.szzz666.KokonaCelestia.command.MenuSession.Item;

/**
 * 菜单树定义。
 * <p>
 * 一级为功能分类，二级为具体功能或 WIKI 页面，三级为页面内部章节。
 * WIKI 里那些动辄上千行的图鉴页（表情包、勋章、基板）在这一层被拆成章节，
 * 用户不需要在一张 4500px 的长图里翻找。
 */
public final class MenuTree {

    private MenuTree() {
    }

    /** 根菜单。 */
    public static MenuSession.Level root() {
        List<Item> items = new ArrayList<>();
        items.add(Item.page("战绩查询", "玩家档案 / 对局 / 排行", "__stats"));
        items.add(Item.page("游戏资料", "角色 / 武器 / 地图 / 玩法", "__data"));
        items.add(Item.page("装饰图鉴", "勋章 / 喷漆 / 头像框 / 基板", "__decor"));
        items.add(Item.page("游戏延伸", "BGM / 壁纸 / 表情包 / 漫画", "__extend"));
        items.add(Item.page("福利与活动", "兑换码 / 活动 / 赛季", "__events"));
        items.add(Item.command("使用说明", "完整指令一览", "__help"));
        return new MenuSession.Level("功能菜单", "共 " + items.size() + " 个分类", items);
    }

    /** 二级菜单：分类内容。 */
    public static MenuSession.Level sub(String key) {
        return switch (key) {
            case "__stats" -> new MenuSession.Level("战绩查询", "卡拉彼丘战绩", List.of(
                    Item.command("玩家概览", "看看 <昵称>", "看看 "),
                    Item.command("对局详情", "看看对局 <昵称> <对局号>", "看看对局 "),
                    Item.command("排行榜", "排行 [页数]", "排行")));

            case "__data" -> new MenuSession.Level("游戏资料", "角色 / 武器 / 地图", List.of(
                    Item.page("角色图鉴", "全部超弦体", "角色筛选"),
                    Item.page("武器图鉴", "主武器 / 副武器", "武器筛选"),
                    Item.page("地图一览", "各模式地图", "地图"),
                    Item.page("角色定位", "6 种定位说明", "超弦体定位"),
                    Item.page("战斗模式", "排位 / 团竞规则", "战斗模式/排位爆破"),
                    Item.page("武器数据", "TTK / DPS 理论值", "主武器理论数据表"),
                    Item.page("信用分", "信誉评级与处罚", "信用分")));

            case "__decor" -> new MenuSession.Level("装饰图鉴", "按分类查看", List.of(
                    Item.page("勋章", "各类奖励勋章", "勋章"),
                    Item.page("喷漆", "段位 / 商城 / 活动", "喷漆"),
                    Item.page("头像框", "移动端专属", "头像框"),
                    Item.page("基板", "宿舍/特别行动奖励", "基板"),
                    Item.page("个性名片", "名片样式", "个性名片"),
                    Item.page("核心", "旧版芯片工艺", "核心")));

            case "__extend" -> new MenuSession.Level("游戏延伸", "官方周边内容", List.of(
                    Item.bgm("BGM 点播", "165 首，可选曲播放"),
                    Item.page("壁纸", "活动 / 赛季 / 节日", "壁纸"),
                    Item.page("表情包", "游戏内 / 装扮 / 画师", "表情包"),
                    Item.page("四格漫画", "全部期数", "官方四格漫画"),
                    Item.page("联动", "历次联动活动", "联动"),
                    Item.page("相关周边", "官方周边商品", "相关周边")));

            case "__events" -> new MenuSession.Level("福利与活动", "兑换与活动", List.of(
                    Item.command("兑换码", "当前可用兑换码", "兑换码"),
                    Item.page("活动", "历史活动一览", "活动"),
                    Item.page("赛季冲刺", "赛季奖励", "赛季冲刺")));

            default -> root();
        };
    }

    /** 图鉴每页展示的条目数（6 列 × 6 行）。 */
    public static final int GALLERY_PAGE_SIZE = 36;

    /**
     * 构造图鉴页的条目菜单。
     * <p>
     * 装饰类页面（头像框 71 个、喷漆 670 个、勋章 352 个）本质是「一堆带图的条目」，
     * 按章节分页看文字没有意义。这里直接按条目分页，每页出一张图片网格，
     * 用户看到实物再点序号看大图。
     *
     * @return 条目菜单；不是图鉴页时返回 null
     */
    public static MenuSession.Level gallery(String page) throws Exception {
        String html = WikiClient.html(page);
        if (!WikiGallery.looksLikeGallery(html)) {
            return null;
        }
        List<WikiGallery.Entry> entries = WikiGallery.parse(html);
        if (entries.isEmpty()) {
            return null;
        }
        List<Item> items = new ArrayList<>();
        int pageNo = 0;
        for (int start = 0; start < entries.size(); start += GALLERY_PAGE_SIZE) {
            int end = Math.min(entries.size(), start + GALLERY_PAGE_SIZE);
            pageNo++;
            items.add(Item.gallery("第 " + pageNo + " 页",
                    "第 " + (start + 1) + "-" + end + " 个", page, pageNo));
        }
        return new MenuSession.Level(page,
                "共 " + entries.size() + " 个条目，分 " + pageNo + " 页", items);
    }

    /**
     * 为某个 WIKI 页面构造章节菜单。
     * <p>
     * 章节数超过阈值时只展示一级章节（h2），避免菜单过长；
     * 否则展示全部层级。条目上标注行数，让用户知道点进去有多少内容。
     */
    public static MenuSession.Level sections(String page) throws Exception {
        List<WikiClient.Section> sections = WikiClient.sections(page);
        List<Item> items = new ArrayList<>();

        for (int i = 0; i < sections.size(); i++) {
            WikiClient.Section section = sections.get(i);
            String detail = section.lines().size() + " 行";
            items.add(Item.section(indent(section.level()) + section.title(), detail, page, i));
        }

        if (items.isEmpty()) {
            // 没有章节结构：直接整页展示
            items.add(Item.section("查看全部内容", "", page, -1));
        }
        return new MenuSession.Level(page, "共 " + Math.max(1, items.size()) + " 个章节", items);
    }

    /** 单个章节超过这个行数时，再拆一层分页（约 4 张图的内容）。 */
    private static final int SECTION_TOO_LONG = 200;

    /**
     * 为超长章节构造二级菜单（分页）。
     * <p>
     * 「排位段位奖励勋章」有 680 行、「角色表情」有 1432 行，
     * 一次性渲染必然被截断。这里按固定行数切成若干段，让用户逐段查看。
     *
     * @return 分页菜单；章节不够长时返回 null（直接展示）
     */
    public static MenuSession.Level paginate(String page, int sectionIndex) throws Exception {
        List<WikiClient.Section> sections = WikiClient.sections(page);
        WikiClient.Section section = sections.get(sectionIndex);
        List<String> lines = section.lines();
        if (lines.size() <= SECTION_TOO_LONG) {
            return null;
        }
        List<Item> items = new ArrayList<>();
        int pageIndex = 0;
        for (int start = 0; start < lines.size(); start += SECTION_TOO_LONG) {
            int end = Math.min(lines.size(), start + SECTION_TOO_LONG);
            pageIndex++;
            items.add(Item.section(
                    "第 " + pageIndex + " 段",
                    "第 " + (start + 1) + "-" + end + " 行",
                    page, sectionIndex * 1000 + pageIndex));
        }
        return new MenuSession.Level(section.title(),
                "内容较长，已分 " + pageIndex + " 段", items);
    }

    /**
     * 取分页内容。
     *
     * @param page         页面标题
     * @param sectionIndex 章节序号
     * @param pageNo       分段序号（从 1 开始）
     */
    public static WikiClient.Article pageContent(String page, int sectionIndex, int pageNo)
            throws Exception {
        List<WikiClient.Section> sections = WikiClient.sections(page);
        WikiClient.Section section = sections.get(sectionIndex);
        List<String> lines = section.lines();
        int start = Math.max(0, (pageNo - 1) * SECTION_TOO_LONG);
        int end = Math.min(lines.size(), start + SECTION_TOO_LONG);
        String title = section.title() + " · 第 " + pageNo + " 段";
        return new WikiClient.Article(title, WikiClient.pageUrl(page),
                new ArrayList<>(lines.subList(start, end)));
    }

    /** 解码分页 index：sectionIndex * 1000 + pageNo。 */
    public static int pageNoOf(int index) {
        return index % 1000;
    }

    /** 解码分页 index：取章节序号。 */
    public static int sectionOf(int index) {
        return index / 1000;
    }

    /** 按标题层级加缩进，让菜单体现出从属关系。 */
    private static String indent(int level) {
        return switch (level) {
            case 2 -> "";
            case 3 -> "  ";
            default -> "    ";
        };
    }

    /** 判断某个 key 是否为内置分类（而非 WIKI 页面）。 */
    public static boolean isCategory(String key) {
        return key != null && key.startsWith("__");
    }

    /**
     * 取章节或整页内容。
     *
     * @param page  页面标题
     * @param index 章节序号；-1 表示整页
     */
    public static WikiClient.Article content(String page, int index) throws Exception {
        if (index < 0) {
            return WikiClient.article(page);
        }
        List<WikiClient.Section> sections = WikiClient.sections(page);
        WikiClient.Section section = sections.get(index);
        return new WikiClient.Article(page + " · " + section.title(),
                WikiClient.pageUrl(page), section.lines());
    }
}
