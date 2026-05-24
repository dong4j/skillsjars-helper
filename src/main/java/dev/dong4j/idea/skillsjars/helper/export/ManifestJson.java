package dev.dong4j.idea.skillsjars.helper.export;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;

/**
 * Manifest 的 JSON 序列化与反序列化.
 *
 * <p>{@link ManifestSchema} 的字段固定且简单 (字符串 + 整数 + 一层对象数组), 没必要为
 * 此引入 Gson 等第三方库; 因此此处提供一个迷你 JSON 解析器:</p>
 * <ul>
 *   <li>写入: 顺序固定、缩进 2 空格, 输出对人友好的 JSON 文本.</li>
 *   <li>读取: 标准 JSON 子集, 支持字符串 / 整数 / 对象 / 数组; 不支持浮点 / 布尔 / null
 *       (manifest 不需要); 未知字段宽容跳过.</li>
 *   <li>转义: 处理标准 JSON 字符串转义 (反斜杠 / 双引号 / 换行 / 回车 / Tab / 斜杠 / 4 位十六进制 unicode).</li>
 * </ul>
 *
 * <p>线程安全: 所有方法静态、无共享状态.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ManifestJson {

    private ManifestJson() {
    }

    // ─────────────────────────── 序列化 ───────────────────────────

    /**
     * 把 manifest 序列化成可读 JSON 字符串.
     */
    @NotNull
    public static String toJson(@NotNull ManifestSchema m) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\n");
        appendField(sb, "schemaVersion", m.getSchemaVersion(), false);
        appendField(sb, "managedBy", m.getManagedBy(), false);
        appendField(sb, "artifact", m.getArtifact(), false);
        appendField(sb, "sourceType", m.getSourceType().name(), false);
        appendField(sb, "skill", m.getSkill(), false);
        appendField(sb, "sourceJarSha256", m.getSourceJarSha256(), false);
        appendField(sb, "skillRoot", m.getSkillRoot(), false);
        appendField(sb, "installedAt", m.getInstalledAt(), false);
        appendField(sb, "targetAgent", m.getTargetAgent(), false);
        appendField(sb, "targetPath", m.getTargetPath(), false);

        sb.append("  \"files\": [");
        List<ManifestSchema.FileEntry> files = m.getFiles();
        if (files.isEmpty()) {
            sb.append("]\n");
        } else {
            sb.append('\n');
            for (int i = 0; i < files.size(); i++) {
                ManifestSchema.FileEntry f = files.get(i);
                sb.append("    {\n");
                sb.append("      \"path\": ").append(escape(f.getPath())).append(",\n");
                sb.append("      \"sha256\": ").append(escape(f.getSha256())).append(",\n");
                sb.append("      \"size\": ").append(f.getSize()).append('\n');
                sb.append("    }");
                if (i < files.size() - 1) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            sb.append("  ]\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendField(@NotNull StringBuilder sb, @NotNull String key, @NotNull String value, boolean trailing) {
        sb.append("  \"").append(key).append("\": ").append(escape(value));
        sb.append(trailing ? "\n" : ",\n");
    }

    private static void appendField(@NotNull StringBuilder sb, @NotNull String key, int value, boolean trailing) {
        sb.append("  \"").append(key).append("\": ").append(value);
        sb.append(trailing ? "\n" : ",\n");
    }

    @NotNull
    private static String escape(@NotNull String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"'  -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    // ─────────────────────────── 反序列化 ───────────────────────────

    /**
     * 解析 JSON 字符串.
     *
     * <p>解析失败时返回 null, 调用方应记录日志并把目录视为 FOREIGN (manifest 损坏 ≈ 没有
     * 可信 manifest).</p>
     */
    @Nullable
    public static ManifestSchema fromJson(@NotNull String json) {
        try {
            Parser p = new Parser(json);
            return p.readManifest();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 字符级递归下降解析器, 仅覆盖 manifest 需要的 JSON 子集.
     */
    private static final class Parser {

        @NotNull private final String src;
        private int pos;

        Parser(@NotNull String src) {
            this.src = src;
            this.pos = 0;
        }

        @NotNull
        ManifestSchema readManifest() {
            this.skipWs();
            this.expect('{');

            int schemaVersion = ManifestSchema.SCHEMA_VERSION;
            String managedBy = "";
            String artifact = "";
            String sourceTypeName = SkillSourceType.LOCAL_JAR.name();
            String skill = "";
            String sourceJarSha = "";
            String skillRoot = "";
            String installedAt = "";
            String targetAgent = "";
            String targetPath = "";
            List<ManifestSchema.FileEntry> files = new ArrayList<>();

            this.skipWs();
            if (this.peek() != '}') {
                while (true) {
                    this.skipWs();
                    String key = this.readString();
                    this.skipWs();
                    this.expect(':');
                    this.skipWs();
                    switch (key) {
                        case "schemaVersion" -> schemaVersion = (int) this.readNumber();
                        case "managedBy" -> managedBy = this.readString();
                        case "artifact" -> artifact = this.readString();
                        case "sourceType" -> sourceTypeName = this.readString();
                        case "skill" -> skill = this.readString();
                        case "sourceJarSha256" -> sourceJarSha = this.readString();
                        case "skillRoot" -> skillRoot = this.readString();
                        case "installedAt" -> installedAt = this.readString();
                        case "targetAgent" -> targetAgent = this.readString();
                        case "targetPath" -> targetPath = this.readString();
                        case "files" -> files = this.readFiles();
                        default -> this.skipValue();
                    }
                    this.skipWs();
                    if (this.peek() == ',') {
                        this.pos++;
                        continue;
                    }
                    break;
                }
            }
            this.skipWs();
            this.expect('}');

            SkillSourceType type;
            try {
                type = SkillSourceType.valueOf(sourceTypeName);
            } catch (IllegalArgumentException e) {
                type = SkillSourceType.LOCAL_JAR;
            }

            return new ManifestSchema(
                schemaVersion, managedBy, artifact, type, skill,
                sourceJarSha, skillRoot, installedAt, targetAgent, targetPath, files
            );
        }

        @NotNull
        List<ManifestSchema.FileEntry> readFiles() {
            this.expect('[');
            List<ManifestSchema.FileEntry> out = new ArrayList<>();
            this.skipWs();
            if (this.peek() == ']') {
                this.pos++;
                return out;
            }
            while (true) {
                this.skipWs();
                this.expect('{');
                String path = "", sha = "";
                long size = 0L;
                this.skipWs();
                if (this.peek() != '}') {
                    while (true) {
                        this.skipWs();
                        String key = this.readString();
                        this.skipWs();
                        this.expect(':');
                        this.skipWs();
                        switch (key) {
                            case "path" -> path = this.readString();
                            case "sha256" -> sha = this.readString();
                            case "size" -> size = this.readNumber();
                            default -> this.skipValue();
                        }
                        this.skipWs();
                        if (this.peek() == ',') {
                            this.pos++;
                            continue;
                        }
                        break;
                    }
                }
                this.skipWs();
                this.expect('}');
                out.add(new ManifestSchema.FileEntry(path, sha, size));
                this.skipWs();
                if (this.peek() == ',') {
                    this.pos++;
                    continue;
                }
                break;
            }
            this.skipWs();
            this.expect(']');
            return out;
        }

        @NotNull
        String readString() {
            this.expect('"');
            StringBuilder out = new StringBuilder();
            while (this.pos < this.src.length()) {
                char c = this.src.charAt(this.pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (this.pos >= this.src.length()) {
                        throw new IllegalStateException("unterminated escape");
                    }
                    char esc = this.src.charAt(this.pos++);
                    switch (esc) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (this.pos + 4 > this.src.length()) {
                                throw new IllegalStateException("bad unicode escape");
                            }
                            int code = Integer.parseInt(this.src.substring(this.pos, this.pos + 4), 16);
                            this.pos += 4;
                            out.append((char) code);
                        }
                        default -> out.append(esc);
                    }
                } else {
                    out.append(c);
                }
            }
            throw new IllegalStateException("unterminated string");
        }

        long readNumber() {
            int start = this.pos;
            if (this.peek() == '-') {
                this.pos++;
            }
            while (this.pos < this.src.length() && Character.isDigit(this.src.charAt(this.pos))) {
                this.pos++;
            }
            return Long.parseLong(this.src.substring(start, this.pos));
        }

        void skipValue() {
            this.skipWs();
            char c = this.peek();
            switch (c) {
                case '"' -> this.readString();
                case '{' -> this.skipBalanced('{', '}');
                case '[' -> this.skipBalanced('[', ']');
                default -> {
                    while (this.pos < this.src.length()) {
                        char cc = this.src.charAt(this.pos);
                        if (cc == ',' || cc == '}' || cc == ']') {
                            break;
                        }
                        this.pos++;
                    }
                }
            }
        }

        void skipBalanced(char open, char close) {
            this.expect(open);
            int depth = 1;
            while (this.pos < this.src.length() && depth > 0) {
                char c = this.src.charAt(this.pos++);
                if (c == '"') {
                    while (this.pos < this.src.length() && this.src.charAt(this.pos) != '"') {
                        if (this.src.charAt(this.pos) == '\\' && this.pos + 1 < this.src.length()) {
                            this.pos++;
                        }
                        this.pos++;
                    }
                    if (this.pos < this.src.length()) {
                        this.pos++;
                    }
                } else if (c == open) {
                    depth++;
                } else if (c == close) {
                    depth--;
                }
            }
        }

        void skipWs() {
            while (this.pos < this.src.length()) {
                char c = this.src.charAt(this.pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    this.pos++;
                } else {
                    break;
                }
            }
        }

        void expect(char c) {
            if (this.pos >= this.src.length() || this.src.charAt(this.pos) != c) {
                throw new IllegalStateException("expected '" + c + "' at " + this.pos);
            }
            this.pos++;
        }

        char peek() {
            if (this.pos >= this.src.length()) {
                return '\0';
            }
            return this.src.charAt(this.pos);
        }
    }
}
