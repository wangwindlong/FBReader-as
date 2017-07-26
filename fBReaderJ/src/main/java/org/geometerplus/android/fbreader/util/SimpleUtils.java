package org.geometerplus.android.fbreader.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.view.WindowManager;
import android.widget.Toast;

import org.geometerplus.android.fbreader.FBReaderApplication;
import org.geometerplus.zlibrary.ui.android.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import group.pals.android.lib.ui.filechooser.utils.ui.Dlg;

/**
 * Created by wangyl on 17-7-22.
 */

public class SimpleUtils {

    public static final String SAMPLE_DIR_NAME = "Books/baiduTTS";
    public static final String SPEECH_FEMALE_MODEL_NAME = "bd_etts_speech_female.dat";
    public static final String SPEECH_MALE_MODEL_NAME = "bd_etts_speech_male.dat";
    public static final String TEXT_MODEL_NAME = "bd_etts_text.dat";
    public static final String LICENSE_FILE_NAME = "temp_license";
    public static final String ENGLISH_SPEECH_FEMALE_MODEL_NAME = "bd_etts_speech_female_en.dat";
    public static final String ENGLISH_SPEECH_MALE_MODEL_NAME = "bd_etts_speech_male_en.dat";
    public static final String ENGLISH_TEXT_MODEL_NAME = "bd_etts_text_en.dat";
    public static String mSampleDirPath;

    public static void initialTTSEnv() {
        if (mSampleDirPath == null) {
            String sdcardPath = Environment.getExternalStorageDirectory().toString();
            mSampleDirPath = sdcardPath + "/" + SAMPLE_DIR_NAME;
        }
        makeDir(mSampleDirPath);
        copyFromAssetsToSdcard(false, SPEECH_FEMALE_MODEL_NAME, mSampleDirPath + "/" + SPEECH_FEMALE_MODEL_NAME);
        copyFromAssetsToSdcard(false, SPEECH_MALE_MODEL_NAME, mSampleDirPath + "/" + SPEECH_MALE_MODEL_NAME);
        copyFromAssetsToSdcard(false, TEXT_MODEL_NAME, mSampleDirPath + "/" + TEXT_MODEL_NAME);
        copyFromAssetsToSdcard(false, LICENSE_FILE_NAME, mSampleDirPath + "/" + LICENSE_FILE_NAME);
        copyFromAssetsToSdcard(false, "english/" + ENGLISH_SPEECH_FEMALE_MODEL_NAME, mSampleDirPath + "/"
                + ENGLISH_SPEECH_FEMALE_MODEL_NAME);
        copyFromAssetsToSdcard(false, "english/" + ENGLISH_SPEECH_MALE_MODEL_NAME, mSampleDirPath + "/"
                + ENGLISH_SPEECH_MALE_MODEL_NAME);
        copyFromAssetsToSdcard(false, "english/" + ENGLISH_TEXT_MODEL_NAME, mSampleDirPath + "/"
                + ENGLISH_TEXT_MODEL_NAME);
    }

    public static void makeDir(String dirPath) {
        File file = new File(dirPath);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    /**
     * 将sample工程需要的资源文件拷贝到SD卡中使用（授权文件为临时授权文件，请注册正式授权）
     *
     * @param isCover 是否覆盖已存在的目标文件
     * @param source
     * @param dest
     */
    public static void copyFromAssetsToSdcard(boolean isCover, String source, String dest) {
        File file = new File(dest);
        if (isCover || !file.exists()) {
            InputStream is = null;
            FileOutputStream fos = null;
            try {
                is = FBReaderApplication.getContext().getResources().getAssets().open(source);
                String path = dest;
                fos = new FileOutputStream(path);
                byte[] buffer = new byte[1024];
                int size = 0;
                while ((size = is.read(buffer, 0, 1024)) >= 0) {
                    fos.write(buffer, 0, size);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    if (is != null) {
                        is.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public static void checkTTSPlugin(Context context) {
//        boolean isExist = checkApkExist("com.hyperionics.fbreader.plugin.tts_plus");
//        if (!isExist) {
////            AlertDialog dialog = new AlertDialog.Builder(context).setTitle("温馨提示")
////                    .setMessage("需要安装tts插件后才能读书哦")
////                    .setCancelable(false)
////                    .setPositiveButton("安装", new DialogInterface.OnClickListener() {
////                        public void onClick(DialogInterface dialog, int id) {
////                            installApk(Uri.parse("file:///android_asset/tts+.apk"));
////                        }
////                    })
////                    .create();
////            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
////            dialog.show();
//            Dlg.confirmYesno(context, "需要安装tts插件后才能读书哦", new DialogInterface.OnClickListener() {
//                @Override
//                public void onClick(DialogInterface dialog, int which) {
//                    installApk("tts+.apk");
//                }
//            });
//        } else {
////            Toast.makeText(FBReaderApplication.getContext(), "已安装应用", Toast.LENGTH_SHORT).show();
//        }
    }

    public static void installApk(String fileName) {
        AssetManager assetManager = FBReaderApplication.getContext().getAssets();

        InputStream in = null;
        OutputStream out = null;

        try {
            in = assetManager.open(fileName);
            File destFile = new File(Environment.getExternalStorageDirectory(), fileName);
            out = new FileOutputStream(destFile);

            byte[] buffer = new byte[1024];

            int read;
            while((read = in.read(buffer)) != -1) {

                out.write(buffer, 0, read);

            }

            in.close();
            in = null;

            out.flush();
            out.close();
            out = null;

            Intent intent = new Intent(Intent.ACTION_VIEW);

            intent.setDataAndType(Uri.fromFile(destFile),
                    "application/vnd.android.package-archive");

            FBReaderApplication.getContext().startActivity(intent);

        } catch(Exception e) { }

//        Intent intent = new Intent(Intent.ACTION_VIEW);
//        intent.setDataAndType(fileUri, "application/vnd.android.package-archive");
//        FBReaderApplication.getContext().startActivity(intent);
    }

//    private boolean checkApkExist(String packageName) {
//        boolean hasInstalled = false;
//        PackageManager pm = FBReaderApplication.getContext().getPackageManager();
//        List<PackageInfo> list = pm
//                .getInstalledPackages(PackageManager.PERMISSION_GRANTED);
//        for (PackageInfo p : list) {
//            if (packageName != null && packageName.equals(p.packageName)) {
//                hasInstalled = true;
//                break;
//            }
//        }
//        return hasInstalled;
//    }

    public static boolean checkApkExist(String packageName) {
        if (TextUtils.isEmpty(packageName))
            return false;
        try {
            ApplicationInfo info = FBReaderApplication.getContext().getPackageManager().getApplicationInfo(packageName,
                    PackageManager.GET_META_DATA);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
