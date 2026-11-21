import top.szzz666.KokonaCelestia.api.DakanuClient;
import top.szzz666.KokonaCelestia.model.PlayerProfile;
import top.szzz666.KokonaCelestia.render.Assets;
import top.szzz666.KokonaCelestia.render.Fonts;
import top.szzz666.KokonaCelestia.render.PlayerCard;

import java.awt.image.BufferedImage;

/** 拆解 measure() 内部耗时：到底是绘制贵，还是图片下载贵。 */
public class PerfDetail {

    public static void main(String[] args) throws Exception {
        Fonts.init();
        Assets.initWebp();

        var list = DakanuClient.search("地蕾女");
        PlayerProfile profile = PlayerProfile.from(DakanuClient.summoner(list.get(0).playerId()));

        // 第一次：所有远程图片都要下载
        long s = System.nanoTime();
        new PlayerCard(profile).measure();
        long cold = (System.nanoTime() - s) / 1_000_000;

        // 第二次：图片全在内存缓存里
        s = System.nanoTime();
        new PlayerCard(profile).measure();
        long warm = (System.nanoTime() - s) / 1_000_000;

        // 第三次：再确认稳定值
        s = System.nanoTime();
        new PlayerCard(profile).measure();
        long warm2 = (System.nanoTime() - s) / 1_000_000;

        System.out.println("measure 第1次(含图片下载): " + cold + " ms");
        System.out.println("measure 第2次(图片已缓存): " + warm + " ms");
        System.out.println("measure 第3次:            " + warm2 + " ms");

        // 单独测空背景绘制成本
        s = System.nanoTime();
        BufferedImage probe = new BufferedImage(1000, 8000, BufferedImage.TYPE_INT_ARGB);
        var g = probe.createGraphics();
        g.setPaint(top.szzz666.KokonaCelestia.render.Theme.pageBackground(1000, 8000));
        g.fillRect(0, 0, 1000, 8000);
        g.dispose();
        System.out.println("空画布 1000x8000 铺背景: " + (System.nanoTime() - s) / 1_000_000 + " ms");

        // 统计图片缓存命中情况
        System.out.println("头像缓存条目数: " + Assets.cacheSize());
    }
}
