package com.simon.gitmcpserver;

import com.simon.gitmcpserver.service.GitService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GitMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitMcpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider weatherTools(GitService gitService) {
        return MethodToolCallbackProvider.builder().toolObjects(gitService).build();
    }
}
