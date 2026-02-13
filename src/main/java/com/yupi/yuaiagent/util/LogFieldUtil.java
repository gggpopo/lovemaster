package com.yupi.yuaiagent.util;

/**
 * 结构化日志字段格式化工具
 */
public final class LogFieldUtil {

    private LogFieldUtil() {
    }

    /**
     * 将可变参数按 key=value 组装为日志字段。
     *
     * 示例：kv("chatId", "c1", "messageLength", 12)
     * 输出：chatId=c1, messageLength=12
     */
    public static String kv(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(String.valueOf(keyValues[i]))
                    .append("=")
                    .append(String.valueOf(keyValues[i + 1]));
        }
        return sb.toString();
    }
}
