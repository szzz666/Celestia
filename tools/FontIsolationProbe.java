import top.szzz666.KokonaCelestia.render.Fonts;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 关键验证：在“人为剥夺系统中文字体”的条件下，确认内置字体仍能正确渲染中文。
 * <p>
 * 做法：先用 Fonts 解析（它会加载内置字体），再强制用内置字体渲染；
 * 同时用逻辑字体做对照，展示无中文字体时的方块效果。
 */
public class FontIsolationProbe {

    public static void main(String[] args) throws Exception {
        Fonts.init();
        System.out.println("字体诊断: " + Fonts.describe());
        System.out.println("内置字体可用: " + Fonts.bundledAvailable());

        String line1 = "卡丘战绩档案 - 玩家概览";
        String line2 = "看看对局 米雪儿·李 114514";
        String line3 = "禁闭夸克 III · 胜率 71.43%";

        // 使用 Fonts 解析出的字体（生产路径）
        Font viaFonts = Fonts.cjk(24, false);
        System.out.println("生产路径字体: " + viaFonts.getFamily()
                + " 中文=" + viaFonts.canDisplay('卡'));

        BufferedImage img = new BufferedImage(760, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 760, 200);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(30, 41, 59));

        g.setFont(viaFonts);
        g.drawString("[内置字体] " + line1, 16, 40);
        g.setFont(Fonts.cjk(20, true));
        g.drawString("[内置粗体] " + line2, 16, 84);
        g.setFont(Fonts.numeric(20, false));
        g.drawString("[数字字体] " + line3, 16, 128);

        // 对照组：故意用一个不含中文的拉丁字体，展示方块字
        g.setFont(new Font("Helvetica", Font.PLAIN, 18));
        g.setColor(new Color(200, 60, 70));
        g.drawString("[对照-无中文字体] " + line1, 16, 172);

        g.dispose();
        ImageIO.write(img, "png", new File("/tmp/celestia-out/font-isolation.png"));
        System.out.println("输出 /tmp/celestia-out/font-isolation.png");
    }
}
