package top.szzz666.KokonaCelestia.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 地图字典。
 * <p>
 * 数据直接取自 dakanu 前端的 {@code o}（ID → 图片）与 {@code u}（ID → 中文名）两张表，
 * 不做任何偏移换算。
 * <p>
 * 之所以不能用「{@code mapId - 200} 再拼 {@code map-NN}」这种规律推导：
 * 站点自己的映射并不连续，例如 203 对应 {@code map-05}、204 对应 {@code map-04}、
 * 207 对应 {@code map-19}、208 对应 {@code map-20}。按偏移算会整体错位一位，
 * 把「88区」显示成「404基地」。
 */
public final class MapCatalog {

    private MapCatalog() {
    }

    /** ID → 中文名。 */
    private static final Map<Integer, String> NAMES = new HashMap<>();

    /** ID → 图片编号（map-NN 的 NN）。 */
    private static final Map<Integer, String> IMAGES = new HashMap<>();

    private static void name(int id, String name) {
        NAMES.put(id, name);
    }

    private static void image(int id, String code) {
        IMAGES.put(id, code);
    }

    static {
        // ---- 爆破模式 ----
        name(200, "404基地");
        name(201, "88区");
        name(202, "欧拉港口");
        name(203, "空间实验室");
        name(204, "风曳镇");
        name(205, "柯西街区");
        name(206, "科斯迷特");
        name(207, "奥卡努斯");
        name(208, "莱布伦城");

        // ---- 团竞 / 乱斗 ----
        name(110, "莱特园区");
        name(111, "鹿鹿潮玩区");
        name(112, "2号仓库");
        name(113, "花明弓道馆");
        name(115, "码头小镇");
        name(118, "水下发电厂");
        name(119, "新水下发电厂");

        // ---- 团队模式（复用爆破地图，ID 不同）----
        name(400, "404基地");
        name(401, "88区");
        name(402, "欧拉港口");
        name(403, "空间实验室");
        name(405, "柯西街区");
        name(414, "风曳镇");

        // ---- 推车模式 ----
        name(628, "离境区");
        name(629, "亚克萨工厂");
        name(630, "簌雪东区");
        name(631, "泰芙绿洲");
        name(632, "厄瑞洞窟");
        name(633, "熔岩工厂");

        name(770, "狄拉克矿区");
        name(1201, "狄拉克矿区");

        // ---- 测试与特殊场景 ----
        name(995, "推车测试");
        name(996, "UGC测试图");
        name(998, "爆破模式-测试");
        name(999, "团竞模式测试");
        name(1000, "新手关");
        name(1001, "宿舍");
        name(1003, "88区");
        name(10020, "靶场");
        name(16000, "大头模式测试图");

        // ---- 晶源追击 ----
        name(12001, "晶源追击-霓虹大逃杀");
        name(12002, "晶源追击-穿越：风曳水之城");
        name(12003, "晶源追击-與你的日常就是奇迹");
        name(12004, "晶源追击-寰行·烬海迷途");

        // ---- 晶源感染 ----
        name(11001, "洛希街区");
        name(11002, "迦纳古城");
        name(11003, "极地研究所");
        name(11004, "猩红废墟");
        name(11005, "欧拉夜港");
        name(11006, "虚空残岸");

        // ---- 图片编号 ----
        image(200, "map-01");
        image(201, "map-02");
        image(202, "map-03");
        image(203, "map-05");
        image(204, "map-04");
        image(205, "map-06");
        image(206, "map-07");
        image(207, "map-19");
        image(208, "map-20");
        image(110, "map-10");
        image(111, "map-09");
        image(112, "map-08");
        image(113, "map-12");
        image(115, "map-11");
        image(118, "map-10");
        image(400, "map-01");
        image(401, "map-02");
        image(402, "map-03");
        image(403, "map-05");
        image(405, "map-06");
        image(414, "map-04");
        image(628, "map-13");
        image(629, "map-14");
        image(630, "map-15");
        image(11001, "map-16");
        image(11002, "map-17");
        image(11003, "map-18");
        image(11004, "map-05");
        image(11005, "map-03");
        image(11006, "map-18");
    }

    /**
     * 地图中文名。
     *
     * @param mapId 地图 ID
     * @return 名称；未收录时返回「地图 N」而不是「未知地图」，
     *         避免被误读成程序出错（「404基地」就常被误认为 HTTP 404）
     */
    public static String name(int mapId) {
        String name = NAMES.get(mapId);
        return name != null ? name : "地图 " + mapId;
    }

    /** 地图缩略图路径；没有对应图片时返回 {@code null}。 */
    public static String imagePath(int mapId) {
        String code = IMAGES.get(mapId);
        return code == null ? null : "/maps/" + code + ".webp";
    }

    /** 是否收录了该地图。 */
    public static boolean known(int mapId) {
        return NAMES.containsKey(mapId);
    }
}
