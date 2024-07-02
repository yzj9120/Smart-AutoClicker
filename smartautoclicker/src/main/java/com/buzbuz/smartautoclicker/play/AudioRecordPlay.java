package com.buzbuz.smartautoclicker.play;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

public class AudioRecordPlay {
    private static final int SAMPLE_RATE_INHZ = 44100;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT_CONFIG = AudioFormat.CHANNEL_OUT_MONO;

    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private boolean isRecording;
    private Thread recordingThread;

    public AudioRecordPlay(Context context) {
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_INHZ, CHANNEL_IN_CONFIG, AUDIO_FORMAT);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Permission to record audio not granted");
        }
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE_INHZ, CHANNEL_IN_CONFIG, AUDIO_FORMAT, minBufferSize);
        audioTrack = new AudioTrack(12, SAMPLE_RATE_INHZ,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize, AudioTrack.MODE_STREAM);


        isRecording = true;
    }

    public void startRecording() {
        audioRecord.startRecording();
        audioTrack.play();

        recordingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                writeAudioData();
            }
        });
        recordingThread.start();
    }

    private void writeAudioData() {
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_INHZ, CHANNEL_IN_CONFIG, AUDIO_FORMAT);
        byte[] buffer = new byte[minBufferSize];
        Log.d("huangzhen::::::", buffer.length +"");
        while (isRecording) {
            int readBytes = audioRecord.read(buffer, 0, buffer.length);

            Log.d("huangzhen:::::readBytes:", buffer.length +"");

            if (readBytes > 0) {
                audioTrack.write(buffer, 0, readBytes);
            }
        }
    }

    public void stopRecording() {
        if (audioRecord != null) {
            isRecording = false;
            audioRecord.stop();
            audioTrack.stop();
            audioRecord.release();
            audioTrack.release();
            audioRecord = null;
            audioTrack = null;

            if (recordingThread != null) {
                recordingThread.interrupt();
                recordingThread = null;
            }
        }
    }
}
