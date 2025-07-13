package com.simon.springboot.exception;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 异步调用AI分析的处理器
 *
 * @author Simon Cai
 * @version 1.0
 * @since 2025-07-13
 */
@Slf4j
@Service
public class AiAnalyzeHandler {

    @Async
    public void handle(Exception e) {

        Map<String, Object> body= new HashMap<>();
        body.put("message", ExceptionUtils.getStackTrace(e));

        HttpResponse httpResponse = HttpUtil.createPost("http://localhost:8891/api/error_analyze").header("Content-Type", "application/json").body(JSONUtil.toJsonStr(body))
                .execute();

        log.info("请求AI结束，响应内容：{}", httpResponse);
    }

}
