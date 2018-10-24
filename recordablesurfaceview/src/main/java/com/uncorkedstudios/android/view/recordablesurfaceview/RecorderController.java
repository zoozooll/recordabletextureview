package com.uncorkedstudios.android.view.recordablesurfaceview;

import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecorderController {

    private static final String TAG = "RecorderController";
    private MediaRecorder mMediaRecorder;
    private Surface mSurface;
    private AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private boolean recorderSet;

    public RecorderController() {
        mSurface = MediaCodec.createPersistentInputSurface();
    }

    public void initRecorder(File saveToFile, int displayWidth, int displayHeight,
                             int orientationHint, MediaRecorder.OnErrorListener errorListener,
                             MediaRecorder.OnInfoListener infoListener) throws IOException {

        MediaRecorder mediaRecorder = new MediaRecorder();

        mediaRecorder.setOnInfoListener(infoListener);

        mediaRecorder.setOnErrorListener(errorListener);

        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setInputSurface(mSurface);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setAudioSamplingRate(44100);
        mediaRecorder.setAudioEncodingBitRate(96000);

        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);

        mediaRecorder.setVideoEncodingBitRate(12000000);
        mediaRecorder.setVideoFrameRate(60);
        mediaRecorder.setVideoSize(displayWidth, displayHeight);

        mediaRecorder.setOrientationHint(orientationHint);

        mediaRecorder.setOutputFile(saveToFile.getPath());
        mediaRecorder.prepare();

        mMediaRecorder = mediaRecorder;
        recorderSet = true;
    }

    public boolean startRecording() {
        if (mMediaRecorder != null && !mIsRecording.get()) {
            boolean success = true;
            try {
                mMediaRecorder.start();
                mIsRecording.set(true);
            } catch (IllegalStateException e) {
                Log.e(TAG, "startRecording failed");
                success = false;
                mIsRecording.set(false);
                mMediaRecorder.reset();
                mMediaRecorder.release();
            }
            return success;
        } else {
            return false;
        }
    }

    public boolean stopRecording() {
        if (mMediaRecorder != null &&  mIsRecording.get()) {
            boolean success = true;
            try {
                mMediaRecorder.stop();
                mIsRecording.set(false);
            } catch (RuntimeException e) {
                Log.e(TAG, "stopRecording failed");
                success = false;
            } finally {
                mMediaRecorder.release();
            }
            recorderSet = false;
            return success;
        }
        return false;
    }

    public boolean isRecording() {
        return  mIsRecording.get();
    }

    public Surface getRecorderSurface() {
        return mSurface;
    }

    public boolean isRecorderSet() {
        return recorderSet;
    }

    public void releaseRecorder() {
        mSurface.release();
        mSurface = null;
        mMediaRecorder.release();
        mMediaRecorder = null;
        recorderSet = false;
    }
}
