package com.yupi.yuaiagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * 终端操作工具
 */
@Slf4j
public class TerminalOperationTool {

    @Tool(description = "Execute a command in the terminal")
    public String executeTerminalCommand(@ToolParam(description = "Command to execute in the terminal") String command) {
        log.info("[TerminalOperationTool-executeTerminalCommand] {}",
                kv("command", command));
        long start = System.currentTimeMillis();
        StringBuilder output = new StringBuilder();
        int exitCode = -1;
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
//            Process process = Runtime.getRuntime().exec(command);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            exitCode = process.waitFor();
            if (exitCode != 0) {
                output.append("Command execution failed with exit code: ").append(exitCode);
            }
        } catch (IOException e) {
            log.error("[TerminalOperationTool-executeTerminalCommand] {}",
                    kv("command", command, "status", "io_error"), e);
            output.append("Error executing command: ").append(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[TerminalOperationTool-executeTerminalCommand] {}",
                    kv("command", command, "status", "interrupted"), e);
            output.append("Error executing command: ").append(e.getMessage());
        }
        long cost = System.currentTimeMillis() - start;
        log.info("[TerminalOperationTool-executeTerminalCommand] {}",
                kv("command", command,
                        "exitCode", exitCode,
                        "durationMs", cost,
                        "outputLength", output.length()));
        return output.toString();
    }
}
