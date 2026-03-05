package com.yupi.yuaiagent.orchestration.core;

import lombok.Getter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 对话执行取消令牌。
 */
@Getter
public class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private volatile String reason = "";

    private volatile long cancelledAt = 0L;

    public boolean cancel(String cancelReason) {
        boolean changed = cancelled.compareAndSet(false, true);
        if (changed) {
            this.reason = cancelReason == null ? "" : cancelReason;
            this.cancelledAt = System.currentTimeMillis();
        }
        return changed;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
