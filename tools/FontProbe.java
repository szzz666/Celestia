import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;

/** 诊断：当前 JVM 能看到哪些字体、能否显示中文字形。 */
public class FontProbe {

    public static void main(String[] args) {
        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
        System.out.println("系统字体族数量: " + families.length);
        System.out.println("字体列表: " + Arrays.toString(families));

        // Theme 的候选链最终会回退到这个逻辑字体
        String[] candidates = {
                "PingFang SC", "Microsoft YaHei", "Hiragino Sans GB", "Noto Sans CJK SC",
                "Source Han Sans SC", "Heiti SC", "Arial Unicode MS", Font.SANS_SERIF
        };
        System.out.println();
        System.out.println("=== 候选中文字体可用性（canDisplay 检验字形）===");
        char han = '\u5361';   // 卡
        char box = '\u25a1';   // □
        for (String name : candidates) {
            Font font = new Font(name, Font.PLAIN, 14);
            System.out.printf("%-22s 存在=%-5s 能显示'卡'=%-5s 家族=%-20s%n",
                    name,
                    Arrays.asList(families).contains(name) || font.getFamily().equalsIgnoreCase(name),
                    font.canDisplay(han),
                    font.getFamily());
        }
        System.out.println();
        System.out.println("提示：能显示'卡'为 false 时，中文会被渲染成方框（豆腐块）");
    }
}
