package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.rag.eval.RagEvalReport;
import com.yupi.yuaiagent.rag.eval.RagEvalRequest;
import com.yupi.yuaiagent.rag.eval.RagEvaluationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * RAG 检索评测调试接口
 *
 * 默认关闭，需设置：app.debug.rag-eval.enabled=true
 */
@Slf4j
@RestController
@RequestMapping("/debug/rag-eval")
@ConditionalOnProperty(name = "app.debug.rag-eval.enabled", havingValue = "true")
public class RagEvalController {

    @Resource
    private RagEvaluationService ragEvaluationService;

    @PostMapping("/run")
    public RagEvalReport run(@RequestBody RagEvalRequest request) {
        log.info("[RagEvalController-run] {}",
                kv("caseCount", request == null || request.cases() == null ? 0 : request.cases().size(),
                        "topK", request == null ? null : request.topK(),
                        "similarityThreshold", request == null ? null : request.similarityThreshold(),
                        "matchMode", request == null ? null : request.matchMode()));
        return ragEvaluationService.evaluate(request);
    }
}

