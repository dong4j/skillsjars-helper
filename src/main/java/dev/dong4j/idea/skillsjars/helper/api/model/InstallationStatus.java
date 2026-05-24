package dev.dong4j.idea.skillsjars.helper.api.model;

/**
 * 一个 Skill 在某个目标 Agent 目录下的安装状态.
 *
 * <p>这是导出流程的核心枚举, 决定了 UI 该展示什么徽标、ExportPlanner 该走哪个分支、
 * 写入前是否需要弹确认对话框. 6 个值的设计目标是把 {@code docs/design.md} 里
 * "目标目录是否存在 / 是否由本插件管理 / 来源 jar 是否更新 / 本地文件是否被改" 这四个
 * 维度的判断结果, 压缩成一个互斥的状态机.</p>
 *
 * <p>状态判定的优先级顺序: {@link #NEW} → {@link #FOREIGN} → {@link #DUPLICATE_NAME}
 * → {@link #LOCALLY_MODIFIED} → {@link #OUTDATED} → {@link #UP_TO_DATE}.
 * 也就是说目录不存在直接是 NEW; 存在但没 manifest 是 FOREIGN; 有 manifest 但来源
 * 不一致是 DUPLICATE_NAME; 来源一致才进入"和当前 jar 比对"的两个状态; 全都对得上
 * 才是 UP_TO_DATE.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public enum InstallationStatus {

    /** 目标目录不存在, 可以直接写入. */
    NEW,

    /** 目标目录存在, manifest 来源一致, jar sha + 所有文件 sha 全部一致. */
    UP_TO_DATE,

    /**
     * 目标目录存在, manifest 来源一致 (同 artifact + 同 jar 内 entry root),
     * 但来源 jar 的 sha 已变化 (用户升级了依赖版本). 默认行为是直接覆盖写入.
     */
    OUTDATED,

    /**
     * 目标目录存在, manifest 来源一致, 但目标目录里至少有一个文件的 sha 与 manifest
     * 记录不符. 这是用户在导出后又手动改过文件的信号, 写入前必须二次确认.
     */
    LOCALLY_MODIFIED,

    /**
     * 目标目录存在, 但里面没有 {@code .skillsjars-helper.json} manifest. 可能是
     * 用户手工创建的, 也可能是其它工具产物. 写入前必须二次确认.
     */
    FOREIGN,

    /**
     * 目标目录存在, manifest 显示是 <b>另一个来源</b> 的同名 skill (artifact 或 jar
     * 内 entry root 不一致). 这是同名冲突, UI 会让用户在 "覆盖原 skill" 与 "改用
     * &lt;name&gt;__&lt;artifactId&gt; 后缀名" 之间做选择.
     */
    DUPLICATE_NAME
}
