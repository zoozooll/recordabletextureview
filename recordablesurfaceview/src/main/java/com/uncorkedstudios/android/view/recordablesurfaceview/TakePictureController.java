package com.uncorkedstudios.android.view.recordablesurfaceview;

import android.graphics.ImageFormat;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

public class TakePictureController {

    private static final String TAG = "TakePictureController";
    private ImageReader mImageReader;

    public TakePictureController(int width, int height, Handler mBackgroundHandler) {
        mImageReader = ImageReader.newInstance(width, height,
                ImageFormat.JPEG,2);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
    }

    public Surface getSurface() {
        return mImageReader.getSurface();
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "onImageAvailable ");
        }

    };
}
