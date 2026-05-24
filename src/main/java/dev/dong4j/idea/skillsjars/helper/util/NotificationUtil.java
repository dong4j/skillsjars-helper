package dev.dong4j.idea.skillsjars.helper.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.dong4j.idea.skillsjars.helper.PluginContents;

/**
 * 通知工具类
 *
 * <p>提供显示信息, 警告和错误通知的功能, 主要用于在 IntelliJ IDEA 插件中向用户展示不同类型的提示信息.
 * 通过 {@link NotificationGroupManager} 直接发送通知, 不再依赖外部的 {@code idea-plugin-kit}, 让本插件
 * 完全独立可发布.</p>
 *
 * <p>通知组 ID 与 {@code plugin.xml} 中 {@code <notificationGroup id="..."/>} 配置一一对应,
 * 调整时需要同步两边.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.25
 * @since 1.0.0
 */
public final class NotificationUtil {

    private static final Logger LOG = Logger.getInstance(NotificationUtil.class);

    /** 通知组 ID, 必须与 plugin.xml 中 {@code notificationGroup id="..."} 保持一致. */
    private static final String GROUP_ID = PluginContents.PLUGIN_NAME + " Notifications";

    /**
     * 私有构造, 禁止实例化.
     */
    private NotificationUtil() {
    }

    /**
     * 显示信息通知
     * <p> 用于在指定项目上下文中显示一条普通的信息通知, 通知标题为插件名称
     *
     * @param project 项目对象, 可为空
     * @param message 通知内容, 不可为空
     * @since 1.0.0
     */
    public static void showInfo(@Nullable Project project, @NotNull String message) {
        notify(project, message, NotificationType.INFORMATION);
    }

    /**
     * 显示警告通知
     * <p> 用于在指定项目上下文中显示一条警告级别的通知
     *
     * @param project 项目对象, 可以为空
     * @param message 要显示的警告内容, 不能为空
     */
    public static void showWarning(@Nullable Project project, @NotNull String message) {
        notify(project, message, NotificationType.WARNING);
    }

    /**
     * 显示错误通知
     * <p> 在指定的项目中显示错误级别的通知信息
     *
     * @param project 项目对象, 可以为空
     * @param message 错误通知的内容, 不能为空
     */
    public static void showError(@Nullable Project project, @NotNull String message) {
        notify(project, message, NotificationType.ERROR);
    }

    /**
     * 实际发送通知.
     *
     * <p>找不到通知组通常意味着 {@code plugin.xml} 中的 {@code notificationGroup} 与 {@link #GROUP_ID}
     * 不一致或还没注册, 这种情况下记 debug 日志并静默返回, 避免在 EDT 上抛异常打断用户操作.</p>
     */
    private static void notify(@Nullable Project project, @NotNull String content, @NotNull NotificationType type) {
        NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID);
        if (group == null) {
            LOG.debug("Notification group not registered: " + GROUP_ID);
            return;
        }
        Notification notification = group.createNotification(PluginContents.PLUGIN_NAME, content, type);
        notification.notify(project);
    }
}
