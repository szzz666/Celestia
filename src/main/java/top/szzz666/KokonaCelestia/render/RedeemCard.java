package top.szzz666.KokonaCelestia.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

/**
 * 兑换码出图。
 * <p>
 * WIKI 的兑换码页面按月分组，每条包含「兑换码 / 奖励 / 有效期」。
 * 这里按有效期分成「可用」与「已失效」两组，把还能用的排在前面，
 * 避免用户逐个试错。
 */
public final class RedeemCard extends CardRenderer {

    /** 一条兑换码。 */
    public record Code(String code, String reward, String expiry) {
        /** 是否已失效。 */
        public boolean expired() {
            if (expiry == null) {
                return false;
            }
            String e = expiry.trim();
            return e.contains("失效") || e.contains("过期");
        }
    }

    private static final int ROW_HEIGHT = 42;
    private static final int SECTION_GAP = 14;

    private final List<Code> codes;

    public RedeemCard(List<Code> codes) {
        this.codes = codes;
    }

    private List<Code> usable() {
        List<Code> list = new ArrayList<>();
        for (Code code : codes) {
            if (!code.expired()) {
                list.add(code);
            }
        }
        return list;
    }

    private List<Code> expired() {
        List<Code> list = new ArrayList<>();
        for (Code code : codes) {
            if (code.expired()) {
                list.add(code);
            }
        }
        return list;
    }

    @Override
    protected String subtitleTitle() {
        return "兑换码";
    }

    @Override
    protected String footerNote() {
        return "兑换码来自 biligame 卡拉彼丘 WIKI，请在游戏内「设置 - 兑换」中使用，以实际为准。";
    }

    @Override
    protected int layoutHeight() {
        int usable = usable().size();
        int expired = Math.min(expired().size(), MAX_EXPIRED);
        int y = HEADER_HEIGHT;
        y += SECTION_GAP;
        y += summaryHeight();
        y += SECTION_GAP;
        y += BAR_HEIGHT + 12 + Math.max(1, usable) * ROW_HEIGHT + 14;
        if (expired > 0) {
            y += SECTION_GAP;
            y += BAR_HEIGHT + 12 + expired * ROW_HEIGHT + 14;
        }
        y += FOOTER_HEIGHT;
        return y;
    }

    /** 已失效的只展示最近若干条，页面不至于过长。 */
    private static final int MAX_EXPIRED = 8;

    private int summaryHeight() {
        return 92;
    }

    @Override
    protected int drawBody(Canvas canvas, int top) {
        int x = Theme.MARGIN;
        int w = Theme.WIDTH - Theme.MARGIN * 2;
        int y = top + SECTION_GAP;

        y = summary(canvas, x, y, w);
        y += SECTION_GAP;

        List<Code> usable = usable();
        int rows = Math.max(1, usable.size());
        int cardH = rows * ROW_HEIGHT + 14;
        int innerTop = sectionBar(canvas, x, y, w, "可用兑换码",
                usable.isEmpty() ? "暂无" : usable.size() + " 条") + 12;
        canvas.glassCard(x, innerTop, w, cardH, 12);
        if (usable.isEmpty()) {
            canvas.text("当前没有可用的兑换码，稍后再来看看。",
                    Theme.font(13, false), Theme.FAINT, x + 22, innerTop + 30);
        } else {
            drawRows(canvas, x, innerTop + 7, w, usable, Theme.WIN);
        }
        y = innerTop + cardH;

        List<Code> expired = expired();
        if (!expired.isEmpty()) {
            List<Code> shown = expired.subList(0, Math.min(expired.size(), MAX_EXPIRED));
            y += SECTION_GAP;
            int expiredCardH = shown.size() * ROW_HEIGHT + 14;
            int expiredTop = sectionBar(canvas, x, y, w, "已失效",
                    expired.size() + " 条") + 12;
            canvas.glassCard(x, expiredTop, w, expiredCardH, 12);
            drawRows(canvas, x, expiredTop + 7, w, shown, Theme.FAINT);
            y = expiredTop + expiredCardH;
        }
        return y;
    }

    /** 顶部概览：可用数量 + 最近更新提示。 */
    private int summary(Canvas canvas, int x, int y, int w) {
        int h = summaryHeight();
        Graphics2D g = canvas.raw();
        Shape shape = Canvas.roundRect(x, y, w, h, 14);
        g.setColor(new Color(255, 255, 255, 200));
        g.fill(shape);
        g.setPaint(new GradientPaint(x + w - 260, y, new Color(255, 114, 50, 0),
                x + w, y + h, new Color(255, 114, 50, 30)));
        g.fill(shape);
        g.setColor(Theme.CARD_EDGE);
        g.setStroke(new BasicStroke(1.1f));
        g.draw(shape);

        int usable = usable().size();
        canvas.text("可用兑换码", Theme.font(12, false),
                new Color(100, 116, 139, 210), x + 24, y + 32);
        canvas.text(String.valueOf(usable), Theme.num(34, true),
                usable > 0 ? Theme.WIN : Theme.FAINT, x + 24, y + 70);

        canvas.text("共收录 " + codes.size() + " 条", Theme.font(12, false),
                new Color(100, 116, 139, 200), x + 130, y + 66);

        canvas.textRight("使用方法", Theme.font(12, false),
                new Color(100, 116, 139, 210), x + w - 24, y + 32);
        canvas.textRight("游戏内 → 设置 → 兑换", Theme.font(13, true),
                Theme.INK, x + w - 24, y + 58);

        return y + h;
    }

    /** 绘制兑换码行。 */
    private void drawRows(Canvas canvas, int x, float startY, int w, List<Code> list, Color accent) {
        float rowY = startY;
        for (int i = 0; i < list.size(); i++) {
            Code code = list.get(i);
            if (i % 2 == 1) {
                canvas.fillRound(x + 10, rowY, w - 20, ROW_HEIGHT - 4, 8, Theme.ROW_ALT);
            }
            float baseY = rowY + 25;

            // 左侧色条
            canvas.fillRound(x + 14, rowY + 8, 3, ROW_HEIGHT - 18, 1.5f,
                    Theme.alpha(accent, 200));

            // 兑换码：等宽字体，便于复制
            canvas.text(code.code(), Theme.num(15, true),
                    Theme.alpha(accent, 255), x + 28, baseY);

            // 奖励
            canvas.text(code.reward(), Theme.font(12, false),
                    new Color(71, 85, 105), x + 300, baseY);

            // 有效期
            String expiry = code.expiry() == null || code.expiry().isBlank() ? "未知" : code.expiry();
            Color expiryColor = code.expired() ? Theme.FAINT : new Color(100, 116, 139, 220);
            canvas.textRight(expiry, Theme.font(11, false), expiryColor, x + w - 18, baseY);

            rowY += ROW_HEIGHT;
        }
    }
}
