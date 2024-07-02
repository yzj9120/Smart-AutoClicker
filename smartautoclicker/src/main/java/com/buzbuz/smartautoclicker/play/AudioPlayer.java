package com.buzbuz.smartautoclicker.play;
import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.io.IOException;
import java.io.InputStream;

/**
 * 动态写入
 */
public class AudioPlayer {
    private static final int SAMPLE_RATE_INHZ = 44100;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static final int CONTENT_TYPE_CUSTOM = 12; //

    private AudioTrack audioTrack;
    private Thread playbackThread;

    public void startPlayback(Context context, String fileName) {
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        final int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE_INHZ, channelConfig, AUDIO_FORMAT);

         audioTrack = new AudioTrack(CONTENT_TYPE_CUSTOM, SAMPLE_RATE_INHZ,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                8* minBufferSize, AudioTrack.MODE_STREAM);

        audioTrack.play();

        AssetManager assetManager = context.getAssets();
        try {
            InputStream inputStream = assetManager.open(fileName);
            playbackThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        byte[] tempBuffer = new byte[minBufferSize];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(tempBuffer)) > 0) {
                            audioTrack.write(tempBuffer, 0, bytesRead);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            inputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            playbackThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopPlayback() {
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
        }
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }
    }
}
