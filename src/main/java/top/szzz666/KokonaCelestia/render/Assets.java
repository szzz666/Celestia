package top.szzz666.KokonaCelestia.render;

import top.szzz666.KokonaCelestia.api.DakanuClient;
import top.szzz666.KokonaCelestia.util.Http;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 远程图片与本地素材加载。
 * <p>
 * 头像、立绘、地图、段位徽章都是内容固定的静态资源，路径即唯一标识（例如
 * {@code /avatars/31001015.webp}），内容不会变。因此采用两级缓存：
 * <ol>
 *   <li>内存缓存：同一进程内直接命中，零开销</li>
 *   <li>磁盘缓存：进程重启后仍可复用，避免重复下载</li>
 * </ol>
 * 只有磁盘上也没有时才发起网络请求，且多个请求并行执行。
 * <p>
 * WebP 解码依赖随插件打包的 TwelveMonkeys，JDK 自带 ImageIO 不支持该格式。
 */
public final class Assets {

    private static final String[] WEBP_JARS = {
            "imageio-webp-3.12.0.jar",
            "imageio-core-3.12.0.jar",
            "common-lang-3.12.0.jar",
            "common-io-3.12.0.jar",
            "common-image-3.12.0.jar"
    };

    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();
    private static final BufferedImage MISSING = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private static final int MEMORY_LIMIT = 400;

    /**
     * 图片下载线程池。
     * <p>
     * 每张图的主要成本是 TLS 握手（约 1 秒），而非传输（图片仅 5KB），
     * 所以并发度直接决定总耗时。一页「看看」最多需要 31 张唯一图片，
     * 实测 8 并发 8.8 秒、12 并发 4.9 秒、16 并发 3.8 秒、24 并发 4.2 秒（开始回落），
     * 因此取 16。
     */
    private static final int FETCH_THREADS = 16;
    private static final ExecutorService FETCH_POOL = Executors.newFixedThreadPool(FETCH_THREADS, r -> {
        Thread t = new Thread(r, "Celestia-Asset");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean webpReady;
    private static final Object LOCK = new Object();

    /** 磁盘缓存目录，由插件在启用阶段设置。 */
    private static volatile Path diskCacheDir;

    private Assets() {
    }

    /** 设置磁盘缓存目录并创建（可重复调用）。 */
    public static void setCacheDir(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            Files.createDirectories(dir);
            diskCacheDir = dir;
        } catch (IOException ignored) {
            // 目录不可写时退化为纯内存缓存
        }
    }

    /** 磁盘缓存是否可用。 */
    public static boolean diskCacheEnabled() {
        return diskCacheDir != null;
    }

    /** 注册 WebP 解码器（幂等，失败不影响 PNG 流程）。 */
    public static void initWebp() {
        if (webpReady) {
            return;
        }
        synchronized (LOCK) {
            if (webpReady) {
                return;
            }
            try {
                Path dir = Files.createTempDirectory("celestia-webp");
                dir.toFile().deleteOnExit();
                URL[] urls = new URL[WEBP_JARS.length];
                for (int i = 0; i < WEBP_JARS.length; i++) {
                    Path target = dir.resolve(WEBP_JARS[i]);
                    try (InputStream in = Assets.class.getResourceAsStream("/webp/" + WEBP_JARS[i])) {
                        if (in == null) {
                            return;
                        }
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    urls[i] = target.toUri().toURL();
                }
                URLClassLoader loader = new URLClassLoader(urls, Assets.class.getClassLoader());
                Class<?> spiClass = Class.forName(
                        "com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi", true, loader);
                ImageReaderSpi spi = (ImageReaderSpi) spiClass.getDeclaredConstructor().newInstance();
                IIORegistry.getDefaultInstance().registerServiceProvider(spi, ImageReaderSpi.class);
                webpReady = true;
            } catch (Throwable ignored) {
                // 解码器不可用时退化为无图占位，不影响文字出图
            }
        }
    }

    /** 当前内存缓存条目数（诊断用）。 */
    public static int cacheSize() {
        return CACHE.size();
    }

    /**
     * 加载任意站点的图片。
     * <p>
     * {@link #site(String)} 只服务 Dakanu 的站内相对路径，
     * 而 WIKI 的缩略图在 {@code patchwiki.biligame.com}，是一整条绝对地址。
     * 这里按 URL 的 host 建连，Host 头与目标域名保持一致。
     */
    public static BufferedImage remote(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        BufferedImage cached = CACHE.get(url);
        if (cached != null) {
            return cached == MISSING ? null : cached;
        }

        BufferedImage image = null;
        Path file = diskFile(url);
        if (file != null) {
            try {
                if (Files.isReadable(file) && Files.size(file) > 0) {
                    image = decode(Files.readAllBytes(file));
                }
            } catch (IOException ignored) {
            }
        }
        if (image == null) {
            try {
                java.net.URL parsed = new java.net.URL(url);
                String host = parsed.getHost();
                String path = parsed.getFile();
                if (host != null && !host.isBlank() && path != null && !path.isBlank()) {
                    byte[] data = Http.get(host, path).body();
                    image = decode(data);
                    if (image != null && file != null) {
                        writeToDisk(file, data);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (CACHE.size() > MEMORY_LIMIT) {
            CACHE.clear();
        }
        CACHE.put(url, image == null ? MISSING : image);
        return image;
    }

    /**
     * 加载站点资源：内存 -> 磁盘 -> 网络，逐级回退。
     *
     * @param path 站内绝对路径，例如 /avatars/31001015.webp
     */
    public static BufferedImage site(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        BufferedImage cached = CACHE.get(path);
        if (cached != null) {
            return cached == MISSING ? null : cached;
        }

        BufferedImage image = null;
        // 先查磁盘
        Path file = diskFile(path);
        if (file != null) {
            try {
                if (Files.isReadable(file) && Files.size(file) > 0) {
                    image = decode(Files.readAllBytes(file));
                }
            } catch (IOException ignored) {
            }
        }

        // 磁盘没有命中才走网络
        if (image == null) {
            try {
                byte[] data = Http.get(DakanuClient.HOST, path).body();
                image = decode(data);
                if (image != null && file != null) {
                    writeToDisk(file, data);
                }
            } catch (Exception ignored) {
            }
        }

        if (CACHE.size() > MEMORY_LIMIT) {
            CACHE.clear();
        }
        CACHE.put(path, image == null ? MISSING : image);
        return image;
    }

    /** 站内路径或绝对 URL 映射到磁盘文件，避免目录穿越。 */
    private static Path diskFile(String path) {
        Path dir = diskCacheDir;
        if (dir == null) {
            return null;
        }
        String name = path.replace('\\', '/');
        // 绝对地址去掉协议前缀，避免生成非法文件名
        int scheme = name.indexOf("://");
        if (scheme >= 0) {
            name = name.substring(scheme + 3);
        }
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        // 去掉 ../ 之类的相对路径片段
        if (name.isEmpty() || name.contains("..")) {
            return null;
        }
        // 只保留安全的文件名字符
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.length() > 120) {
            name = name.substring(name.length() - 120);
        }
        return dir.resolve(name);
    }

    /** 原子写入磁盘，避免并发写坏文件。 */
    private static void writeToDisk(Path file, byte[] data) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp" + Thread.currentThread().getId());
            Files.write(tmp, data);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    /**
     * 并行预取一批图片。
     * <p>
     * 绘制阶段调用 {@link #site} 是同步的，若不在绘制前把图拉齐，
     * 就会退化成逐张串行下载。这里先并发发起全部请求并等待完成，
     * 之后的绘制全部命中缓存。
     *
     * @param paths 站内绝对路径列表，null / 空值会被忽略
     */
    public static void prefetch(Collection<String> paths) {
        collect(paths);
        awaitAll(12_000);
    }

    /** 把路径加入待下载集合，但不等待（供多类资源一起发起）。 */
    private static final java.util.Set<String> INFLIGHT = ConcurrentHashMap.newKeySet();
    private static final List<Future<?>> PENDING = java.util.Collections.synchronizedList(new ArrayList<>());

    /** 是否为完整 URL（http/https 开头）。 */
    private static boolean isAbsoluteUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static void collect(Collection<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        for (String path : paths) {
            if (path == null || path.isBlank() || CACHE.containsKey(path)) {
                continue;
            }
            if (!INFLIGHT.add(path)) {
                continue;
            }
            PENDING.add(FETCH_POOL.submit(() -> {
                try {
                    // 绝对 URL（WIKI 图片在 patchwiki 域名）与站内相对路径要走不同入口，
                    // 早先统一用 site() 会让外部图片全部失败。
                    if (isAbsoluteUrl(path)) {
                        remote(path);
                    } else {
                        site(path);
                    }
                } finally {
                    INFLIGHT.remove(path);
                }
                return null;
            }));
        }
    }

    private static void awaitAll(long timeoutMs) {
        List<Future<?>> snapshot;
        synchronized (PENDING) {
            snapshot = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        for (Future<?> future : snapshot) {
            long remain = deadline - System.currentTimeMillis();
            if (remain <= 0) {
                future.cancel(true);
                continue;
            }
            try {
                future.get(remain, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                // 单张图超时或失败时放弃它，其余图继续
            }
        }
        INFLIGHT.clear();
    }

    /**
     * 一次性并发预取多类资源，然后统一等待。
     * <p>
     * 早先的实现是“命中一类再等一类”，四类资源串行等待，
     * 总耗时接近各类之和；改为全部发起后统一等待，总耗时约为最慢的一类。
     */
    public static void prefetchAll(Collection<String>... groups) {
        for (Collection<String> group : groups) {
            collect(group);
        }
        awaitAll(12_000);
    }

    /** 把头像 ID 转成待下载路径（不等待）。 */
    public static Collection<String> avatarPaths(Collection<Integer> avatarIds) {
        List<String> paths = new ArrayList<>(avatarIds.size());
        for (Integer id : avatarIds) {
            if (id != null && id > 0) {
                paths.add("/avatars/" + id + ".webp");
            }
        }
        return paths;
    }

    /** 把角色 ID 转成待下载路径（不等待）。 */
    public static Collection<String> rolePaths(Collection<Integer> roleIds) {
        List<String> paths = new ArrayList<>(roleIds.size());
        for (Integer id : roleIds) {
            String path = Theme.rolePortraitPath(id == null ? 0 : id);
            if (path != null) {
                paths.add(path);
            }
        }
        return paths;
    }

    /** 把地图 ID 转成待下载路径（不等待）。 */
    public static Collection<String> mapPaths(Collection<Integer> mapIds) {
        List<String> paths = new ArrayList<>(mapIds.size());
        for (Integer id : mapIds) {
            String path = Theme.mapImagePath(id == null ? 0 : id);
            if (path != null) {
                paths.add(path);
            }
        }
        return paths;
    }

    /** 把段位号转成待下载路径（不等待）。 */
    public static Collection<String> tierPaths(Collection<Integer> segments) {
        List<String> paths = new ArrayList<>(segments.size());
        for (Integer segment : segments) {
            String icon = Theme.tier(segment == null ? 0 : segment).icon();
            if (icon != null) {
                paths.add(icon);
            }
        }
        return paths;
    }

    public static void prefetchAvatars(Collection<Integer> avatarIds) {
        List<String> paths = new ArrayList<>(avatarIds.size());
        for (Integer id : avatarIds) {
            if (id != null && id > 0) {
                paths.add("/avatars/" + id + ".webp");
            }
        }
        prefetch(paths);
    }

    public static void prefetchRoles(Collection<Integer> roleIds) {
        List<String> paths = new ArrayList<>(roleIds.size());
        for (Integer id : roleIds) {
            String path = Theme.rolePortraitPath(id == null ? 0 : id);
            if (path != null) {
                paths.add(path);
            }
        }
        prefetch(paths);
    }

    public static void prefetchMaps(Collection<Integer> mapIds) {
        List<String> paths = new ArrayList<>(mapIds.size());
        for (Integer id : mapIds) {
            String path = Theme.mapImagePath(id == null ? 0 : id);
            if (path != null) {
                paths.add(path);
            }
        }
        prefetch(paths);
    }

    public static void prefetchTiers(Collection<Integer> segments) {
        List<String> paths = new ArrayList<>(segments.size());
        for (Integer segment : segments) {
            String icon = Theme.tier(segment == null ? 0 : segment).icon();
            if (icon != null) {
                paths.add(icon);
            }
        }
        prefetch(paths);
    }

    /**
     * 小图缓存阈值：绘制时头像最大 84px、列表头像 38px，原始资源为 96px，
     * 立绘原图 129px 但主要用于 30~48px 的小图。保留原图既占内存又拖慢缩放，
     * 这里统一缩到 2 倍显示尺寸，兼顾清晰度与开销。
     */
    private static final int AVATAR_PIXELS = 192;
    private static final int PORTRAIT_PIXELS = 132;

    /** 玩家头像（缩放到常用显示尺寸）。 */
    public static BufferedImage avatar(int avatarId) {
        if (avatarId <= 0) {
            return null;
        }
        BufferedImage image = site("/avatars/" + avatarId + ".webp");
        return shrink(image, AVATAR_PIXELS);
    }

    /** 角色立绘（缩放到常用显示尺寸）。 */
    public static BufferedImage rolePortrait(int roleId) {
        BufferedImage image = site(Theme.rolePortraitPath(roleId));
        return shrink(image, PORTRAIT_PIXELS);
    }

    /** 超过目标边长时等比缩小，否则原样返回。 */
    private static BufferedImage shrink(BufferedImage image, int maxPixels) {
        if (image == null) {
            return null;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= maxPixels && height <= maxPixels) {
            return image;
        }
        float ratio = Math.min(maxPixels / (float) width, maxPixels / (float) height);
        int targetWidth = Math.max(1, Math.round(width * ratio));
        int targetHeight = Math.max(1, Math.round(height * ratio));
        return Theme.scale(image, targetWidth, targetHeight);
    }

    /** 地图缩略图。 */
    public static BufferedImage mapImage(int mapId) {
        return site(Theme.mapImagePath(mapId));
    }

    /** 段位徽章。 */
    public static BufferedImage tierBadge(int segment) {
        String icon = Theme.tier(segment).icon();
        return icon == null ? null : site(icon);
    }

    private static BufferedImage decode(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            return image == null ? null : toArgb(image);
        } catch (IOException e) {
            return null;
        }
    }

    /** 统一转成 ARGB，便于合成与缩放。 */
    public static BufferedImage toArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        var g = out.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return out;
    }
}
