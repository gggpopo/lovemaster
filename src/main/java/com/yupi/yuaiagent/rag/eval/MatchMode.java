package com.yupi.yuaiagent.rag.eval;

import cn.hutool.core.util.StrUtil;

/**
 * 命中匹配模式
 */
public enum MatchMode {
    ANY,
    ALL;

    public static MatchMode of(String raw) {
        if (StrUtil.equalsIgnoreCase(raw, "ALL")) {
            return ALL;
        }
        return ANY;
    }
}

