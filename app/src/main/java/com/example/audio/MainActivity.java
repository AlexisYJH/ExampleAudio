package com.example.audio;

import static com.example.audio.Config.AUDIO_FORMAT;
import static com.example.audio.Config.CHANNEL_CONFIG;
import static com.example.audio.Config.SAMPLE_RATE_INHZ;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 0;
    private static final String RECORD_FILE_NAME = "test.pcm";
    private static final String RECORD_WAV_NAME = "test.wav";

    /**
     * 需要申请的运行时权限
     */
    private String[] mPermissions = new String[] {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * 被用户拒绝的权限列表
     */
    private ArrayList<String> mPermissionList = new ArrayList<>();
    private AudioRecord mAudioRecord;
    private boolean mIsRecording;
    private AudioTrack mAudioTrack;
    private PlayInModeStreamTask mPlayTask;
    private PlayInModeStaticTask mWavPlayTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        addOnClickListener(R.id.btn_record, R.id.btn_play, R.id.btn_convert, R.id.btn_play_wav);
        checkPermissions();
    }


    private void addOnClickListener(int... ids) {
        for (int i = 0; i < ids.length; i++) {
            Button button = findViewById(ids[i]);
            button.setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_record:
                Button btn_record = (Button) view;
                if (btn_record.getText().toString().equals(getString(R.string.start_record))) {
                    btn_record.setText(getString(R.string.stop_record));
                    startRecord();
                } else {
                    btn_record.setText(getString(R.string.start_record));
                    stopRecord();
                }
                break;

            case R.id.btn_play:
                Button btnPlay = (Button) view;
                if (btnPlay.getText().toString().equals(getString(R.string.start_play))) {
                    btnPlay.setText(getString(R.string.stop_play));
                    // 播放pcm
                    playInModeStream();
                } else {
                    stopPlayInModeStream();
                }
                break;
            case R.id.btn_convert:
                pcmToWav();
                break;
            case R.id.btn_play_wav:
                Button btnWav = (Button) view;
                if (btnWav.getText().toString().equals(getString(R.string.start_play_wav))) {
                    btnWav.setText(getString(R.string.stop_play));
                    // 播放wav文件
                    playInModeStatic();
                } else {
                    stopPlayInModeStatic();
                }
                break;
            default:
                break;
        }
    }

    private void startRecord() {
        // 获取buffer的大小
        final int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_INHZ, CHANNEL_CONFIG, AUDIO_FORMAT);
        Log.d(TAG, "startRecord: minBufferSize=" + minBufferSize);
        // 创建AudioRecord
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_INHZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize);
        // 初始化一个buffer
        final byte data[] = new byte[minBufferSize];
        final File file =  getFile(RECORD_FILE_NAME);
        if (!file.mkdirs()) {
            Log.w(TAG, "Directory not created");
        }
        Log.d(TAG, "file: " + file);
        if (file.exists()) {
            file.delete();
        }

        //开始录音
        mAudioRecord.startRecording();
        mIsRecording = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                FileOutputStream out = null;
                try {
                    out = new FileOutputStream(file);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "out: " + out);

                if (out != null) {
                    while (mIsRecording) {
                        int read = mAudioRecord.read(data, 0, minBufferSize);
                        // 如果读取音频数据没有出现错误，就将数据写入到文件
                        if (read != AudioRecord.ERROR_INVALID_OPERATION) {
                            try {
                                out.write(data);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    try {
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        }).start();

    }

    private File getFile(String fileName) {
        return new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), fileName);
    }


    private void stopRecord() {
        mIsRecording = false;
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
        }
    }

    private void pcmToWav(){
        File pcmFile =  getFile(RECORD_FILE_NAME);
        File mavFile =  getFile(RECORD_WAV_NAME);
        if (!mavFile.mkdirs()) {
            Log.w(TAG, "wavFile Directory not created");
        }
        if (mavFile.exists()) {
            mavFile.delete();
        }
        new PcmToWavUtil(SAMPLE_RATE_INHZ, CHANNEL_CONFIG, AUDIO_FORMAT)
                .pcmToWav(pcmFile.getAbsolutePath(), mavFile.getAbsolutePath());
        Toast.makeText(this, R.string.pcm_to_wav_finish, Toast.LENGTH_SHORT).show();
    }

    /**
     * 播放，使用stream模式
     */
    private void playInModeStream() {

        mPlayTask = new PlayInModeStreamTask(this);
        mPlayTask.execute();
    }

    private void playInModeStatic() {
        mWavPlayTask = new PlayInModeStaticTask(this);
        mWavPlayTask.execute();
    }


    private void stopPlayInModeStream() {
        stopPlay(R.id.btn_play, R.string.start_play);
    }

    private void stopPlayInModeStatic() {
        stopPlay(R.id.btn_play_wav, R.string.start_play_wav);
    }

    private void stopPlay(int id, int stringId) {
        Button button = findViewById(id);
        button.setText(getString(stringId));
        stopPlay();
    }

    /**
     * 停止播放
     */
    private void stopPlay() {
        if (mPlayTask != null) {
            mPlayTask.cancel(true);
            mPlayTask = null;
        }
        if (mWavPlayTask != null) {
            mWavPlayTask.cancel(true);
            mWavPlayTask = null;
        }
        if (mAudioTrack != null) {
            Log.d(TAG, "Stopping");
            mAudioTrack.stop();
            Log.d(TAG, "Releasing");
            mAudioTrack.release();
            Log.d(TAG, "Nulling");
            mAudioTrack = null;
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (int i = 0; i < mPermissions.length; i++) {
                if (ContextCompat.checkSelfPermission(this, mPermissions[i])
                        != PackageManager.PERMISSION_GRANTED) {
                    mPermissionList.add(mPermissions[i]);
                }
            }

            if (!mPermissionList.isEmpty()) {
                String[] permissions = mPermissionList.toArray(new String[mPermissionList.size()]);
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, permissions[i] + "权限被禁止");
                }
            }
        }
    }

    private static class PlayInModeStreamTask extends AsyncTask<Void, Void, Void> {
        WeakReference<MainActivity> activity;
        PlayInModeStreamTask(MainActivity activity) {
            this.activity = new WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() {
            if (activity == null || activity.get() == null) {
                return;
            }
            /*
             * SAMPLE_RATE_INHZ 对应pcm音频的采样率
             * channelConfig 对应pcm音频的声道
             * AUDIO_FORMAT 对应pcm音频的格式
             * */
            int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
            int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE_INHZ, channelConfig, AUDIO_FORMAT);
            activity.get().mAudioTrack = new AudioTrack(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE_INHZ)
                            .setEncoding(AUDIO_FORMAT)
                            .setChannelMask(channelConfig)
                            .build(),
                    minBufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
            activity.get().mAudioTrack.play();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (activity == null || activity.get() == null) {
                return null;
            }
            int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
            int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE_INHZ, channelConfig, AUDIO_FORMAT);
            File file = activity.get().getFile(RECORD_FILE_NAME);
            Log.d(TAG, "playInModeStream: " + file);
            try {
                FileInputStream in = new FileInputStream(file);
                try {
                    byte[] buffer = new byte[minBufferSize];
                    while (!isCancelled() && in.available() > 0) {
                        int readCount = in.read(buffer);
                        if (readCount == AudioTrack.ERROR_INVALID_OPERATION || readCount == AudioTrack.ERROR_BAD_VALUE) {
                            continue;
                        }
                        if (readCount != 0 && readCount != -1 && activity.get().mAudioTrack != null) {
                            activity.get().mAudioTrack.write(buffer, 0, readCount);
                        }
                    }
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            if (activity == null || activity.get() == null) {
                return;
            }
            activity.get().stopPlayInModeStream();
        }
    }

    private static class PlayInModeStaticTask extends AsyncTask<Void, Void, byte[]> {
        WeakReference<MainActivity> activity;
        PlayInModeStaticTask(MainActivity activity) {
            this.activity = new WeakReference<>(activity);
        }

        @Override
        protected byte[] doInBackground(Void... voids) {
            if (activity == null || activity.get() == null) {
                return null;
            }
            // static模式，需要将音频数据一次性write到AudioTrack的内部缓冲区
            // 读取wav数据
            File file = activity.get().getFile(RECORD_WAV_NAME);
            try {
                InputStream in = new FileInputStream(file);
                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    for (int b; (b= in.read()) != -1; ) {
                        out.write(b);
                    }
                    Log.d(TAG, "Got the data");
                    return out.toByteArray();
                } finally {
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(byte[] data) {
            if (activity == null || activity.get() == null) {
                return;
            }
            Log.i(TAG, "Creating track...audioData.length = " + data.length);
            activity.get().mAudioTrack = new AudioTrack(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE_INHZ)
                            .setEncoding(AUDIO_FORMAT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    data.length,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
            Log.d(TAG, "Writing audio data...");
            activity.get().mAudioTrack.write(data, 0, data.length);
            Log.d(TAG, "Starting playback");
            activity.get().mAudioTrack.play();
            Log.d(TAG, "Playing");
        }
    }
}