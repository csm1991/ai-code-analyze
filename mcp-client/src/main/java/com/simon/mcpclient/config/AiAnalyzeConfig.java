package com.simon.mcpclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI分析配置类
 *
 * @author Simon Cai
 * @version 1.0
 * @since 2025-07-13
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "simon.ai")
public class AiAnalyzeConfig {

    private String localRepository;

    private String remoteRepository;
}
