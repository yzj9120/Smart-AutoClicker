package com.buzbuz.smartautoclicker.play;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

/**
 * static模式，需要将音频数据一次性write到AudioTrack的内部缓冲区
 */
public class AudioUtils {
    public static final int CONTENT_TYPE_CUSTOM = AudioManager.STREAM_MUSIC;

    public static final int SAMPLE_RATE_INHZ = 44100;
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    public static void playInModeStatic(Context context, String fileName) {
        new AudioLoadTask(context, fileName).execute();
    }

    private static class AudioLoadTask extends AsyncTask<Void, Void, File> {
        private final WeakReference<Context> contextReference;
        private final String fileName;

        AudioLoadTask(Context context, String fileName) {
            contextReference = new WeakReference<>(context);
            this.fileName = fileName;
        }

        @Override
        protected File doInBackground(Void... params) {
            Context context = contextReference.get();
            if (context == null) {
                return null;
            }
            return readAudioFileFromAssets(context, fileName);
        }

        @Override
        protected void onPostExecute(File audioFile) {
            Context context = contextReference.get();
            if (context == null || audioFile == null) {
                // Handle the error appropriately, e.g., show a message to the user
                return;
            }
            playAudioDataFromFile(audioFile);
        }
    }

    private static File readAudioFileFromAssets(Context context, String fileName) {
        File tempFile = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            AssetManager assetManager = context.getAssets();
            in = assetManager.open(fileName);
            tempFile = File.createTempFile("audioData", ".tmp", context.getCacheDir());
            out = new FileOutputStream(tempFile);
            byte[] buffer = new byte[1024]; // Read in 1 KB chunks
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace(); // Log the exception
            if (tempFile != null) {
                tempFile.delete(); // Delete the temp file in case of an error
            }
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace(); // Log the exception
            }
        }
        return tempFile;
    }

    public static void playAudioDataFromFile(File audioFile) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(audioFile);
            byte[] audioData = new byte[(int) audioFile.length()];
            fis.read(audioData);
            int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE_INHZ, AudioFormat.CHANNEL_OUT_STEREO, AUDIO_FORMAT);

            AudioTrack audioTrack = new AudioTrack(CONTENT_TYPE_CUSTOM, SAMPLE_RATE_INHZ,
                    AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize, AudioTrack.MODE_STATIC);

            audioTrack.write(audioData, 0, audioData.length);
            audioTrack.play();
        } catch (IOException e) {
            e.printStackTrace(); // Log the exception
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace(); // Log the exception
                }
            }
            // Delete the temporary file after use
            if (audioFile != null && audioFile.exists()) {
                audioFile.delete();
            }
        }
    }
}
