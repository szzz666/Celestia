package top.szzz666.KokonaCelestia.command;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 机器人自己发出的卡片，按消息 ID 索引。
 * <p>
 * 用于支持「引用某张卡片再发序号」：QQ 的回复消息里只带被引用消息的 id，
 * 不带内容，所以必须由发送方把 id 与当时的列表记下来，才能知道
 * 用户引用的到底是哪一张卡。
 * <p>
 * 顺带解决了「只能查一次」的问题：即使之后又发了别的卡片，
 * 引用回原来那张仍然按那张的序号解析。
 */
public final class BotCardRegistry {

    private BotCardRegistry() {
    }

    /** 卡片有效期，比上下文略长，方便用户回翻。 */
    private static final long TTL_MS = 1_800_000L;

    /** 一张卡片的快照。 */
    public record Card(String playerId, List<String> rooms, long createdAt) {
    }

    private static final Map<Long, Card> CARDS = new ConcurrentHashMap<>();

    /** 登记一张刚发出的卡片。 */
    public static void register(long messageId, String playerId, List<String> rooms) {
        if (messageId <= 0 || rooms == null || rooms.isEmpty()) {
            return;
        }
        purge();
        CARDS.put(messageId, new Card(playerId, List.copyOf(rooms), System.currentTimeMillis()));
    }

    /** 按被引用的消息 ID 取卡片；不存在或过期返回 null。 */
    public static Card get(long messageId) {
        Card card = CARDS.get(messageId);
        if (card == null) {
            return null;
        }
        if (System.currentTimeMillis() - card.createdAt() > TTL_MS) {
            CARDS.remove(messageId);
            return null;
        }
        return card;
    }

    private static void purge() {
        long now = System.currentTimeMillis();
        CARDS.entrySet().removeIf(e -> now - e.getValue().createdAt() > TTL_MS);
    }
}
