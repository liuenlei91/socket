package com.example.msi.websocketdemo.websocket;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;

import com.example.msi.websocketdemo.BuildConfig;
import com.example.msi.websocketdemo.WsApplication;
import com.example.msi.websocketdemo.websocket.common.CallbackDataWrapper;
import com.example.msi.websocketdemo.websocket.common.CallbackWrapper;
import com.example.msi.websocketdemo.websocket.common.Codec;
import com.example.msi.websocketdemo.websocket.common.ErrorCode;
import com.example.msi.websocketdemo.websocket.common.ICallback;
import com.example.msi.websocketdemo.websocket.common.IWsCallback;
import com.example.msi.websocketdemo.websocket.common.WsStatus;
import com.example.msi.websocketdemo.websocket.notify.NotifyListenerManager;
import com.example.msi.websocketdemo.websocket.request.Action;
import com.example.msi.websocketdemo.websocket.request.Request;
import com.example.msi.websocketdemo.websocket.response.ChildResponse;
import com.example.msi.websocketdemo.websocket.response.Response;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.orhanobut.logger.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


public class WsManager {
    private final String TAG = this.getClass().getSimpleName();

    /**
     * WebSocket config
     */
    private static final long HEARTBEAT_INTERVAL = 30000;//????????????
    private static final int FRAME_QUEUE_SIZE = 5;
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int REQUEST_TIMEOUT = 30000;//??????????????????
    private static final String DEF_TEST_URL = "";//"ws://121.40.165.18:8800";//?????????????????????
    private static final String DEF_RELEASE_URL = "";//?????????????????????
    private static final String DEF_URL = BuildConfig.DEBUG ? DEF_TEST_URL : DEF_RELEASE_URL;
    private String url;

    private WsStatus mStatus;
    private WebSocket ws;
    private WsListener mListener;
    private AtomicLong seqId = new AtomicLong(SystemClock.uptimeMillis());//???????????????????????????
    private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private Map<Long, CallbackWrapper> callbacks = new HashMap<>();

    private final int SUCCESS_HANDLE = 0x01;
    private final int ERROR_HANDLE = 0x02;

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SUCCESS_HANDLE:
                    CallbackDataWrapper successWrapper = (CallbackDataWrapper) msg.obj;
                    successWrapper.getCallback().onSuccess(successWrapper.getData());
                    break;
                case ERROR_HANDLE:
                    CallbackDataWrapper errorWrapper = (CallbackDataWrapper) msg.obj;
                    errorWrapper.getCallback().onFail((String) errorWrapper.getData());
                    break;
            }
        }
    };


    private WsManager() {
    }

    private static class WsManagerHolder {
        private static WsManager mInstance = new WsManager();
    }

    public static WsManager getInstance() {
        return WsManagerHolder.mInstance;
    }


    public void init() {
        try {
            /**
             * configUrl???????????????????????????????????????
             * ?????????????????????????????????app?????????????????????http???????????????????????????,
             * ??????app??????????????????????????????????????????????????????,??????6?????????????????????????????????????????????????????????????????????
             */
            String configUrl = "ws://192.168.0.10:8081/ws/1";
            url = TextUtils.isEmpty(configUrl) ? DEF_URL : configUrl;
            ws = new WebSocketFactory().createSocket(url, CONNECT_TIMEOUT)
                    .setFrameQueueSize(FRAME_QUEUE_SIZE)//???????????????????????????5
                    .setMissingCloseFrameAllowed(false)//?????????????????????????????????????????????????????????
                    .addListener(mListener = new WsListener())//??????????????????
                    .connectAsynchronously();//????????????
            setStatus(WsStatus.CONNECTING);
            Logger.t(TAG).d("???????????????");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void doAuth() {
        sendReq(Action.LOGIN, null, new ICallback() {
            @Override
            public void onSuccess(Object o) {
                Logger.t(TAG).d("????????????");
                setStatus(WsStatus.AUTH_SUCCESS);
                startHeartbeat();
                delaySyncData();
            }


            @Override
            public void onFail(String msg) {

            }
        });
    }

    //????????????
    private void delaySyncData() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendReq(Action.SYNC, null, new ICallback() {
                    @Override
                    public void onSuccess(Object o) {

                    }


                    @Override
                    public void onFail(String msg) {

                    }
                });
            }
        }, 300);
    }


    private void startHeartbeat() {
        mHandler.postDelayed(heartbeatTask, HEARTBEAT_INTERVAL);
    }


    private void cancelHeartbeat() {
        heartbeatFailCount = 0;
        mHandler.removeCallbacks(heartbeatTask);
    }


    private int heartbeatFailCount = 0;
    private Runnable heartbeatTask = new Runnable() {
        @Override
        public void run() {
            sendReq(Action.HEARTBEAT, null, new ICallback() {
                @Override
                public void onSuccess(Object o) {
                    heartbeatFailCount = 0;
                }


                @Override
                public void onFail(String msg) {
                    heartbeatFailCount++;
                    if (heartbeatFailCount >= 3) {
                        reconnect();
                    }
                }
            });

            mHandler.postDelayed(this, HEARTBEAT_INTERVAL);
        }
    };


    public void sendReq(Action action, Object req, ICallback callback) {
        sendReq(action, req, callback, REQUEST_TIMEOUT);
    }


    public void sendReq(Action action, Object req, ICallback callback, long timeout) {
        sendReq(action, req, callback, timeout, 1);
    }


    /**
     * @param action   Action
     * @param req      ????????????
     * @param callback ??????
     * @param timeout  ????????????
     * @param reqCount ????????????
     */
    @SuppressWarnings("unchecked")
    private <T> void sendReq(Action action, T req, final ICallback callback, final long timeout, int reqCount) {
        if (!isNetConnect()) {
            callback.onFail("???????????????");
            return;
        }

        if (WsStatus.AUTH_SUCCESS.equals(getStatus()) || true) {
            Request request = new Request.Builder<T>()
                    .action(action.getAction())
                    .reqEvent(action.getReqEvent())
                    .seqId(seqId.getAndIncrement())
                    .reqCount(reqCount)
                    .req(req)
                    .build();

            ScheduledFuture timeoutTask = enqueueTimeout(request.getSeqId(), timeout);//??????????????????

            IWsCallback tempCallback = new IWsCallback() {

                @Override
                public void onSuccess(Object o) {
                    mHandler.obtainMessage(SUCCESS_HANDLE, new CallbackDataWrapper(callback, o))
                            .sendToTarget();
                }


                @Override
                public void onError(String msg, Request request, Action action) {
                    mHandler.obtainMessage(ERROR_HANDLE, new CallbackDataWrapper(callback, msg))
                            .sendToTarget();
                }


                @Override
                public void onTimeout(Request request, Action action) {
                    timeoutHandle(request, action, callback, timeout);
                }
            };

            callbacks.put(request.getSeqId(),
                    new CallbackWrapper(tempCallback, timeoutTask, action, request));

            Logger.t(TAG).d("send text : %s", new Gson().toJson(request));
            ws.sendText(new Gson().toJson(request));
        } else {
            callback.onFail("??????????????????");
        }
    }


    /**
     * ??????????????????
     */
    private ScheduledFuture enqueueTimeout(final long seqId, long timeout) {
        return executor.schedule(new Runnable() {
            @Override
            public void run() {
                CallbackWrapper wrapper = callbacks.remove(seqId);
                if (wrapper != null) {
                    Logger.t(TAG).d("(action:%s)???%d???????????????", wrapper.getAction().getAction(), wrapper.getRequest().getReqCount());
                    wrapper.getTempCallback().onTimeout(wrapper.getRequest(), wrapper.getAction());
                }
            }
        }, timeout, TimeUnit.MILLISECONDS);
    }


    /**
     * ????????????
     */
    private void timeoutHandle(Request request, Action action, ICallback callback, long timeout) {
        if (request.getReqCount() > 3) {
            Logger.t(TAG).d("(action:%s)??????3??????????????? ??????http??????", action.getAction());
            //???http??????
        } else {
            sendReq(action, request.getReq(), callback, timeout, request.getReqCount() + 1);
            Logger.t(TAG).d("(action:%s)?????????%d?????????", action.getAction(), request.getReqCount());
        }
    }


    /**
     * ??????????????????????????????WebSocketAdapter,???????????????????????????
     * onTextMessage ??????????????????
     * onConnected ????????????
     * onConnectError ????????????
     * onDisconnected ????????????
     */
    class WsListener extends WebSocketAdapter {
        @Override
        public void onTextMessage(WebSocket websocket, String text) throws Exception {
            super.onTextMessage(websocket, text);
            Logger.t(TAG).d("receiverMsg:%s", text);

            Response response = Codec.decoder(text);//??????????????????bean
            if (response.getRespEvent() == 10) {//??????
                CallbackWrapper wrapper = callbacks.remove(
                        Long.parseLong(response.getSeqId()));//????????????callback
                if (wrapper == null) {
                    Logger.t(TAG).d("(action:%s) not found callback", response.getAction());
                    return;
                }

                try {
                    wrapper.getTimeoutTask().cancel(true);//??????????????????
                    ChildResponse childResponse = Codec.decoderChildResp(
                            response.getResp());//???????????????bean
                    if (childResponse.isOK()) {
                        Object o;
                        if (wrapper.getAction().getRespClazz() != null){
                            o = new Gson().fromJson(childResponse.getData(),
                                    wrapper.getAction().getRespClazz());

                        }else{
                            o = childResponse.getData();
                        }

                        wrapper.getTempCallback().onSuccess(o);
                    } else {
                        wrapper.getTempCallback()
                                .onError(ErrorCode.BUSINESS_EXCEPTION.getMsg(), wrapper.getRequest(),
                                        wrapper.getAction());
                    }
                } catch (JsonSyntaxException e) {
                    e.printStackTrace();
                    wrapper.getTempCallback()
                            .onError(ErrorCode.PARSE_EXCEPTION.getMsg(), wrapper.getRequest(),
                                    wrapper.getAction());
                }

            } else if (response.getRespEvent() == 20) {//??????
                NotifyListenerManager.getInstance().fire(response);
            }
        }


        @Override
        public void onConnected(WebSocket websocket, Map<String, List<String>> headers)
                throws Exception {
            super.onConnected(websocket, headers);
            Logger.t(TAG).d("????????????");
            setStatus(WsStatus.CONNECT_SUCCESS);
            cancelReconnect();//?????????????????????????????????,?????????????????????
//            doAuth();
        }


        @Override
        public void onConnectError(WebSocket websocket, WebSocketException exception)
                throws Exception {
            super.onConnectError(websocket, exception);
            Logger.t(TAG).d("????????????");
            setStatus(WsStatus.CONNECT_FAIL);
            reconnect();//???????????????????????????????????????
        }


        @Override
        public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer)
                throws Exception {
            super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer);
            Logger.t(TAG).d("????????????");
            setStatus(WsStatus.CONNECT_FAIL);
            reconnect();//???????????????????????????????????????
        }

        @Override
        public void handleCallbackError(WebSocket websocket, Throwable cause) throws Exception {
            super.handleCallbackError(websocket, cause);
        }
    }


    private void setStatus(WsStatus status) {
        this.mStatus = status;
    }


    private WsStatus getStatus() {
        return mStatus;
    }


    public void disconnect() {
        if (ws != null) {
            ws.disconnect();
        }
    }


    private int reconnectCount = 0;//????????????
    private long minInterval = 3000;//????????????????????????
    private long maxInterval = 60000;//????????????????????????


    public void reconnect() {
        if (!isNetConnect()) {
            reconnectCount = 0;
            Logger.t(TAG).d("???????????????????????????");
            return;
        }

        //????????????????????????????????????????????????????????? ??????????????????????????????????????????????????????????????????????????????
        //????????????????????????demo???????????????
        if (ws != null &&
                !ws.isOpen() &&//?????????????????????
                getStatus() != WsStatus.CONNECTING) {//????????????????????????

            reconnectCount++;
            setStatus(WsStatus.CONNECTING);
            cancelHeartbeat();

            long reconnectTime = minInterval;
            if (reconnectCount > 3) {
                url = DEF_URL;
                long temp = minInterval * (reconnectCount - 2);
                reconnectTime = temp > maxInterval ? maxInterval : temp;
            }

            Logger.t(TAG).d("???????????????%d?????????,????????????%d -- url:%s", reconnectCount, reconnectTime, url);
            mHandler.postDelayed(mReconnectTask, reconnectTime);
        }
    }


    private Runnable mReconnectTask = new Runnable() {

        @Override
        public void run() {
            try {
                ws = new WebSocketFactory().createSocket(url, CONNECT_TIMEOUT)
                        .setFrameQueueSize(FRAME_QUEUE_SIZE)//???????????????????????????5
                        .setMissingCloseFrameAllowed(false)//?????????????????????????????????????????????????????????
                        .addListener(mListener = new WsListener())//??????????????????
                        .connectAsynchronously();//????????????
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };


    private void cancelReconnect() {
        reconnectCount = 0;
        mHandler.removeCallbacks(mReconnectTask);
    }


    private boolean isNetConnect() {
        ConnectivityManager connectivity = (ConnectivityManager) WsApplication.getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo info = connectivity.getActiveNetworkInfo();
            if (info != null && info.isConnected()) {
                // ????????????????????????
                if (info.getState() == NetworkInfo.State.CONNECTED) {
                    // ??????????????????????????????
                    return true;
                }
            }
        }
        return false;
    }
}
