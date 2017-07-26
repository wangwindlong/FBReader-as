/*
 * Copyright (C) 2007-2015 FBReader.ORG Limited <contact@fbreader.org>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.android.fbreader;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Build;

import org.geometerplus.android.fbreader.plugin.tts.BluetoothConnectReceiver;
import org.geometerplus.android.fbreader.plugin.tts.GlobalExceptionHandler;
import org.geometerplus.android.fbreader.plugin.tts.HeadsetPlugReceiver;
import org.geometerplus.android.fbreader.plugin.tts.InstallInfo;
import org.geometerplus.android.fbreader.plugin.tts.MediaButtonIntentReceiver;
import org.geometerplus.android.fbreader.plugin.tts.util.FileUtil;
import org.geometerplus.zlibrary.ui.android.library.ZLAndroidApplication;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FBReaderApplication extends ZLAndroidApplication {

    private static FBReaderApplication myApplication;
    private static boolean componentsEnabled = false;
    private static boolean myIsDebug = true;
    private static HeadsetPlugReceiver headsetPlugReceiver = null;
    private static boolean nativeOk;

    static String versionName = "";
    static int versionCode = 0;
    static PackageManager myPackageManager;
    static String myPackageName;

    @TargetApi(Build.VERSION_CODES.FROYO)
    public static void enableComponents(boolean enabled) {
        componentsEnabled = enabled;
        int flag = (enabled ?
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
//        ComponentName component = new ComponentName(myPackageName,
//                MediaButtonIntentReceiver.class.getName());
//        if (enabled || !SpeakService.getPrefs().getBoolean("fbrStart", false))
//            myPackageManager.setComponentEnabledSetting(component, flag,
//                PackageManager.DONT_KILL_APP);
//        component = new ComponentName(myPackageName,
//                BluetoothConnectReceiver.class.getName());
//        myPackageManager.setComponentEnabledSetting(component, flag,
//                PackageManager.DONT_KILL_APP);

        if (enabled) {
            if (headsetPlugReceiver == null) {
                headsetPlugReceiver = new HeadsetPlugReceiver();
                myApplication.registerReceiver(headsetPlugReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
                myApplication.registerReceiver(headsetPlugReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
            }
        }
        else if (headsetPlugReceiver != null) {
            myApplication.unregisterReceiver(headsetPlugReceiver);
            headsetPlugReceiver = null;
        }

//        if (SpeakService.mAudioManager != null && SpeakService.componentName != null) {
//            if (enabled) {
//                SpeakService.mAudioManager.registerMediaButtonEventReceiver(SpeakService.componentName);
//            }
//            else {
//                if (!SpeakService.getPrefs().getBoolean("fbrStart", false))
//                    SpeakService.mAudioManager.unregisterMediaButtonEventReceiver(SpeakService.componentName);
//                SpeakService.mAudioManager.abandonAudioFocus(SpeakService.afChangeListener);
//            }
//        }
    }

    static boolean areComponentsEnabled() {
        return componentsEnabled;
    }

    static void ExitApp() {
    	enableComponents(false);
//    	SpeakService.doStop();
    	System.exit(0);
    }

    public static boolean isDebug() {
        return myIsDebug;
    }

    @SuppressLint("NewApi")
    public static long getLastUpdateTime() {
        if (Build.VERSION.SDK_INT < 10)
            return 0;
        try {
            PackageInfo pi = myPackageManager.getPackageInfo(myPackageName, 0);
            return pi.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {

        } catch (NoSuchFieldError e2) {

        }
        return 0;
    }

    static String copyAsset(String fileName, String targetDir) {
        if (myApplication == null)
            return null;
        AssetManager assetManager = myApplication.getAssets();
        if (targetDir == null)
            targetDir = myApplication.getFilesDir().toString();
        String targetName = targetDir + "/" + fileName;
        new File(targetName).getParentFile().mkdirs(); // just in case, create the directory.

        // Check if this asset was already copied or if we have a newer asset
        long lut = getLastUpdateTime();
        File targetFile = new File(targetName);
        if (lut > 0 && targetFile.exists()) {
            long fmt = targetFile.lastModified();
            if (fmt >= lut) { // asset already copied, and there are no newer asset to replace it.
                return targetName;
            }
        }

        InputStream in;
        OutputStream out;
        try {
            in = assetManager.open(fileName);
            out = new FileOutputStream(targetName);
            FileUtil.copyFile(in, out);
            in.close();
            out.flush();
            out.close();
        } catch(IOException e) {
            return null;
        }
        return targetName;
    }

    public FBReaderApplication() {
        (new GlobalExceptionHandler()).init(this);
    }

    @Override public void onCreate() {
        InstallInfo.init(this);
        myApplication = this;
        myIsDebug = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        myPackageManager = getPackageManager();
        myPackageName = getPackageName();
//        nativeOk = AndyUtil.setApp(this);
//        Lt.d("TtsApp created, nativeOK = " + nativeOk);
        //startService(new Intent(this, SpeakService.class));
        try {
            versionName = myPackageManager.getPackageInfo(myPackageName, 0).versionName;
            versionCode = myPackageManager.getPackageInfo(myPackageName, 0).versionCode;
//            Lt.d("- version = " + versionName + " (" + versionCode + ")");
        } catch (PackageManager.NameNotFoundException e) {
        }

        super.onCreate();
    }

    public static Context getContext() { return myApplication; }

    public static boolean isNativeOk() { return nativeOk; }
}
