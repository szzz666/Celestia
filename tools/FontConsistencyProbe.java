import top.szzz666.KokonaCelestia.render.Fonts;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 对比「系统字体」与「内置字体」的渲染差异。
 * 结论用于判断：跨平台部署时，出图样式是否还能保持一致。
 */
public class FontConsistencyProbe {

    public static void main(String[] args) throws Exception {
        Fonts.init();

        // 系统字体（当前机器 macOS 上是 PingFang SC）
        Font system = new Font("PingFang SC", Font.PLAIN, 22);
        boolean systemOk = system.canDisplay('卡');
        // 内置字体（Noto Sans SC）
        Font bundled = Fonts.cjk(22, false);

        String sample = "卡丘战绩档案 玩家概览 胜率 71.43%";
        String sample2 = "禁闭夸克 III · 无限超弦 · 奇点";

        BufferedImage img = new BufferedImage(820, 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 820, 150);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(30, 41, 59));

        g.setFont(system);
        g.drawString("系统字体(" + system.getFamily() + "): " + sample, 14, 38);
        g.drawString(sample2, 14, 70);

        g.setColor(new Color(2, 132, 199));
        g.setFont(bundled);
        g.drawString("内置字体(" + bundled.getFamily() + "): " + sample, 14, 106);
        g.drawString(sample2, 14, 138);

        g.dispose();
        ImageIO.write(img, "png", new File("/tmp/celestia-out/font-compare.png"));
        System.out.println("系统字体可显示中文: " + systemOk + " (" + system.getFamily() + ")");
        System.out.println("内置字体: " + bundled.getFamily());
    }
}
