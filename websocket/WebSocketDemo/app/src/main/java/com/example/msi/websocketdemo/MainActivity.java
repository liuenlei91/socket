package com.example.msi.websocketdemo;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.msi.websocketdemo.websocket.WsManager;
import com.example.msi.websocketdemo.websocket.common.ICallback;
import com.example.msi.websocketdemo.websocket.model.LoginResp;
import com.example.msi.websocketdemo.websocket.request.Action;

public class MainActivity extends AppCompatActivity {

    private TextView res;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WsManager.getInstance().init();
        EditText editText = (EditText)findViewById(R.id.tv_ed);
        res =(TextView) findViewById(R.id.res);
        Button btn = (Button) findViewById(R.id.btn);
        if (btn != null){
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String text = editText.getText().toString().trim();
                    if (!TextUtils.isEmpty(text)){
                        request(text);
                    }
                }
            });
        }
    }

    public void requestLogin(String msg){
        WsManager.getInstance().sendReq(Action.LOGIN, msg, new ICallback<LoginResp>() {
            @Override public void onSuccess(LoginResp o) {
                if (o != null){
                    res.setText(o.getName());
                }
            }


            @Override public void onFail(String msg) {

            }
        });
    }

    public void request(String msg){
        WsManager.getInstance().sendReq(Action.MSG, msg, new ICallback() {
            @Override public void onSuccess(Object o) {
                if (o != null){
                    res.setText(o.toString());
                }
            }


            @Override public void onFail(String msg) {

            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WsManager.getInstance().disconnect();
    }
}
