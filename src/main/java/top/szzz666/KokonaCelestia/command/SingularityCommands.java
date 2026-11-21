package top.szzz666.KokonaCelestia.command;

import top.szzz666.KokonaCelestia.api.SingularityClient;
import top.szzz666.KokonaCelestia.render.Renderer;
import top.szzz666.KokonaCelestia.render.SingularityCard;
import top.szzz666.KokonaCelestia.util.Reply;

/**
 * 奇点分指令。
 * <p>
 * 只发一张图：奇点分、进入奇点的两个条件，以及第 500 名高亮的排行榜。
 * 这些信息都画在卡片上，不再额外发文字消息。
 */
public final class SingularityCommands {

    private SingularityCommands() {
    }

    public static void show(Reply reply) {
        // 实测首次查询要 7 秒左右（排行榜第 25 页 + 头像），稳态约 4 秒
        reply.text("请稍候…");
        try {
            SingularityClient.Result result = SingularityClient.compute();
            reply.image(Renderer.render(
                    new SingularityCard(result, SingularityClient.rankWindow())));
        } catch (Exception e) {
            // 只有出错时才发文字，正常流程纯图
            reply.text("查询失败：" + e.getMessage());
        }
    }
}
