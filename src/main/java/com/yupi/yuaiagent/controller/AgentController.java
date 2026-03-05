package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.conversation.model.AgentDescriptor;
import com.yupi.yuaiagent.conversation.service.AgentRegistryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * Agent 资源接口。
 */
@Slf4j
@RestController
@RequestMapping("/agents")
public class AgentController {

    @Resource
    private AgentRegistryService agentRegistryService;

    @GetMapping
    public List<AgentDescriptor> listAgents() {
        List<AgentDescriptor> list = agentRegistryService.listAgents();
        log.info("[AgentController-listAgents] {}", kv("count", list.size()));
        return list;
    }
}
