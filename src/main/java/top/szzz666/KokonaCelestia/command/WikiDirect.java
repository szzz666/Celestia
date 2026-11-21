package top.szzz666.KokonaCelestia.command;

import top.szzz666.KokonaCelestia.api.WikiClient;
import top.szzz666.KokonaCelestia.api.WikiGallery;
import top.szzz666.KokonaCelestia.render.GalleryCard;
import top.szzz666.KokonaCelestia.render.MenuCard;
import top.szzz666.KokonaCelestia.render.Renderer;
import top.szzz666.KokonaCelestia.render.WikiCard;
import top.szzz666.KokonaCelestia.util.Reply;

import java.util.List;

/**
 * WIKI 页面的直接指令入口。
 * <p>
 * 菜单是「不知道有什么」时的探索路径；已经知道要查什么时，应当能一条指令直达。
 * 因此菜单里的每个页面都同时注册了直接指令，两者行为一致：
 * <ul>
 *   <li>不带参数 —— 图鉴页出图标网格第一页，其余页面出章节菜单；</li>
 *   <li>带页码 / 章节序号 —— 直接打开对应内容。</li>
 * </ul>
 */
public final class WikiDirect {

    private WikiDirect() {
    }

    /**
     * 查看某个 WIKI 页面。
     *
     * @param reply   回复出口
     * @param page    WIKI 页面标题
     * @param label   出图显示名
     * @param argument 参数：页码（图鉴）或章节序号（其他页面）；空则展示第一层
     */
    public static void show(Reply reply, String page, String label, String argument) {
        String arg = argument == null ? "" : argument.trim();
        try {
            String html = WikiClient.html(page);

            // 图鉴页：出图标网格
            if (WikiGallery.looksLikeGallery(html)) {
                List<WikiGallery.Entry> all = WikiGallery.parse(html);
                if (all.isEmpty()) {
                    reply.text("「" + label + "」暂时没有可展示的条目。");
                    return;
                }
                int pageNo = 1;
                if (!arg.isEmpty()) {
                    if (!arg.matches("\\d{1,2}")) {
                        reply.text("页码需要是数字，例如：" + label + " 2");
                        return;
                    }
                    pageNo = Integer.parseInt(arg);
                }
                int pageSize = MenuTree.GALLERY_PAGE_SIZE;
                int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) pageSize));
                if (pageNo < 1 || pageNo > totalPages) {
                    reply.text("页码范围是 1 - " + totalPages + "。");
                    return;
                }
                int from = (pageNo - 1) * pageSize;
                int to = Math.min(all.size(), from + pageSize);
                reply.image(Renderer.render(new GalleryCard(
                        label, all.subList(from, to), all.size())));
                return;
            }

            // 普通页面：不带参数时列出章节，带序号时打开该章节
            List<WikiClient.Section> sections = WikiClient.sections(page);
            if (sections.isEmpty()) {
                WikiClient.Article article = WikiClient.article(page);
                reply.image(Renderer.render(new WikiCard(label, article)));
                return;
            }
            if (arg.isEmpty()) {
                MenuSession.Level level = MenuTree.sections(page);
                reply.image(Renderer.render(new MenuCard(level, "指令 · " + label, false)));
                return;
            }
            if (!arg.matches("\\d{1,2}")) {
                reply.text("章节序号需要是数字，例如：" + label + " 2");
                return;
            }
            int index = Integer.parseInt(arg);
            if (index < 1 || index > sections.size()) {
                reply.text("章节范围是 1 - " + sections.size() + "。");
                return;
            }
            WikiClient.Section section = sections.get(index - 1);
            // 章节过长时提示分段，避免出图被截断
            if (section.lines().size() > 200) {
                int pages = (int) Math.ceil(section.lines().size() / 200.0);
                reply.text("「" + section.title() + "」共 " + section.lines().size()
                        + " 行，已分 " + pages + " 段。\n"
                        + "发送「" + label + " " + index + " 1」查看第 1 段。");
                return;
            }
            reply.image(Renderer.render(new WikiCard(
                    label + " · " + section.title(),
                    new WikiClient.Article(section.title(),
                            WikiClient.pageUrl(page), section.lines()))));
        } catch (Exception e) {
            reply.text("查询失败：" + e.getMessage() + "\n" + WikiClient.pageUrl(page));
        }
    }
}
