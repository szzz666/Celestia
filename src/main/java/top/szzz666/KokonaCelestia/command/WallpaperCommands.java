package top.szzz666.KokonaCelestia.command;

import top.szzz666.KokonaCelestia.api.WikiWallpaper;
import top.szzz666.KokonaCelestia.render.Renderer;
import top.szzz666.KokonaCelestia.render.WallpaperCard;
import top.szzz666.KokonaCelestia.util.Reply;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 壁纸指令。
 * <p>
 * 三种用法：
 * <ul>
 *   <li>{@code 壁纸} —— 缩略图网格，带序号</li>
 *   <li>{@code 壁纸 3} —— 取第 3 张原图（1920px）</li>
 *   <li>{@code 壁纸 随机} / {@code 随机壁纸} —— 随机一张原图</li>
 * </ul>
 * 原图直接以 URL 交给 OneBot，由客户端拉取，不占机器人带宽。
 */
public final class WallpaperCommands {

    private WallpaperCommands() {
    }

    /** 每页展示的壁纸数（3 列 × 5 行）。 */
    private static final int PAGE_SIZE = 15;

    /** 处理壁纸指令。 */
    public static void show(Reply reply, String argument) {
        String arg = argument == null ? "" : argument.trim();
        try {
            List<WikiWallpaper.Wallpaper> all = WikiWallpaper.all();
            if (all.isEmpty()) {
                reply.text("暂时没能从 WIKI 取到壁纸。");
                return;
            }

            // 随机
            if (isRandom(arg)) {
                random(reply, all);
                return;
            }

            // 不带参数：出预览网格
            if (arg.isEmpty()) {
                grid(reply, all, 1);
                return;
            }

            // 翻页：壁纸 第2页 / 下一页 / 上一页
            Integer page = parsePage(reply, arg);
            if (page != null) {
                if (page > 0) {
                    grid(reply, all, page);
                }
                return;
            }

            // 纯数字：取第 N 张原图
            if (arg.matches("\\d{1,3}")) {
                int index = Integer.parseInt(arg);
                if (index < 1 || index > all.size()) {
                    reply.text("序号范围是 1 - " + all.size() + "。发送「壁纸」查看全部。");
                    return;
                }
                send(reply, all.get(index - 1));
                return;
            }

            // 按标题查找
            WikiWallpaper.Wallpaper hit = WikiWallpaper.find(all, arg);
            if (hit != null) {
                send(reply, hit);
                return;
            }
            reply.text("没有找到「" + arg + "」这张壁纸。\n"
                    + "发送「壁纸」查看全部，或「随机壁纸」来一张随机的。");
        } catch (Exception e) {
            reply.text("查询失败：" + e.getMessage());
        }
    }

    /** 按序号取原图（裸数字与「壁纸 n」都走这里）。 */
    public static void play(Reply reply, int index) {
        try {
            List<WikiWallpaper.Wallpaper> all = WikiWallpaper.all();
            if (all.isEmpty()) {
                reply.text("暂时没能从 WIKI 取到壁纸。");
                return;
            }
            if (index < 1 || index > all.size()) {
                reply.text("序号范围是 1 - " + all.size() + "。发送「壁纸」查看全部。");
                return;
            }
            send(reply, all.get(index - 1));
        } catch (Exception e) {
            reply.text("查询失败：" + e.getMessage());
        }
    }

    /** 随机发送一张。 */
    public static void random(Reply reply) {
        try {
            List<WikiWallpaper.Wallpaper> all = WikiWallpaper.all();
            if (all.isEmpty()) {
                reply.text("暂时没能从 WIKI 取到壁纸。");
                return;
            }
            random(reply, all);
        } catch (Exception e) {
            reply.text("查询失败：" + e.getMessage());
        }
    }

    private static void random(Reply reply, List<WikiWallpaper.Wallpaper> all) {
        int index = ThreadLocalRandom.current().nextInt(all.size());
        send(reply, all.get(index));
    }

    /** 发送一张原图。 */
    private static void send(Reply reply, WikiWallpaper.Wallpaper wallpaper) {
        // 先说明是哪张，再发图；原图由客户端拉取
        reply.text("壁纸：" + wallpaper.title() + "（" + wallpaper.category() + "）");
        reply.image(wallpaper.fullUrl());
    }

    /** 出预览网格。 */
    private static void grid(Reply reply, List<WikiWallpaper.Wallpaper> all, int page) {
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) PAGE_SIZE));
        int pageNo = Math.min(Math.max(1, page), totalPages);
        int from = (pageNo - 1) * PAGE_SIZE;
        int to = Math.min(all.size(), from + PAGE_SIZE);
        reply.image(Renderer.render(new WallpaperCard(all.subList(from, to), from + 1, all.size())));
        CardContext.remember(reply.groupId(), reply.userId(),
                CardContext.Kind.WALLPAPER, "", pageNo, all.size());
    }

    /**
     * 解析翻页参数。
     *
     * @return 目标页码；不是翻页指令时返回 null
     */
    private static Integer parsePage(Reply reply, String arg) {
        if (arg.equals("下一页") || arg.equals("下页")) {
            CardContext.Entry ctx = CardContext.get(reply.groupId(), reply.userId());
            return (ctx == null ? 0 : ctx.pageNo()) + 1;
        }
        if (arg.equals("上一页") || arg.equals("上页")) {
            CardContext.Entry ctx = CardContext.get(reply.groupId(), reply.userId());
            return Math.max(1, (ctx == null ? 2 : ctx.pageNo()) - 1);
        }
        if (arg.startsWith("第") && arg.endsWith("页")) {
            String num = arg.substring(1, arg.length() - 1).trim();
            if (num.matches("\\d{1,2}")) {
                return Integer.parseInt(num);
            }
            reply.text("页码格式：壁纸 第2页");
            return -1;
        }
        return null;
    }

    private static boolean isRandom(String arg) {
        if (arg.isEmpty()) {
            return false;
        }
        for (String word : new String[]{"随机", "random", "随便", "来一张"}) {
            if (word.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }
}
