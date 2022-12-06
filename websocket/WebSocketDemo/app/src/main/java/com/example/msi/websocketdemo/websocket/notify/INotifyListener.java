package com.example.msi.websocketdemo.websocket.notify;



public interface INotifyListener<T> {
    void fire(T t);
}
