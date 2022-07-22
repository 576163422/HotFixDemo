package com.faytian.hotfixdemo;

import android.app.Application;
import android.content.Context;


import com.example.patchlib.HotFixUtils;

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
            String patchDir = dirPath + "/patch.dex";
            HotFixUtils.installPatch(appApplication, new File(patchDir));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
