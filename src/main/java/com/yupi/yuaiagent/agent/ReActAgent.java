package com.yupi.yuaiagent.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * 实现了思考-行动的循环模式
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    public abstract boolean think();

    /**
     * 执行决定的行动
     *
     * @return 行动执行结果
     */
    public abstract String act();

    /**
     * 执行单个步骤：思考和行动
     *
     * @return 步骤执行结果
     */
    @Override
    public String step() {
        try {
            log.info("[ReActAgent-step] {}", kv("status", "start", "agent", getClass().getSimpleName()));
            // 先思考
            boolean shouldAct = think();
            if (!shouldAct) {
                log.info("[ReActAgent-step] {}",
                        kv("status", "skip_act", "agent", getClass().getSimpleName(), "shouldAct", false));
                return "思考完成 - 无需行动";
            }
            // 再行动
            String result = act();
            log.info("[ReActAgent-step] {}",
                    kv("status", "completed",
                            "agent", getClass().getSimpleName(),
                            "shouldAct", true,
                            "resultLength", result == null ? 0 : result.length(),
                            "result", result));
            return result;
        } catch (Exception e) {
            log.error("[ReActAgent-step] {}",
                    kv("status", "error", "agent", getClass().getSimpleName()), e);
            return "步骤执行失败：" + e.getMessage();
        }
    }

}
