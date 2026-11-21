package top.szzz666.KokonaCelestia.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;

/**
 * 奇点分计算。
 * <p>
 * 进入奇点需要同时满足两个条件：
 * <ol>
 *   <li><b>超弦分 ≥ 200</b>（保底门槛）</li>
 *   <li><b>全服排名进入前 500</b></li>
 * </ol>
 * 因此「奇点分」就是第 500 名当前的超弦分，但不低于 200：
 * 第 500 名分数超过 200 时取实际值，否则按 200 计。
 * <p>
 * 注意：{@code score} 字段是**超弦分**，只有进入无限超弦（段位 29）才会计分；
 * 榜尾玩家多为禁闭夸克（26），其 {@code score} 为 0 属于正常现象。
 */
public final class SingularityClient {

    private SingularityClient() {
    }

    /** 超弦分保底门槛。 */
    public static final int MIN_SCORE = 200;

    /** 排行榜名额上限：进入前 500 名才有资格。 */
    public static final int RANK_LIMIT = 500;

    private static final int PAGE_SIZE = 20;

    /**
     * 奇点信息。
     *
     * @param floor       奇点分（= 第 500 名超弦分，不低于 200）
     * @param rawScore    第 500 名的原始超弦分
     * @param playerName  第 500 名玩家昵称
     * @param superstring 该玩家是否已进入无限超弦（未进入则超弦分为 0）
     */
    public record Result(int floor, int rawScore, String playerName, boolean superstring) {
    }

    /** 计算奇点分。 */
    public static Result compute() throws IOException {
        JsonObject lastPage = DakanuClient.rankings(1, RANK_LIMIT / PAGE_SIZE, PAGE_SIZE);

        int rawScore = 0;
        String playerName = "";
        boolean superstring = false;

        for (JsonElement element : DakanuClient.optArray(lastPage, "entries")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            if (DakanuClient.optInt(entry, "position", 0) != RANK_LIMIT) {
                continue;
            }
            rawScore = DakanuClient.optInt(entry, "score", 0);
            playerName = DakanuClient.optString(entry, "nickname", "");
            // 段位 29 = 无限超弦，30 = 奇点
            superstring = DakanuClient.optInt(entry, "segment", 0) >= 29;
            break;
        }

        return new Result(Math.max(MIN_SCORE, rawScore), rawScore, playerName, superstring);
    }

    /** 取第 500 名附近的排行榜窗口（用于高亮展示）。 */
    public static JsonObject rankWindow() throws IOException {
        return DakanuClient.rankings(1, RANK_LIMIT / PAGE_SIZE, PAGE_SIZE);
    }
}
