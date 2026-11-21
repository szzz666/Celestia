package top.szzz666.KokonaCelestia;

import top.szzz666.KokonaCelestia.command.BgmCommands;
import top.szzz666.KokonaCelestia.command.CardContext;
import top.szzz666.KokonaCelestia.command.Commands;
import top.szzz666.KokonaCelestia.command.MenuCommands;
import top.szzz666.KokonaCelestia.command.WikiCommands;
import top.szzz666.KokonaCelestia.command.WallpaperCommands;
import top.szzz666.KokonaCelestia.command.WikiDirect;
import top.szzz666.KokonaCelestia.listener.QQListeners;
import top.szzz666.KokonaCelestia.config.MyConfig;
import top.szzz666.KokonaCelestia.render.Assets;
import top.szzz666.KokonaCelestia.render.Fonts;
import top.szzz666.KokonaCelestia.render.SystemFontInstaller;
import top.szzz666.KokonaCelestia.util.Reply;
import top.szzz666.command.CommandManage;
import top.szzz666.plugin.KokonaPlugin;
import top.szzz666.plugin.PluginBase;
import top.szzz666.qq.entity.Event;
import top.szzz666.qq.listener.QQEventManage;
import top.szzz666.tools.TaskUtil;

import java.util.List;

import static top.szzz666.command.Command.PERM_ALL;
import static top.szzz666.command.Command.SCOPE_BOTH;

/**
 * 卡丘战绩档案：基于 Kokona 的《卡拉彼丘》战绩查询插件。
 * <p>
 * 指令采用精确前缀匹配，群聊中直接发送即可触发，不需要 @机器人。
 * 所有网络请求与出图都在异步线程中执行，绝不阻塞 QQ 事件线程。
 */
@KokonaPlugin(name = "Celestia")
public class KokonaCelestiaMain extends PluginBase {

    public static PluginBase plugin;

    @Override
    public void onLoad() {
        plugin = this;
        MyConfig.initConfig();
        plugin.getLogger().info("{} 插件读取...", this.getName());
    }

    @Override
    public void onEnable() {
        // 字体与 WebP 解码器初始化较慢，提前在启用阶段预热，避免首次出图卡顿
        try {
            Fonts.init();
            plugin.getLogger().info("字体就绪：{}", Fonts.describe());
            if (!Fonts.bundledAvailable()) {
                plugin.getLogger().warn("内置中文字体加载失败，将依赖系统字体；"
                        + "若服务器缺少中文字体，出图会显示为方块");
                tryInstallSystemFont();
            }
        } catch (Exception e) {
            plugin.getLogger().warn("字体初始化失败: {}", e.getMessage());
        }

        try {
            Assets.initWebp();
        } catch (Exception e) {
            plugin.getLogger().warn("WebP 解码器初始化失败: {}", e.getMessage());
        }

        try {
            java.nio.file.Path cacheDir = resolveCacheDir();
            Assets.setCacheDir(cacheDir);
            plugin.getLogger().info("素材缓存目录: {}", cacheDir.toAbsolutePath());
        } catch (Exception e) {
            plugin.getLogger().warn("素材缓存初始化失败，将仅使用内存缓存: {}", e.getMessage());
        }

        // 框架只对「插件主类实例」扫描 @QQEventHandler（PluginManager.loadPlugin 中
        // registerListener(instance)），因此独立的监听器类需要手动注册，
        // 否则其中的 @QQEventHandler 方法永远不会被调用。
        QQEventManage.registerListener(new QQListeners());

        // 帮助：静态的指令一览；菜单：可逐层浏览的功能菜单
        CommandManage.register(names(MyConfig.helpCommands, "帮助"),
                "查看全部指令一览", "卡丘战绩", PERM_ALL, SCOPE_BOTH,
                (event, name, args) -> async(event, Commands::help));

        CommandManage.register(names(MyConfig.menuCommands, "菜单"),
                "打开功能菜单，逐层浏览全部内容", "卡丘战绩", PERM_ALL, SCOPE_BOTH,
                (event, name, args) -> async(event, MenuCommands::open));

        CommandManage.register(names(MyConfig.matchCommands, "看看对局"),
                "查询单场对局详情：看看对局 <昵称> <对局号>", "卡丘战绩", PERM_ALL, SCOPE_BOTH,
                (event, name, args) -> {
                    String argument = String.join(" ", args);
                    async(event, reply -> Commands.match(reply, argument));
                });

        CommandManage.register(names(MyConfig.profileCommands, "看看"),
                "查询玩家档案概览：看看 <昵称>", "卡丘战绩", PERM_ALL, SCOPE_BOTH,
                (event, name, args) -> {
                    String argument = String.join(" ", args);
                    async(event, reply -> Commands.profile(reply, argument));
                });

        CommandManage.register(names(MyConfig.wikiCommands, "wiki"),
                "查询 WIKI 任意页面：wiki <关键词>", "卡丘战绩", PERM_ALL, SCOPE_BOTH,
                (event, name, args) -> {
                    String argument = String.join(" ", args);
                    async(event, reply -> WikiCommands.query(reply, argument));
                });

        CommandManage.register(names(MyConfig.redeemCommands, "兑换码"),
                "查看当前可用的兑换码", "卡丘战绩", PERM_ALL, SCOPE_BOTH,
                (event, name, args) -> async(event, WikiCommands::redeem));

        // WIKI「游戏延伸」栏目：每个栏目一个独立指令
        // BGM 与其他栏目不同：支持按序号/曲名点播，直接发送语音
        CommandManage.register(names(MyConfig.bgmCommands, "BGM"),
                "点播 BGM：BGM 查看曲目，BGM 序号/曲名 播放", "卡丘战绩", PERM_ALL, SCOPE_BOTH,
                (event, name, args) -> {
                    String argument = String.join(" ", args);
                    async(event, reply -> BgmCommands.handle(reply, argument));
                });
        // 菜单能到达的每个页面都有直达指令，知道要查什么时不必逐层点选。
        // 参数为页码（图鉴页）或章节序号（其他页面）。
        // 壁纸单独实现：带序号取原图，带「随机」随机发一张
        CommandManage.register(names(MyConfig.wallpaperCommands, "壁纸"),
                "查看壁纸：壁纸 看列表，壁纸 3 看原图，壁纸 随机 随机一张",
                "卡丘战绩", PERM_ALL, SCOPE_BOTH,
                (event, name, args) -> {
                    String argument = String.join(" ", args);
                    async(event, reply -> WallpaperCommands.show(reply, argument));
                });

        CommandManage.register(names(MyConfig.randomWallpaperCommands, "随机壁纸"),
                "随机发送一张官方壁纸", "卡丘战绩", PERM_ALL, SCOPE_BOTH,
                (event, name, args) -> async(event, WallpaperCommands::random));
        registerWiki("表情包", "官方表情包", MyConfig.emojiCommands);
        registerWiki("勋章", "勋章", MyConfig.medalCommands);
        registerWiki("喷漆", "喷漆", MyConfig.sprayCommands);
        registerWiki("头像框", "头像框", MyConfig.avatarFrameCommands);
        registerWiki("基板", "基板", MyConfig.baseplateCommands);
        registerWiki("个性名片", "个性名片", MyConfig.namecardCommands);
        registerWiki("核心", "核心", MyConfig.coreCommands);
        registerWiki("地图", "地图", MyConfig.mapCommands);
        registerWiki("角色筛选", "角色图鉴", MyConfig.roleCommands);
        registerWiki("武器筛选", "武器图鉴", MyConfig.weaponCommands);
        registerWiki("超弦体定位", "角色定位", MyConfig.rolePositionCommands);
        registerWiki("战斗模式/排位爆破", "战斗模式", MyConfig.battleModeCommands);
        registerWiki("主武器理论数据表", "武器数据", MyConfig.weaponDataCommands);
        registerWiki("信用分", "信用分", MyConfig.creditCommands);
        registerWiki("活动", "活动", MyConfig.eventCommands);
        registerWiki("赛季冲刺", "赛季冲刺", MyConfig.seasonCommands);

        CommandManage.register(names(MyConfig.rankCommands, "排行"),
                "查询全服排行榜：排行 [页数]", "卡丘战绩", PERM_ALL, SCOPE_BOTH,
                (event, name, args) -> {
                    String argument = String.join(" ", args);
                    async(event, reply -> Commands.rank(reply, argument));
                });

        plugin.getLogger().info("{}  插件已启用", this.getName());
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("{} 插件已关闭", this.getName());
    }

    /**
     * 素材缓存目录：{@code plugins/{插件名}/assets-cache}。
     * <p>
     * 框架在 {@code setPluginInfo()} 中按 {@code @KokonaPlugin(name=...)} 设置
     * {@code configFolderPath = plugins/{name}}，因此这里可以直接拼接。
     * 仍保留 null 兜底只为防御框架版本差异，不至于因此崩溃。
     */
    private static java.nio.file.Path resolveCacheDir() {
        String configFolder = plugin.getConfigFolderPath();
        if (configFolder == null || configFolder.isBlank()) {
            configFolder = "plugins/" + plugin.getName();
        }
        return java.nio.file.Path.of(configFolder, "assets-cache").toAbsolutePath().normalize();
    }

    /**
     * 注册一个 WIKI 栏目指令。
     *
     * @param page   WIKI 页面标题
     * @param label  出图时显示的名称
     * @param names  指令名列表
     */
    private static void registerWiki(String page, String label, java.util.List<String> names) {
        CommandManage.register(names(names, label),
                "查看 WIKI 的" + label + "（可带页码或章节序号）",
                "卡丘战绩", PERM_ALL, SCOPE_BOTH,
                (event, name, args) -> {
                    String argument = String.join(" ", args);
                    async(event, reply -> WikiDirect.show(reply, page, label, argument));
                });
    }

    /** 内置字体不可用时，按配置尝试安装系统字体包。 */
    private static void tryInstallSystemFont() {
        if (!MyConfig.fontInstallMissing) {
            plugin.getLogger().warn("未开启自动安装（font.install-missing=false）。"
                    + "可手动安装 fonts-noto-cjk，或把该配置设为 true 由插件尝试安装");
            return;
        }
        if (!SystemFontInstaller.supported()) {
            plugin.getLogger().warn("当前系统不支持自动安装字体，请手动安装中文字体");
            return;
        }
        plugin.getLogger().info("检测到缺少中文字体，尝试自动安装...");
        SystemFontInstaller.Result result = SystemFontInstaller.install();
        if (result.success()) {
            plugin.getLogger().info("字体安装完成：{}（当前：{}）", result.message(), Fonts.describe());
        } else {
            plugin.getLogger().warn("字体自动安装失败：{}", result.message());
        }
    }

    /** 命令名回退：配置为空时使用默认值。 */
    private static List<String> names(List<String> configured, String fallback) {
        return configured == null || configured.isEmpty() ? List.of(fallback) : configured;
    }

    /**
     * 在框架线程池中异步执行耗时任务，异常统一回报为文本消息。
     * 用 {@link TaskUtil#Async} 而非自建线程，符合框架文档约定，
     * 也避免与框架的线程池策略冲突。
     */
    private static void async(Event event, java.util.function.Consumer<Reply> task) {
        // 用户发了新指令，说明注意力已转移：清掉旧的卡片上下文，
        // 避免此后回数字还作用于上一张列表。
        try {
            long groupId = event.isGroupMessage() ? event.getGroup_id() : 0L;
            CardContext.clear(groupId, event.getUser_id());
        } catch (Exception ignored) {
            // 清理失败不影响主流程
        }
        TaskUtil.Async(() -> {
            Reply reply = Reply.of(event);
            try {
                task.accept(reply);
            } catch (Exception e) {
                plugin.getLogger().warn("指令执行异常: {}", e.getMessage());
                reply.text("查询失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage()));
            }
        });
    }
}
