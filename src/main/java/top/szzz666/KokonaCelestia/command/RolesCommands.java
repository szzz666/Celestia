package top.szzz666.KokonaCelestia.command;

import top.szzz666.KokonaCelestia.api.RolesClient;
import top.szzz666.KokonaCelestia.render.Renderer;
import top.szzz666.KokonaCelestia.render.RolesCard;
import top.szzz666.KokonaCelestia.util.Reply;

/** 角色数据指令（对应 dakanu.com/roles 页面）。 */
public final class RolesCommands {

    private RolesCommands() {
    }

    /**
     * 出角色数据总览。
     *
     * @param argument 预留：可传地图名查看该图阵容
     */
    public static void show(Reply reply, String argument) {
        String arg = argument == null ? "" : argument.trim();
        // 实测首次查询要 36 秒（要拉 24 个角色头像与 6 张地图缩略图），
        // 素材缓存后约 2 秒；先给提示，避免长时间无响应
        reply.text("请稍候…");
        try {
            RolesClient.Snapshot data = RolesClient.fetch();
            if (data.roles().isEmpty() && data.maps().isEmpty()) {
                reply.text("暂时没有可用的角色数据，请稍后再试。");
                return;
            }
            reply.image(Renderer.render(new RolesCard(data)));
        } catch (Exception e) {
            reply.text("查询失败：" + e.getMessage());
        }
    }
}
