/*
 * Copyright (C) 2009-2011 Geometer Plus <contact@geometerplus.com>
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

package org.geometerplus.android.fbreader.plugin.tts;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.Time;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import com.baidu.tts.auth.AuthInfo;
import com.baidu.tts.client.SpeechError;
import com.baidu.tts.client.SpeechSynthesizer;
import com.baidu.tts.client.SpeechSynthesizerListener;
import com.baidu.tts.client.TtsMode;

import org.geometerplus.android.fbreader.FBReaderApplication;
import org.geometerplus.android.fbreader.api.ApiClientImplementation;
import org.geometerplus.android.fbreader.api.ApiException;
import org.geometerplus.android.fbreader.api.PluginApi;
import org.geometerplus.android.fbreader.api.TextPosition;
import org.geometerplus.android.fbreader.plugin.tts.util.TtsSentenceExtractor;
import org.geometerplus.zlibrary.ui.android.BuildConfig;
import org.geometerplus.zlibrary.ui.android.R;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.geometerplus.android.fbreader.util.SimpleUtils.ENGLISH_SPEECH_FEMALE_MODEL_NAME;
import static org.geometerplus.android.fbreader.util.SimpleUtils.ENGLISH_TEXT_MODEL_NAME;
import static org.geometerplus.android.fbreader.util.SimpleUtils.LICENSE_FILE_NAME;
import static org.geometerplus.android.fbreader.util.SimpleUtils.SPEECH_FEMALE_MODEL_NAME;
import static org.geometerplus.android.fbreader.util.SimpleUtils.TEXT_MODEL_NAME;
import static org.geometerplus.android.fbreader.util.SimpleUtils.mSampleDirPath;

public class SpeakActivity extends Activity implements SpeechSynthesizerListener,
        TextToSpeech.OnInitListener,
        TextToSpeech.OnUtteranceCompletedListener,
        ApiClientImplementation.ConnectionListener {

    private static final String TAG = "SpeakActivity";
    private static LockscreenManager _lockscreenManager = null;
    public static ApiClientImplementation myApi;
    private static final String UTTERANCE_ID = "FBReaderTTSPlugin";
    //    private TextToSpeech myTTS;
    private SpeechSynthesizer mSpeechSynthesizer;
    private SharedPreferences myPreferences;
    public static AudioManager mAudioManager;
    private ComponentName componentName;
    private int myParagraphIndex = -1;
    private int myParagraphsNumber;
    private boolean myIsActive = false;
    private boolean myWasActive = false;
    private String paragraphsNumber;
    private int readPosition;


    static final String BOOK_LANG = "book";
    static boolean myHighlightSentences = true;
    static int myParaPause = 300;
    static int mySntPause = 0;
    static String selectedLanguage = BOOK_LANG; // either "book" or locale code like "eng-USA"
    static float myCurrentPitch = 1f;
    static int haveNewApi = 1;
    private boolean isServiceTalking = false;
    private boolean myHasNetworkTts = false;
    private boolean isTTSPause = false;
    //static boolean readingStarted = false;
    private boolean wordPauses = false;

    private final int utIdLen = UTTERANCE_ID.length();
    private TtsSentenceExtractor.SentenceIndex mySentences[] = new TtsSentenceExtractor.SentenceIndex[0];
    private int myCurrentSentence = 0;
    private boolean sntConcurrent = true;
    private static int sntLastAdded = -1;
    private String myBookHash = null;

    private HashMap<String, String> myParamMap;

    private int SERVICE_INITIALIZED = 4;
    private final String SVC_STARTED = "com.hyperionics.fbreader.plugin.tts_plus.SVC_STARTED";
    private final String API_CONNECTED = "com.hyperionics.fbreader.plugin.tts_plus.API_CONNECTED";
    private final String TTSP_KILL = "com.hyperionics.fbreader.plugin.tts_plus.TTSP_KILL";
    private int audioStream = AudioManager.STREAM_MUSIC;
    private boolean allowBackgroundMusic = false;
    private String avarDefaultPath = null;

    private int myMaxVolume;
    private int savedBottomMargin = -1;
    private int hidePromo = 0;
    private final int promoMaxPress = 3;


    private static final int PRINT = 0;
    private static final int UI_CHANGE_INPUT_TEXT_SELECTION = 1;
    private static final int UI_CHANGE_SYNTHES_TEXT_SELECTION = 2;

    private void setListener(int id, View.OnClickListener listener) {
        findViewById(id).setOnClickListener(listener);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFinishOnTouchOutside(false);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        componentName = new ComponentName(getPackageName(), MediaButtonIntentReceiver.class.getName());

        myPreferences = getSharedPreferences("FBReaderTTS", MODE_PRIVATE);

        setContentView(R.layout.control_panel);

        findViewById(R.id.controll_ll).setVisibility(View.VISIBLE);
        setListener(R.id.button_previous_paragraph, new View.OnClickListener() {
            public void onClick(View v) {
                stopTalking();
                prevToSpeak();
//                gotoPreviousParagraph();
            }
        });
        setListener(R.id.button_next_paragraph, new View.OnClickListener() {
            public void onClick(View v) {
                nextToSpeak();
//                stopTalking();
//                if (myParagraphIndex < myParagraphsNumber) {
//                    ++myParagraphIndex;
//                    gotoNextParagraph();
//                }
            }
        });
        setListener(R.id.button_close, new View.OnClickListener() {
            public void onClick(View v) {
                stopTalking();
                doDestroy();
                finish();
            }
        });
        setListener(R.id.button_pause, new View.OnClickListener() {
            public void onClick(View v) {
                stopTalking();
            }
        });
        setListener(R.id.button_play, new View.OnClickListener() {
            public void onClick(View v) {
                startTalking();
//                speakString(gotoNextParagraph());
            }
        });
        final SeekBar speedControl = (SeekBar) findViewById(R.id.speed_control);
        speedControl.setMax(200);
        speedControl.setProgress(myPreferences.getInt("rate", 100));
        speedControl.setEnabled(false);
        speedControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private SharedPreferences.Editor myEditor = myPreferences.edit();

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mSpeechSynthesizer != null) {
                    if (!myWasActive)
                        myWasActive = myIsActive;
                    stopTalking();
                    setSpeechRate(progress);
                    myEditor.putInt("rate", progress);
                }
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                myEditor.commit();
                if (myWasActive) {
                    myWasActive = false;
                    startTalking();
                }
            }
        });

        ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).listen(
                new PhoneStateListener() {
                    public void onCallStateChanged(int state, String incomingNumber) {
                        if (state == TelephonyManager.CALL_STATE_RINGING) {
                            stopTalking();
                        }
                    }
                },
                PhoneStateListener.LISTEN_CALL_STATE
        );

        setActive(false);
        setActionsEnabled(false);

        String prefix = ApiClientImplementation.FBREADER_PREFIX;
        final Intent intent = getIntent();
        if (intent != null) {
            final String action = getIntent().getAction();
            if (action != null && action.endsWith(PluginApi.ACTION_RUN_POSTFIX)) {
                prefix = action.substring(0, action.length() - PluginApi.ACTION_RUN_POSTFIX.length());
            }
        }
        myApi = new ApiClientImplementation(this, this, prefix);
        doStartup();
        try {
            startActivityForResult(
                    new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA), 0
            );
        } catch (ActivityNotFoundException e) {
            showErrorMessage(getText(R.string.no_tts_installed), true);
        }

        setTitle(R.string.initializing);
        initialTts();
    }

    boolean doStartup() {
        if (myPreferences == null)
            myPreferences = getSharedPreferences("FBReaderTTS", MODE_PRIVATE);
        selectedLanguage = BOOK_LANG; // myPreferences.getString("lang", BOOK_LANG);
        myHighlightSentences = myPreferences.getBoolean("hiSentences", true);
        myParaPause = myPreferences.getInt("paraPause", myParaPause);
        mySntPause = myPreferences.getInt("sntPause", mySntPause);
        allowBackgroundMusic = myPreferences.getBoolean("allowBackgroundMusic", false);

        if (myParamMap == null) {
            myParamMap = new HashMap<String, String>();
            myParamMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID);
            // STREAM_MUSIC is the default stream for TTS
            myParamMap.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                    String.valueOf(audioStream));
        }
        if (myPreferences.getBoolean("ShowLockWidget", true)) {
            if (_lockscreenManager == null)
                _lockscreenManager = new LockscreenManager();
        }
        return true;
    }

    private void initialTts() {
        mSpeechSynthesizer = SpeechSynthesizer.getInstance();
        mSpeechSynthesizer.setContext(this);
        mSpeechSynthesizer.setSpeechSynthesizerListener(this);
        // 文本模型文件路径 (离线引擎使用)
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_TTS_TEXT_MODEL_FILE, mSampleDirPath + "/"
                + TEXT_MODEL_NAME);
        // 声学模型文件路径 (离线引擎使用)
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_TTS_SPEECH_MODEL_FILE, mSampleDirPath + "/"
                + SPEECH_FEMALE_MODEL_NAME);
        // 本地授权文件路径,如未设置将使用默认路径.设置临时授权文件路径，LICENCE_FILE_NAME请替换成临时授权文件的实际路径，仅在使用临时license文件时需要进行设置，如果在[应用管理]中开通了正式离线授权，不需要设置该参数，建议将该行代码删除（离线引擎）
        // 如果合成结果出现临时授权文件将要到期的提示，说明使用了临时授权文件，请删除临时授权即可。
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_TTS_LICENCE_FILE, mSampleDirPath + "/"
                + LICENSE_FILE_NAME);
        // 请替换为语音开发者平台上注册应用得到的App ID (离线授权)
        mSpeechSynthesizer.setAppId("9927588"/*这里只是为了让Demo运行使用的APPID,请替换成自己的id。*/);
        // 请替换为语音开发者平台注册应用得到的apikey和secretkey (在线授权)
        mSpeechSynthesizer.setApiKey("hEoVK0qOud48TzVHqP3uGH6O",
                "06268577c6213960da627383b014edd5"/*这里只是为了让Demo正常运行使用APIKey,请替换成自己的APIKey*/);
        // 发音人（在线引擎），可用参数为0,1,2,3。。。（服务器端会动态增加，各值含义参考文档，以文档说明为准。0--普通女声，1--普通男声，2--特别男声，3--情感男声。。。）
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_SPEAKER, "0");
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_SPEED, getRate(myPreferences.getInt("rate", 100)));
//        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_VOCODER_OPTIM_LEVEL, "1");//设置引擎合成速度 [0,2]
        // 设置Mix模式的合成策略
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_MIX_MODE, SpeechSynthesizer.MIX_MODE_DEFAULT);
        // 授权检测接口(只是通过AuthInfo进行检验授权是否成功。)
        // AuthInfo接口用于测试开发者是否成功申请了在线或者离线授权，如果测试授权成功了，可以删除AuthInfo部分的代码（该接口首次验证时比较耗时），不会影响正常使用（合成使用时SDK内部会自动验证授权）
        AuthInfo authInfo = mSpeechSynthesizer.auth(TtsMode.MIX);

        if (authInfo.isSuccess()) {
            toPrint("auth success");
            onInit(0);
        } else {
            String errorMsg = authInfo.getTtsError().getDetailMessage();
            toPrint("auth failed errorMsg=" + errorMsg);
        }

        // 初始化tts
        mSpeechSynthesizer.initTts(TtsMode.MIX);
        // 加载离线英文资源（提供离线英文合成功能）
        int result =
                mSpeechSynthesizer.loadEnglishModel(mSampleDirPath + "/" + ENGLISH_TEXT_MODEL_NAME, mSampleDirPath
                        + "/" + ENGLISH_SPEECH_FEMALE_MODEL_NAME);
        toPrint("loadEnglishModel result=" + result);
    }

    private String getRate(int progress) {
        int rate = progress / 10;
        if (rate >= 10) {
            rate = 9;
        } else if (rate < 0) {
            rate = 0;
        }
        return "" + rate;
    }

    private void setSpeechRate(int progress) {
        toPrint("setSpeechRate progress="+progress);
        if (mSpeechSynthesizer != null) {
            mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_SPEED, getRate(progress));//离线合成参数[0,9]
//            myTTS.setSpeechRate((float) Math.pow(2.0, (progress - 100.0) / 75));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
//            myTTS = new TextToSpeech(this, this);
        } else {
            try {
                startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
            } catch (ActivityNotFoundException e) {
                showErrorMessage(getText(R.string.no_tts_installed), true);
            }
        }
    }

    @Override
    protected void onResume() {
        myApi.connect();
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void switchOff() {
        stopTalking();
        if (_lockscreenManager != null)
            _lockscreenManager.setLockscreenStopped();
        if (mAudioManager != null)
            mAudioManager.abandonAudioFocus(afChangeListener);
        try {
            mHandler.removeCallbacks(myTimerTask);
            mySentences = new TtsSentenceExtractor.SentenceIndex[0];
        } catch (Exception dontCare) {
        }

        if (myApi != null && myApi.isConnected()) {
            try {
                myApi.clearHighlighting();
                myApi.disconnect();
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
        myApi = null;
        try {
            mSpeechSynthesizer.release();
//            if (SpeakService.myTTS != null) {
//                TtsWrapper.shutdownTts(SpeakService.myTTS);
//                SpeakService.myTTS = null;
//            }
        } catch (Exception e) {
        }
        cleanupPositions();
        myInitializationStatus &= ~TTS_INITIALIZED;
//        doStop();

//        stopTalking();
//        try {
//            myApi.clearHighlighting();
//        } catch (ApiException e) {
//            e.printStackTrace();
//        }
//        myApi.disconnect();
////        if (myTTS != null) {
////            myTTS.shutdown();
////            myTTS = null;
////        }
//        if (mSpeechSynthesizer != null)
//            mSpeechSynthesizer.release();
//        mSpeechSynthesizer = null;
    }

    @Override
    protected void onDestroy() {
//        switchOff();
//        super.onDestroy();
        restoreBottomMargin();
        if (isFinishing())
            doDestroy();
        super.onDestroy();
        System.gc();
    }

    private volatile int myInitializationStatus;
    private static int API_INITIALIZED = 1;
    private static int TTS_INITIALIZED = 2;
    private static int FULLY_INITIALIZED = API_INITIALIZED | TTS_INITIALIZED;

    // implements ApiClientImplementation.ConnectionListener
    public void onConnected() {
        Log.d(TAG, "onConnected myInitializationStatus=" + myInitializationStatus);
        if (myInitializationStatus != FULLY_INITIALIZED) {
            myInitializationStatus |= API_INITIALIZED;
            if (myInitializationStatus == FULLY_INITIALIZED) {
                onInitializationCompleted();
            }
        }
    }

    // implements TextToSpeech.OnInitListener
    public void onInit(int status) {
        Log.d(TAG, "onConnected onInit=" + myInitializationStatus);
        if (myInitializationStatus != FULLY_INITIALIZED) {
            myInitializationStatus |= TTS_INITIALIZED;
            if (myInitializationStatus == FULLY_INITIALIZED) {
                onInitializationCompleted();
            }
        }
    }

    private void setActionsEnabled(final boolean enabled) {
        Log.d(TAG, "setActionsEnabled enabled =" + enabled);
        runOnUiThread(new Runnable() {
            public void run() {
                findViewById(R.id.button_previous_paragraph).setEnabled(enabled);
                findViewById(R.id.button_next_paragraph).setEnabled(enabled);
                findViewById(R.id.button_play).setEnabled(enabled);
            }
        });
    }

    private String getDisplayLanguage(Locale locale, String defaultValue) {
        if (locale == null) {
            return defaultValue;
        }
        String language = locale.getDisplayLanguage();
        if (language != null) {
            return language;
        }
        language = locale.getLanguage();
        return language != null ? language : defaultValue;
    }

    private void onInitializationCompleted() {
        Log.d(TAG, "onInitializationCompleted ");
        FBReaderApplication.enableComponents(true);
        try {
            setTitle(myApi.getBookTitle());
            //SpeakService.myBookHash = "BP:" + SpeakService.myApi.getBookHash();
            myParagraphIndex = myApi.getPageStart().ParagraphIndex;
            myParagraphsNumber = myApi.getParagraphsNumber();

            final SeekBar speedControl = (SeekBar) findViewById(R.id.speed_control);
            speedControl.setEnabled(true);
            setSpeechRate(speedControl.getProgress());

            adjustBottomMargin();
            restorePosition();
            setActionsEnabled(true);
            startTalking();
        } catch (Exception e) {
//            if (SpeakService.myTTS == null)
//                ErrorReporter.getInstance().putCustomData("myTTS_null", "Yes");
//            if (SpeakService.myApi == null)
//                ErrorReporter.getInstance().putCustomData("myApi_null", "Yes");
            Lt.df("Exception in onInitializationCompleted(): " + e);
            e.printStackTrace();
            if (mSpeechSynthesizer != null) {
                mSpeechSynthesizer.release();
            }
            if (myApi != null) {
                try {
                    myApi.disconnect();
                } catch (Exception e3) {
                }
            }
            myApi = null;
            myInitializationStatus = 0;
            setActionsEnabled(false);
//                ErrorReporter.getInstance().handleException(e);
            finish();

        }

////        myTTS.setOnUtteranceCompletedListener(this);
//        try {
//            setTitle(myApi.getBookTitle());
//
////            Locale locale = null;
////            final String languageCode = myApi.getBookLanguage();
////            if (languageCode == null || "other".equals(languageCode)) {
////                locale = Locale.getDefault();
////                if (myTTS.isLanguageAvailable(locale) < 0) {
////                    locale = Locale.ENGLISH;
////                }
////                showErrorMessage(
////                        getText(R.string.language_is_not_set).toString()
////                                .replace("%0", getDisplayLanguage(locale, "???")),
////                        false
////                );
////            } else {
////                try {
////                    locale = new Locale(languageCode);
////                } catch (Exception e) {
////                }
////                if (locale == null || myTTS.isLanguageAvailable(locale) < 0) {
////                    final Locale originalLocale = locale;
////                    locale = Locale.getDefault();
////                    if (myTTS.isLanguageAvailable(locale) < 0) {
////                        locale = Locale.ENGLISH;
////                    }
////                    showErrorMessage(
////                            getText(R.string.no_data_for_language).toString()
////                                    .replace("%0", getDisplayLanguage(originalLocale, languageCode))
////                                    .replace("%1", getDisplayLanguage(locale, "???")),
////                            false
////                    );
////                }
////            }
////            myTTS.setLanguage(locale);
//
//            final SeekBar speedControl = (SeekBar) findViewById(R.id.speed_control);
//            speedControl.setEnabled(true);
//            setSpeechRate(speedControl.getProgress());
//
//            myParagraphIndex = myApi.getPageStart().ParagraphIndex;
//            myParagraphsNumber = myApi.getParagraphsNumber();
//            setActionsEnabled(true);
//            setActive(true);
//            speakString(gotoNextParagraph());
//        } catch (ApiException e) {
//            setActionsEnabled(false);
//            showErrorMessage(getText(R.string.initialization_error), true);
//            e.printStackTrace();
//        }
    }

    void adjustBottomMargin() {
        // Calculate the extra bottom margin needed for navigation buttons
//        if (myApi != null) {
//            try {
//                if (savedBottomMargin < 0)
//                    savedBottomMargin = myApi.getBottomMargin();
//                Lt.d("savedBottomMargin = " + savedBottomMargin);
//                Rect rectf = new Rect();
//                View v = findViewById(R.id.nav_buttons);
//                v.getLocalVisibleRect(rectf);
//                int d = rectf.bottom;
//                d += d / 5 + savedBottomMargin;
//                if (savedBottomMargin < d) {
//                    myApi.setBottomMargin(d);
//                    myApi.setPageStart(myApi.getPageStart());
//                }
//            } catch (ApiException e) {
//                Lt.df("ApiException " + e);
//                e.printStackTrace();
//                haveNewApi = 0;
//            }
//        }
    }

    @Override
    public void onUtteranceCompleted(String uttId) {
        Log.d(TAG, "onUtteranceCompleted myIsActive=" + myIsActive + ",uttId=" + uttId + ",wordPauses=" + wordPauses);

        regainBluetoothFocus();
        if (myIsActive) {
            if (uttId != null && uttId.startsWith(UTTERANCE_ID)) {
                int sntLastFinished = Integer.parseInt(uttId.substring(utIdLen));
                if (sntLastFinished < 0)
                    return;
                myCurrentSentence++; // this one is probably read aloud now, or about to be started
                if (sntLastFinished == mySentences.length - 1) { // end of paragraph
                    if (myParaPause > 0) {
                        myParamMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID + (-1));
//                        mSpeechSynthesizer.playSilence(myParaPause, TextToSpeech.QUEUE_ADD, myParamMap);
                    }
                    do {
                        ++myParagraphIndex;
                        processCurrentParagraph();
                        if (myParagraphIndex >= myParagraphsNumber) {
                            stopTalking();
                            return;
                        }
                    } while (mySentences.length == 0);
                    if (sntConcurrent && myCurrentSentence < mySentences.length) {
                        speakString(mySentences[myCurrentSentence].s, UTTERANCE_ID + myCurrentSentence);
                        sntLastAdded = myCurrentSentence;
                    }
                }
                // Highlight the sentence here...
                highlightSentence();
                if (wordPauses) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            stopTalking();
                        }
                    });
                } else if (myCurrentSentence < mySentences.length - (sntConcurrent ? 1 : 0)) {
                    sntLastAdded = myCurrentSentence + (sntConcurrent ? 1 : 0);
                    speakString(mySentences[sntLastAdded].s, UTTERANCE_ID + sntLastAdded);
                }
            }
        } else {
            setActive(false);
            isServiceTalking = false;
        }

//        if (myIsActive && UTTERANCE_ID.equals(uttId)) {
//            ++myParagraphIndex;
//            speakString(gotoNextParagraph());
//            Log.d(TAG, "onUtteranceCompleted myParagraphIndex=" + myParagraphIndex +
//                    ",myParagraphsNumber=" + myParagraphsNumber);
//            if (myParagraphIndex >= myParagraphsNumber) {
//                stopTalking();
//            }
//        } else {
//            setActive(false);
//        }
    }

    private void highlightParagraph() throws ApiException {
//        if (0 <= myParagraphIndex && myParagraphIndex < myParagraphsNumber) {
//            myApi.highlightArea(
//                    new TextPosition(myParagraphIndex, 0, 0),
//                    new TextPosition(myParagraphIndex, Integer.MAX_VALUE, 0)
//            );
//        } else {
//            myApi.clearHighlighting();
//        }
        TextPosition stPos = new TextPosition(myParagraphIndex, 0, 0);
        TextPosition edPos = new TextPosition(myParagraphIndex, Integer.MAX_VALUE, 0);
        if (stPos.compareTo(myApi.getPageStart()) < 0 || edPos.compareTo(myApi.getPageEnd()) > 0)
            myApi.setPageStart(stPos);
        if (myHighlightSentences && 0 <= myParagraphIndex && myParagraphIndex < myParagraphsNumber) {
            myApi.highlightArea(
                    new TextPosition(myParagraphIndex, 0, 0),
                    new TextPosition(myParagraphIndex, Integer.MAX_VALUE, 0)
            );
        } else {
            myApi.clearHighlighting();
        }
    }

    private void stopTalking() {
        setActive(false);
        savePosition();
        if (isServiceTalking && mSpeechSynthesizer != null) {
            isServiceTalking = false;
            try {
                int i;
                isTTSPause = true;
                mSpeechSynthesizer.stop();
                for (i = 0; i < 10; i++) {
                    try {
                        synchronized (SpeakActivity.this) {
                            SpeakActivity.this.wait(100);
                        }
                    } catch (InterruptedException e) {
                    }
                }
            } catch (Exception e) {
            }
        }
        if (mAudioManager != null) {
            // mAudioManager.abandonAudioFocus(afChangeListener);
            regainBluetoothFocus();
        }
        if (_lockscreenManager != null)
            _lockscreenManager.setLockscreenPaused();
    }

    void regainBluetoothFocus() {
        if (mAudioManager != null && componentName != null) {
            //TtsApp.enableComponents(true); // takes a long time on some hardware?.
            mAudioManager.registerMediaButtonEventReceiver(componentName);
        }
    }

    private void savePosition() {
        try {
            if (myCurrentSentence < mySentences.length) {
                if (myBookHash == null)
                    myBookHash = "BP:" + myApi.getBookHash();
                SharedPreferences.Editor myEditor = myPreferences.edit();
                Time time = new Time();
                time.setToNow();
                String lang = " l:" + selectedLanguage;
                myEditor.putString(myBookHash, lang +
                        "p:" + myParagraphIndex + " s:" + myCurrentSentence + " e:" + mySentences[myCurrentSentence].i +
                        " d:" + time.format2445()
                );

                myEditor.commit();
            }
        } catch (ApiException e) {

        }
    }

    private boolean restorePosition() {
        try {
            if (myBookHash == null)
                myBookHash = "BP:" + myApi.getBookHash();
            String s = myPreferences.getString(myBookHash, "");
            int il = s.indexOf("l:");
            int para = s.indexOf("p:");
            int sent = s.indexOf("s:");
            int idx = s.indexOf("e:");
            int dt = s.indexOf("d:");
            if (para > -1 && sent > -1 && idx > -1 && dt > -1) {
                if (il > -1) {
                    selectedLanguage = s.substring(il + 2, para);
                    int n = selectedLanguage.lastIndexOf('|');
                    if (n > 0) {
                        selectedLanguage = selectedLanguage.substring(0, n);
                    }
                }
                para = Integer.parseInt(s.substring(para + 2, sent - 1));
                sent = Integer.parseInt(s.substring(sent + 2, idx - 1));
                idx = Integer.parseInt(s.substring(idx + 2, dt - 1));
                TextPosition tp = new TextPosition(para, idx, 0);
                if (tp.compareTo(myApi.getPageStart()) >= 0 && tp.compareTo(myApi.getPageEnd()) < 0) {
                    myParagraphIndex = para;
                    processCurrentParagraph();
                    myCurrentSentence = sent;
                }
            }
        } catch (ApiException e) {
        }
        return true;
    }

    private void cleanupPositions() {
        // Cleanup - delete any hashes older than 6 months
        try {
            Map<String, ?> prefs = myPreferences.getAll();
            SharedPreferences.Editor myEditor = myPreferences.edit();
            for (Map.Entry<String, ?> entry : prefs.entrySet()) {
                if (entry.getKey().substring(0, 3).equals("BP:")) {
                    String s = entry.getValue().toString();
                    int i = s.indexOf("d:");
                    if (i > -1) {
                        Time time = new Time();
                        time.parse(s.substring(i + 2));
                        Time now = new Time();
                        now.setToNow();
                        long days = (now.toMillis(false) - time.toMillis(false)) / 1000 / 3600 / 24;
                        if (days > 182)
                            myEditor.remove(entry.getKey());
                    } else
                        myEditor.remove(entry.getKey());
                }
            }
            myEditor.commit();
        } catch (NullPointerException e) {

        }
    }

    private void showErrorMessage(final CharSequence text, final boolean fatal) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (fatal) {
                    setTitle(R.string.failure);
                }
                Toast.makeText(SpeakActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private volatile PowerManager.WakeLock myWakeLock;

    private synchronized void setActive(final boolean active) {
        myIsActive = active;

        runOnUiThread(new Runnable() {
            public void run() {
                findViewById(R.id.button_play).setVisibility(active ? View.GONE : View.VISIBLE);
                findViewById(R.id.button_pause).setVisibility(active ? View.VISIBLE : View.GONE);
            }
        });

        if (active) {
            if (myWakeLock == null) {
                myWakeLock = ((PowerManager) getSystemService(POWER_SERVICE))
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FBReader TTS plugin");
                myWakeLock.acquire();
            }
        } else {
            if (myWakeLock != null) {
                myWakeLock.release();
                myWakeLock = null;
            }
        }
    }


    private void highlightSentence() {
        try {
            int endEI = myCurrentSentence < mySentences.length - 1 ?
                    mySentences[myCurrentSentence + 1].i - 1 : Integer.MAX_VALUE;
            TextPosition stPos;
            if (myCurrentSentence >= mySentences.length)
                myCurrentSentence = mySentences.length - 1;
            if (myCurrentSentence <= 0) {
                myCurrentSentence = 0;
                stPos = new TextPosition(myParagraphIndex, 0, 0);
            } else
                stPos = new TextPosition(myParagraphIndex, mySentences[myCurrentSentence].i, 0);
            TextPosition edPos = new TextPosition(myParagraphIndex, endEI, 0);
            if (stPos.compareTo(myApi.getPageStart()) < 0 || edPos.compareTo(myApi.getPageEnd()) > 0)
                myApi.setPageStart(stPos);
            if (myHighlightSentences)
                myApi.highlightArea(stPos, edPos);
            else
                myApi.clearHighlighting();
        } catch (ApiException e) {
            switchOff();
            finish();
        }
    }

    boolean isStreamAvailable(int strNum) {
        if (strNum == AudioManager.STREAM_MUSIC)
            return true;
        int origMusicVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0);
        boolean isAvailable = false;
        try {
            int maxVol = mAudioManager.getStreamMaxVolume(strNum);
            int vol = mAudioManager.getStreamVolume(strNum);
            // Try to change the volume of each stream and see if STREAM_MUSIC changes as well...
            mAudioManager.setStreamVolume(strNum, maxVol, 0);

            int newMusicVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            mAudioManager.setStreamVolume(strNum, vol, 0);
            isAvailable = (newMusicVol != maxVol);
        } catch (Exception e) {
        }

        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, origMusicVol, 0);
        return isAvailable;
    }

    private void startTalking() {
        setActive(true);
        sntConcurrent = myPreferences.getBoolean("sntConcurrent", true);
        wordPauses = myPreferences.getBoolean("WORD_OPTS", false) &&
                myPreferences.getBoolean("SINGLE_WORDS", false) &&
                myPreferences.getBoolean("PAUSE_WORDS", false);
        if (myCurrentSentence >= mySentences.length) {
            processCurrentParagraph();
        }
        if (myCurrentSentence < mySentences.length) {
            if (haveNewApi > 0)
                highlightSentence();
            if (myApi != null && myApi.isConnected()) {

                if (allowBackgroundMusic && mAudioManager.isMusicActive()) {
                    // also consider using Activity method:  setVolumeControlStream (int streamType) to set
                    // which stream will be affected by the hardware volume buttons.
                    if (isStreamAvailable(AudioManager.STREAM_DTMF))
                        audioStream = AudioManager.STREAM_DTMF;
                    else if (isStreamAvailable(AudioManager.STREAM_RING))
                        audioStream = AudioManager.STREAM_RING; // STREAM_RING works well on Kindle Fire HDX...
                    else
                        audioStream = AudioManager.STREAM_MUSIC;
                    mAudioManager.requestAudioFocus(afChangeListener,
                            // Use the selected stream.
                            audioStream,
                            // Request permanent focus.
                            0); // non-exclusive...
                } else {
                    audioStream = AudioManager.STREAM_MUSIC;
                    mAudioManager.requestAudioFocus(afChangeListener,
                            // Use the music stream.
                            AudioManager.STREAM_MUSIC,
                            // Request permanent focus.
                            AudioManager.AUDIOFOCUS_GAIN);
                }
//                SeekBar volumeControl = (SeekBar) findViewById(R.id.speed_control);
//                int vol = mAudioManager.getStreamVolume(audioStream);
//                myMaxVolume = mAudioManager.getStreamMaxVolume(audioStream);
//                volumeControl.setMax(myMaxVolume);
//                volumeControl.setProgress(vol);
                if (myParamMap != null)
                    myParamMap.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                            String.valueOf(audioStream));


                myHasNetworkTts = false;
//                if (Build.VERSION.SDK_INT > 14) try {
//                    myParamMap.remove(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS);
//                    Set<String> ss = myTTS.getFeatures(myTTS.getLanguage());
//                    if (ss != null) {
//                        for (String s : ss) {
//                            if (s.equals(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS))
//                                myHasNetworkTts = true;
//                        }
//                    }
//                } catch (Exception e) {}

                if (myPreferences.getBoolean("ShowLockWidget", true)) {
                    if (_lockscreenManager == null)
                        _lockscreenManager = new LockscreenManager();
                    _lockscreenManager.setLockscreenPlaying();
                } else
                    _lockscreenManager = null;
                if (myCurrentSentence < mySentences.length) {
                    speakString(mySentences[myCurrentSentence].s, UTTERANCE_ID + myCurrentSentence);
                    sntLastAdded = myCurrentSentence;
                    if (sntConcurrent && !wordPauses && sntLastAdded < mySentences.length - 1) {
                        sntLastAdded++;
                        speakString(mySentences[sntLastAdded].s, UTTERANCE_ID + sntLastAdded);
                    }
                }
            }
        } else
            stopTalking();
    }

    private void processCurrentParagraph() {
        try {
            List<String> wl = null;
            List<Integer> il = null;
            myCurrentSentence = 0;
            for (; myParagraphIndex < myParagraphsNumber; ++myParagraphIndex) {
                // final String s = myApi.getParagraphText(myParagraphIndex);
                wl = myApi.getParagraphWords(myParagraphIndex);
                if (wl != null && wl.size() > 0) {
                    il = myApi.getParagraphWordIndices(myParagraphIndex);
                    break;
                }
            }
            if (wl == null || myParagraphIndex >= myParagraphsNumber) {
                findViewById(R.id.button_next_paragraph).setEnabled(false);
                findViewById(R.id.button_play).setEnabled(false);
            } else {
                boolean wordsOnly = myPreferences.getBoolean("WORD_OPTS", false) &&
                        myPreferences.getBoolean("SINGLE_WORDS", false);
                mySentences = TtsSentenceExtractor.build(wl, il, null, wordsOnly);
            }
        } catch (ApiException e) {
            stopTalking();
            showErrorMessage("接口错误", true);
            e.printStackTrace();
        }
    }

    private void speakString(String text, String utId) {
        toPrint("speakString text.length()=" + text.length() + ",text =" + text);
        text = text.replaceAll(" ", "");
//        if (!TextUtils.isEmpty(text) && text.length() > 102) {
//            text = text.substring(0, 102);
//        }
//        HashMap<String, String> callbackMap = new HashMap<String, String>();
//        callbackMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID);

//        if (mSpeechSynthesizer != null) {
//            mSpeechSynthesizer.pause();
//        }

        int ret;
        myParamMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utId);
        if (text.length() > 0) {
            if (myHasNetworkTts) {
                myParamMap.remove(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS);
                int n = 2;
                try {
                    n = myPreferences.getInt("netSynth", 2); // bit 0 - use net, bit 1 - wifi only
                } catch (ClassCastException e) {
                    SharedPreferences.Editor ed = myPreferences.edit();
                    ed.remove("netSynth");
                    ed.commit();
                }
                int conn = connectionType();
                boolean useNet = (n & 1) == 1;
                boolean wifiOnly = (n & 2) == 2;
                if (useNet && (conn == 2 || conn == 1 && !wifiOnly)) {
                    myParamMap.put(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "true");
                }
            }
            ret = mSpeechSynthesizer.resume();
            toPrint("mSpeechSynthesizer.resume ret="+ret);
            ret = mSpeechSynthesizer.speak(text, utId);//, TextToSpeech.QUEUE_FLUSH, callbackMap);
            toPrint("mSpeechSynthesizer.speak ret="+ret);
            isServiceTalking = ret == TextToSpeech.SUCCESS;
            if (isServiceTalking && mySntPause > 0) {
                myParamMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID + "-1");
//                myTTS.playSilence(mySntPause, TextToSpeech.QUEUE_ADD, myParamMap);
            }
        } else {
//            ret = myTTS.playSilence(50, TextToSpeech.QUEUE_ADD, myParamMap); // to call utteranceCompleted() on TTS thread...
        }
    }

    int connectionType() { // ret. 0 no connection, 1 mobile only, 2 wifi
        boolean haveConnection = false;
        ConnectivityManager cm = (ConnectivityManager) FBReaderApplication.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] netInfo = cm.getAllNetworkInfo();
        for (NetworkInfo ni : netInfo) {
            if (ni.getTypeName().toLowerCase().startsWith("wifi"))
                if (ni.isConnected())
                    return 2;
            if (!haveConnection)
                haveConnection = ni.isConnected();
        }
        return haveConnection ? 1 : 0;
    }

    private void speakString(String text) {
        speakString(text, UTTERANCE_ID + sntLastAdded);
    }

    void nextToSpeak() {
        boolean wasSpeaking = isServiceTalking;
        if (wasSpeaking)
            stopTalking();
        gotoNextSentence();
        if (wasSpeaking)
            startTalking();
    }

    private void prevToSpeak() {
        boolean wasSpeaking = isServiceTalking || myParagraphIndex >= myParagraphsNumber;
        if (wasSpeaking)
            stopTalking();
        gotoPreviousSentence();
        if (wasSpeaking)
            startTalking();
    }

    void gotoNextSentence() {
        try {
            myApi.clearHighlighting();
        } catch (ApiException e) {

        }
        if (myCurrentSentence < mySentences.length - 1) {
            myCurrentSentence++;
            highlightSentence();
        } else if (myParagraphIndex < myParagraphsNumber) {
            ++myParagraphIndex;
            processCurrentParagraph();
            myCurrentSentence = 0;
            highlightSentence();
        }
    }

    private void gotoPreviousParagraph() {
        mySentences = new TtsSentenceExtractor.SentenceIndex[0];
        try {
            if (myParagraphIndex > myParagraphsNumber)
                myParagraphIndex = myParagraphsNumber;
            for (int i = myParagraphIndex - 1; i >= 0; --i) {
                if (myApi.getParagraphText(i).length() > 2) { // empty paragraph breaks previous function
                    myParagraphIndex = i;
                    break;
                }
            }
            runOnUiThread(new Runnable() {
                public void run() {
                    findViewById(R.id.button_next_paragraph).setEnabled(true);
                    findViewById(R.id.button_play).setEnabled(true);
                }
            });
        } catch (ApiException e) {
            e.printStackTrace();
        }

//        try {
//            for (int i = myParagraphIndex - 1; i >= 0; --i) {
//                if (myApi.getParagraphText(i).length() > 0) {
//                    myParagraphIndex = i;
//                    break;
//                }
//            }
//            if (myApi.getPageStart().ParagraphIndex >= myParagraphIndex) {
//                myApi.setPageStart(new TextPosition(myParagraphIndex, 0, 0));
//            }
//            highlightParagraph();
//            runOnUiThread(new Runnable() {
//                public void run() {
//                    findViewById(R.id.button_next_paragraph).setEnabled(true);
//                    findViewById(R.id.button_play).setEnabled(true);
//                }
//            });
//        } catch (ApiException e) {
//            e.printStackTrace();
//        }
    }

    void gotoPreviousSentence() {
        try {
            myApi.clearHighlighting();
        } catch (ApiException e) {
        }
        if (myCurrentSentence > 0) {
            myCurrentSentence--;
            highlightSentence();
        } else if (myParagraphIndex > 0) {
            gotoPreviousParagraph();
            processCurrentParagraph();
            myCurrentSentence = mySentences.length - 1;
            highlightSentence();
        }
    }

    private String gotoNextParagraph() {
        try {
            String text = "";
            for (; myParagraphIndex < myParagraphsNumber; ++myParagraphIndex) {
                final String s = myApi.getParagraphText(myParagraphIndex);
                if (s.length() > 0) {
                    text = s;
                    text = text.replaceAll(" ", "");
                    break;
                }
            }
            if (!"".equals(text) && !myApi.isPageEndOfText()) {
                myApi.setPageStart(new TextPosition(myParagraphIndex, 0, 0));
            }
            highlightParagraph();
            if (myParagraphIndex >= myParagraphsNumber) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        findViewById(R.id.button_next_paragraph).setEnabled(false);
                        findViewById(R.id.button_play).setEnabled(false);
                    }
                });
            }
            return text;
        } catch (ApiException e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public void onSynthesizeStart(String utteranceId) {
        toPrint("onSynthesizeStart utteranceId=" + utteranceId);
    }

    /**
     * 合成数据和进度的回调接口，分多次回调
     *
     * @param utteranceId
     * @param data        合成的音频数据。该音频数据是采样率为16K，2字节精度，单声道的pcm数据。
     * @param progress    文本按字符划分的进度，比如:你好啊 进度是0-3
     */
    @Override
    public void onSynthesizeDataArrived(String utteranceId, byte[] data, int progress) {
        mHandler.sendMessage(mHandler.obtainMessage(UI_CHANGE_SYNTHES_TEXT_SELECTION, progress, 0));
    }

    /**
     * 合成正常结束，每句合成正常结束都会回调，如果过程中出错，则回调onError，不再回调此接口
     *
     * @param utteranceId
     */
    @Override
    public void onSynthesizeFinish(String utteranceId) {
        toPrint("onSynthesizeFinish utteranceId=" + utteranceId);
    }

    /**
     * 播放开始，每句播放开始都会回调
     *
     * @param utteranceId
     */
    @Override
    public void onSpeechStart(String utteranceId) {
        toPrint("onSpeechStart utteranceId=" + utteranceId);
        isServiceTalking = true;
    }

    /**
     * 播放进度回调接口，分多次回调
     *
     * @param utteranceId
     * @param progress    文本按字符划分的进度，比如:你好啊 进度是0-3
     */
    @Override
    public void onSpeechProgressChanged(String utteranceId, int progress) {
        // toPrint("onSpeechProgressChanged");
        mHandler.sendMessage(mHandler.obtainMessage(UI_CHANGE_INPUT_TEXT_SELECTION, progress, 0));
    }

    /**
     * 播放正常结束，每句播放正常结束都会回调，如果过程中出错，则回调onError,不再回调此接口
     *
     * @param utteranceId
     */
    @Override
    public void onSpeechFinish(String utteranceId) {
        toPrint("onSpeechFinish utteranceId=" + utteranceId);
        onUtteranceCompleted(utteranceId);
    }

    /**
     * 当合成或者播放过程中出错时回调此接口
     *
     * @param utteranceId
     * @param error       包含错误码和错误信息
     */
    @Override
    public void onError(String utteranceId, SpeechError error) {
        toPrint("onError error=" + "(" + error.code + ")" + error.description + "--utteranceId=" + utteranceId);
    }

    private Handler mHandler = new Handler() {

        /*
         * @param msg
         */
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int what = msg.what;
            switch (what) {
                case PRINT:
                    print(msg);
                    break;
                case UI_CHANGE_INPUT_TEXT_SELECTION:
//                    if (msg.arg1 <= mInput.getText().length()) {
//                        mInput.setSelection(0, msg.arg1);
//                    }
                    break;
                case UI_CHANGE_SYNTHES_TEXT_SELECTION:
//                    SpannableString colorfulText = new SpannableString(mInput.getText().toString());
//                    if (msg.arg1 <= colorfulText.toString().length()) {
//                        colorfulText.setSpan(new ForegroundColorSpan(Color.GRAY), 0, msg.arg1, Spannable
//                                .SPAN_EXCLUSIVE_EXCLUSIVE);
//                        mInput.setText(colorfulText);
//                    }
                    break;
                default:
                    break;
            }
        }

    };

    private void toPrint(String str) {
        if (BuildConfig.DEBUG) {
            Message msg = Message.obtain();
            msg.obj = str;
            mHandler.sendMessage(msg);
        }
    }

    private void print(Message msg) {
        if (BuildConfig.DEBUG) {
            String message = (String) msg.obj;
            if (message != null) {
                Log.w(TAG, message);
//                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
//            scrollLog(message);
            }
        }
    }

//    private void scrollLog(String message) {
//        Spannable colorMessage = new SpannableString(message + "\n");
//        colorMessage.setSpan(new ForegroundColorSpan(0xff0000ff), 0, message.length(),
//                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//        mShowText.append(colorMessage);
//        Layout layout = mShowText.getLayout();
//        if (layout != null) {
//            int scrollAmount = layout.getLineTop(mShowText.getLineCount()) - mShowText.getHeight();
//            if (scrollAmount > 0) {
//                mShowText.scrollTo(0, scrollAmount + mShowText.getCompoundPaddingBottom());
//            } else {
//                mShowText.scrollTo(0, 0);
//            }
//        }
//    }

    // The listener below is needed to stop talking when Voice Dialer button is pressed,
    // and resume talking if cancelled or call finished.
    private AudioManager.OnAudioFocusChangeListener afChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                if (!myWasActive)
                    myWasActive = myIsActive;
                stopTalking(); // Pause playback
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                if (myWasActive) {
                    myWasActive = false;
                    startTalking();// Resume playback
                }
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                myWasActive = myIsActive;
                stopTalking();
                //mAudioManager.unregisterMediaButtonEventReceiver(componentName);
            }
        }
    };

    private Runnable myTimerTask = new Runnable() {
        public void run() {
            restoreBottomMargin();
            FBReaderApplication.enableComponents(false);
            doDestroy();
            finish();
            switchOff();
        }
    };

    void restoreBottomMargin() {
        if (savedBottomMargin > -1) {
            try {
                myApi.setBottomMargin(savedBottomMargin);
                myApi.setPageStart(myApi.getPageStart());
                savedBottomMargin = -1;
            } catch (Exception e) {

            }
        }
    }

    void doDestroy() {
        restoreBottomMargin();
        if (myIsActive) {
            myIsActive = false;
            switchOff();
        }
    }
}
