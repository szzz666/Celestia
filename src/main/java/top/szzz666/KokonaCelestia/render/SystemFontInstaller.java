package top.szzz666.KokonaCelestia.render;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 尝试为当前系统安装中文字体。
 * <p>
 * 仅在 Linux 上生效，按发行版选择包管理器。需要 root 权限；非 root 时会尝试
 * 无交互 sudo。安装完成后直接把字体文件注册进 JVM，绕开 fontconfig 缓存问题。
 * <p>
 * 注意：这会修改宿主机环境。容器部署时改动在重启后可能丢失，
 * 应改为在镜像构建阶段安装，或改用内置字体。
 */
public final class SystemFontInstaller {

    private SystemFontInstaller() {
    }

    /** 安装结果。 */
    public record Result(boolean success, String message) {
    }

    /** 各发行版对应的包管理器与包名。 */
    private record Recipe(String manager, String[] packages, String[] commands) {
    }

    private static final List<Recipe> RECIPES = List.of(
            new Recipe("apt-get",
                    new String[]{"fonts-noto-cjk"},
                    new String[]{"apt-get", "update", "-qq"}),
            new Recipe("dnf",
                    new String[]{"google-noto-sans-cjk-fonts"},
                    new String[]{"dnf", "makecache", "-q"}),
            new Recipe("yum",
                    new String[]{"google-noto-sans-cjk-fonts"},
                    new String[]{"yum", "makecache", "-q"}),
            new Recipe("apk",
                    new String[]{"font-noto-cjk"},
                    new String[]{"apk", "update"}),
            new Recipe("pacman",
                    new String[]{"noto-fonts-cjk"},
                    new String[]{})
    );

    /** 判断是否具备自动安装的前提条件（Linux + 有包管理器）。 */
    public static boolean supported() {
        return isLinux() && detectRecipe() != null;
    }

    /** 执行安装。 */
    public static Result install() {
        if (!isLinux()) {
            return new Result(false, "仅支持 Linux，当前系统请手动安装中文字体");
        }
        Recipe recipe = detectRecipe();
        if (recipe == null) {
            return new Result(false, "未识别到受支持的包管理器（apt/dnf/yum/apk/pacman）");
        }

        boolean root = "root".equals(System.getProperty("user.name"));
        String sudo = root ? "" : "sudo";
        if (!root && !hasPasswordlessSudo()) {
            return new Result(false, "需要 root 权限，且当前用户无法免密 sudo");
        }

        try {
            // 刷新包索引
            if (recipe.commands().length > 0) {
                run(withSudo(sudo, recipe.commands()), 120);
            }

            // 安装字体包
            List<String> install = new ArrayList<>();
            if (!sudo.isEmpty()) {
                install.add(sudo);
                install.add("-n");
            }
            install.add(recipe.manager());
            install.add("install");
            install.add("-y");
            install.add("--no-install-recommends");
            install.addAll(List.of(recipe.packages()));

            CmdResult result = run(install.toArray(new String[0]), 300);
            if (result.exitCode() != 0 && result.output().toLowerCase(Locale.ROOT).contains("unable to locate")) {
                // --no-install-recommends 在部分包管理器上不支持，重试一次
                List<String> retry = new ArrayList<>(install);
                retry.remove("--no-install-recommends");
                result = run(retry.toArray(new String[0]), 300);
            }
            if (result.exitCode() != 0) {
                return new Result(false, "安装失败: " + brief(result.output()));
            }

            // 刷新 fontconfig 缓存
            run(new String[]{sudo.isEmpty() ? "fc-cache" : "sudo", "-f", "-v"}, 120);

            // 直接把装好的字体注册进 JVM，避免缓存未刷新导致读不到
            int registered = registerInstalledFonts();
            if (registered > 0) {
                return new Result(true, "已安装中文字体并注册 " + registered + " 个字体文件");
            }
            return new Result(true, "已安装中文字体包，若仍显示方块请重启机器人进程");
        } catch (Exception e) {
            return new Result(false, "安装异常: " + e.getMessage());
        }
    }

    private static String[] withSudo(String sudo, String[] command) {
        if (sudo.isEmpty()) {
            return command;
        }
        String[] out = new String[command.length + 2];
        out[0] = sudo;
        out[1] = "-n";
        System.arraycopy(command, 0, out, 2, command.length);
        return out;
    }

    /** 在常见字体目录中查找刚安装的中文字体并注册。 */
    private static int registerInstalledFonts() {
        String[] dirs = {
                "/usr/share/fonts", "/usr/local/share/fonts",
                System.getProperty("user.home") + "/.fonts"
        };
        int count = 0;
        for (String dir : dirs) {
            File root = new File(dir);
            if (!root.isDirectory()) {
                continue;
            }
            try (var stream = Files.walk(Path.of(dir), 6)) {
                List<Path> fonts = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return n.endsWith(".ttf") || n.endsWith(".otf") || n.endsWith(".ttc");
                        })
                        .toList();
                for (Path path : fonts) {
                    // 只注册确实含中文字形的字体，避免把几百个拉丁字体全塞进 JVM
                    if (Fonts.registerFontFile(path.toFile())) {
                        count++;
                        if (count >= 4) {
                            return count;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return count;
    }

    private static boolean hasPasswordlessSudo() {
        try {
            CmdResult r = run(new String[]{"sudo", "-n", "true"}, 10);
            return r.exitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Recipe detectRecipe() {
        for (Recipe recipe : RECIPES) {
            if (commandExists(recipe.manager())) {
                return recipe;
            }
        }
        return null;
    }

    private static boolean commandExists(String command) {
        try {
            CmdResult r = run(new String[]{"sh", "-c", "command -v " + command}, 10);
            return r.exitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isLinux() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("linux");
    }

    private record CmdResult(int exitCode, String output) {
    }

    private static CmdResult run(String[] command, int timeoutSeconds) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.environment().put("DEBIAN_FRONTEND", "noninteractive");
        Process process = builder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lines = 0;
            while ((line = reader.readLine()) != null && lines < 400) {
                output.append(line).append('\n');
                lines++;
            }
        }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return new CmdResult(-1, "命令超时: " + String.join(" ", command));
        }
        return new CmdResult(process.exitValue(), output.toString());
    }

    private static String brief(String text) {
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() > 160 ? flat.substring(0, 160) + "…" : flat;
    }
}
