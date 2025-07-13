package com.simon.mcpclient.req;

import lombok.Data;

import java.io.Serializable;

/**
 * AI对话接口入参
 *
 * @author Simon Cai
 * @version 1.0
 * @since 2025-07-13
 */
@Data
public class ChatReq implements Serializable {

    private String message;
}
