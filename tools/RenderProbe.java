import com.google.gson.JsonObject;
import top.szzz666.KokonaCelestia.api.DakanuClient;
import top.szzz666.KokonaCelestia.model.PlayerProfile;
import top.szzz666.KokonaCelestia.render.Assets;
import top.szzz666.KokonaCelestia.render.CardRenderer;
import top.szzz666.KokonaCelestia.render.HelpCard;
import top.szzz666.KokonaCelestia.render.MatchCard;
import top.szzz666.KokonaCelestia.render.PlayerCard;
import top.szzz666.KokonaCelestia.render.RankCard;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/** 本地出图探针：不依赖 QQ 环境，直接生成 PNG 便于比对设计稿。 */
public class RenderProbe {

    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "/tmp/celestia-out";
        File dir = new File(outDir);
        dir.mkdirs();

        Assets.initWebp();

        String mode = args.length > 1 ? args[1] : "all";
        String playerId = args.length > 2 ? args[2] : "8780938";

        if (mode.equals("help") || mode.equals("all")) {
            write(new HelpCard(), new File(dir, "help.png"));
        }

        if (mode.equals("player") || mode.equals("all")) {
            JsonObject raw = DakanuClient.summoner(playerId);
            PlayerProfile profile = PlayerProfile.from(raw);
            System.out.println("玩家: " + profile.nickname() + " 段位=" + profile.rank().segment()
                    + " 对局=" + profile.recentMatches().size() + " 模式=" + profile.modes().size());
            write(new PlayerCard(profile), new File(dir, "player.png"));
        }

        if (mode.equals("match") || mode.equals("all")) {
            JsonObject raw = DakanuClient.summoner(playerId);
            PlayerProfile profile = PlayerProfile.from(raw);
            if (!profile.recentMatches().isEmpty()) {
                String roomId = profile.recentMatches().get(0).roomId();
                JsonObject detail = DakanuClient.matchDetail(roomId);
                System.out.println("对局: " + roomId);
                write(new MatchCard(detail, profile.nickname(), profile.recentMatches().get(0)),
                        new File(dir, "match.png"));
            }
        }

        if (mode.equals("rank") || mode.equals("all")) {
            JsonObject ranks = DakanuClient.rankings(1, 1, 20);
            JsonObject season = null;
            try {
                season = DakanuClient.currentSeason();
            } catch (Exception ignored) {
            }
            write(new RankCard(ranks, season, 1, 20), new File(dir, "rank.png"));
        }
    }

    private static void write(CardRenderer card, File file) throws Exception {
        BufferedImage image = card.render();
        ImageIO.write(image, "png", file);
        System.out.println("输出 " + file + " " + image.getWidth() + "x" + image.getHeight()
                + " (" + (file.length() / 1024) + " KB)");
    }
}
