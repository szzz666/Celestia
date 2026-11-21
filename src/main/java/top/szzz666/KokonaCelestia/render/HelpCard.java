package top.szzz666.KokonaCelestia.render;

import top.szzz666.KokonaCelestia.config.MyConfig;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;

/** “帮助”指令菜单出图。 */
public final class HelpCard extends CardRenderer {
    /**
     * 一条指令的展示信息。
     *
     * @param command     主指令名
     * @param aliases     别名（取自配置，可为空）
     * @param args        参数说明
     * @param description 功能说明
     * @param example     使用示例
     */
    private record Item(String command, String aliases, String args,
                        String description, String example) {
        /** 主名 + 别名的完整展示串，例如「兑换码 cdk / 礼包码」。 */
        String fullName() {
            return aliases == null || aliases.isBlank()
                    ? command : command + " " + aliases;
        }
    }

    /**
     * 指令一览。
     * <p>
     * 每条依次为：主指令名 / 别名 / 参数 / 说明 / 示例。
     * 别名从 {@code MyConfig} 动态读取，改配置后帮助图会同步更新。
     */
    private static Item[] items() {
        return new Item[] {
            // ---- 通用 ----
            new Item("帮助", alias(MyConfig.helpCommands), "无", "查看本指令一览", "帮助"),
            new Item("菜单", alias(MyConfig.menuCommands), "无",
                    "逐层浏览全部内容（含漫画 / 联动 / 周边）", "菜单"),

            // ---- 战绩 ----
            new Item("看看", alias(MyConfig.profileCommands), "<玩家昵称 / UID>",
                    "玩家档案概览：段位、胜率、KDA、最近战绩", "看看米雪儿"),
            new Item("看看对局", alias(MyConfig.matchCommands), "<玩家昵称> <对局号>",
                    "单场对局详情，支持短号尾数匹配", "看看对局米雪儿114514"),
            new Item("排行", alias(MyConfig.rankCommands), "[页数]",
                    "全服段位排行榜，默认第 1 页", "排行2"),

            // ---- WIKI ----
            new Item("wiki", alias(MyConfig.wikiCommands), "<关键词>",
                    "查询 WIKI 任意页面", "wiki 信用分"),
            new Item("兑换码", alias(MyConfig.redeemCommands), "无",
                    "当前可用兑换码，已自动过滤失效", "兑换码"),

            // ---- 游戏延伸 ----
            new Item("壁纸", alias(MyConfig.wallpaperCommands), "[序号/随机]",
                    "壁纸列表、按序号取原图、随机一张", "壁纸 3"),
            new Item("随机壁纸", alias(MyConfig.randomWallpaperCommands), "无",
                    "随机发送一张 1920px 原图", "随机壁纸"),
            new Item("BGM", alias(MyConfig.bgmCommands), "[序号/曲名]",
                    "曲目列表与点播", "BGM 5"),
            new Item("表情包", alias(MyConfig.emojiCommands), "[页码/名称]",
                    "官方表情包合集", "表情包"),
            new Item("角色数据", alias(MyConfig.rolesCommands), "无",
                    "角色胜率排行与地图攻防阵容", "角色数据"),
            new Item("奇点分", alias(MyConfig.singularityCommands), "无",
                    "进入奇点所需分数与第 500 名分界线", "奇点分"),
        };
    }

    /**
     * 从配置取别名，拼成「别名1 / 别名2」。
     * <p>
     * 第一个名字是主名（已在指令名前显示），这里只返回其余别名。
     */
    private static String alias(java.util.List<String> names) {
        if (names == null || names.size() <= 1) {
            return "";
        }
        return String.join(" / ", names.subList(1, names.size()));
    }

    @Override
    protected String subtitleTitle() {
        return "使用说明";
    }

    private static final int HERO_HEIGHT = 108;
    private static final int SECTION_GAP = 16;
    private static final int COMMAND_ROW_HEIGHT = 52;

    @Override
    protected int layoutHeight() {
        int y = HEADER_HEIGHT;
        y += HERO_HEIGHT + SECTION_GAP;
        y += BAR_HEIGHT + 12 + items().length * COMMAND_ROW_HEIGHT + 16;
        y += FOOTER_HEIGHT;
        return y;
    }

    @Override
    protected int drawBody(Canvas canvas, int top) {
        int x = Theme.MARGIN;
        int w = Theme.WIDTH - Theme.MARGIN * 2;
        int y = top;

        y = hero(canvas, x, y, w);
        y += SECTION_GAP;
        y = commandList(canvas, x, y, w);
        return y;
    }

    private int hero(Canvas canvas, int x, int y, int w) {
        int h = HERO_HEIGHT;
        canvas.glassCard(x, y, w, h, 16);
        Graphics2D g = canvas.raw();

        // 右上角装饰渐变
        g.setPaint(new GradientPaint(x + w - 260, y, new Color(255, 114, 50, 0),
                x + w, y + h, new Color(255, 114, 50, 34)));
        g.fill(Canvas.roundRect(x, y, w, h, 16));

        canvas.text(Theme.brand(), Theme.font(28, true), Theme.INK, x + 26, y + 50);
        canvas.text("直接发送指令即可查询，无需 @机器人 · 发送「菜单」可逐层浏览",
                Theme.font(13, false), new Color(100, 116, 139, 220), x + 26, y + 76);

        String stamp = "Strinova · Dakanu 数据源";
        canvas.textRight(stamp, Theme.num(12, false), new Color(100, 116, 139, 180),
                x + w - 26, y + 46);
        canvas.textRight("指令一览", Theme.font(15, true), Theme.AMBER, x + w - 26, y + 74);
        return y + h;
    }

    private int commandList(Canvas canvas, int x, int y, int w) {
        int innerTop = sectionBar(canvas, x, y, w, "指令", "COMMANDS") + 12;

        // 取一次，避免循环内重复构建
        Item[] list = items();
        int rowH = COMMAND_ROW_HEIGHT;
        int cardH = list.length * rowH + 16;
        canvas.glassCard(x, innerTop, w, cardH, 14);

        float rowY = innerTop + 12;
        for (int i = 0; i < list.length; i++) {
            Item item = list[i];
            if (i > 0) {
                canvas.divider(x + 18, rowY, w - 36, Theme.LINE);
            }
            float baseY = rowY + 21;

            // 指令名
            canvas.text(item.command(), Theme.font(15, true), Theme.INK, x + 22, baseY);
            float nameWidth = canvas.width(item.command(), Theme.font(15, true));

            // 别名（灰色小字，跟在指令名后）
            float aliasX = x + 22 + nameWidth + 8;
            if (item.aliases() != null && !item.aliases().isBlank()) {
                canvas.text(item.aliases(), Theme.font(11, false),
                        new Color(148, 163, 184, 235), aliasX, baseY);
                aliasX += canvas.width(item.aliases(), Theme.font(11, false)) + 10;
            }

            // 参数
            canvas.text(item.args(), Theme.num(11, false), new Color(100, 116, 139, 210),
                    aliasX, baseY);

            // 说明
            canvas.text(item.description(), Theme.font(11, false),
                    new Color(71, 85, 105, 230), x + 22, baseY + 19);

            // 示例
            String example = "例：" + item.example();
            canvas.textRight(example, Theme.font(11, false), Theme.ACCENT, x + w - 22, baseY + 4);

            rowY += rowH;
        }
        return innerTop + cardH;
    }

}
