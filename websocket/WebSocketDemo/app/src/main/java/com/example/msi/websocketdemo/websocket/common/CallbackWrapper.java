package com.example.msi.websocketdemo.websocket.common;

import com.example.msi.websocketdemo.websocket.common.IWsCallback;
import com.example.msi.websocketdemo.websocket.request.Action;
import com.example.msi.websocketdemo.websocket.request.Request;

import java.util.concurrent.ScheduledFuture;



public class CallbackWrapper {

    private final IWsCallback tempCallback;
    private final ScheduledFuture timeoutTask;
    private final Action action;
    private final Request request;


    public CallbackWrapper(IWsCallback tempCallback, ScheduledFuture timeoutTask, Action action, Request request) {
        this.tempCallback = tempCallback;
        this.timeoutTask = timeoutTask;
        this.action = action;
        this.request = request;
    }


    public IWsCallback getTempCallback() {
        return tempCallback;
    }


    public ScheduledFuture getTimeoutTask() {
        return timeoutTask;
    }


    public Action getAction() {
        return action;
    }


    public Request getRequest() {
        return request;
    }
}
