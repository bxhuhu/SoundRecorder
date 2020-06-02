package com.cree.soundrecorder.util;

/**
 * Title:
 * Description:
 * Copyright:Copyright(c)2020
 * Company: Cree
 * CreateTime:2020/6/1  23:19
 *
 * @author luyongjiang
 * @version 1.0
 */
public class ADTSHeadUtil {

    /**
     * Add ADTS header at the beginning of each and every AAC packet.
     * This is needed as MediaCodec encoder generates a packet of raw
     * AAC data.
     * <p>
     * Note the packetLen must count in the ADTS header itself !!! .
     * 注意，这里的packetLen参数为raw aac Packet Len + 7; 7 bytes adts header
     **/
    public static void addADTStoPacket(byte[] packet, int packetLen) {
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






}
