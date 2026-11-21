import top.szzz666.KokonaCelestia.api.DakanuClient;
import top.szzz666.KokonaCelestia.model.PlayerProfile;
import top.szzz666.KokonaCelestia.render.Assets;
import top.szzz666.KokonaCelestia.render.CardRenderer;
import top.szzz666.KokonaCelestia.render.Fonts;
import top.szzz666.KokonaCelestia.render.PlayerCard;
import top.szzz666.KokonaCelestia.render.Renderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/** 分段计时：定位出图慢在哪一环。 */
public class PerfProbe {

    static long t(String label, Runnable r) {
        long s = System.nanoTime();
        r.run();
        long ms = (System.nanoTime() - s) / 1_000_000;
        System.out.printf("%-28s %6d ms%n", label, ms);
        return ms;
    }

    public static void main(String[] args) throws Exception {
        String playerId = args.length > 0 ? args[0] : "8780938";

        System.out.println("===== 冷启动（进程刚起，无任何缓存）=====");
        t("字体初始化", () -> Fonts.init());
        t("WebP 解码器注册", () -> Assets.initWebp());

        System.out.println();
        System.out.println("===== 一次完整查询（冷缓存）=====");
        long[] search = new long[1];
        long[] summoner = new long[1];

        DakanuClient.clearCache();
        final PlayerProfile[] holder = new PlayerProfile[1];

        t("1. 搜索玩家", () -> {
            try {
                long s = System.nanoTime();
                var list = DakanuClient.search("地蕾女");
                search[0] = (System.nanoTime() - s) / 1_000_000;
                holder[0] = PlayerProfile.from(DakanuClient.summoner(list.get(0).playerId()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        PlayerProfile profile = holder[0];
        CardRenderer card = new PlayerCard(profile);

        long[] measure = new long[1];
        long[] render = new long[1];
        long[] encode = new long[1];
        final BufferedImage[] img = new BufferedImage[1];

        t("2. 布局测量(measure)", () -> {
            long s = System.nanoTime();
            card.measure();
            measure[0] = (System.nanoTime() - s) / 1_000_000;
        });

        t("3. 绘制(render)", () -> {
            long s = System.nanoTime();
            img[0] = card.render();
            render[0] = (System.nanoTime() - s) / 1_000_000;
        });

        t("4. PNG 编码 + Base64", () -> {
            try {
                long s = System.nanoTime();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(img[0], "png", out);
                String b64 = java.util.Base64.getEncoder().encodeToString(out.toByteArray());
                encode[0] = (System.nanoTime() - s) / 1_000_000;
                System.out.printf("   PNG 字节=%dKB, Base64 长度=%dKB%n",
                        out.size() / 1024, b64.length() / 1024);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        System.out.println();
        System.out.println("===== 热缓存（第二次查询）=====");
        t("搜索+档案(命中缓存)", () -> {
            try {
                var list = DakanuClient.search("地蕾女");
                PlayerProfile.from(DakanuClient.summoner(list.get(0).playerId()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        t("完整 render()", () -> new PlayerCard(profile).render());

        System.out.println();
        System.out.printf("画像拆解: 网络搜索 %d ms | 测量 %d ms | 绘制 %d ms | 编码 %d ms%n",
                search[0], measure[0], render[0], encode[0]);
    }
}
