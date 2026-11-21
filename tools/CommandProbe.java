import top.szzz666.KokonaCelestia.api.DakanuClient;
import top.szzz666.KokonaCelestia.model.PlayerProfile;
import top.szzz666.KokonaCelestia.render.Assets;
import top.szzz666.KokonaCelestia.render.MatchCard;
import top.szzz666.KokonaCelestia.render.PlayerCard;
import top.szzz666.KokonaCelestia.render.Renderer;

import java.util.List;

/** 命令行探针：验证解析逻辑与出图链路（不含 QQ 网络发送）。 */
public class CommandProbe {

    public static void main(String[] args) throws Exception {
        Assets.initWebp();

        // 1. 昵称解析
        for (String q : new String[]{"地蕾女", "浅海梦", "8780938", "不存在的玩家xyz"}) {
            List<DakanuClient.PlayerSuggestion> list = DakanuClient.search(q);
            System.out.println("[搜索] " + q + " -> " + list.size() + " 个候选");
            for (DakanuClient.PlayerSuggestion s : list) {
                System.out.println("    " + s.playerId() + " | " + s.nickname()
                        + " | matched=" + s.matchedName() + " by=" + s.matchedBy()
                        + " exact=" + s.exact());
            }
        }

        // 2. 完整链路：概览出图
        DakanuClient.PlayerSuggestion target = DakanuClient.search("地蕾女").get(0);
        PlayerProfile profile = PlayerProfile.from(DakanuClient.summoner(target.playerId()));
        String dataUri = Renderer.render(new PlayerCard(profile));
        System.out.println("[出图] player dataUri 长度 = " + dataUri.length()
                + " 前缀=" + dataUri.substring(0, 16));

        // 3. 短号匹配
        String fullRoom = profile.recentMatches().get(0).roomId();
        String shortRoom = fullRoom.substring(fullRoom.length() - 6);
        System.out.println("[短号] 完整=" + fullRoom + " 短号=" + shortRoom);
        PlayerProfile.Match hit = null;
        for (PlayerProfile.Match m : profile.recentMatches()) {
            if (m.roomId().endsWith(shortRoom)) {
                hit = m;
                break;
            }
        }
        System.out.println("[短号] 命中=" + (hit == null ? "无" : hit.roomId()));

        if (hit != null) {
            var detail = DakanuClient.matchDetail(hit.roomId());
            String uri = Renderer.render(new MatchCard(detail, profile.nickname(), hit));
            System.out.println("[出图] match dataUri 长度 = " + uri.length());
        }

        // 4. 分页边界
        for (int page : new int[]{1, 2, 100}) {
            try {
                var ranks = DakanuClient.rankings(1, page, 20);
                System.out.println("[排行] page=" + page + " start="
                        + DakanuClient.optInt(ranks, "start", 0) + " end="
                        + DakanuClient.optInt(ranks, "end", 0) + " total="
                        + DakanuClient.optInt(ranks, "total", 0) + " entries="
                        + DakanuClient.optArray(ranks, "entries").size());
            } catch (Exception e) {
                System.out.println("[排行] page=" + page + " 失败: " + e.getMessage());
            }
        }
    }
}
