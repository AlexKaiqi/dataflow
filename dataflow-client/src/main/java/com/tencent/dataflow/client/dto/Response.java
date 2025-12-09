package com.tencent.dataflow.client.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Base Response DTO
 */
@Data
public class Response implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success = true;
    private String errCode;
    private String errMessage;

    public static Response buildSuccess() {
        Response response = new Response();
        response.setSuccess(true);
        return response;
    }

    public static Response buildFailure(String errCode, String errMessage) {
        Response response = new Response();
        response.setSuccess(false);
        response.setErrCode(errCode);
        response.setErrMessage(errMessage);
        return response;
    }
}
