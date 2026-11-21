package top.szzz666.KokonaCelestia.render;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;

/** “帮助”指令菜单出图。 */
public final class HelpCard extends CardRenderer {

    private record Item(String command, String args, String description, String example) {
    }

    private static final Item[] ITEMS = {
            new Item("看看", "<玩家昵称 / UID>", "查询玩家档案概览：段位、胜率、KDA、最近战绩", "看看米雪儿"),
            new Item("看看对局", "<玩家昵称> <对局号>", "查询单场对局详情，支持短号尾数匹配", "看看对局米雪儿114514"),
            new Item("排行", "[页数]", "查询全服段位排行榜，默认第 1 页", "排行2"),
            new Item("兑换码", "无", "查看当前可用的游戏兑换码，已自动过滤失效项", "兑换码"),
            new Item("BGM", "[序号/曲名]", "查看曲目或点播：BGM 查看列表，BGM 5 播放", "BGM 5"),
            new Item("wiki", "<关键词>", "查询 WIKI 任意页面：地图、信用分、勋章、角色、剧情等", "wiki 信用分"),
            new Item("勋章 / 喷漆 / 头像框 / 基板", "[页码]", "装饰图鉴，直接出图，带页码可翻页", "勋章 2"),
            new Item("壁纸 / 表情包", "[页码]", "游戏延伸内容，直接查", "壁纸"),
            new Item("地图 / 角色 / 武器", "[序号]", "WIKI 其他栏目，带序号看具体章节", "地图 2"),
            new Item("信用分 / 活动 / 赛季 / 核心", "[序号]", "玩法与规则说明", "信用分"),
            new Item("帮助", "无", "查看全部指令一览", "帮助"),
            new Item("菜单", "无", "逐层浏览全部内容（含漫画 / 联动 / 周边）", "菜单"),
    };

    private static final String[][] TIPS = {
            {"防误触", "指令需精确匹配前缀，群聊日常对话不会触发机器人。"},
            {"空格可省略", "“看看米雪儿”与“看看 米雪儿”效果相同，带不带空格都行。"},
            {"同名玩家", "匹配到多个同名玩家时会列出候选，回复序号选择，回复「取消」放弃。"},
            {"昵称含空格", "昵称可以带空格，机器人会把指令后的内容整体当作昵称。"},
            {"短号查询", "对局号支持只写尾数，会自动匹配最近的对局。"},
            {"隐私保护", "玩家开启生涯隐私时，只能看到基础档案。"},
            {"WIKI 功能", "wiki 支持查询 WIKI 全部 1000+ 页面，找不到时可用更短的关键词。"},
            {"长页面", "图鉴类页面内容过多时只显示前 160 行，末尾会提示并附原文链接。"},
            {"响应速度", "高峰期上游接口较慢，图片通常需要几秒生成。"},
    };

    @Override
    protected String subtitleTitle() {
        return "使用说明";
    }

    private static final int HERO_HEIGHT = 108;
    private static final int SECTION_GAP = 16;
    private static final int COMMAND_ROW_HEIGHT = 62;
    private static final int TIP_ROW_HEIGHT = 34;

    @Override
    protected int layoutHeight() {
        int y = HEADER_HEIGHT;
        y += HERO_HEIGHT + SECTION_GAP;
        y += BAR_HEIGHT + 12 + ITEMS.length * COMMAND_ROW_HEIGHT + 16;
        y += SECTION_GAP;
        y += BAR_HEIGHT + 12 + TIPS.length * TIP_ROW_HEIGHT + 18;
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
        y += SECTION_GAP;
        y = tips(canvas, x, y, w);
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

        int rowH = COMMAND_ROW_HEIGHT;
        int cardH = ITEMS.length * rowH + 16;
        canvas.glassCard(x, innerTop, w, cardH, 14);

        float rowY = innerTop + 12;
        for (int i = 0; i < ITEMS.length; i++) {
            Item item = ITEMS[i];
            if (i > 0) {
                canvas.divider(x + 18, rowY, w - 36, Theme.LINE);
            }
            float baseY = rowY + 24;

            // 指令名
            canvas.text(item.command(), Theme.font(16, true), Theme.INK, x + 22, baseY);
            float nameWidth = canvas.width(item.command(), Theme.font(16, true));

            // 参数
            canvas.text(item.args(), Theme.num(12, false), new Color(100, 116, 139, 210),
                    x + 22 + nameWidth + 12, baseY);

            // 说明
            canvas.text(item.description(), Theme.font(12, false),
                    new Color(71, 85, 105, 230), x + 22, baseY + 22);

            // 示例
            String example = "例：" + item.example();
            canvas.textRight(example, Theme.font(12, false), Theme.ACCENT, x + w - 22, baseY + 6);

            rowY += rowH;
        }
        return innerTop + cardH;
    }

    private int tips(Canvas canvas, int x, int y, int w) {
        int innerTop = sectionBar(canvas, x, y, w, "使用提示", "NOTES") + 12;

        int rowH = TIP_ROW_HEIGHT;
        int cardH = TIPS.length * rowH + 18;
        canvas.glassCard(x, innerTop, w, cardH, 14);

        float rowY = innerTop + 16;
        for (String[] tip : TIPS) {
            canvas.fillRound(x + 18, rowY - 12, 4, 16, 2, Theme.alpha(Theme.AMBER, 190));
            canvas.text(tip[0], Theme.font(12, true), Theme.INK, x + 32, rowY);
            canvas.text(tip[1], Theme.font(12, false), new Color(100, 116, 139, 220), x + 132, rowY);
            rowY += rowH;
        }
        return innerTop + cardH;
    }
}
