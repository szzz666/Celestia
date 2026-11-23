package top.szzz666.KokonaCelestia.listener;

import top.szzz666.KokonaCelestia.command.BgmCommands;
import top.szzz666.KokonaCelestia.command.CardContext;
import top.szzz666.KokonaCelestia.command.Commands;
import top.szzz666.KokonaCelestia.command.GalleryCommands;
import top.szzz666.KokonaCelestia.command.WallpaperCommands;
import top.szzz666.KokonaCelestia.command.MenuCommands;
import top.szzz666.KokonaCelestia.command.MenuSession;
import top.szzz666.KokonaCelestia.command.PendingSelection;
import top.szzz666.KokonaCelestia.config.MyConfig;
import top.szzz666.KokonaCelestia.util.Reply;
import top.szzz666.tools.TaskUtil;
import top.szzz666.qq.entity.Event;
import top.szzz666.qq.entity.Message;
import top.szzz666.qq.entity.MessageData;
import top.szzz666.qq.listener.QQEventHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 兼容“无空格”指令写法。
 * <p>
 * 框架的 {@code CommandManage.dispatch} 用 {@code content.split("\\s+")} 取第一个词作为命令名，
 * 因此 {@code 看看米雪儿} 会被当作一个整体命令名而匹配失败，只有 {@code 看看 米雪儿} 生效。
 * 群聊里省略空格是常见输入，这里把它补回来。
 * <p>
 * 分发顺序是 {@code QQEventManage.triggerEvent(event)} -> {@code CommandDispatch(event)}，
 * 监听器先于命令执行，所以在此改写事件中的文本消息即可让后续解析生效，
 * 权限、作用域校验仍由框架统一处理。
 * <p>
 * 改写策略：命中已知指令前缀后，在指令名与其余部分之间插入一个空格，
 * 其余部分整体作为参数（内含空格时同样保留，符合“昵称可含空格”的约定）。
 */
public class QQListeners {

    @QQEventHandler
    public void onEvent(Event event) {
        if (!event.isGroupMessage() && !event.isPrivateMessage()) {
            return;
        }
        String text = event.getText();
        if (text == null) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        // 数字归属的优先级（越靠前越优先）：
        //   1. 同名玩家选择 —— 用户刚查了玩家，正等着挑人，必须最先响应
        //   2. 菜单会话    —— 用户正在逐层浏览
        //   3. 卡片上下文  —— 用户刚看了 BGM / 壁纸 / 图鉴列表
        // 否则一个残留的卡片上下文会把「选玩家」的数字抢走。
        if (handleSelection(event, trimmed)) {
            applyText(event, "");
            return;
        }

        if (handleMenu(event, trimmed)) {
            applyText(event, "");
            return;
        }

        if (handleBareNumber(event, trimmed)) {
            applyText(event, "");
            return;
        }

        if (containsWhitespace(trimmed)) {
            // 已带空格，交由框架正常解析
            return;
        }

        String rewritten = rewrite(trimmed);
        if (rewritten == null) {
            return;
        }
        applyText(event, rewritten);
    }

    /**
     * 处理「裸数字」：作用于用户最近查看的卡片。
     * <p>
     * 菜单、BGM、壁纸都带序号，此前只有菜单支持直接回数字，
     * BGM 要发「BGM 1」、壁纸要发「壁纸 1」，操作习惯不统一。
     * 这里统一为：看完任何带序号的图，直接回数字即可。
     * 带命令的写法（「BGM 1」「壁纸 1」）仍然有效。
     *
     * @return 是否由上下文接管
     */
    private static boolean handleBareNumber(Event event, String text) {
        if (!text.matches("\\d{1,3}")) {
            return false;
        }
        long groupId = event.isGroupMessage() ? event.getGroup_id() : 0L;
        long userId = event.getUser_id();
        CardContext.Entry ctx = CardContext.get(groupId, userId);
        if (ctx == null) {
            return false;
        }
        // 菜单有自己的会话状态，交给它处理
        if (ctx.kind() == CardContext.Kind.MENU) {
            return false;
        }
        TaskUtil.Async(() -> {
            Reply reply = Reply.of(event);
            try {
                int index = Integer.parseInt(text);
                CardContext.touch(groupId, userId);
                switch (ctx.kind()) {
                    case BGM -> BgmCommands.play(reply, index);
                    case WALLPAPER -> WallpaperCommands.play(reply, index);
                    case GALLERY -> GalleryCommands.page(reply, ctx.page(), index);
                    case MATCH -> {
                        // 玩家卡片最近战绩的序号：直接取对应对局号出详情
                        if (index < 1 || index > ctx.rooms().size()) {
                            reply.text("对局序号范围是 1 - " + ctx.rooms().size()
                                    + "，重新发送「看看」可刷新列表。");
                        } else {
                            Commands.matchById(reply, ctx.page(), ctx.rooms().get(index - 1));
                        }
                    }
                    default -> { }
                }
            } catch (Exception e) {
                reply.text("操作失败，请重新查看列表后再试。");
            }
        });
        return true;
    }

    /**
     * 处理菜单导航。
     * <p>
     * 仅在用户有进行中的菜单会话时介入，且只消费序号 / 返回 / 取消，
     * 其余输入继续走普通指令，避免菜单把正常聊天吞掉。
     *
     * @return 是否由菜单接管
     */
    private static boolean handleMenu(Event event, String text) {
        long groupId = event.isGroupMessage() ? event.getGroup_id() : 0L;
        long userId = event.getUser_id();
        if (!MenuSession.has(groupId, userId)) {
            return false;
        }
        if (!isMenuInput(text)) {
            return false;
        }
        TaskUtil.Async(() -> {
            Reply reply = Reply.of(event);
            try {
                MenuCommands.handle(reply, text);
            } catch (Exception e) {
                reply.text("操作失败，请重新发送「帮助」打开菜单。");
            }
        });
        return true;
    }

    /** 是否为菜单导航输入（序号 / 返回 / 取消）。 */
    private static boolean isMenuInput(String text) {
        if (text.matches("\\d{1,3}")) {
            return true;
        }
        for (String word : new String[]{"返回", "上一级", "back", "b", "0",
                "取消", "退出", "q", "exit", "cancel"}) {
            if (word.equalsIgnoreCase(text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理候选选择。
     * <p>
     * 这里有网络与出图操作，放到框架线程池执行，避免阻塞事件线程。
     *
     * @return 是否由选择流程接管
     */
    private static boolean handleSelection(Event event, String text) {
        long groupId = event.isGroupMessage() ? event.getGroup_id() : 0L;
        long userId = event.getUser_id();
        if (!PendingSelection.has(groupId, userId)) {
            return false;
        }
        TaskUtil.Async(() -> {
            Reply reply = Reply.of(event);
            try {
                Commands.handleSelection(reply, text);
            } catch (Exception e) {
                reply.text("查询失败，请稍后再试。");
            }
        });
        return true;
    }

    /** 对局号至少 3 位数字，用于把“昵称+对局号”切开。 */
    private static final java.util.regex.Pattern TRAILING_NUMBER =
            java.util.regex.Pattern.compile("^(.*?)(\\d{3,})$");

    /**
     * 把无空格指令改写为“指令名 + 空格 + 参数”。
     *
     * @return 改写后的文本；无需改写时返回 null
     */
    private static String rewrite(String text) {
        // 文本本身就是完整指令名（如“排行榜”“看看”），保持原样
        for (Rule rule : allRules()) {
            if (text.equalsIgnoreCase(rule.name())) {
                return null;
            }
        }

        for (Rule rule : sortedRules()) {
            String name = rule.name();
            if (text.length() <= name.length()) {
                continue;
            }
            if (!text.regionMatches(true, 0, name, 0, name.length())) {
                continue;
            }
            String rest = text.substring(name.length()).trim();
            if (rest.isEmpty()) {
                continue;
            }

            // “看看对局米雪儿114514”需要再切一刀：末尾数字串是对局号。
            // 用非贪婪前缀 + 至少 3 位数字，避免把昵称里的单个数字切开。
            if (rule.multiPart()) {
                var matcher = TRAILING_NUMBER.matcher(rest);
                if (matcher.matches()) {
                    String nickname = matcher.group(1).trim();
                    if (!nickname.isEmpty()) {
                        return name + " " + nickname + " " + matcher.group(2);
                    }
                }
            }
            // 保留指令名的原始写法，只插入分隔空格
            return text.substring(0, name.length()) + " " + rest;
        }
        return null;
    }

    /** 全部可用的无空格指令。 */
    private static List<Rule> allRules() {
        List<Rule> rules = new ArrayList<>();
        for (String name : MyConfig.matchCommands) {
            rules.add(new Rule(name, true));
        }
        for (String name : MyConfig.profileFreshCommands) {
            rules.add(new Rule(name, false));
        }
        for (String name : MyConfig.profileCommands) {
            rules.add(new Rule(name, false));
        }
        for (String name : MyConfig.rankCommands) {
            rules.add(new Rule(name, false));
        }
        return rules;
    }

    /** 长名优先（看看对局 先于 看看），避免短名抢先匹配。 */
    private static List<Rule> sortedRules() {
        List<Rule> rules = allRules();
        rules.sort(Comparator.comparingInt((Rule r) -> r.name().length()).reversed());
        return rules;
    }

    /** 把改写后的文本写回事件的消息段。 */
    private static void applyText(Event event, String rewritten) {
        List<Message> messages = event.getMessage();
        if (messages == null) {
            return;
        }
        for (Message message : messages) {
            if (!"text".equals(message.getType())) {
                continue;
            }
            MessageData data = message.getData();
            if (data == null) {
                continue;
            }
            data.setText(rewritten);
            return;
        }
    }

    private static boolean containsWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == '\u3000') {
                return true;
            }
        }
        return false;
    }

    /**
     * @param name     指令名
     * @param multiPart 是否为“指令 + 昵称 + 对局号”的多段形态
     */
    private record Rule(String name, boolean multiPart) {
    }
}
