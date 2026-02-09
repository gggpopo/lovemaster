package com.yupi.yuaiagent.dto;

import lombok.Data;

import java.util.List;

@Data
public class VisionChatRequest {
    private String message;
    private String chatId;
    private List<String> images;
}
