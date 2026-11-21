package top.szzz666.KokonaCelestia.util;

import top.szzz666.qq.bot.MsgBuilder;
import top.szzz666.qq.entity.Event;

/** 统一的群聊 / 私聊回复封装。 */
public final class Reply {

    private final Event event;

    private Reply(Event event) {
        this.event = event;
    }

    public static Reply of(Event event) {
        return new Reply(event);
    }

    /** 原始事件，供需要消息 ID / 发送者等信息的调用方使用。 */
    public Event event() {
        return event;
    }

    /** 发送者 QQ 号。 */
    public long userId() {
        return event.getUser_id();
    }

    /** 所在群号（私聊为 0）。 */
    public long groupId() {
        return event.getGroup_id();
    }

    public boolean isGroup() {
        return event.isGroupMessage();
    }

    public void text(String message) {
        send(MsgBuilder.builder().text(message).build());
    }

    /** 引用回复一条文本消息。 */
    public void quoteText(long messageId, String message) {
        send(MsgBuilder.builder().reply(messageId).text(message).build());
    }

    /** @ 某人并附带文本。 */
    public void atText(String qq, String message) {
        send(MsgBuilder.builder().at(qq).text(" " + message).build());
    }

    public void image(String base64) {
        send(MsgBuilder.builder().image(base64).build());
    }

    /**
     * 发送音频（语音消息）。
     * <p>
     * 传入 http(s) 直链时由 OneBot 实现自行拉取，无需先下载到本地。
     */
    public void audio(String url) {
        send(MsgBuilder.builder().record(url).build());
    }

    /** 先发一段文字再发音频，用于附带曲名说明。 */
    public void textWithAudio(String text, String url) {
        send(MsgBuilder.builder().text(text).record(url).build());
    }

    private void send(top.szzz666.qq.send.Params params) {
        if (event.isGroupMessage()) {
            event.getQq_bot().sendGroupMsg(event.getGroup_id(), params);
            return;
        }
        if (event.isPrivateMessage()) {
            event.getQq_bot().sendPrivateMsg(event.getUser_id(), params);
        }
    }
}
