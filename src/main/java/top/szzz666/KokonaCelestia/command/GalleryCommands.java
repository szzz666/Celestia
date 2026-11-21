package top.szzz666.KokonaCelestia.command;

import top.szzz666.KokonaCelestia.api.WikiClient;
import top.szzz666.KokonaCelestia.api.WikiGallery;
import top.szzz666.KokonaCelestia.render.GalleryCard;
import top.szzz666.KokonaCelestia.render.MenuCard;
import top.szzz666.KokonaCelestia.render.Renderer;
import top.szzz666.KokonaCelestia.util.Reply;

import java.util.List;

/**
 * 图鉴分页。
 * <p>
 * 图鉴条目数远多于壁纸/BGM，逐条编号没有意义，
 * 因此这里的「序号」指**页码**：回 2 就翻到第 2 页。
 */
public final class GalleryCommands {

    private GalleryCommands() {
    }

    /**
     * 查看单个条目的大图。
     * <p>
     * 图鉴条目数量多（勋章 404、喷漆 670），逐个编号会与分页冲突，
     * 因此单条查看用「名称」定位，不走序号。
     *
     * @param keyword 条目名称（支持模糊匹配）
     */
    public static void item(Reply reply, String page, String keyword) {
        try {
            List<WikiGallery.Entry> all = WikiGallery.parse(WikiClient.html(page));
            if (all.isEmpty()) {
                reply.text("「" + page + "」没有可展示的条目。");
                return;
            }
            WikiGallery.Entry hit = findEntry(all, keyword);
            if (hit == null) {
                reply.text("没有找到「" + keyword + "」。发送「" + page + "」查看全部条目名称。");
                return;
            }
            reply.text(hit.name());
            reply.image(originalUrl(hit.imageUrl()));
        } catch (Exception e) {
            reply.text("查询失败：" + e.getMessage());
        }
    }

    /**
     * 把缩略图地址还原成原图。
     * <p>
     * 图鉴列表用的是 {@code .../thumb/xxx/<w>px-name.png} 缩略图（180px），
     * 单张查看时用原图更清晰（表情包原图 256px，勋章与喷漆更大）。
     * 已经是原图地址则原样返回。
     */
    static String originalUrl(String thumbUrl) {
        if (thumbUrl == null || !thumbUrl.contains("/thumb/")) {
            return thumbUrl;
        }
        String full = thumbUrl.replace("/thumb/", "/");
        // 去掉末段 "/<宽>px-<文件名>"
        int slash = full.lastIndexOf('/');
        if (slash > 0) {
            String tail = full.substring(slash + 1);
            if (tail.matches("\\d+px-.*")) {
                full = full.substring(0, slash);
            }
        }
        return full;
    }

    /** 名称精确匹配优先，其次包含匹配。 */
    private static WikiGallery.Entry findEntry(List<WikiGallery.Entry> all, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String q = keyword.trim();
        for (WikiGallery.Entry e : all) {
            if (e.name().equalsIgnoreCase(q)) {
                return e;
            }
        }
        for (WikiGallery.Entry e : all) {
            if (e.name().contains(q)) {
                return e;
            }
        }
        return null;
    }

    /** 出指定页。 */
    public static void page(Reply reply, String page, int pageNo) {
        try {
            List<WikiGallery.Entry> all = WikiGallery.parse(WikiClient.html(page));
            if (all.isEmpty()) {
                reply.text("「" + page + "」没有可展示的条目。");
                return;
            }
            int pageSize = MenuTree.GALLERY_PAGE_SIZE;
            int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) pageSize));
            if (pageNo < 1 || pageNo > totalPages) {
                reply.text("页码范围是 1 - " + totalPages + "。");
                return;
            }
            int from = (pageNo - 1) * pageSize;
            int to = Math.min(all.size(), from + pageSize);
            reply.image(Renderer.render(new GalleryCard(page, all.subList(from, to), all.size())));
            CardContext.remember(reply.groupId(), reply.userId(),
                    CardContext.Kind.GALLERY, page, pageNo, totalPages);
        } catch (Exception e) {
            reply.text("查询失败：" + e.getMessage());
        }
    }
}
