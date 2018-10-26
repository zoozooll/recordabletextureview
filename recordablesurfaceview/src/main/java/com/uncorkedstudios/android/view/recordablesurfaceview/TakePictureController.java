package com.uncorkedstudios.android.view.recordablesurfaceview;

import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

public class TakePictureController {

    private static final String TAG = "TakePictureController";
    private ImageReader mImageReader;

    public TakePictureController(int width, int height) {
        mImageReader = ImageReader.newInstance(width, height,
                PixelFormat.RGBA_8888,2);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);
    }

    public Surface getSurface() {
        return mImageReader.getSurface();
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            Log.d(TAG, "onImageAvailable ");
            image.close();
        }

    };
}
