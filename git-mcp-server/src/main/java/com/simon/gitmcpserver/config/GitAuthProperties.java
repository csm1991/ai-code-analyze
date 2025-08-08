package com.simon.gitmcpserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "git.auth")
public class GitAuthProperties {

    /**
     * Git 远程操作使用的用户名。对于 GitHub 使用 PAT 时，可为任意非空字符串。
     */
    private String username;

    /**
     * GitHub Personal Access Token 或其他远端需要的 token。
     */
    private String token;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}


