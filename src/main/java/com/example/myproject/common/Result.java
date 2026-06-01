package com.example.myproject.common;

import java.io.Serializable;

/**
 * 统一响应封装，前端使用 code(0成功/-1失败), msg, data
 */
public class Result<T> implements Serializable {

    private int code;
    private String msg;
    private T data;

    public Result() {}

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /** 成功，带数据 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "success", data);
    }

    /** 成功，无数据 */
    public static <T> Result<T> ok() {
        return new Result<>(0, "success", null);
    }

    /** 业务失败 */
    public static <T> Result<T> fail(String msg) {
        return new Result<>(-1, msg, null);
    }

    /** 未登录 */
    public static <T> Result<T> unauthorized() {
        return new Result<>(-1, "请先登录", null);
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
