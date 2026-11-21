package top.szzz666.KokonaCelestia.render;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 字体解析。
 * <p>
 * 出图依赖中文字形，而各平台预装字体差异极大：
 * macOS 有 PingFang SC，Windows 有微软雅黑，但多数 Linux 服务器（尤其是最小化安装的
 * Docker 镜像）根本没有中文字体，此时任何逻辑字体都会把汉字画成方块。
 * <p>
 * 因此插件自带一份思源黑体（Noto Sans SC，SIL OFL 协议），运行时通过
 * {@link Font#createFont} 注册到 JVM。查找顺序为：
 * <ol>
 *   <li>插件内置字体（保证跨平台一致）</li>
 *   <li>系统常见中文字体</li>
 *   <li>逻辑字体兜底</li>
 * </ol>
 * 每一步都会用 {@link Font#canDisplay} 校验字形，避免“字体名存在但没有中文字形”的假命中。
 */
public final class Fonts {

    private Fonts() {
    }

    /** 用于校验字形是否齐全的探测字符。 */
    private static final char[] HAN_PROBE = {'卡', '丘', '战', '绩', '档', '案', '引', '航', '者'};

    /**
     * 内置字体资源。只带一份 Regular（完整 CJK 基本区 + 符号，压缩后约 4.3MB），
     * 粗体由 {@link Font#deriveFont(int, float)} 合成 —— 实测与真实粗体字形几乎无差别，
     * 但省掉一整份字体文件。
     */
    private static final String BUNDLED_REGULAR = "/fonts/NotoSansSC-Regular.ttf";

    /** 系统中文字体候选（按观感优先级排列）。 */
    private static final String[] SYSTEM_CJK = {
            "PingFang SC", "Microsoft YaHei", "Hiragino Sans GB", "Noto Sans CJK SC",
            "Source Han Sans SC", "Source Han Sans CN", "Noto Sans SC",
            "WenQuanYi Micro Hei", "WenQuanYi Zen Hei", "Droid Sans Fallback",
            "Heiti SC", "Arial Unicode MS", "SimHei", "SimSun"
    };

    /** 数字与拉丁字形候选。 */
    private static final String[] NUMERIC_CANDIDATES = {
            "DIN Alternate", "Bahnschrift", "SF Pro Display", "Helvetica Neue",
            "Segoe UI", "DejaVu Sans", "Arial"
    };

    private static volatile boolean initialized;
    private static volatile String cjkFamily = Font.SANS_SERIF;
    private static volatile String numericFamily = "SansSerif";
    private static volatile Font bundledRegular;
    private static volatile boolean bundledOk;

    private static final Object LOCK = new Object();

    /** 初始化字体解析（幂等）。 */
    public static void init() {
        if (initialized) {
            return;
        }
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            loadBundled();
            resolveFamilies();
            initialized = true;
        }
    }

    /** 是否有可用的内置字体。 */
    public static boolean bundledAvailable() {
        init();
        return bundledOk;
    }

    /** 当前用于中文的字体族名，便于排查问题。 */
    public static String cjkFamily() {
        init();
        return cjkFamily;
    }

    /**
     * 正文字体。
     *
     * @param size 字号
     * @param bold 是否加粗
     */
    public static Font cjk(int size, boolean bold) {
        init();
        if (bundledOk && bundledRegular != null) {
            // 粗体走 Java2D 合成，避免额外打包一份字体文件
            return bundledRegular.deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size);
        }
        return new Font(cjkFamily, bold ? Font.BOLD : Font.PLAIN, size);
    }

    /**
     * 数字 / 拉丁字体。优先使用系统里观感更好的窄体数字字形，
     * 但必须保证能显示中文，否则回退到正文字体，避免同一行里出现方块。
     */
    public static Font numeric(int size, boolean bold) {
        init();
        Font candidate = new Font(numericFamily, bold ? Font.BOLD : Font.PLAIN, size);
        if (canDisplayHan(candidate)) {
            return candidate;
        }
        return cjk(size, bold);
    }

    // ==================== 内部实现 ====================

    /** 从插件资源加载内置字体。 */
    private static void loadBundled() {
        bundledRegular = readFont(BUNDLED_REGULAR);
        if (bundledRegular == null) {
            return;
        }
        // 注册到 JVM 字体表，便于外部按名称引用
        try {
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(bundledRegular);
        } catch (Throwable ignored) {
            // 注册失败不影响 createFont 返回值的使用
        }
        bundledOk = canDisplayHan(bundledRegular.deriveFont(14f));
    }

    private static Font readFont(String resource) {
        try (InputStream in = Fonts.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (FontFormatException | IOException | RuntimeException e) {
            // 字体缺失或损坏时静默降级，由后续的系统字体兜底
            return null;
        }
    }

    /** 选择中文字体族与数字字体族。 */
    private static void resolveFamilies() {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));

        // 中文字体：先看系统候选，但必须真正有中文字形
        String chosen = null;
        for (String name : SYSTEM_CJK) {
            if (!available.contains(name)) {
                continue;
            }
            if (canDisplayHan(new Font(name, Font.PLAIN, 14))) {
                chosen = name;
                break;
            }
        }
        if (chosen == null) {
            // 逻辑字体兜底：某些系统（如装了 noto-cjk 的 Linux）逻辑字体自带中文
            for (String logical : new String[]{Font.SANS_SERIF, Font.DIALOG, Font.MONOSPACED}) {
                if (canDisplayHan(new Font(logical, Font.PLAIN, 14))) {
                    chosen = logical;
                    break;
                }
            }
        }
        if (chosen == null) {
            chosen = Font.SANS_SERIF;
        }
        cjkFamily = chosen;

        // 数字字体：优先观感，但同样要求能显示中文
        String numeric = chosen;
        for (String name : NUMERIC_CANDIDATES) {
            if (!available.contains(name)) {
                continue;
            }
            Font probe = new Font(name, Font.PLAIN, 14);
            if (canDisplayHan(probe)) {
                numeric = name;
                break;
            }
        }
        numericFamily = numeric;
    }

    /** 字体是否能显示常用汉字。 */
    private static boolean canDisplayHan(Font font) {
        if (font == null) {
            return false;
        }
        for (char c : HAN_PROBE) {
            if (!font.canDisplay(c)) {
                return false;
            }
        }
        return true;
    }

    /** 诊断信息，供日志输出。 */
    public static String describe() {
        init();
        List<String> parts = new ArrayList<>();
        parts.add("内置字体=" + (bundledOk ? "已加载" : "不可用"));
        parts.add("中文字体=" + cjkFamily);
        parts.add("数字字体=" + numericFamily);
        return String.join(", ", parts);
    }

    /** 供外部按名称覆盖（例如配置指定字体文件）。 */
    public static void overrideCjkFamily(String family) {
        if (family == null || family.isBlank()) {
            return;
        }
        String normalized = family.trim();
        if (canDisplayHan(new Font(normalized, Font.PLAIN, 14))) {
            cjkFamily = normalized;
        }
    }

    /** 判断某字体名是否可用于中文。 */
    public static boolean supportsHan(String family) {
        return family != null && !family.isBlank()
                && canDisplayHan(new Font(family, Font.PLAIN, 14));
    }

    /**
     * 把磁盘上的字体文件注册进 JVM（供系统字体安装器调用）。
     * 只有确实含中文字形的字体才会被接受，避免注册大量拉丁字体拖慢启动。
     *
     * @return 是否注册成功
     */
    public static boolean registerFontFile(java.io.File file) {
        if (file == null || !file.isFile() || file.length() > 40L * 1024 * 1024) {
            return false;
        }
        try {
            Font font = Font.createFont(Font.TRUETYPE_FONT, file);
            if (!canDisplayHan(font.deriveFont(14f))) {
                return false;
            }
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            // 系统字体可用后，重新解析字体族，让后续出图用上它
            initialized = false;
            init();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    static {
        // 尽早解析，避免首次出图时的抖动
        Thread warmup = new Thread(Fonts::init, "Celestia-FontWarmup");
        warmup.setDaemon(true);
        warmup.start();
    }
}
