package com.cree.soundrecorder;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.cree.soundrecorder.sound.RecorderController;
import com.cree.soundrecorder.util.ADTSHeadUtil;
import com.cree.soundrecorder.util.LogUtil;
import com.iammert.rangeview.library.RangeView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Title:
 * Description:
 * Copyright:Copyright(c)2020
 * Company: Cree
 * CreateTime:2020/6/1  21:59
 *
 * @author luyongjiang
 * @version 1.0
 */
public class CutActivity extends AppCompatActivity {

    private float mLeftValue, mRightValue;
    private int mDuration;
    private TextView mTvRang;
    private String mPath;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_cut);
        RangeView rangView = findViewById(R.id.rangeView);
        mTvRang = findViewById(R.id.tvRang);
        mDuration = RecorderController.getDuration();
        mPath = RecorderController.getFilePathToString();
        rangView.setRangeValueChangeListener(new RangeView.OnRangeValueListener() {
            @Override
            public void rangeChanged(float max, float min, float currentLeftValue, float currentRightValue) {
                LogUtil.e("---------max------" + max + "   min:" + min + "  currentLeftValue:" + currentLeftValue + "  currentRightValue:" + currentRightValue);
                mLeftValue = currentLeftValue;
                mRightValue = currentRightValue;
                mTvRang.setText(((int) mDuration / 1000 * mLeftValue) + "s----" + ((int) mDuration / 1000 * mRightValue) + "s");
            }
        });


    }


    public void onClick(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // 打开一个随机访问文件流，按读写方式
                RandomAccessFile randomFile = null;
                try {
                    randomFile = new RandomAccessFile(mPath, "rw");

                    // 文件长度，字节数
                    long fileLength = randomFile.length();
                    // 将写文件指针移到文件尾。
                    randomFile.seek(2000);
                    byte[] bytes = new byte[1024];
                    int length = randomFile.read(bytes);

                    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
                    byteBuffer.rewind();
                    int rightHead = getIndex(bytes, length);
                    byte[] headByte = new byte[bytes.length + 7];
                    ADTSHeadUtil.addADTStoPacket(headByte, rightHead);

                    byteBuffer.get(headByte, 7, byteBuffer.limit());
                    byteBuffer.rewind();

                    byteBuffer.position(rightHead);
                    int offset = byteBuffer.limit() - byteBuffer.position();
                    byte[] bytes1 = new byte[offset];
                    byteBuffer.get(bytes1, 0, offset);
                    StringBuilder stringBuilder = new StringBuilder();
                    for (byte by : bytes1) {
                        stringBuilder.append(by + ", ");
                    }
                    LogUtil.e("---------观察流数据------:" + stringBuilder.toString());
                    randomFile.close();

                    File file = new File(mPath);
                    File parentFile = file.getParentFile();
                    File cutFile = new File(parentFile, "cut_newFile.m4a");
                    FileOutputStream fileOutputStream = new FileOutputStream(cutFile);
                    fileOutputStream.write(bytes1, 0, bytes1.length);
                    fileOutputStream.flush();
                    fileOutputStream.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private int getIndex(byte[] bytes, int length) {
        int headIndex1 = -1;
        int index = -1;
        for (int i = 0; i < length; i++) {
            switch (bytes[i]) {
                case -1:
                    headIndex1 = i;
                    break;
                case -15:
                    if (i == (headIndex1 + 1)) {
                        index = headIndex1;
                        return index;
                    }
                    break;
            }

        }
        return index;
    }

    private void testFunction() {
        byte by = (byte) 0xFF;
        byte by1 = (byte) 0xF1;

        LogUtil.e("---------开始裁切------0xFF:" + by + "  0xF1:" + by1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String filePathToString = RecorderController.getFilePathToString();
                try {
                    FileInputStream fileInputStream = new FileInputStream(new File(filePathToString));
                    byte[] bytes = new byte[1024];
                    int read = -1;
                    StringBuilder stringBuilder = new StringBuilder();
                    while ((read = fileInputStream.read(bytes)) != -1) {
                        for (int i = 0; i < read; i++) {
                            stringBuilder.append(bytes[i]).append(" ,");
                        }
                    }
                    LogUtil.e("---------观察流数据------" + stringBuilder.toString());
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
