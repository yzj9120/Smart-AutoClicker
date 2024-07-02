package com.buzbuz.smartautoclicker.play;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.concurrent.LinkedBlockingQueue;

public class AudioStreamPlayer {
    private static final int SAMPLE_RATE_INHZ = 44100;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int CHANNEL_OUT_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private AudioTrack audioTrack;
    private static final String TAG = "AudioStreamPlayer";
    private LinkedBlockingQueue<byte[]> audioQueue;
    private Thread playbackThread;
    private boolean isPlaying;
    public static final int CONTENT_TYPE_CUSTOM = AudioManager.STREAM_MUSIC; //

    public AudioStreamPlayer() {
        int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE_INHZ, CHANNEL_OUT_CONFIG, AUDIO_FORMAT);
        audioTrack = new AudioTrack(CONTENT_TYPE_CUSTOM, SAMPLE_RATE_INHZ, CHANNEL_OUT_CONFIG, AUDIO_FORMAT, 8 * minBufferSize, AudioTrack.MODE_STREAM);
        audioQueue = new LinkedBlockingQueue<>();
        isPlaying = true;
        startPlaybackThread();
        audioTrack.play();
    }

    private void startPlaybackThread() {
        playbackThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isPlaying) {
                    try {
                        byte[] buffer = audioQueue.take();
                        Log.d("playAudioData:", "buffer==" + buffer);
                        audioTrack.write(buffer, 0, buffer.length);
                    } catch (InterruptedException e) {

                        Log.d("playAudioData:", "buffer==错误" + e);
                        e.printStackTrace();
                    }
                }
            }
        });
        playbackThread.start();
    }

    public void playAudioData(byte[] audioData) {
        try {
            Log.d("playAudioData:", audioData.length + "");
            Thread.sleep(10);

            audioQueue.put(audioData);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void stopPlayback() {
        isPlaying = false;
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
            try {
                playbackThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
        }
    }
}
