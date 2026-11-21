package top.szzz666.KokonaCelestia.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多级菜单的会话状态。
 * <p>
 * 用导航栈实现「进入 / 返回」：每一层记录标题与条目列表，
 * 用户回复序号向下钻取，回复「返回」出栈，回复「取消」退出。
 * <p>
 * 状态按「群号 + 用户号」隔离，避免多人同时操作时互相串台；2 分钟无操作自动失效。
 */
public final class MenuSession {

    /** 会话有效期。 */
    private static final long TTL_MS = 180_000L;

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    private MenuSession() {
    }

    /** 条目的动作类型。 */
    public enum Action {
        /** 进入某个 WIKI 页面的章节列表。 */
        PAGE,
        /** 展示某个章节的内容。 */
        SECTION,
        /** 执行既有指令（payload 为指令与参数）。 */
        COMMAND,
        /** 播放 BGM。 */
        BGM,
        /** 图鉴条目分页。 */
        GALLERY
    }

    /**
     * 菜单条目。
     *
     * @param label  显示名称
     * @param detail 右侧补充说明（可为空）
     * @param action 点击后的动作
     * @param page   相关页面标题或指令名
     * @param index  章节序号（SECTION 用）
     */
    public record Item(String label, String detail, Action action, String page, int index) {
        public static Item page(String label, String detail, String page) {
            return new Item(label, detail, Action.PAGE, page, 0);
        }

        public static Item section(String label, String detail, String page, int index) {
            return new Item(label, detail, Action.SECTION, page, index);
        }

        public static Item command(String label, String detail, String command) {
            return new Item(label, detail, Action.COMMAND, command, 0);
        }

        public static Item bgm(String label, String detail) {
            return new Item(label, detail, Action.BGM, "", 0);
        }

        public static Item gallery(String label, String detail, String page, int pageNo) {
            return new Item(label, detail, Action.GALLERY, page, pageNo);
        }
    }

    /** 导航栈中的一层。 */
    public record Level(String title, String subtitle, List<Item> items) {
    }

    /** 一个用户的菜单会话。 */
    public static final class Session {
        private final List<Level> stack = new ArrayList<>();
        private volatile long touchedAt = System.currentTimeMillis();

        /** 当前层。 */
        public Level current() {
            return stack.isEmpty() ? null : stack.get(stack.size() - 1);
        }

        public void push(Level level) {
            stack.add(level);
            touch();
        }

        /** 返回上一层，返回是否成功。 */
        public boolean pop() {
            if (stack.size() <= 1) {
                return false;
            }
            stack.remove(stack.size() - 1);
            touch();
            return true;
        }

        public boolean canGoBack() {
            return stack.size() > 1;
        }

        /** 面包屑，例如「游戏延伸 › 表情包」。 */
        public String breadcrumb() {
            StringBuilder sb = new StringBuilder();
            for (Level level : stack) {
                if (sb.length() > 0) {
                    sb.append(" › ");
                }
                sb.append(level.title());
            }
            return sb.toString();
        }

        public void touch() {
            touchedAt = System.currentTimeMillis();
        }

        public boolean expired() {
            return System.currentTimeMillis() - touchedAt > TTL_MS;
        }
    }

    private static String key(long groupId, long userId) {
        return groupId + ":" + userId;
    }

    /** 取会话（不存在则按需创建根菜单）。 */
    public static Session get(long groupId, long userId) {
        purge();
        String k = key(groupId, userId);
        Session session = SESSIONS.get(k);
        if (session != null && session.expired()) {
            SESSIONS.remove(k);
            session = null;
        }
        if (session == null) {
            session = new Session();
            SESSIONS.put(k, session);
        }
        return session;
    }

    /** 是否有进行中的会话。 */
    public static boolean has(long groupId, long userId) {
        Session session = SESSIONS.get(key(groupId, userId));
        return session != null && !session.expired();
    }

    /** 关闭会话。 */
    public static void close(long groupId, long userId) {
        SESSIONS.remove(key(groupId, userId));
    }

    /** 清理过期会话。 */
    private static void purge() {
        SESSIONS.entrySet().removeIf(e -> e.getValue().expired());
    }
}
