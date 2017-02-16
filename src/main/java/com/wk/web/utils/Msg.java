package com.wk.web.utils;

/**
 * Created by 005689 on 2016/6/13.
 */
public class Msg {
    private boolean success;
    private String msg;
    private Object obj;

    public Msg() {
    }

    public Msg(Object obj) {
        this.obj = obj;
        this.success = obj != null;
        this.msg = obj == null ? "空指针" : null;
    }

    public Msg(boolean success, String message) {
        this.success = success;
        this.msg = message;
    }

    public Object getObj() {
        return obj;
    }

    public void setObj(Object obj) {
        this.obj = obj;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
