package com.simon.springboot.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 模拟接口入参，通过参数来触发异常
 *
 * @author Simon Cai
 * @version 1.0
 * @since 2025-07-13
 */
@Data
public class GetReq implements Serializable {

    private Integer userId;

    private String userName;
}
