package com.cree.soundrecorder.sound;

import android.Manifest;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.cree.soundrecorder.CutActivity;
import com.cree.soundrecorder.MainActivity;
import com.cree.soundrecorder.R;
import com.cree.soundrecorder.util.LogUtil;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import androidx.annotation.NonNull;

/**
 * Title:
 * Description:
 * Copyright:Copyright(c)2020
 * Company: Cree
 * CreateTime:2020/5/30  10:20
 *
 * @author cree
 * @version 1.0
 */
public class RecorderController implements View.OnClickListener {

    private static final int RECORDER_STATUS_RECORDING = 0, RECORDER_STATUS_PAUSE = 1, RECORDER_STATUS_STOP = 2;

    private MainActivity mActivity;

    private AudioRecordManagerADTS mAudioRecordManagerADTS;
    private int mRecorderStatus = RECORDER_STATUS_STOP;
    private Toast mToast;


    public RecorderController(MainActivity activity) {
        mActivity = activity;
        mAudioRecordManagerADTS = new AudioRecordManagerADTS(getFilePathToString());
    }

    public void requestPer() {
        if (mActivity != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mActivity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            }
        }
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bStartRecorder:
                if (mRecorderStatus == RECORDER_STATUS_RECORDING) {
                    intercept();
                    return;
                }
                mRecorderStatus = RECORDER_STATUS_RECORDING;
                LogUtil.e("---------开始录制------" + v.getId());
                mAudioRecordManagerADTS.startRecord();
                mHandler.sendEmptyMessageDelayed(0x11, 70);
                ((TextView) v).setText("正在录制中...");
                break;
            case R.id.bStopRecorder:
                if (!(mRecorderStatus == RECORDER_STATUS_RECORDING || mRecorderStatus == RECORDER_STATUS_PAUSE)) {
                    intercept();
                    return;
                }
                mRecorderStatus = RECORDER_STATUS_STOP;
                LogUtil.e("---------停止录制------" + v.getId());
                mHandler.removeCallbacksAndMessages(null);
                mAudioRecordManagerADTS.stopRecord();
                ((TextView) mActivity.findViewById(R.id.bStartRecorder)).setText("开始录音");
                break;
            case R.id.bPauseRecorder:
                if (mRecorderStatus != RECORDER_STATUS_RECORDING) {
                    intercept();
                    return;
                }
                mRecorderStatus = RECORDER_STATUS_PAUSE;
                LogUtil.e("---------暂停录制------" + v.getId());
                mAudioRecordManagerADTS.pauseRecord();
                ((TextView) mActivity.findViewById(R.id.bStartRecorder)).setText("继续录制");
                break;
            case R.id.bInsertContent:
                if (mRecorderStatus != RECORDER_STATUS_STOP) {
                    intercept();
                    return;
                }
                LogUtil.e("---------追加内容------" + v.getId());
                Toast.makeText(mActivity, "追加内容", Toast.LENGTH_SHORT).show();
                String appendContent = getAppendContent();
                appendMethod(getFilePathToString(), appendContent);
                break;
            case R.id.bGetFileTime:
                LogUtil.e("---------读取时长------" + v.getId());
                int duration = getDuration();
                File file = new File(getFilePathToString());
                long length = file.length();
                mActivity.setDuration("文件长度:" + length + " 音频时长:" + duration);
                break;
            case R.id.bIntentToCutActivity:
                if (mRecorderStatus != RECORDER_STATUS_STOP) {
                    intercept();
                    return;
                }
                LogUtil.e("---------前往裁切界面------" + v.getId());
                Intent intent = new Intent(mActivity, CutActivity.class);
                mActivity.startActivity(intent);
                break;
        }
    }

    private void intercept() {
        if (mToast != null) {
            mToast.cancel();
            mToast = null;
        }
        mToast = Toast.makeText(mActivity, "拦截", Toast.LENGTH_SHORT);
        mToast.show();
    }


    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (mAudioRecordManagerADTS != null) {
                double volume = mAudioRecordManagerADTS.getVolume();
                LogUtil.e("---------获取到的音量值------" + volume);
                double voice = volume - 34.4;
                if (voice < 0) {
                    voice = 0;
                }
                mActivity.setVoiceLineView(voice);
            }
            mHandler.sendEmptyMessageDelayed(0x11, 70);
            return false;
        }
    });


    public static String getFilePathToString() {
        String path = Environment.getExternalStorageDirectory().getPath();
        String groupPath = path + File.separator + "test";
        File groupFile = new File(groupPath);
        if (!groupFile.exists()) {
            groupFile.mkdirs();
        }
        return groupFile + File.separator + ("newFile_adts.m4a");
    }

    /**
     * 方法追加文件：使用RandomAccessFile
     *
     * @param fileName 文件名
     * @param content  追加的内容
     */
    public static void appendMethod(String fileName, String content) {
        try {
            // 打开一个随机访问文件流，按读写方式
            RandomAccessFile randomFile = new RandomAccessFile(fileName, "rw");
            // 文件长度，字节数
            long fileLength = randomFile.length();
            // 将写文件指针移到文件尾。
            randomFile.seek(fileLength);
            randomFile.writeBytes(content);
            randomFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getAppendContent() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            stringBuilder.append(i).append(",");
        }
        return stringBuilder.toString();
    }

    public static int getDuration() {
        try {
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(getFilePathToString());
            mediaPlayer.prepare();
            int duration = mediaPlayer.getDuration();
            LogUtil.e("---------获取时长------" + duration);
            return duration;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }


}
