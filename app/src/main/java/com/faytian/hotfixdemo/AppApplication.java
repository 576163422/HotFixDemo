package com.faytian.hotfixdemo;

import android.app.Application;
import android.content.Context;

import java.io.File;

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
            String dirPath = getCacheDir().getAbsolutePath();
            String patchDir = dirPath + "/patch.jar";
            HotFixUtils.installPatch(new File(patchDir));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
