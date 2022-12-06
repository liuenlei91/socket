package com.example.msi.websocketdemo.websocket.common;



public interface ICallback<T> {

    void onSuccess(T t);

    void onFail(String msg);

}
