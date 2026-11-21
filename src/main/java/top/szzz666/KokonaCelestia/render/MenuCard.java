package top.szzz666.KokonaCelestia.render;

import top.szzz666.KokonaCelestia.command.MenuSession;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.List;

/**
 * 多级菜单出图。
 * <p>
 * 与内容图区分开：顶部显示面包屑，条目带序号与右侧说明，
 * 底部给出操作提示（回复序号 / 返回 / 取消）。
 */
public final class MenuCard extends CardRenderer {

    private static final int ROW_HEIGHT = 44;
    private static final int HEADER_BOX_HEIGHT = 84;

    private final MenuSession.Level level;
    private final String breadcrumb;
    private final boolean canGoBack;

    public MenuCard(MenuSession.Level level, String breadcrumb, boolean canGoBack) {
        this.level = level;
        this.breadcrumb = breadcrumb;
        this.canGoBack = canGoBack;
    }

    @Override
    protected String subtitleTitle() {
        return "菜单 · " + level.title();
    }

    @Override
    protected String footerNote() {
        // 根层提示两个入口的区别，避免用户以为「帮助」就是菜单
        if (!canGoBack) {
            return "回复序号进入下一层 · 回复「帮助」查看全部指令 · 回复「取消」退出";
        }
        return "回复序号进入下一层 · 回复「返回」上一层 · 回复「取消」退出";
    }

    @Override
    protected int layoutHeight() {
        int rows = Math.max(1, level.items().size());
        int y = HEADER_HEIGHT;
        y += 14;
        y += HEADER_BOX_HEIGHT;
        y += 14;
        y += rows * ROW_HEIGHT + 40;
        y += FOOTER_HEIGHT;
        return y;
    }

    @Override
    protected int drawBody(Canvas canvas, int top) {
        int x = Theme.MARGIN;
        int w = Theme.WIDTH - Theme.MARGIN * 2;
        int y = top + 14;

        y = header(canvas, x, y, w);
        y += 14;

        List<MenuSession.Item> items = level.items();
        int rows = Math.max(1, items.size());
        int cardH = rows * ROW_HEIGHT + 34;
        canvas.glassCard(x, y, w, cardH, 14);

        if (items.isEmpty()) {
            canvas.text("这里还没有内容。", Theme.font(13, false),
                    Theme.FAINT, x + 22, y + 34);
            return y + cardH;
        }

        float rowY = y + 12;
        for (int i = 0; i < items.size(); i++) {
            MenuSession.Item item = items.get(i);
            if (i % 2 == 1) {
                canvas.fillRound(x + 10, rowY, w - 20, ROW_HEIGHT - 4, 8, Theme.ROW_ALT);
            }
            float baseY = rowY + 27;

            // 序号徽标
            String number = String.valueOf(i + 1);
            float badgeW = 30;
            canvas.fillRound(x + 18, rowY + 8, badgeW, 24, 7,
                    new Color(2, 132, 199, 26));
            canvas.textCenter(number, Theme.num(13, true), Theme.ACCENT,
                    x + 18 + badgeW / 2f, baseY - 2);

            // 名称
            boolean primary = item.detail() == null || item.detail().isEmpty();
            canvas.text(item.label(), Theme.font(14, primary), Theme.INK,
                    x + 62, baseY);

            // 右侧说明
            if (item.detail() != null && !item.detail().isEmpty()) {
                canvas.textRight(item.detail(), Theme.font(12, false),
                        new Color(100, 116, 139, 220), x + w - 20, baseY);
            }

            // 可下钻的条目标一个箭头
            if (item.action() != MenuSession.Action.COMMAND) {
                canvas.textRight("›", Theme.font(16, true),
                        new Color(148, 163, 184, 200), x + w - 16, baseY);
            }

            rowY += ROW_HEIGHT;
        }

        return y + cardH;
    }

    private int header(Canvas canvas, int x, int y, int w) {
        int h = HEADER_BOX_HEIGHT;
        Graphics2D g = canvas.raw();
        Shape shape = Canvas.roundRect(x, y, w, h, 14);
        g.setColor(new Color(255, 255, 255, 200));
        g.fill(shape);
        g.setPaint(new GradientPaint(x, y, new Color(2, 132, 199, 22),
                x + 260, y + h, new Color(2, 132, 199, 0)));
        g.fill(shape);
        g.setColor(Theme.CARD_EDGE);
        g.setStroke(new BasicStroke(1.1f));
        g.draw(shape);

        // 面包屑
        canvas.text(breadcrumb, Theme.font(12, false),
                new Color(100, 116, 139, 220), x + 24, y + 30);

        // 当前层标题
        canvas.text(level.title(), Theme.font(24, true), Theme.INK, x + 24, y + 62);

        // 右侧统计
        canvas.textRight(level.subtitle(), Theme.font(12, false),
                new Color(100, 116, 139, 210), x + w - 24, y + 62);

        return y + h;
    }
}
