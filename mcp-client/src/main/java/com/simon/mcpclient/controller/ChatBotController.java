package com.simon.mcpclient.controller;

import com.simon.mcpclient.config.AiAnalyzeConfig;
import com.simon.mcpclient.req.ChatReq;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AI相关接口
 *
 * @author Simon Cai
 * @version 1.0
 * @since 2025-07-13
 */
@Slf4j
@RestController
public class ChatBotController {

    private static final String FAIL = "FAIL";

    private AiAnalyzeConfig aiAnalyzeConfig;

    private ChatClient chatClient;

    public ChatBotController(ChatClient.Builder chatClientBuilder,
                             ToolCallbackProvider toolCallbackProvider,
                             AiAnalyzeConfig aiAnalyzeConfig) {
        this.chatClient = chatClientBuilder
                .defaultSystem("你是一个Java资深专家，并且擅长使用不同的MCP工具来解决用户反馈的问题。")
                // 注册工具方法
                .defaultToolCallbacks(toolCallbackProvider)
                .build();
        this.aiAnalyzeConfig = aiAnalyzeConfig;
    }

    /**
     * 处理聊天请求，使用AI和MCP工具进行响应
     */
    @RequestMapping(value = "/api/error_analyze")
    public String errorAnalyze(@RequestBody ChatReq req) {
        log.info(">>> 异常分析，入参: {}", req.getMessage());

        //region 检查本地仓库，如果不存在则从远程仓库检出
        String cloneStr = "我需要你从git远程仓库中检出最新的代码到我指定的本地仓库目录\n" +
                "git远程仓库地址为：" + aiAnalyzeConfig.getRemoteRepository() + "\n" +
                "本地仓库地址为：" + aiAnalyzeConfig.getLocalRepository() + "\n" +
                "处理成功需回答SUCCESS，处理失败需回答FAIL，不需要回答其他内容";
        String cloneResult = doChat(cloneStr);
        if (FAIL.equals(cloneResult)) {
            return "cloneFail";
        }
        //endregion

        //region 提取堆栈核心信息
        String extractStr = "我的java程序在运行过程中遇到了异常，下面我将提供给你对应的异常堆栈信息，你需要帮我提取需要分析的类和具体行数，如果有多个类需要分析，那么就用'|'进行分割\n" +
                "异常堆栈信息如下：\n" +
                req.getMessage() + "\n" +
                "我仅需要你输出类+具体行数，不需要回答其他内容，输出示例如下：\n" +
                "[A.java,16]|[B.java,20]";
        String extractResult = doChat(extractStr);
        if (StringUtils.isEmpty(extractResult)) {
            return "extractFail";
        }
        //endregion

        //提取类的文件名
        StringBuilder fileNameStringBuilder = new StringBuilder();
        String[] classNames = extractResult.split("\\|");
        for (String className : classNames) {
            String[] classAndLine = className.split(",");
            String className1 = classAndLine[0];
            fileNameStringBuilder.append(",").append(className1);
        }
        String classNameStr = fileNameStringBuilder.toString().replaceFirst(",", "");

        //region 定位文件
        String locateStr = "我会提供给你java类的文件名，可能是一个或者多个，如果是多个会用逗号拼接起来\n" +
                "我需要你在" + aiAnalyzeConfig.getLocalRepository() + "目录下，帮我查找所有文件的具体位置\n" +
                "文件名信息如下：\n" +
                classNameStr + "\n" +
                "我仅需要你输出具体的位置，不需要回答其他内容，输出示例如下：\n" +
                "A.java:/Users/Documents/git_repo/ai-code-analyze/A.java" + "\n" +
                "B.java:/Users/Documents/git_repo/ai-code-analyze/B.java";
        String locateResult = doChat(locateStr);
        if (StringUtils.isEmpty(locateResult)) {
            return "locateFail";
        }
        //endregion

        //region 执行具体分析，以及修复
        String analyzeStr = "我的java程序在运行过程中遇到了异常，下面我将提供给你对应的异常堆栈信息、异常代码文件位置、本地代码目录这3个信息\n" +
                "异常堆栈信息如下：\n" +
                req.getMessage() + "\n" +
                "异常代码文件位置：" + locateResult + "\n" +
                "本地代码目录：" + aiAnalyzeConfig.getLocalRepository() + "\n" +
                "我需要你通过我提供的异常代码文件位置，查看具体的代码内容，并修复这个异常，注意需要保存修复后的代码，请直接保存必须要询问我";
        String analyzeResult = doChat(analyzeStr);
        log.info(">>> 修复结果: {}", analyzeResult);
        //endregion

        //region 创建hotfix分支并提交推送
        String branchName = generateHotfixBranchName();
        String branchStr = "请在本地仓库目录创建并推送hotfix分支以提交刚才的修复代码，具体要求如下：\n" +
                "本地仓库目录：" + aiAnalyzeConfig.getLocalRepository() + "\n" +
                "远程仓库地址：" + aiAnalyzeConfig.getRemoteRepository() + "\n" +
                "分支命名规则：hotfix_yyyyMMdd_+六位随机数\n" +
                "目标分支名称：" + branchName + "\n" +
                "操作步骤：1) 若存在未提交变更，请先 git add . 并提交，提交信息中包含 'AI Fix'；2) 基于当前最新修复创建并切换到分支" + branchName + "；3) 将提交推送到远程。\n" +
                "处理成功请仅返回 SUCCESS:" + branchName + "，处理失败请仅返回 FAIL，不需要回答其他内容。";
        String branchResult = doChat(branchStr);
        log.info(">>> hotfix分支结果: {}", branchResult);
        return branchResult;
        //endregion
    }

    private String doChat(String question) {
        log.info(">>> 问题: {}", question);

        // 使用API调用聊天
        String content = chatClient.prompt()
                .user(question)
                .call()
                .content();

        log.info(">>> 回答：{}", content);
        return content;
    }

    private String generateHotfixBranchName() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int random = ThreadLocalRandom.current().nextInt(0, 1_000_000);
        String randomSix = String.format("%06d", random);
        return "hotfix_" + date + "_" + randomSix;
    }
}
