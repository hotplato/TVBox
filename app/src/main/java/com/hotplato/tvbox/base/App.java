package com.hotplato.tvbox.base;

import android.os.Environment;

import androidx.multidex.MultiDexApplication;

import com.github.catvod.Init;
import com.hotplato.tvbox.callback.EmptyCallback;
import com.hotplato.tvbox.callback.LoadingCallback;
import com.hotplato.tvbox.crawler.JsLoader;
import com.hotplato.tvbox.data.AppDataManager;
import com.hotplato.tvbox.server.ControlManager;
import com.hotplato.tvbox.util.HawkConfig;
import com.hotplato.tvbox.util.OkGoHelper;
import com.hotplato.tvbox.util.PlayerHelper;
import com.kingja.loadsir.core.LoadSir;
import com.orhanobut.hawk.Hawk;
import com.whl.quickjs.android.QuickJSLoader;

import java.io.File;

import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.unit.Subunits;

/**
 * @author pj567
 * @date :2020/12/17
 * @description:
 */
public class App extends MultiDexApplication {
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Init.set(this);
        initParams();
        // OKGo
        OkGoHelper.init();
        QuickJSLoader.init();
        // 初始化Web服务器
        ControlManager.init(this);
        //初始化数据库
        AppDataManager.init();
        LoadSir.beginBuilder()
                .addCallback(new EmptyCallback())
                .addCallback(new LoadingCallback())
                .commit();
        AutoSizeConfig.getInstance().setCustomFragment(true).getUnitsManager()
                .setSupportDP(false)
                .setSupportSP(false)
                .setSupportSubunits(Subunits.MM);
        PlayerHelper.init();
        ensureSpiderWorkDir();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        JsLoader.destroy();
    }

    /** 部分爬虫 JAR 会写 /sdcard/TVBox/ 下的配置文件 */
    private void ensureSpiderWorkDir() {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), "TVBox");
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
        } catch (Throwable ignored) {
        }
    }

    private void initParams() {
        // Hawk
        Hawk.init(this).build();
        Hawk.put(HawkConfig.DEBUG_OPEN, false);
        if (!Hawk.contains(HawkConfig.PLAY_TYPE)) {
            Hawk.put(HawkConfig.PLAY_TYPE, 2);
        }
    }

    public static App getInstance() {
        return instance;
    }
}