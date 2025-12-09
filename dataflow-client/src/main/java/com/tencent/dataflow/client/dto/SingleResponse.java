package com.tencent.dataflow.client.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Single Response with data
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SingleResponse<T> extends Response {
    private T data;

    public static <T> SingleResponse<T> of(T data) {
        SingleResponse<T> response = new SingleResponse<>();
        response.setSuccess(true);
        response.setData(data);
        return response;
    }

    public static <T> SingleResponse<T> buildFailureWith(String errCode, String errMessage) {
        SingleResponse<T> response = new SingleResponse<>();
        response.setSuccess(false);
        response.setErrCode(errCode);
        response.setErrMessage(errMessage);
        return response;
    }
}
