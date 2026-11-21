package top.szzz666.KokonaCelestia.config;

import top.szzz666.config.ConfigItem;
import top.szzz666.config.EasyConfig;

import java.util.List;

import static top.szzz666.KokonaCelestia.KokonaCelestiaMain.plugin;

/** 插件配置。 */
public class MyConfig {

    /** 帮助：列出全部指令一览。 */
    @ConfigItem(key = "command.help", comment = "帮助指令名（指令一览）")
    public static List<String> helpCommands = List.of("帮助", "卡丘帮助");

    /** 菜单：进入可逐层浏览的功能菜单。 */
    @ConfigItem(key = "command.menu", comment = "菜单指令名（功能菜单）")
    public static List<String> menuCommands = List.of("菜单", "功能");

    @ConfigItem(key = "command.profile", comment = "玩家概览指令名")
    public static List<String> profileCommands = List.of("看看");

    @ConfigItem(key = "command.match", comment = "对局详情指令名（需放在玩家昵称之前）")
    public static List<String> matchCommands = List.of("看看对局");

    @ConfigItem(key = "command.rank", comment = "排行榜指令名")
    public static List<String> rankCommands = List.of("排行", "排行榜");

    /**
     * 出图标题的品牌名。显示在每张图片的左上角，形如「{brand} - 玩家概览」。
     * 留空时回退为「卡丘战绩档案」。
     */
    @ConfigItem(key = "image.brand", comment = "图片左上角的品牌标题")
    public static String imageBrand = "卡丘战绩档案";

    /** 图片右下角的署名。留空则不显示。 */
    @ConfigItem(key = "image.watermark", comment = "图片右下角的署名（留空不显示）")
    public static String imageWatermark = "Celestia";

    @ConfigItem(key = "command.wiki", comment = "WIKI 通用查询指令名")
    public static List<String> wikiCommands = List.of("wiki", "维基");

    @ConfigItem(key = "command.redeem", comment = "兑换码指令名")
    public static List<String> redeemCommands = List.of("兑换码", "cdk", "礼包码");

    @ConfigItem(key = "command.bgm", comment = "BGM 指令名")
    public static List<String> bgmCommands = List.of("BGM", "bgm", "音乐");

    /**
     * BGM 点播方式：
     * <ul>
     *   <li>{@code record} —— 发送语音消息（QQ 对语音有时长/格式限制，
     *       多为 60 秒内且部分客户端要求 silk/amr，长曲可能被降级为文件）；</li>
     *   <li>{@code link} —— 只发送曲名与音频直链，由用户自行播放（最稳妥）。</li>
     * </ul>
     */
    @ConfigItem(key = "bgm.send-mode", comment = "BGM 点播方式：record（语音）/ link（直链）")
    public static String bgmSendMode = "record";

    // ==================== WIKI 页面直达指令 ====================
    // 菜单里的每个页面也都有直接指令，知道要查什么时不必逐层点。

    @ConfigItem(key = "command.wallpaper", comment = "壁纸指令名")
    public static List<String> wallpaperCommands = List.of("壁纸");

    @ConfigItem(key = "command.emoji", comment = "表情包指令名")
    public static List<String> emojiCommands = List.of("表情包", "表情");

    @ConfigItem(key = "command.medal", comment = "勋章指令名")
    public static List<String> medalCommands = List.of("勋章");

    @ConfigItem(key = "command.spray", comment = "喷漆指令名")
    public static List<String> sprayCommands = List.of("喷漆");

    @ConfigItem(key = "command.avatarFrame", comment = "头像框指令名")
    public static List<String> avatarFrameCommands = List.of("头像框");

    @ConfigItem(key = "command.baseplate", comment = "基板指令名")
    public static List<String> baseplateCommands = List.of("基板");

    @ConfigItem(key = "command.namecard", comment = "个性名片指令名")
    public static List<String> namecardCommands = List.of("个性名片", "名片");

    @ConfigItem(key = "command.core", comment = "核心指令名")
    public static List<String> coreCommands = List.of("核心");

    @ConfigItem(key = "command.map", comment = "地图指令名")
    public static List<String> mapCommands = List.of("地图");

    @ConfigItem(key = "command.role", comment = "角色指令名")
    public static List<String> roleCommands = List.of("角色", "超弦体");

    @ConfigItem(key = "command.weapon", comment = "武器指令名")
    public static List<String> weaponCommands = List.of("武器", "枪械");

    @ConfigItem(key = "command.rolePosition", comment = "角色定位指令名")
    public static List<String> rolePositionCommands = List.of("角色定位", "定位");

    @ConfigItem(key = "command.battleMode", comment = "战斗模式指令名")
    public static List<String> battleModeCommands = List.of("战斗模式", "排位规则");

    @ConfigItem(key = "command.weaponData", comment = "武器数据指令名")
    public static List<String> weaponDataCommands = List.of("武器数据", "ttk", "dps");

    @ConfigItem(key = "command.credit", comment = "信用分指令名")
    public static List<String> creditCommands = List.of("信用分", "信誉分");

    @ConfigItem(key = "command.event", comment = "活动指令名")
    public static List<String> eventCommands = List.of("活动");

    @ConfigItem(key = "command.season", comment = "赛季指令名")
    public static List<String> seasonCommands = List.of("赛季", "赛季冲刺");

    @ConfigItem(key = "rank.page-size", comment = "排行榜每页人数（最大 100）")
    public static int rankPageSize = 20;

    @ConfigItem(key = "rank.max-page", comment = "排行榜最大页数")
    public static int rankMaxPage = 100;

    /**
     * 缺少中文字体时是否尝试自动安装系统字体包（仅 Linux）。
     * 需要 root 或免密 sudo；安装会修改宿主机环境，容器内重启后可能丢失。
     * 默认关闭：插件自带思源黑体，正常情况下无需安装。
     */
    @ConfigItem(key = "font.install-missing", comment = "缺少中文字体时自动安装（仅 Linux，需 root）")
    public static boolean fontInstallMissing = false;

    /**
     * 查询结果缓存时长（秒），作用于玩家概览 / 对局详情 / 排行榜 / 玩家搜索。
     * 从结果返回时刻开始计时，<=0 时回退为默认 60 秒。
     * 赛季起止、角色字典、榜单类型属于长周期数据，使用独立的固定 TTL，不受此项影响。
     */
    @ConfigItem(key = "cache.ttl-seconds", comment = "查询结果缓存时长（秒，<=0 用默认 60）")
    public static int cacheTtlSeconds = 60;

    public static EasyConfig ec;

    public static void initConfig() {
        ec = new EasyConfig(plugin.getConfigFolderPath() + "/config.yml");
        ec.loadFromClass(MyConfig.class);
        ec.load();
    }
}
