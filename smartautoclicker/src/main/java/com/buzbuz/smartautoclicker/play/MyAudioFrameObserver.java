package com.buzbuz.smartautoclicker.play;

import android.util.Log;

import com.netease.lava.nertc.sdk.audio.NERtcAudioFrame;
import com.netease.lava.nertc.sdk.audio.NERtcAudioFrameObserver;

public class MyAudioFrameObserver implements NERtcAudioFrameObserver {
    private AudioStreamPlayer audioStreamPlayer;

    public MyAudioFrameObserver(AudioStreamPlayer audioPlayer) {
        this.audioStreamPlayer = audioPlayer;
    }

    @Override
    public void onRecordFrame(NERtcAudioFrame audioFrame) {
//        byte[] buffer = new byte[audioFrame.getData().remaining()];
//        audioFrame.getData().get(buffer);
//        audioStreamPlayer.playAudioData(buffer);
    }

    @Override
    public void onRecordSubStreamAudioFrame(NERtcAudioFrame audioFrame) {


    }

    @Override
    public void onPlaybackFrame(NERtcAudioFrame audioFrame) {


    }

    @Override
    public void onPlaybackAudioFrameBeforeMixingWithUserID(long userID, NERtcAudioFrame audioFrame) {

    }

    @Override
    public void onPlaybackAudioFrameBeforeMixingWithUserID(long userID, NERtcAudioFrame audioFrame, long channelId) {

    }

    @Override
    public void onMixedAudioFrame(NERtcAudioFrame audioFrame) {
        byte[] buffer = new byte[audioFrame.getData().remaining()];
        audioFrame.getData().get(buffer);
        audioStreamPlayer.playAudioData(buffer);
    }

    @Override
    public void onPlaybackSubStreamAudioFrameBeforeMixingWithUserID(long userID, NERtcAudioFrame audioFrame, long channelId) {

    }
}
