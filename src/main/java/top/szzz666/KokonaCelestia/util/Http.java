package top.szzz666.KokonaCelestia.util;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.zip.GZIPInputStream;

/**
 * 极简 HTTPS 客户端。
 * <p>
 * Dakanu 站点对部分网络环境存在 TLS 握手阻断，同时其网关会校验 Host 头
 * （Host 缺失时返回 418）。{@code HttpsURLConnection} 会忽略手工设置的 Host 头，
 * 因此这里直接基于 {@link SSLSocket} 手写 HTTP/1.1 请求：
 * 连接目标为解析后的 IP，TLS SNI 置空，Host 头显式写入域名，证书校验关闭。
 * <p>
 * 性能设计：一次 TLS 握手在本机实测约 1 秒，而单张头像只有 5KB。
 * 因此这里维护一个 keep-alive 连接池，同一批图片复用已建立的连接，
 * 避免每张图都重新握手；同时按 Content-Length 精确读取响应体，
 * 不再依赖 "读到 EOF"，这是连接复用的前提。
 */
public final class Http {

    private static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static final int CONNECT_TIMEOUT = 8_000;
    private static final int READ_TIMEOUT = 15_000;

    /** 连接池容量上限，与图片下载并发度匹配。 */
    private static final int POOL_LIMIT = 12;
    /** 空闲连接的最大存活时间，避免复用到已被服务端关闭的连接。 */
    private static final long IDLE_TTL_MS = 20_000L;

    private static final ConcurrentLinkedDeque<PooledConnection> POOL = new ConcurrentLinkedDeque<>();

    private static volatile SSLContext sslContext;

    private Http() {
    }

    /** HTTP 响应。 */
    public record Response(int status, byte[] body, String contentType) {
        public String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /** 池化连接：记录创建时间用于判断空闲时长。 */
    private record PooledConnection(SSLSocket socket, String ip, long lastUsedAt) {
    }

    private static SSLContext context() throws Exception {
        SSLContext ctx = sslContext;
        if (ctx != null) {
            return ctx;
        }
        synchronized (Http.class) {
            if (sslContext == null) {
                TrustManager[] trustAll = {new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }};
                SSLContext created = SSLContext.getInstance("TLS");
                created.init(null, trustAll, new SecureRandom());
                sslContext = created;
            }
            return sslContext;
        }
    }

    /**
     * 发起 GET 请求（优先复用连接池中的空闲连接）。
     *
     * @param host 站点域名，写入 Host 头
     * @param path 请求路径（含查询串）
     */
    public static Response get(String host, String path) throws IOException {
        List<String> ips = resolve(host);
        IOException last = null;

        // 先尝试复用一个同 IP 的空闲连接
        PooledConnection reused = take(ips);
        if (reused != null) {
            try {
                return exchange(reused.socket(), host, path);
            } catch (IOException e) {
                closeQuietly(reused.socket());
                last = e;
            }
        }

        // 新建连接
        for (String ip : ips) {
            SSLSocket socket = null;
            try {
                socket = open(ip);
                Response response = exchange(socket, host, path);
                release(socket, ip);
                return response;
            } catch (IOException e) {
                closeQuietly(socket);
                last = e;
            }
        }
        throw last != null ? last : new IOException("无法解析主机: " + host);
    }

    /** 建连：IP 直连 + 清空 SNI。 */
    private static SSLSocket open(String ip) throws IOException {
        SSLContext ctx;
        try {
            ctx = context();
        } catch (Exception e) {
            throw new IOException("初始化 TLS 上下文失败", e);
        }
        SSLSocket socket = (SSLSocket) ctx.getSocketFactory().createSocket();
        socket.connect(new InetSocketAddress(ip, 443), CONNECT_TIMEOUT);
        socket.setSoTimeout(READ_TIMEOUT);
        socket.setTcpNoDelay(true);

        SSLParameters params = socket.getSSLParameters();
        // 阻断环境下携带 SNI 会被重置，这里显式清空
        params.setServerNames(Collections.emptyList());
        socket.setSSLParameters(params);
        socket.startHandshake();
        return socket;
    }

    /** 在一个已建连的 socket 上完成一次请求/响应。 */
    private static Response exchange(SSLSocket socket, String host, String path) throws IOException {
        String request = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + host + "\r\n"
                + "User-Agent: " + UA + "\r\n"
                + "Accept: application/json,image/webp,image/*,*/*\r\n"
                + "Accept-Language: zh-CN,zh;q=0.9\r\n"
                + "Accept-Encoding: gzip\r\n"
                + "Referer: https://" + host + "/\r\n"
                + "Connection: keep-alive\r\n\r\n";

        OutputStream out = socket.getOutputStream();
        out.write(request.getBytes(StandardCharsets.UTF_8));
        out.flush();

        InputStream in = socket.getInputStream();
        String head = readHeader(in);
        if (head == null) {
            throw new IOException("响应头为空，连接可能已被服务端关闭");
        }
        return parse(head, in);
    }

    /** 逐字节读响应头，直到空行。 */
    private static String readHeader(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(512);
        int p3 = -1;
        int p2 = -1;
        int p1 = -1;
        int b;
        while ((b = in.read()) >= 0) {
            buffer.write(b);
            if (p3 == 13 && p2 == 10 && p1 == 13 && b == 10) {
                return buffer.toString(StandardCharsets.ISO_8859_1);
            }
            p3 = p2;
            p2 = p1;
            p1 = b;
        }
        return buffer.size() > 0 ? buffer.toString(StandardCharsets.ISO_8859_1) : null;
    }

    /** 解析响应头并按需读取响应体。 */
    private static Response parse(String head, InputStream in) throws IOException {
        String[] lines = head.split("\r\n");
        String statusLine = lines.length > 0 ? lines[0] : "";
        int status = 0;
        String[] parts = statusLine.split(" ");
        if (parts.length >= 2) {
            try {
                status = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        lines[i].substring(colon + 1).trim());
            }
        }

        byte[] body;
        String transferEncoding = headers.getOrDefault("transfer-encoding", "");
        String contentLength = headers.getOrDefault("content-length", "");

        if (transferEncoding.toLowerCase(Locale.ROOT).contains("chunked")) {
            body = readChunked(in);
        } else if (!contentLength.isEmpty()) {
            int length;
            try {
                length = Integer.parseInt(contentLength);
            } catch (NumberFormatException e) {
                length = -1;
            }
            body = length >= 0 ? in.readNBytes(length) : in.readAllBytes();
        } else if (status == 204 || status == 304) {
            body = new byte[0];
        } else {
            // 没有长度信息，只能读到 EOF（此时连接不可复用）
            body = in.readAllBytes();
        }

        String encoding = headers.getOrDefault("content-encoding", "");
        if ("gzip".equalsIgnoreCase(encoding) && body.length > 0) {
            body = gunzip(body);
        }
        return new Response(status, body, headers.getOrDefault("content-type", ""));
    }

    /** 按 chunked 编码读取响应体。 */
    private static byte[] readChunked(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        while (true) {
            StringBuilder sizeLine = new StringBuilder();
            int b;
            while ((b = in.read()) >= 0) {
                if (b == 13) {
                    int lf = in.read();
                    if (lf == 10) {
                        break;
                    }
                    continue;
                }
                sizeLine.append((char) b);
            }
            String text = sizeLine.toString().trim();
            int semicolon = text.indexOf(';');
            if (semicolon >= 0) {
                text = text.substring(0, semicolon);
            }
            int size;
            try {
                size = Integer.parseInt(text, 16);
            } catch (NumberFormatException e) {
                break;
            }
            if (size == 0) {
                // 吃掉尾部空行
                while ((b = in.read()) >= 0) {
                    if (b == 10) {
                        break;
                    }
                }
                break;
            }
            byte[] chunk = in.readNBytes(size);
            out.write(chunk, 0, chunk.length);
            in.read();
            in.read();
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] data) throws IOException {
        if (data.length < 2) {
            return data;
        }
        try (GZIPInputStream gzip = new GZIPInputStream(
                new java.io.ByteArrayInputStream(data))) {
            return gzip.readAllBytes();
        }
    }

    // ==================== 连接池 ====================

    /** 取出一个可复用的空闲连接。 */
    private static PooledConnection take(List<String> ips) {
        long now = System.currentTimeMillis();
        PooledConnection candidate;
        while ((candidate = POOL.pollFirst()) != null) {
            boolean expired = now - candidate.lastUsedAt() > IDLE_TTL_MS;
            boolean wrongIp = !ips.contains(candidate.ip());
            if (expired || wrongIp || !isUsable(candidate.socket())) {
                closeQuietly(candidate.socket());
                continue;
            }
            return candidate;
        }
        return null;
    }

    /** 归还连接以便复用。 */
    private static void release(SSLSocket socket, String ip) {
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            closeQuietly(socket);
            return;
        }
        if (POOL.size() >= POOL_LIMIT) {
            closeQuietly(socket);
            return;
        }
        POOL.addFirst(new PooledConnection(socket, ip, System.currentTimeMillis()));
    }

    /** 粗判连接是否仍然可用（对端已关闭但本地未感知时不复用的场景由调用方重试兜底）。 */
    private static boolean isUsable(SSLSocket socket) {
        return socket != null && !socket.isClosed() && socket.isConnected()
                && !socket.isInputShutdown() && !socket.isOutputShutdown();
    }

    private static void closeQuietly(SSLSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    /** 关闭连接池中全部空闲连接。 */
    public static void closeIdleConnections() {
        PooledConnection connection;
        while ((connection = POOL.pollFirst()) != null) {
            closeQuietly(connection.socket());
        }
    }

    private static List<String> resolve(String host) throws IOException {
        List<String> out = new ArrayList<>();
        for (InetAddress address : InetAddress.getAllByName(host)) {
            String ip = address.getHostAddress();
            // IPv6 字面量需要方括号包装，部分网络下也不可用，优先使用 IPv4
            if (ip.indexOf(':') >= 0) {
                continue;
            }
            out.add(ip);
        }
        if (out.isEmpty()) {
            throw new IOException("没有可用的 IPv4 地址: " + host);
        }
        return out;
    }
}
