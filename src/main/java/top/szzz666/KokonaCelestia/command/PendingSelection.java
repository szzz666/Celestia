package top.szzz666.KokonaCelestia.command;

import top.szzz666.KokonaCelestia.api.DakanuClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 待用户选择的多候选状态。
 * <p>
 * 同名玩家在卡拉彼丘并不少见（例如两个都叫「心心子」的玩家），
 * 此时直接把第一个结果出图会让用户拿到错误的人，因此改为列出候选让用户回复序号。
 * <p>
 * 状态以「群号 + 用户号」为键，避免群里多人同时查询时互相串台；
 * 有效期 2 分钟，过期后需要重新发起查询。
 */
public final class PendingSelection {

    /** 选择状态有效期。 */
    private static final long TTL_MS = 120_000L;

    private static final Map<String, Entry> PENDING = new ConcurrentHashMap<>();

    private PendingSelection() {
    }

    /**
     * 一次待选记录。
     *
     * @param keyword    用户原始输入
     * @param candidates 候选玩家
     * @param kind       查询类型（概览 / 对局）
     * @param extra      附加信息（例如对局号）
     * @param createdAt  创建时间
     */
    public record Entry(String keyword, List<DakanuClient.PlayerSuggestion> candidates,
                        Kind kind, String extra, long createdAt) {
    }

    /** 选择后续要执行的动作。 */
    public enum Kind {
        /** 玩家概览。 */
        PROFILE,
        /** 对局详情（extra 为对局号）。 */
        MATCH
    }

    private static String key(long groupId, long userId) {
        return groupId + ":" + userId;
    }

    /** 记录待选状态。 */
    public static void remember(long groupId, long userId, String keyword,
                                List<DakanuClient.PlayerSuggestion> candidates,
                                Kind kind, String extra) {
        purge();
        PENDING.put(key(groupId, userId),
                new Entry(keyword, candidates, kind, extra, System.currentTimeMillis()));
    }

    /** 取出并清除待选状态（一次性使用）。 */
    public static Entry take(long groupId, long userId) {
        Entry entry = PENDING.remove(key(groupId, userId));
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.createdAt() > TTL_MS) {
            return null;
        }
        return entry;
    }

    /** 是否有待选状态。 */
    public static boolean has(long groupId, long userId) {
        Entry entry = PENDING.get(key(groupId, userId));
        return entry != null && System.currentTimeMillis() - entry.createdAt() <= TTL_MS;
    }

    /** 清除指定用户的状态。 */
    public static void clear(long groupId, long userId) {
        PENDING.remove(key(groupId, userId));
    }

    /** 清理过期条目，避免长期运行后堆积。 */
    private static void purge() {
        long now = System.currentTimeMillis();
        PENDING.entrySet().removeIf(e -> now - e.getValue().createdAt() > TTL_MS);
    }

    /** 生成候选列表文案。 */
    public static String describe(Entry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(entry.candidates().size())
                .append(" 个与「").append(entry.keyword()).append("」匹配的引航者，请回复序号：\n");
        int index = 1;
        for (DakanuClient.PlayerSuggestion item : entry.candidates()) {
            sb.append(index++).append(". ")
                    .append(item.nickname());
            if (!item.nickname().equals(item.matchedName())) {
                sb.append("（曾用名：").append(item.matchedName()).append("）");
            }
            sb.append("  ID ").append(item.playerId());
            if (item.level() > 0) {
                sb.append("  Lv.").append(item.level());
            }
            sb.append('\n');
        }
        sb.append("回复 1 - ").append(entry.candidates().size())
                .append(" 选择，回复「取消」放弃（2 分钟内有效）。");
        return sb.toString();
    }
}
