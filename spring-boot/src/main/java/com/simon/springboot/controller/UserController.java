package com.simon.springboot.controller;

import com.simon.springboot.req.GetReq;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模拟异常相关的控制器
 *
 * @author Simon Cai
 * @version 1.0
 * @since 2025-07-13
 */
@RestController
public class UserController {

    @RequestMapping(value = "/api/get")
    public String get(@RequestBody GetReq req) {
        if (req.getUserId() == 0) {
            return "ERROR: User ID cannot be zero.";
        }
        System.out.println(1000 / req.getUserId());
        System.out.println(req.getUserName().equals("123"));

        return "SUCCESS";
    }
}