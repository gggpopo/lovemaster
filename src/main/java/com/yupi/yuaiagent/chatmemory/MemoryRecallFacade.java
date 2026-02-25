package com.yupi.yuaiagent.chatmemory;

import com.yupi.yuaiagent.chatmemory.model.MemoryCandidate;

import java.util.List;

/**
 * 主动记忆召回门面，供工具调用。
 */
public interface MemoryRecallFacade {

    List<MemoryCandidate> recall(String conversationId, String query, int topK);
}
