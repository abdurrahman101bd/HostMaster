package com.ar.hostmaster;

import android.app.Application;

public class HostMasterApplication extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
        LogManager.init(this);
    }
}