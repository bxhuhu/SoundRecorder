package com.cree.soundrecorder.sound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

import androidx.annotation.RequiresApi;

/**
 * Title:
 * Description:
 * Copyright:Copyright(c)2020
 * Company: Cree
 * CreateTime:2020/5/28  22:26
 *
 * @author cree
 * @version 1.0
 */
public class AudioRecordManagerMP4 {
    private final int sampleRateInHz = 44100;//44100 16000
    private final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private String audioFilePath;
    private String tag = "AudioRecordManager";

    private class AudioData {
        private ByteBuffer buffer;
        private int size;
    }

    private AudioRecorder mAudioRecorder;
    private AudioEncorder mAudioEncorder;

    private ArrayBlockingQueue queue;

    /**
     * @param audioFilePath 文件名
     */
    public AudioRecordManagerMP4(String audioFilePath) {
        this.audioFilePath = audioFilePath;

        queue = new ArrayBlockingQueue<>(1024);
        mAudioRecorder = new AudioRecorder();
        mAudioEncorder = new AudioEncorder();
    }

    public void startRecord() {
        if (mAudioRecorder == null) {
            mAudioRecorder = new AudioRecorder();
        }
        mAudioRecorder.start();
        if (mAudioEncorder != null && !mAudioEncorder.isEncording) {
            mAudioEncorder.start();
        }
    }

    public void stopRecord() {
        mAudioRecorder.stopRecording();
        mAudioEncorder.stopEncording();
    }

    public void pauseRecord() {
        mAudioRecorder.stopRecording();
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
                Log.i(tag, "录制耗时-毫秒==" + b);
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
    public class AudioEncorder extends Thread {

        private MediaCodec mEncorder;
        private Boolean isEncording = false;
        private int minBufferSize;
        private int trackIndex;

        MediaMuxer mediaMuxer;
        private MediaFormat mFormat;

        public AudioEncorder() {
            initEncorder();
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void run() {
            super.run();
            isEncording = true;
            startEncording();
        }

        /**
         * 初始化编码器
         */
        private void initEncorder() {
            minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat) * 2;// 乘以2 加大缓冲区，防止其他意外
            try {
                mEncorder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRateInHz, 1);
            mFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
            mFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            mFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
            mFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBufferSize * 4);
//            mFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
            mEncorder.configure(mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }


        /**
         * 开始编码
         */
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        public void startEncording() {
            if (mEncorder == null) {
                return;
            }
            long startTime = System.nanoTime();

            mEncorder.start();
            try {
                mediaMuxer = new MediaMuxer(audioFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
                AudioData audioData;

                trackIndex = mediaMuxer.addTrack(mEncorder.getOutputFormat());
                mediaMuxer.start();
                while (isEncording) {
                    long a = System.currentTimeMillis();
                    // 从队列中取出录音的一帧音频数据
                    audioData = getAudioData();

                    if (audioData == null) {
                        continue;
                    }

                    // 取出InputBuffer，填充音频数据，然后输送到编码器进行编码
                    int inputBufferIndex = mEncorder.dequeueInputBuffer(0);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = mEncorder.getInputBuffer(inputBufferIndex);
                        inputBuffer.clear();
                        inputBuffer.put(audioData.buffer);
                        long startTimeUS = (System.nanoTime() - startTime) / 1000;
                        mEncorder.queueInputBuffer(inputBufferIndex, 0, audioData.size, startTimeUS, 0);
                    }

                    int outputBufferIndex = mEncorder.dequeueOutputBuffer(mBufferInfo, 0);
                    while (outputBufferIndex >= 0 && mBufferInfo.size != 0) {
                        ByteBuffer outputBuffer = mEncorder.getOutputBuffer(outputBufferIndex);
                        int outBufferSize = outputBuffer.limit();
                        byte[] aacBytes = new byte[outBufferSize];
                        outputBuffer.get(aacBytes, 0, outputBuffer.limit());
                        ByteBuffer allocate = ByteBuffer.allocate(outBufferSize);
                        allocate.put(aacBytes);
                        allocate.rewind();
                        mediaMuxer.writeSampleData(trackIndex, outputBuffer, mBufferInfo);

                        mEncorder.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = mEncorder.dequeueOutputBuffer(mBufferInfo, 0);
                    }

                    long b = System.currentTimeMillis() - a;
                    Log.i(tag, "编码耗时-毫秒==" + b);
                }
                release();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * 停止编码
         */
        public void stopEncording() {
            isEncording = false;
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
         * 释放资源
         */
        public void release() {

            if (mEncorder != null) {
                mEncorder.stop();
            }
            if (mediaMuxer != null) {
                mediaMuxer.stop();
                mediaMuxer.release();
                mediaMuxer = null;
            }
        }
    }

}
