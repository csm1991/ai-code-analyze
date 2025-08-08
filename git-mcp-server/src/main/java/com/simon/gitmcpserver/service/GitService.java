package com.simon.gitmcpserver.service;

import lombok.extern.slf4j.Slf4j;
import com.simon.gitmcpserver.config.GitAuthProperties;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * GitClone工具
 * 为什么自己写一个，是因为用了开源的其他GIT工具，不知道为啥DeepSeek都无法检出代码仓库
 *
 * @author Simon Cai
 * @version 1.0
 * @since 2025-07-13
 */
@Service
@Slf4j
public class GitService {

    private final GitAuthProperties gitAuthProperties;

    public GitService(GitAuthProperties gitAuthProperties) {
        this.gitAuthProperties = gitAuthProperties;
    }

    @Tool(description = "Clone the specified Github repository code to the specified local directory and return SUCCESS or FAIL to indicate the processing result.")
    public String clone(String repositoryUrl, String localPath) {

        log.info("Start Clone Github Repository,repositoryUrl:{},localPath:{}", repositoryUrl, localPath);

        //init
        File repoDir = new File(localPath);
        Git git = null;

        try {
            if (isGitRepository(repoDir)) {
                git = Git.open(repoDir);
                log.info("pulling");
                PullResult pullResult = git.pull()
                        .setCredentialsProvider(credentialsProvider())
                        .call();
                log.info("✅ pull result:{}", pullResult);
            } else {
                CloneCommand cloneCommand = Git.cloneRepository()
                        .setURI(repositoryUrl)
                        .setDirectory(new File(localPath))
                        .setCredentialsProvider(credentialsProvider());
                git = cloneCommand.call();
                log.info("✅ Clone Github Repository Success!");
            }
            return "SUCCESS";
        } catch (IOException ioException) {
            log.error("❌ Github Repository Pull Fail，ex:", ioException);
        } catch (GitAPIException e) {
            log.error("❌ Clone Github Repository Fail，ex:", e);
        } finally {
            if (git != null) {
                git.close();
            }
        }

        return "FAIL";
    }

    @Tool(description = "Create/switch to branch for the specified local Git repo, stage and commit local changes with an 'AI Fix' message, and push the branch to the given remote. Return 'SUCCESS:branchName' or 'FAIL'.")
    public String createAndPushBranch(String localPath, String remoteUrl, String branchName) {
        log.info("Start createAndPushBranch, localPath:{}, remoteUrl:{}, branchName:{}, hasToken:{}", localPath, maskToken(remoteUrl), branchName, gitAuthProperties.getToken() != null && !gitAuthProperties.getToken().isBlank());

        File repoDir = new File(localPath);
        if (!isGitRepository(repoDir)) {
            log.error("❌ Local path is not a git repository: {}", localPath);
            return "FAIL";
        }

        try (Git git = Git.open(repoDir)) {
            ensureRemoteOrigin(git, remoteUrl);

            // 先在当前分支提交变更，避免切换分支导致未提交变更被覆盖
            git.add().addFilepattern(".").call();
            git.add().setUpdate(true).addFilepattern(".").call();
            boolean hasAnythingToCommit = hasPendingChanges(git);
            if (hasAnythingToCommit) {
                ensureUserConfig(git);
                git.commit().setMessage("AI Fix: auto commit by MCP").setAll(true).call();
                log.info("Committed local changes on current branch");
            } else {
                log.info("No local changes to commit before branching.");
            }

            // 再创建/切换到目标分支
            if (localBranchExists(git, branchName)) {
                log.info("Branch exists, checkout: {}", branchName);
                git.checkout().setName(branchName).call();
            } else {
                log.info("Create and checkout branch: {}", branchName);
                try {
                    git.checkout()
                            .setCreateBranch(true)
                            .setName(branchName)
                            .call();
                } catch (RefAlreadyExistsException e) {
                    git.checkout().setName(branchName).call();
                }
            }

            // push branch to remote origin
            RefSpec refSpec = new RefSpec(branchName + ":refs/heads/" + branchName);
            git.push()
                    .setRemote("origin")
                    .setRefSpecs(refSpec)
                    .setCredentialsProvider(credentialsProvider())
                    .call();
            log.info("✅ Push success: {} -> origin/{}", branchName, branchName);
            return "SUCCESS:" + branchName;
        } catch (Exception e) {
            log.error("❌ createAndPushBranch failed", e);
            return "FAIL";
        }
    }

    private static void ensureRemoteOrigin(Git git, String remoteUrl) throws URISyntaxException, IOException {
        if (remoteUrl == null || remoteUrl.trim().isEmpty()) {
            return; // assume existing remote is fine
        }
        StoredConfig config = git.getRepository().getConfig();
        String existing = config.getString("remote", "origin", "url");
        if (existing == null || existing.trim().isEmpty()) {
            log.info("Configuring remote 'origin' -> {}", maskToken(remoteUrl));
            RemoteConfig remoteConfig = new RemoteConfig(config, "origin");
            remoteConfig.addURI(new URIish(remoteUrl));
            remoteConfig.update(config);
            config.save();
        } else if (!existing.equals(remoteUrl)) {
            log.info("Updating remote 'origin' url: {} -> {}", maskToken(existing), maskToken(remoteUrl));
            RemoteConfig remoteConfig = new RemoteConfig(config, "origin");
            // Clear existing URIs then set new one
            remoteConfig.getURIs().clear();
            remoteConfig.addURI(new URIish(remoteUrl));
            remoteConfig.update(config);
            config.save();
        }
    }

    private static boolean localBranchExists(Git git, String branchName) throws IOException, GitAPIException {
        Ref ref = git.getRepository().findRef("refs/heads/" + branchName);
        return ref != null;
    }

    private static boolean hasPendingChanges(Git git) throws GitAPIException {
        var status = git.status().call();
        return !(status.isClean() && status.getUntracked().isEmpty());
    }

    private static void ensureUserConfig(Git git) throws IOException {
        StoredConfig config = git.getRepository().getConfig();
        String name = config.getString("user", null, "name");
        String email = config.getString("user", null, "email");
        boolean updated = false;
        if (name == null || name.isBlank()) {
            config.setString("user", null, "name", "AI Bot");
            updated = true;
        }
        if (email == null || email.isBlank()) {
            config.setString("user", null, "email", "ai-bot@example.com");
            updated = true;
        }
        if (updated) {
            config.save();
        }
    }

    private static boolean isGitRepository(File directory) {
        if (!directory.exists()) {
            directory.mkdirs();  // 创建目录（如果不存在）
            return false;
        }
        File gitDir = new File(directory, ".git");
        return gitDir.exists() && gitDir.isDirectory();
    }

    @Tool(description = "Create and checkout a new local branch in the specified repo. Return 'SUCCESS:branchName' or 'FAIL'.")
    public String createAndCheckoutBranch(String localPath, String branchName) {
        File repoDir = new File(localPath);
        if (!isGitRepository(repoDir)) {
            log.error("❌ Local path is not a git repository: {}", localPath);
            return "FAIL";
        }
        try (Git git = Git.open(repoDir)) {
            if (localBranchExists(git, branchName)) {
                git.checkout().setName(branchName).call();
            } else {
                git.checkout().setCreateBranch(true).setName(branchName).call();
            }
            return "SUCCESS:" + branchName;
        } catch (Exception e) {
            log.error("❌ createAndCheckoutBranch failed", e);
            return "FAIL";
        }
    }

    @Tool(description = "Checkout an existing local branch in the specified repo. Return 'SUCCESS:branchName' or 'FAIL'.")
    public String checkoutBranch(String localPath, String branchName) {
        File repoDir = new File(localPath);
        if (!isGitRepository(repoDir)) {
            log.error("❌ Local path is not a git repository: {}", localPath);
            return "FAIL";
        }
        try (Git git = Git.open(repoDir)) {
            git.checkout().setName(branchName).call();
            return "SUCCESS:" + branchName;
        } catch (Exception e) {
            log.error("❌ checkoutBranch failed", e);
            return "FAIL";
        }
    }

    @Tool(description = "Stage all changes and commit with the given message in the specified repo. Return 'SUCCESS' or 'FAIL'.")
    public String commitAll(String localPath, String message) {
        File repoDir = new File(localPath);
        if (!isGitRepository(repoDir)) {
            log.error("❌ Local path is not a git repository: {}", localPath);
            return "FAIL";
        }
        try (Git git = Git.open(repoDir)) {
            git.add().addFilepattern(".").call();
            git.add().setUpdate(true).addFilepattern(".").call();
            if (hasPendingChanges(git)) {
                ensureUserConfig(git);
                git.commit().setMessage(message == null || message.isBlank() ? "AI Fix" : message).setAll(true).call();
            }
            return "SUCCESS";
        } catch (Exception e) {
            log.error("❌ commitAll failed", e);
            return "FAIL";
        }
    }

    @Tool(description = "Push the specified local branch to the remote 'origin' (create if missing). Return 'SUCCESS:branchName' or 'FAIL'.")
    public String pushBranch(String localPath, String remoteUrl, String branchName) {
        File repoDir = new File(localPath);
        if (!isGitRepository(repoDir)) {
            log.error("❌ Local path is not a git repository: {}", localPath);
            return "FAIL";
        }
        try (Git git = Git.open(repoDir)) {
            ensureRemoteOrigin(git, remoteUrl);
            RefSpec refSpec = new RefSpec(branchName + ":refs/heads/" + branchName);
            git.push()
                    .setRemote("origin")
                    .setRefSpecs(refSpec)
                    .setCredentialsProvider(credentialsProvider())
                    .call();
            return "SUCCESS:" + branchName;
        } catch (Exception e) {
            log.error("❌ pushBranch failed", e);
            return "FAIL";
        }
    }

    private UsernamePasswordCredentialsProvider credentialsProvider() {
        String token = gitAuthProperties.getToken();
        if (token == null || token.isBlank()) {
            return null;
        }
        String username = gitAuthProperties.getUsername();
        if (username == null || username.isBlank()) {
            // GitHub 官方推荐：PAT 作为密码，用户名可为账户用户名或 'x-access-token'
            username = "x-access-token";
        }
        return new UsernamePasswordCredentialsProvider(username, token);
    }

    private static String maskToken(String value) {
        if (value == null) {
            return null;
        }
        // 简单掩码：如果 URL 中包含 '@' 前的凭证，或值很长时仅保留前后 3 位
        int atIndex = value.indexOf('@');
        int colonIndex = value.indexOf(':');
        if (atIndex > 0 && colonIndex > 0 && colonIndex < atIndex) {
            String prefix = value.substring(0, colonIndex + 1);
            return prefix + "***@" + value.substring(atIndex + 1);
        }
        if (value.length() > 12) {
            return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
        }
        return value;
    }
}
