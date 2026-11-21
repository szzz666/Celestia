package top.szzz666.KokonaCelestia.command;

import top.szzz666.KokonaCelestia.api.WikiBgm;
import top.szzz666.KokonaCelestia.config.MyConfig;
import top.szzz666.KokonaCelestia.render.BgmCard;
import top.szzz666.KokonaCelestia.render.Renderer;
import top.szzz666.KokonaCelestia.util.Reply;

import java.util.List;

/** BGM 指令：列表与点播。 */
public final class BgmCommands {

    private BgmCommands() {
    }

    /** 每页曲目数。 */
    private static final int PAGE_SIZE = 60;

    /** 出曲目列表。 */
    public static void list(Reply reply) {
        try {
            List<WikiBgm.Track> tracks = WikiBgm.tracks();
            if (tracks.isEmpty()) {
                reply.text("暂时没能从 WIKI 取到曲目。");
                return;
            }
            reply.image(Renderer.render(new BgmCard(tracks, 1, PAGE_SIZE)));
            CardContext.remember(reply.groupId(), reply.userId(),
                    CardContext.Kind.BGM, "", 1, tracks.size());
        } catch (Exception e) {
            reply.text("查询失败：" + e.getMessage());
        }
    }

    /** 按序号点播（裸数字与「BGM n」都走这里）。 */
    public static void play(Reply reply, int index) {
        try {
            List<WikiBgm.Track> tracks = WikiBgm.tracks();
            if (tracks.isEmpty()) {
                reply.text("暂时没能从 WIKI 取到曲目。");
                return;
            }
            if (index < 1 || index > tracks.size()) {
                reply.text("曲目序号范围是 1 - " + tracks.size() + "。");
                return;
            }
            play(reply, tracks.get(index - 1));
        } catch (Exception e) {
            reply.text("查询失败：" + e.getMessage());
        }
    }

    /** 点播一首。 */
    static void play(Reply reply, WikiBgm.Track track) {
        String mode = MyConfig.bgmSendMode == null ? "record" : MyConfig.bgmSendMode.trim();
        if ("link".equalsIgnoreCase(mode)) {
            reply.text("♪ " + track.name() + "\n" + track.url());
            return;
        }
        try {
            reply.textWithAudio("♪ " + track.name(), track.url());
        } catch (Exception e) {
            reply.text("♪ " + track.name() + "\n语音发送失败，请直接播放：\n" + track.url());
        }
    }

    /**
     * 处理 BGM 指令的完整逻辑。
     *
     * @param argument 空则出列表，数字或曲名则点播
     */
    public static void handle(Reply reply, String argument) {
        String arg = argument == null ? "" : argument.trim();
        if (arg.isEmpty()) {
            list(reply);
            return;
        }
        if (arg.matches("\\d{1,3}")) {
            play(reply, Integer.parseInt(arg));
            return;
        }
        try {
            List<WikiBgm.Track> tracks = WikiBgm.tracks();
            WikiBgm.Track track = WikiBgm.find(tracks, arg);
            if (track != null) {
                play(reply, track);
                return;
            }
            List<WikiBgm.Track> hits = WikiBgm.search(tracks, arg);
            if (!hits.isEmpty()) {
                StringBuilder sb = new StringBuilder("没有完全匹配的曲目，你是不是想听：\n");
                for (WikiBgm.Track hit : hits) {
                    sb.append("· ").append(hit.name()).append('\n');
                }
                sb.append("发送「BGM 完整曲名」即可点播。");
                reply.text(sb.toString());
                return;
            }
            reply.text("没有找到「" + arg + "」这首曲子。\n发送「BGM」查看全部曲目与序号。");
        } catch (Exception e) {
            reply.text("查询失败：" + e.getMessage());
        }
    }
}
