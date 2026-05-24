package dev.dong4j.idea.skillsjars.helper.export;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * sha256 工具.
 *
 * <p>导出流程在三个地方需要 sha:</p>
 * <ul>
 *   <li>计算来源 jar 的 sha (写入 manifest, 用于判断 jar 升级).</li>
 *   <li>计算 jar 内 skill 文件的 sha (写入 manifest, 用于判断本地修改).</li>
 *   <li>计算磁盘上目标目录文件的 sha (用于和 manifest 比对).</li>
 * </ul>
 *
 * <p>统一使用 SHA-256, 输出小写 hex 字符串. 不抛检查异常, 把底层 IOException 包装成
 * 运行时异常以简化调用. </p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class HashUtil {

    private static final int BUFFER_SIZE = 8192;

    private HashUtil() {
    }

    /**
     * 计算给定字节数组的 sha256, 返回小写 hex.
     */
    @NotNull
    public static String sha256(byte @NotNull [] bytes) {
        MessageDigest md = newDigest();
        md.update(bytes);
        return toHex(md.digest());
    }

    /**
     * 计算给定文件的 sha256, 返回小写 hex.
     *
     * @throws RuntimeException 文件不存在 / 不可读时
     */
    @NotNull
    public static String sha256(@NotNull Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return sha256Stream(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to compute sha256 for " + file, e);
        }
    }

    /**
     * 计算输入流内容的 sha256, 返回小写 hex. 调用方负责关闭流.
     */
    @NotNull
    public static String sha256Stream(@NotNull InputStream in) throws IOException {
        MessageDigest md = newDigest();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) > 0) {
            md.update(buffer, 0, read);
        }
        return toHex(md.digest());
    }

    @NotNull
    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // 不可达: SHA-256 是 JDK 必选算法
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @NotNull
    private static String toHex(byte @NotNull [] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
