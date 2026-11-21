import top.szzz666.KokonaCelestia.api.DakanuClient;
import top.szzz666.KokonaCelestia.config.MyConfig;

/**
 * 验证 cache.ttl-seconds 配置是否真正驱动缓存过期。
 * 做法：把 TTL 设为 1 秒，连续两次调用（第一次回源、第二次命中缓存），
 * 再休眠 1.2 秒后调用一次（应重新回源）。用耗时差异判断是否命中缓存。
 */
public class CacheProbe {

    public static void main(String[] args) throws Exception {
        String playerId = args.length > 0 ? args[0] : "8780938";

        // 必须先清空一次，确保首次调用真正回源
        System.out.println("== TTL = 1 秒 ==");
        MyConfig.cacheTtlSeconds = 1;
        DakanuClient.clearCache();

        long t0 = System.nanoTime();
        DakanuClient.summoner(playerId);
        long first = (System.nanoTime() - t0) / 1_000_000;

        long t1 = System.nanoTime();
        DakanuClient.summoner(playerId);
        long second = (System.nanoTime() - t1) / 1_000_000;

        Thread.sleep(1200);

        long t2 = System.nanoTime();
        DakanuClient.summoner(playerId);
        long third = (System.nanoTime() - t2) / 1_000_000;

        System.out.println("首次回源   : " + first + " ms");
        System.out.println("立即再取   : " + second + " ms  (命中缓存应明显更快)");
        System.out.println("等待1.2秒后: " + third + " ms  (已过期应重新回源)");

        System.out.println();
        System.out.println("== TTL = 120 秒 ==");
        MyConfig.cacheTtlSeconds = 120;
        DakanuClient.clearCache();

        long t3 = System.nanoTime();
        DakanuClient.summoner(playerId);
        long a = (System.nanoTime() - t3) / 1_000_000;

        Thread.sleep(1200);

        long t4 = System.nanoTime();
        DakanuClient.summoner(playerId);
        long b = (System.nanoTime() - t4) / 1_000_000;

        System.out.println("首次回源   : " + a + " ms");
        System.out.println("等待1.2秒后: " + b + " ms  (TTL=120s 应仍命中缓存)");

        System.out.println();
        boolean ttl1Expired = third > second * 2;
        boolean ttl120Cached = b < a;
        System.out.println("结论: TTL=1s 过期生效=" + ttl1Expired + ", TTL=120s 缓存生效=" + ttl120Cached);

        MyConfig.cacheTtlSeconds = 60;
    }
}
