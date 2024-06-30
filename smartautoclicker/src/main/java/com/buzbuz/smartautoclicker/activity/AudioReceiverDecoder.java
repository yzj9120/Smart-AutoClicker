package com.buzbuz.smartautoclicker.activity;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

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

public class AudioReceiverDecoder implements NERtcCallback {
    private static final String TAG = "AudioReceiverDecoder::::";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    private static final int TIMEOUT_US = 10000;

    private MediaCodec mediaCodec;
    private AudioTrack audioTrack;
    private byte[] audioOutTempBuf;

    public void start() {

        try {
            MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1);
            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mediaCodec.configure(format, null, null, 0);
            mediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        audioTrack = new AudioTrack(12, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE, AudioTrack.MODE_STREAM);
        audioTrack.play();
    }

    private long startMs;

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


    private void initAudioTrack() {
        int sampleRate = 44100; // 示例采样率
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO; // 示例通道配置
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT; // 示例采样位数

        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        audioOutTempBuf = new byte[minBufferSize];

        audioTrack = new AudioTrack(
                12,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize,
                AudioTrack.MODE_STREAM
        );
        audioTrack.play();
    }


    private void playAudioFrame(NERtcAudioFrame audioFrame) {

        try {
            byte[] buffer = new byte[audioFrame.getData().remaining()];
            audioFrame.getData().get(buffer);
            int ret = audioTrack.write(buffer, 0, buffer.length);
            if (ret > 0) {
                audioTrack.play();
            } else {
                Log.d(TAG, "error code is " + ret);
            }
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
