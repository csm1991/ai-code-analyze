package com.simon.gitmcpserver.service;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

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
                PullResult pullResult = git.pull().call();
                log.info("✅ pull result:{}", pullResult);
            } else {
                CloneCommand cloneCommand = Git.cloneRepository()
                        .setURI(repositoryUrl)
                        .setDirectory(new File(localPath));
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

    private static boolean isGitRepository(File directory) {
        if (!directory.exists()) {
            directory.mkdirs();  // 创建目录（如果不存在）
            return false;
        }
        File gitDir = new File(directory, ".git");
        return gitDir.exists() && gitDir.isDirectory();
    }
}
