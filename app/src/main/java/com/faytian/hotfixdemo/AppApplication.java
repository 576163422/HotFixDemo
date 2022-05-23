package com.faytian.hotfixdemo;

import android.app.Application;
import android.content.Context;

public class AppApplication extends Application {

    public static AppApplication appApplication;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        appApplication = this;
        try {
            HotFixUtils.demo();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
