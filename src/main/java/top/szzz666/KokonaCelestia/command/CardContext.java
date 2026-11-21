package top.szzz666.KokonaCelestia.command;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户当前「正在看哪张卡片」的上下文。
 * <p>
 * 出图带序号时登记一条上下文，用户随后回裸数字（如「3」）即可作用于该卡片，
 * 不必再带指令前缀。这样「菜单回 1」和「BGM 回 1」是同一套操作习惯。
 * <p>
 * 同一用户只保留最近一次：看了壁纸再看 BGM，回「1」作用于 BGM。
 * 状态按「群号 + 用户号」隔离，3 分钟无操作自动失效。
 */
public final class CardContext {

    private static final long TTL_MS = 180_000L;
    private static final Map<String, Entry> CONTEXTS = new ConcurrentHashMap<>();

    private CardContext() {
    }

    /** 卡片类型。 */
    public enum Kind {
        /** 多级菜单：序号进入下一层。 */
        MENU,
        /** BGM 曲目：序号点播。 */
        BGM,
        /** 壁纸条目：序号取原图。 */
        WALLPAPER,
        /** 图鉴分页：序号切换页码。 */
        GALLERY
    }

    /**
     * 一条上下文。
     *
     * @param kind      卡片类型
     * @param page      关联页面（壁纸 / 图鉴用）
     * @param pageNo    当前页码（图鉴用）
     * @param size      条目数，用于校验序号范围
     * @param touchedAt 最后操作时间
     */
    public record Entry(Kind kind, String page, int pageNo, int size, long touchedAt) {
    }

    private static String key(long groupId, long userId) {
        return groupId + ":" + userId;
    }

    /** 登记上下文（出图时调用）。 */
    public static void remember(long groupId, long userId, Kind kind,
                                String page, int pageNo, int size) {
        purge();
        CONTEXTS.put(key(groupId, userId),
                new Entry(kind, page, pageNo, size, System.currentTimeMillis()));
    }

    /** 取上下文；不存在或已过期返回 null。 */
    public static Entry get(long groupId, long userId) {
        Entry entry = CONTEXTS.get(key(groupId, userId));
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.touchedAt() > TTL_MS) {
            CONTEXTS.remove(key(groupId, userId));
            return null;
        }
        return entry;
    }

    /** 刷新时间戳（用户操作后延长有效期）。 */
    public static void touch(long groupId, long userId) {
        Entry entry = get(groupId, userId);
        if (entry != null) {
            CONTEXTS.put(key(groupId, userId),
                    new Entry(entry.kind(), entry.page(), entry.pageNo(),
                            entry.size(), System.currentTimeMillis()));
        }
    }

    /** 清除上下文。 */
    public static void clear(long groupId, long userId) {
        CONTEXTS.remove(key(groupId, userId));
    }

    private static void purge() {
        long now = System.currentTimeMillis();
        CONTEXTS.entrySet().removeIf(e -> now - e.getValue().touchedAt() > TTL_MS);
    }
}
