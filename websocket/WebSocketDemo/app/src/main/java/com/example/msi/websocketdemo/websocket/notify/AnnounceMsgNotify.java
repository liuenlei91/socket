package com.example.msi.websocketdemo.websocket.notify;

import com.google.gson.annotations.SerializedName;


public class AnnounceMsgNotify {
    @SerializedName("msg_version")
    private String msgVersion;

    public String getMsgVersion() {
        return msgVersion;
    }

    public void setMsgVersion(String msgVersion) {
        this.msgVersion = msgVersion;
    }

}
