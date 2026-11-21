package top.szzz666.KokonaCelestia.command;

import top.szzz666.KokonaCelestia.api.WikiBgm;
import top.szzz666.KokonaCelestia.api.WikiClient;
import top.szzz666.KokonaCelestia.render.BgmCard;
import top.szzz666.KokonaCelestia.render.MenuCard;
import top.szzz666.KokonaCelestia.render.Renderer;
import top.szzz666.KokonaCelestia.render.WikiCard;
import top.szzz666.KokonaCelestia.util.Reply;

import java.util.List;

/**
 * 多级菜单导航。
 * <p>
 * 交互模型：回复序号向下钻取，回复「返回」出栈，回复「取消」退出。
 * 所有层级都渲染成同一套菜单图，用户始终看得到自己在哪一级。
 */
public final class MenuCommands {

    private MenuCommands() {
    }

    /** 打开菜单（根层级）。 */
    public static void open(Reply reply) {
        long group = reply.groupId();
        long user = reply.userId();
        MenuSession.close(group, user);

        MenuSession.Session session = MenuSession.get(group, user);
        session.push(MenuTree.root());
        render(reply, session);
    }

    /**
     * 处理菜单中的一次输入。
     *
     * @return 是否由菜单消费了这条消息（false 表示交给普通指令处理）
     */
    public static boolean handle(Reply reply, String text) {
        long group = reply.groupId();
        long user = reply.userId();
        if (!MenuSession.has(group, user)) {
            return false;
        }
        MenuSession.Session session = MenuSession.get(group, user);
        MenuSession.Level current = session.current();
        if (current == null) {
            return false;
        }

        String input = text == null ? "" : text.trim();
        if (input.isEmpty()) {
            return false;
        }

        // 退出
        if (matches(input, "取消", "退出", "q", "exit", "cancel")) {
            MenuSession.close(group, user);
            reply.text("已退出菜单。随时回复「帮助」重新打开。");
            return true;
        }

        // 返回上一层
        if (matches(input, "返回", "上一级", "back", "b", "0")) {
            if (session.pop()) {
                render(reply, session);
            } else {
                MenuSession.close(group, user);
                reply.text("已退出菜单。");
            }
            return true;
        }

        // 序号选择
        if (!input.matches("\\d{1,2}")) {
            return false;
        }
        int index = Integer.parseInt(input);
        List<MenuSession.Item> items = current.items();
        if (index < 1 || index > items.size()) {
            reply.text("序号超出范围，请回复 1 - " + items.size() + "。");
            return true;
        }

        MenuSession.Item item = items.get(index - 1);
        try {
            dispatch(reply, session, item);
        } catch (Exception e) {
            reply.text("打开失败：" + e.getMessage());
        }
        return true;
    }

    /** 执行条目动作。 */
    private static void dispatch(Reply reply, MenuSession.Session session,
                                 MenuSession.Item item) throws Exception {
        switch (item.action()) {
            case PAGE -> {
                String key = item.page();
                if (MenuTree.isCategory(key)) {
                    session.push(MenuTree.sub(key));
                    render(reply, session);
                    return;
                }
                // 图鉴页优先走条目网格，其余页面走章节列表
                MenuSession.Level gallery = MenuTree.gallery(key);
                session.push(gallery != null ? gallery : MenuTree.sections(key));
                render(reply, session);
            }
            case SECTION -> {
                int index = item.index();
                // 分页内容：index >= 1000 表示「章节 * 1000 + 段号」
                if (index >= 1000) {
                    WikiClient.Article article = MenuTree.pageContent(
                            item.page(), MenuTree.sectionOf(index), MenuTree.pageNoOf(index));
                    reply.image(Renderer.render(new WikiCard(article.title(), article)));
                    return;
                }
                // 长章节先进入分段菜单
                if (index >= 0) {
                    MenuSession.Level paged = MenuTree.paginate(item.page(), index);
                    if (paged != null) {
                        session.push(paged);
                        render(reply, session);
                        return;
                    }
                }
                WikiClient.Article article = MenuTree.content(item.page(), index);
                reply.image(Renderer.render(new WikiCard(article.title(), article)));
            }
            case COMMAND -> {
                // 交给既有指令，保留原行为的完整性
                MenuSession.close(reply.groupId(), reply.userId());
                String command = item.page();
                if ("__help".equals(command)) {
                    Commands.help(reply);
                    return;
                }
                if (command.endsWith(" ")) {
                    reply.text("请直接发送完整指令，例如：" + command.trim() + " 米雪儿");
                    return;
                }
                Commands.rank(reply, "");
            }
            case GALLERY -> {
                java.util.List<top.szzz666.KokonaCelestia.api.WikiGallery.Entry> all =
                        top.szzz666.KokonaCelestia.api.WikiGallery.parse(
                                WikiClient.html(item.page()));
                int pageSize = MenuTree.GALLERY_PAGE_SIZE;
                int from = Math.max(0, (item.index() - 1) * pageSize);
                int to = Math.min(all.size(), from + pageSize);
                reply.image(Renderer.render(new top.szzz666.KokonaCelestia.render.GalleryCard(
                        item.page(), all.subList(from, to), all.size())));
            }
            case BGM -> {
                List<WikiBgm.Track> tracks = WikiBgm.tracks();
                reply.text("回复「BGM 序号」点播，发送「BGM」查看全部曲目。");
                reply.image(Renderer.render(new BgmCard(tracks, 1, 60)));
            }
        }
    }

    /** 渲染当前层级。 */
    private static void render(Reply reply, MenuSession.Session session) {
        MenuSession.Level level = session.current();
        if (level == null) {
            reply.text("菜单已结束。");
            return;
        }
        reply.image(Renderer.render(
                new MenuCard(level, session.breadcrumb(), session.canGoBack())));
    }

    private static boolean matches(String input, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(input)) {
                return true;
            }
        }
        return false;
    }
}
