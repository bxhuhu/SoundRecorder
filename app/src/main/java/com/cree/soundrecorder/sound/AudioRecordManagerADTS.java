package com.cree.soundrecorder.sound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import com.cree.soundrecorder.util.LogUtil;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

import androidx.annotation.RequiresApi;

/**
 * Title:
 * Description:这是adts的头文件方式
 * Copyright:Copyright(c)2020
 * Company: Cree
 * CreateTime:2020/5/28  22:26
 *
 * @author cree
 * @version 1.0
 */
public class AudioRecordManagerADTS {
    private final int sampleRateInHz = 44100;//44100 16000
    private final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

    private String mAudioFilePath;
    private volatile double mCurrentVolume = 0;
    private long mBufferCount = 0;
    private final String TAG = "AudioRecordManager";


    private class AudioData {
        private ByteBuffer buffer;
        private int size;
    }

    private AudioRecorder mAudioRecorder;
    private AudioEncoder mAudioEncoder;

    private ArrayBlockingQueue queue;

    /**
     * @param audioFilePath 文件名
     */
    public AudioRecordManagerADTS(String audioFilePath) {
        this.mAudioFilePath = audioFilePath;

        queue = new ArrayBlockingQueue<>(1024);
        mAudioRecorder = new AudioRecorder();
        mAudioEncoder = new AudioEncoder();
    }

    public void startRecord() {
        if (mAudioRecorder == null) {
            mAudioRecorder = new AudioRecorder();
        }
        mAudioRecorder.start();
        if (mAudioEncoder == null) {
            mAudioEncoder = new AudioEncoder();
        }
        if (!mAudioEncoder.isEncoding) {
            mAudioEncoder.start();
        }
    }

    public void stopRecord() {
        if (mAudioRecorder != null) {
            mAudioRecorder.stopRecording();
            mAudioRecorder = null;
        }
        if (mAudioEncoder != null) {
            mAudioEncoder.stopEncording();
            mAudioEncoder = null;
        }
    }

    public void pauseRecord() {
        if (mAudioRecorder != null) {
            mAudioRecorder.stopRecording();
        }
        mAudioRecorder = null;
    }

    /**
     * 录音线程
     */
    public class AudioRecorder extends Thread {

        private AudioRecord mAudioRecord;
        private boolean isRecording;
        private int minBufferSize;

        public AudioRecorder() {
            isRecording = true;
            initRecorder();
        }

        @Override
        public void run() {
            super.run();
            startRecording();
        }

        /**
         * 初始化录音
         */
        public void initRecorder() {
            minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat) * 2;// 乘以2 加大缓冲区，防止其他意外
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz, channelConfig, audioFormat, minBufferSize);
            if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                isRecording = false;
                return;
            }
        }

        /**
         * 释放资源
         */
        public void release() {
            if (mAudioRecord != null && mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                mAudioRecord.stop();
            }
        }

        /**
         * 开始录音
         */
        public void startRecording() {
            if (mAudioRecord == null) {
                return;
            }

            while (isRecording) {
                if (mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    mAudioRecord.startRecording();
                    break;
                }
            }
            while (isRecording) {
                long a = System.currentTimeMillis();
                AudioData audioDate = new AudioData();
                audioDate.buffer = ByteBuffer.allocateDirect(minBufferSize);
                audioDate.size = mAudioRecord.read(audioDate.buffer, minBufferSize);
                try {
                    if (queue != null) {
                        queue.put(audioDate);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                long b = System.currentTimeMillis() - a;
                Log.i(TAG, "录制耗时-毫秒==" + b);
            }
            release();
        }

        /**
         * 结束录音
         */
        public void stopRecording() {
            isRecording = false;
        }
    }

    /**
     * 音频编码线程
     */
    public class AudioEncoder extends Thread {

        private MediaCodec mMediaCodec;
        private Boolean isEncoding = false;
        private int minBufferSize;

        private OutputStream mFileStream;


        public AudioEncoder() {
            initEncorder();
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void run() {
            super.run();
            isEncoding = true;
            startEncording();
        }

        /**
         * 初始化编码器
         */
        private void initEncorder() {
            minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat) * 2;// 乘以2 加大缓冲区，防止其他意外
            try {
                mMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            } catch (Exception e) {
                e.printStackTrace();
            }
            MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRateInHz, 1);
            format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBufferSize * 4);
            mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }


        /**
         * 开始编码
         */
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        public void startEncording() {
            if (mMediaCodec == null) {
                return;
            }

            mMediaCodec.start();
            try {

                mFileStream = new FileOutputStream(mAudioFilePath);
                MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
                AudioData audioData;


                while (isEncoding) {
                    long a = System.currentTimeMillis();
                    // 从队列中取出录音的一帧音频数据
                    audioData = getAudioData();

                    if (audioData == null) {
                        continue;
                    }
                    onSec(audioData);
                    // 取出InputBuffer，填充音频数据，然后输送到编码器进行编码
                    int inputBufferIndex = mMediaCodec.dequeueInputBuffer(0);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferIndex);
                        inputBuffer.clear();
                        inputBuffer.put(audioData.buffer);
                        mMediaCodec.queueInputBuffer(inputBufferIndex, 0, audioData.size, System.nanoTime(), 0);
                    }

                    // 取出编码好的一帧音频数据，然后给这一帧添加ADTS头
                    int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 0);
                    while (outputBufferIndex >= 0) {

                        ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex);
                        int outBufferSize = outputBuffer.limit() + 7;
                        byte[] aacBytes = new byte[outBufferSize];
                        addADTStoPacket(aacBytes, outBufferSize);
                        outputBuffer.get(aacBytes, 7, outputBuffer.limit());
                        mFileStream.write(aacBytes);
                        mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 0);
                    }

                    long b = System.currentTimeMillis() - a;
                    Log.i(TAG, "编码耗时-毫秒==" + b);
                }
                release();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }



        private void onSec(AudioData audioData) {
            mBufferCount += audioData.buffer.limit();
            float sec = mBufferCount * 1f / sampleRateInHz;


            byte[] bytes = new byte[audioData.buffer.limit()];
            audioData.buffer.get(bytes, 0, bytes.length);
            audioData.buffer.rewind();
            ArrayList<Short> arrayList = new ArrayList();

            for (byte aByte : bytes) {
                arrayList.add((short) aByte);
            }
            for (int i = 0; i < arrayList.size(); i++) {
                byte bus[] = getBytes(arrayList.get(i));
                arrayList.set(i, (short) ((0x0000 | bus[1]) << 8 | bus[0]));//高低位交换
            }


            mCurrentVolume = getVolume(arrayList);
            LogUtil.e("---------sec------" + sec + " mCurrentVolume:" + mCurrentVolume);
        }

        public byte[] getBytes(short s) {
            byte[] buf = new byte[2];
            for (int i = 0; i < buf.length; i++) {
                buf[i] = (byte) (s & 0x00ff);
                s >>= 8;
            }
            return buf;
        }


        private double getVolume(ArrayList<Short> byteBuffer) {
            long v = 0;
            // 将 buffer 内容取出，进行平方和运算
            for (short value : byteBuffer) {
                v += value * value;
            }
            // 平方和除以数据总长度，得到音量大小。
            double mean = v / (double) byteBuffer.size();
            double volume = 10 * Math.log10(mean);
            LogUtil.e("分贝值 = " + volume + "dB" + " --- mean:" + mean);
            return volume;
        }

        /**
         * 停止编码
         */
        public void stopEncording() {
            isEncoding = false;
        }

        /**
         * 从队列中取出一帧待编码的音频数据
         *
         * @return
         */
        public AudioData getAudioData() {
            if (queue != null) {
                try {
                    return (AudioData) queue.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        /**
         * Add ADTS header at the beginning of each and every AAC packet.
         * This is needed as MediaCodec encoder generates a packet of raw
         * AAC data.
         * <p>
         * Note the packetLen must count in the ADTS header itself !!! .
         * 注意，这里的packetLen参数为raw aac Packet Len + 7; 7 bytes adts header
         **/
        private void addADTStoPacket(byte[] packet, int packetLen) {
            int profile = 2;  //AAC LC，MediaCodecInfo.CodecProfileLevel.AACObjectLC;
            int freqIdx = 4;  //见后面注释avpriv_mpeg4audio_sample_rates中32000对应的数组下标，来自ffmpeg源码
            int chanCfg = 1;  //见后面注释channel_configuration，AudioFormat.CHANNEL_IN_MONO 单声道(声道数量)

        /*int avpriv_mpeg4audio_sample_rates[] = {96000, 88200, 64000, 48000, 44100, 32000,24000, 22050, 16000, 12000, 11025, 8000, 7350};
        channel_configuration: 表示声道数chanCfg
        0: Defined in AOT Specifc Config
        1: 1 channel: front-center
        2: 2 channels: front-left, front-right
        3: 3 channels: front-center, front-left, front-right
        4: 4 channels: front-center, front-left, front-right, back-center
        5: 5 channels: front-center, front-left, front-right, back-left, back-right
        6: 6 channels: front-center, front-left, front-right, back-left, back-right, LFE-channel
        7: 8 channels: front-center, front-left, front-right, side-left, side-right, back-left, back-right, LFE-channel
        8-15: Reserved
        */
            // fill in ADTS data
            packet[0] = (byte) 0xFF;
            //packet[1] = (byte)0xF9;
            packet[1] = (byte) 0xF1;//解决ios 不能播放问题
            packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
            packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
            packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
            packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
            packet[6] = (byte) 0xFC;


        }

        /**
         * 释放资源
         */
        public void release() {
            if (mFileStream != null) {
                try {
                    mFileStream.flush();
                    mFileStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (mMediaCodec != null) {
                mMediaCodec.stop();
            }
        }
    }

    public double getVolume() {
        return mCurrentVolume;
    }

}
