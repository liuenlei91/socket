package com.example.msi.websocketdemo.websocket.common;

import com.example.msi.websocketdemo.websocket.request.Action;
import com.example.msi.websocketdemo.websocket.request.Request;



public interface IWsCallback<T> {
    void onSuccess(T t);
    void onError(String msg, Request request, Action action);
    void onTimeout(Request request, Action action);
}
