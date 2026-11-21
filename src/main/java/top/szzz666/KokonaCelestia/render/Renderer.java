package top.szzz666.KokonaCelestia.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/** 出图入口：统一转成 QQ 可直接发送的 data URI。 */
public final class Renderer {

    private Renderer() {
    }

    /**
     * 渲染并编码为 OneBot 图片段可识别的 Base64 串。
     * <p>
     * 前缀使用 {@code base64://}，与框架文档《HTML 转图片》一节的发送示例保持一致。
     */
    public static String render(CardRenderer card) {
        try {
            BufferedImage image = card.render();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return "base64://" + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("出图失败：" + e.getMessage(), e);
        }
    }

    /** 仅生成图片字节，便于本地调试。 */
    public static byte[] renderBytes(CardRenderer card) throws IOException {
        BufferedImage image = card.render();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
