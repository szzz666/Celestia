package top.szzz666.KokonaCelestia.render;

import top.szzz666.KokonaCelestia.util.MapCatalog;

import top.szzz666.KokonaCelestia.api.DakanuClient;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** 出图视觉规范。所有机器人出图统一走这里的色板与排版常量。 */
public final class Theme {

    private Theme() {
    }

    // ==================== 品牌 ====================

    /** 配置缺省时的品牌名。 */
    private static final String DEFAULT_BRAND = "卡丘战绩档案";

    /**
     * 图片左上角的品牌标题，由 {@code image.brand} 配置项决定。
     * 配置为空或读取失败时回退到默认值，保证出图不会出现空白标题。
     */
    public static String brand() {
        try {
            String configured = top.szzz666.KokonaCelestia.config.MyConfig.imageBrand;
            if (configured != null && !configured.isBlank()) {
                return configured.trim();
            }
        } catch (Throwable ignored) {
            // 配置尚未加载（例如出图探针直接调用）时走默认值
        }
        return DEFAULT_BRAND;
    }

    /** 页脚署名，由 {@code image.watermark} 配置项决定；留空则不显示。 */
    public static String watermark() {
        try {
            String configured = top.szzz666.KokonaCelestia.config.MyConfig.imageWatermark;
            return configured == null ? "" : configured.trim();
        } catch (Throwable ignored) {
            return "Celestia";
        }
    }

    // ==================== 版式 ====================

    /** 出图逻辑宽度（像素），高度按内容自适应。 */
    public static final int WIDTH = 1000;
    /** 内容左右外边距。 */
    public static final int MARGIN = 26;
    /** 超出画布多少像素后进入下一列。 */
    public static final int BASE_SCALE = 2;

    // ==================== 色板（取自设计稿采样） ====================

    /** 主文字：深蓝灰。 */
    public static final Color INK = new Color(30, 41, 59);
    /** 次要文字。 */
    public static final Color MUTED = new Color(100, 116, 139);
    /** 更浅的说明文字。 */
    public static final Color FAINT = new Color(148, 163, 184);
    /** 强调蓝：数值高亮。 */
    public static final Color ACCENT = new Color(2, 132, 199);
    /** 强调橙：分组条左侧色标、图表强调。 */
    public static final Color AMBER = new Color(255, 114, 50);
    /** 胜利绿。 */
    public static final Color WIN = new Color(5, 150, 105);
    /** 失败红。 */
    public static final Color LOSE = new Color(225, 29, 72);
    /** 深色分组条底色。 */
    public static final Color BAR = new Color(26, 27, 32);
    /** 白色（卡片高光）。 */
    public static final Color WHITE = Color.WHITE;

    /** 卡片底色（半透明白）。 */
    public static final Color CARD = new Color(255, 255, 255, 196);
    /** 卡片描边。 */
    public static final Color CARD_EDGE = new Color(255, 255, 255, 226);
    /** 卡片柔和投影。 */
    public static final Color CARD_SHADOW = new Color(120, 100, 140, 26);
    /** 表格斑马纹。 */
    public static final Color ROW_ALT = new Color(255, 255, 255, 92);
    /** 分隔线。 */
    public static final Color LINE = new Color(148, 163, 184, 58);

    // ==================== 字体 ====================

    private static final Map<String, Font> FONT_CACHE = new LinkedHashMap<>();

    /**
     * 正文字体。字体解析交给 {@link Fonts}：优先使用插件内置的思源黑体，
     * 保证在没有中文字体的 Linux 服务器上也不会出现方块字。
     */
    public static Font font(int size, boolean bold) {
        String key = "c" + size + (bold ? "b" : "r");
        return FONT_CACHE.computeIfAbsent(key, k -> Fonts.cjk(size, bold));
    }

    /** 数字与拉丁字符使用的字形，兼顾等宽观感。 */
    public static Font num(int size, boolean bold) {
        String key = "n" + size + (bold ? "b" : "r");
        return FONT_CACHE.computeIfAbsent(key, k -> Fonts.numeric(size, bold));
    }

    // ==================== 绘制辅助 ====================

    /** 开启高质量抗锯齿。 */
    public static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    }

    /** 页面背景：柔和的粉紫到淡蓝渐变，模拟设计稿的雾面底色。 */
    public static Paint pageBackground(int width, int height) {
        return new LinearGradientPaint(
                new Point2D.Float(0, 0),
                new Point2D.Float(width, height),
                new float[]{0f, 0.32f, 0.62f, 1f},
                new Color[]{
                        new Color(253, 244, 248),
                        new Color(243, 241, 252),
                        new Color(236, 243, 253),
                        new Color(250, 244, 247)
                },
                MultipleGradientPaint.CycleMethod.NO_CYCLE);
    }

    /** 强调色横向渐变（用于渐变文字）。 */
    public static Paint accentGradient(float x0, float x1) {
        return new GradientPaint(x0, 0, ACCENT, x1, 0, new Color(56, 189, 248));
    }

    /** 深色分组条渐变。 */
    public static Paint barPaint(int x0, int x1) {
        return new LinearGradientPaint(
                new Point2D.Float(x0, 0),
                new Point2D.Float(x1, 0),
                new float[]{0f, 1f},
                new Color[]{new Color(26, 27, 32), new Color(44, 45, 54)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE);
    }

    /** 段位配色（与站点 tiers 表一致）。 */
    public record Tier(String name, String division, int segment, Color color, String icon) {
    }

    private static final Object[][] TIERS = {
            {"substance", "至纯物质", 1, 3, "#6C7178", "/tiers/tier-1.png"},
            {"molecule", "升格分子", 4, 6, "#C07A52", "/tiers/tier-2.png"},
            {"atom", "虚空原子", 7, 10, "#9AA7B5", "/tiers/tier-3.png"},
            {"proton", "聚变质子", 11, 14, "#D0A63C", "/tiers/tier-4.png"},
            {"neutron", "自由中子", 15, 18, "#28B3A4", "/tiers/tier-5.png"},
            {"electron", "束缚电子", 19, 23, "#3F8FD6", "/tiers/tier-6.png"},
            {"quark", "禁闭夸克", 24, 28, "#9B5CD6", "/tiers/tier-7.png"},
            {"superstring", "无限超弦", 29, 29, "#D94A44", "/tiers/tier-8.png"},
            {"singularity", "奇点", 30, 30, "#D84A4A", "/tiers/tier-9.png"},
    };

    private static final String[][] DIVISIONS = {
            {"III", "II", "I"},
            {"III", "II", "I"},
            {"IV", "III", "II", "I"},
            {"IV", "III", "II", "I"},
            {"IV", "III", "II", "I"},
            {"V", "IV", "III", "II", "I"},
            {"V", "IV", "III", "II", "I"},
            {},
            {},
    };

    /** 按段位序号解析段位信息。 */
    public static Tier tier(int segment) {
        if (segment < 1 || segment > 30) {
            return new Tier("未定级", "", 0, new Color(148, 163, 184), null);
        }
        for (int i = 0; i < TIERS.length; i++) {
            int from = (int) TIERS[i][2];
            int to = (int) TIERS[i][3];
            if (segment >= from && segment <= to) {
                String[] divisions = DIVISIONS[i];
                int index = segment - from;
                String division = index < divisions.length ? divisions[index] : "";
                return new Tier((String) TIERS[i][1], division, segment,
                        Color.decode((String) TIERS[i][4]), (String) TIERS[i][5]);
            }
        }
        return new Tier("未定级", "", 0, new Color(148, 163, 184), null);
    }

    /** 段位的完整显示名，例如“禁闭夸克 III”。 */
    public static String tierLabel(int segment) {
        Tier tier = tier(segment);
        return tier.division().isEmpty() ? tier.name() : tier.name() + " " + tier.division();
    }

    /** 角色立绘文件名（按站点 slug 表）。 */
    private static final Map<Integer, String> ROLE_SLUGS = new LinkedHashMap<>();

    static {
        ROLE_SLUGS.put(101, "michele");
        ROLE_SLUGS.put(105, "audrey");
        ROLE_SLUGS.put(107, "maddelena");
        ROLE_SLUGS.put(108, "nobunaga");
        ROLE_SLUGS.put(109, "reiichi");
        ROLE_SLUGS.put(110, "bai_mo");
        ROLE_SLUGS.put(112, "fuchsia");
        ROLE_SLUGS.put(115, "flavia");
        ROLE_SLUGS.put(119, "eika");
        ROLE_SLUGS.put(120, "fragrans");
        ROLE_SLUGS.put(121, "yugiri");
        ROLE_SLUGS.put(122, "mara");
        ROLE_SLUGS.put(123, "leona");
        ROLE_SLUGS.put(124, "kokona");
        ROLE_SLUGS.put(125, "qian_dai");
        ROLE_SLUGS.put(126, "nono");
        ROLE_SLUGS.put(128, "lawine");
        ROLE_SLUGS.put(130, "xi");
        ROLE_SLUGS.put(131, "yvette");
        ROLE_SLUGS.put(132, "ming");
        ROLE_SLUGS.put(133, "meredith");
        ROLE_SLUGS.put(137, "kanami");
        ROLE_SLUGS.put(146, "celestia");
        ROLE_SLUGS.put(205, "galatea");
    }

    public static String rolePortraitPath(int roleId) {
        String slug = ROLE_SLUGS.get(roleId);
        return slug == null ? null : "/roles/" + slug + ".webp";
    }

    /**
     * 地图缩略图路径。
     * <p>
     * 映射完全取自站点前端的 ID → 图片表，不做偏移推导：
     * 站点编号并不连续（203 是 map-05、204 是 map-04、207 是 map-19）。
     */
    public static String mapImagePath(int mapId) {
        return MapCatalog.imagePath(mapId);
    }

    /**
     * 地图名称。
     * <p>
     * 直接查站点前端的 ID → 中文名表。此前用「{@code mapId - 200} 再查数组」推导，
     * 结果整体错位一位（把 88区 显示成 404基地），且生化 / 推车等模式一律显示未知。
     */
    public static String mapName(int mapId) {
        return MapCatalog.name(mapId);
    }


    /** 阵营名与配色。 */
    public static String factionName(int campCode) {
        return switch (campCode) {
            case 1 -> "欧泊";
            case 2 -> "剪刀手";
            case 4 -> "乌尔比诺";
            default -> "未知";
        };
    }

    public static String positionName(int code) {
        return switch (code) {
            case 1 -> "守护";
            case 2 -> "决斗";
            case 3 -> "控场";
            case 4 -> "支援";
            case 6 -> "先锋";
            default -> "未知";
        };
    }

    /** 阵营配色。 */
    public static Color factionColor(int campCode) {
        return switch (campCode) {
            case 1 -> new Color(56, 108, 244);
            case 2 -> new Color(253, 56, 63);
            case 4 -> new Color(255, 181, 38);
            default -> MUTED;
        };
    }

    /** 玩家在线状态文案。 */
    public static String presenceLabel(String state) {
        if (state == null) {
            return "未知";
        }
        return switch (state) {
            case "online" -> "在线";
            case "offline" -> "离线";
            case "in_game" -> "对局中";
            default -> "未知";
        };
    }

    /** 昵称截断，避免长昵称撑破布局。 */
    public static String ellipsis(String text, java.awt.FontMetrics metrics, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int ellipsisWidth = metrics.stringWidth(ellipsis);
        StringBuilder sb = new StringBuilder();
        int width = 0;
        for (char c : text.toCharArray()) {
            int charWidth = metrics.charWidth(c);
            if (width + charWidth + ellipsisWidth > maxWidth) {
                break;
            }
            sb.append(c);
            width += charWidth;
        }
        return sb + ellipsis;
    }

    /** 角色 ID 到官方全名的映射（回退用）。 */
    public static String roleName(int roleId, String fallback) {
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        try {
            return DakanuClient.optString(DakanuClient.meta(), "roles", "")
                    .isEmpty() ? "未知角色" : "未知角色";
        } catch (Exception e) {
            return "未知角色";
        }
    }

    /** 把颜色调成同色系更浅的版本。 */
    public static Color lighter(Color color, float amount) {
        int r = Math.min(255, Math.round(color.getRed() + (255 - color.getRed()) * amount));
        int g = Math.min(255, Math.round(color.getGreen() + (255 - color.getGreen()) * amount));
        int b = Math.min(255, Math.round(color.getBlue() + (255 - color.getBlue()) * amount));
        return new Color(r, g, b, color.getAlpha());
    }

    /** 带透明度的同色。 */
    public static Color alpha(Color color, int a) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    /** 缩放图片。 */
    public static BufferedImage scale(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        quality(g);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return out;
    }
}
