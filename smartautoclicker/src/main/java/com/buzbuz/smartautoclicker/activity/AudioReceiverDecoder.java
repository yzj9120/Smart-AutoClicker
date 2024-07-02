package com.buzbuz.smartautoclicker.activity;

import static android.media.AudioTrack.PLAYSTATE_PLAYING;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import androidx.annotation.NonNull;

import com.netease.lava.nertc.sdk.NERtcCallback;
import com.netease.lava.nertc.sdk.NERtcEx;
import com.netease.lava.nertc.sdk.NERtcUserJoinExtraInfo;
import com.netease.lava.nertc.sdk.NERtcUserLeaveExtraInfo;
import com.netease.lava.nertc.sdk.audio.NERtcAudioFrame;
import com.netease.lava.nertc.sdk.audio.NERtcAudioFrameObserver;
import com.netease.lava.nertc.sdk.audio.NERtcAudioFrameOpMode;
import com.netease.lava.nertc.sdk.audio.NERtcAudioFrameRequestFormat;

import java.io.IOException;
import java.nio.ByteBuffer;

import okio.ByteString;

public class AudioReceiverDecoder implements NERtcCallback {
    private static final String TAG = "AudioReceiverDecoder::::";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    private static final int TIMEOUT_US = 10000;

    private MediaCodec mediaCodec;
    private AudioTrack audioTrack;
    private AudioTrack mAudioTrack;
    private long bufferCount;

    /**
     * 音频流类型  AudioManager.STREAM_MUSIC;
     */
    private static final int mStreamType =12;
    /**
     * 指定采样率 （MediaRecoder 的采样率通常是8000Hz AAC的通常是44100Hz。
     * 设置采样率为44100，目前为常用的采样率，官方文档表示这个值可以兼容所有的设置）
     */
    private static final int mSampleRateInHz = 16000;
    /**
     * 指定捕获音频的声道数目。在AudioFormat类中指定用于此的常量
     */
    private static final int mChannelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO; //单声道

    /**
     * 指定音频量化位数 ,在AudioFormaat类中指定了以下各种可能的常量。
     * 通常我们选择ENCODING_PCM_16BIT和ENCODING_PCM_8BIT PCM代表的是脉冲编码调制，它实际上是原始音频样本。
     * 因此可以设置每个样本的分辨率为16位或者8位，16位将占用更多的空间和处理能力,表示的音频也更加接近真实。
     */
    private static final int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;

    /**
     * 指定缓冲区大小。调用AudioTrack类的getMinBufferSize方法可以获得。
     */
    private int mMinBufferSize;

    /**
     * STREAM的意思是由用户在应用程序通过write方式把数据一次一次得写到audiotrack中。
     * 这个和我们在socket中发送数据一样，
     * 应用层从某个地方获取数据，例如通过编解码得到PCM数据，然后write到audiotrack。
     */
    private static int mMode = AudioTrack.MODE_STREAM;

    private static final int BUFFER_CAPITAL = 10;



    public void setMediaComm() {
        initAudioTrack();
        NERtcEx.getInstance().setAudioFrameObserver(new NERtcAudioFrameObserver() {

            ///SDK 收到输入的采集数据和播放的音频数据时
            @Override
            public void onRecordFrame(NERtcAudioFrame audioFrame) {
                byte[] remaining = new byte[audioFrame.getData().remaining()];
               // Log.e(TAG, "onRecordFrame....." + remaining.length);


            }

            ///SDK 收到输入的采集数据和播放的音频数据时
            @Override
            public void onPlaybackFrame(NERtcAudioFrame audioFrame) {
                byte[] remaining = new byte[audioFrame.getData().remaining()];
                //Log.e(TAG, "onPlaybackFrame....." + remaining.length);
                playAudioFrame(audioFrame);

            }

            @Override
            public void onRecordSubStreamAudioFrame(NERtcAudioFrame audioFrame) {
               // byte[] remaining = new byte[audioFrame.getData().remaining()];
               // Log.e(TAG, "onRecordSubStreamAudioFrame....." + remaining);


            }


            @Override
            public void onPlaybackAudioFrameBeforeMixingWithUserID(long userID, NERtcAudioFrame audioFrame) {
                byte[] remaining = new byte[audioFrame.getData().remaining()];
               // Log.e(TAG, "onPlaybackAudioFrameBeforeMixingWithUserID....." + remaining);
//                playAudioFrame(audioFrame);
            }

            //收到某一远端用户播放的音频数据时，
            @Override
            public void onPlaybackAudioFrameBeforeMixingWithUserID(long userID, NERtcAudioFrame audioFrame, long channelId) {
             //   byte[] remaining = new byte[audioFrame.getData().remaining()];
              //  Log.e(TAG, "aaaaaaa....." + remaining);
//                playAudioFrame(audioFrame);

            }

            @Override
            public void onPlaybackSubStreamAudioFrameBeforeMixingWithUserID(long userID, NERtcAudioFrame audioFrame, long channelId) {
                byte[] remaining = new byte[audioFrame.getData().remaining()];
               // Log.e(TAG, "bbbbbb....." + remaining);
//                playAudioFrame(audioFrame);

            }

            // 收到音频采集与播放混合后数据帧时
            @Override
            public void onMixedAudioFrame(NERtcAudioFrame audioFrame) {
                byte[] remaining = new byte[audioFrame.getData().remaining()];
               // Log.e(TAG, "onMixedAudioFrame....." + remaining);
                //decodeAndPlayAudioFrame(audioFrame);
            }

        });

    }


//    private void initAudioTrack() {
//        int sampleRate = 8000; // 示例采样率
//        int channelConfig = AudioFormat.CHANNEL_OUT_MONO; // 示例通道配置
//        int audioFormat = AudioFormat.ENCODING_PCM_16BIT; // 示例采样位数
//
//        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
//
//        audioTrack = new AudioTrack(
//                12,
//                sampleRate,
//                channelConfig,
//                audioFormat,
//                minBufferSize,
//                AudioTrack.MODE_STREAM
//        );
//       // audioTrack.play();
//    }
    private void initAudioTrack() {
        //根据采样率，采样精度，单双声道来得到frame的大小。
        //计算最小缓冲区 *10
        mMinBufferSize = AudioTrack.getMinBufferSize(mSampleRateInHz, mChannelConfig, mAudioFormat);
        //注意，按照数字音频的知识，这个算出来的是一秒钟buffer的大小。
        mAudioTrack = new AudioTrack(mStreamType, mSampleRateInHz, mChannelConfig,
                mAudioFormat, mMinBufferSize * BUFFER_CAPITAL, mMode);
        audioTrack.play();
    }

    public synchronized void write(@NonNull final ByteString bytes) {
        if (null != mAudioTrack) {
            int byteSize = bytes.size();
            bufferCount += byteSize;
            int write = mAudioTrack.write(bytes.toByteArray(), 0, bytes.size());
            if (write == 0 ) {
                //由于缓存的缘故，会先把缓存的bytes填满再播放，当write=0的时候存在没有播完的情况
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public void setBufferParams(int pcmFileSize) {
        //设置缓冲的大小 为PCM文件大小的10%
        if (pcmFileSize < mMinBufferSize * BUFFER_CAPITAL) {
            mAudioTrack = new AudioTrack(mStreamType, mSampleRateInHz, mChannelConfig,
                    mAudioFormat, mMinBufferSize, mMode);
        } else {
            //缓存大小为PCM文件大小的10%，如果小于mMinBufferSize * BUFFER_CAPITAL，则按默认值设置
            int cacheFileSize = (int) (pcmFileSize * 0.1);
            int realBufferSize = (cacheFileSize / mMinBufferSize + 1) * mMinBufferSize;
            if (realBufferSize < mMinBufferSize * BUFFER_CAPITAL) {
                realBufferSize=mMinBufferSize * BUFFER_CAPITAL;
            }
            mAudioTrack = new AudioTrack(mStreamType, mSampleRateInHz, mChannelConfig,
                    mAudioFormat, realBufferSize, mMode);
        }
        bufferCount = 0;
    }

    private void playAudioFrame(NERtcAudioFrame audioFrame) {

        try {
            byte[] buffer = new byte[audioFrame.getData().remaining()];
            write(ByteString.of(buffer));
//            audioFrame.getData().get(buffer);
//
//
//            int ret = audioTrack.write(buffer, 0, buffer.length);
//            if (ret > 0) {
//                audioTrack.play();
//            } else {
//                Log.d(TAG, "error code is " + ret);
//            }
        }catch (Exception e){
            Log.d(TAG, "===e=== " + e);
        }

    }



    @Override
    public void onJoinChannel(int result, long channelId, long elapsed, long uid) {

    }

    @Override
    public void onLeaveChannel(int result) {

    }

    @Override
    public void onUserJoined(long uid) {

    }

    @Override
    public void onUserJoined(long uid, NERtcUserJoinExtraInfo joinExtraInfo) {

    }

    @Override
    public void onUserLeave(long uid, int reason) {

    }

    @Override
    public void onUserLeave(long uid, int reason, NERtcUserLeaveExtraInfo leaveExtraInfo) {

    }

    @Override
    public void onUserAudioStart(long uid) {

    }

    @Override
    public void onUserAudioStop(long uid) {

    }

    @Override
    public void onUserVideoStart(long uid, int maxProfile) {

    }

    @Override
    public void onUserVideoStop(long uid) {

    }

    @Override
    public void onDisconnect(int reason) {

    }

    @Override
    public void onClientRoleChange(int oldRole, int newRole) {

    }


}
