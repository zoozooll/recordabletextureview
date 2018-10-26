package com.uncorkedstudios.android.view.recordablesurfaceview;

import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.opengles.GL10;

import static com.uncorkedstudios.android.view.recordablesurfaceview.RecordableTextureView.LOG_PAUSE_RESUME;
import static com.uncorkedstudios.android.view.recordablesurfaceview.RecordableTextureView.LOG_RENDERER;
import static com.uncorkedstudios.android.view.recordablesurfaceview.RecordableTextureView.LOG_RENDERER_DRAW_FRAME;
import static com.uncorkedstudios.android.view.recordablesurfaceview.RecordableTextureView.LOG_SURFACE;
import static com.uncorkedstudios.android.view.recordablesurfaceview.RecordableTextureView.LOG_THREADS;
import static com.uncorkedstudios.android.view.recordablesurfaceview.RecordableTextureView.RENDERMODE_CONTINUOUSLY;
import static com.uncorkedstudios.android.view.recordablesurfaceview.RecordableTextureView.RENDERMODE_WHEN_DIRTY;

/**
 * A generic GL Thread. Takes care of initializing EGL and GL. Delegates
 * to a Renderer instance to do the actual drawing. Can be configured to
 * render continuously or on request.
 * <p>
 * All potentially blocking synchronization is done through the
 * RecordableTextureView.sGLThreadManager object. This avoids multiple-lock ordering issues.
 */
public class GLThread extends Thread {

    // Once the thread is started, all accesses to the following member
    // variables are protected by the RecordableTextureView.sGLThreadManager monitor
    private boolean mShouldExit;
    boolean mExited;
    private boolean mRequestPaused;
    private boolean mPaused;
    private boolean mHasSurface;
    private boolean mSurfaceIsBad;
    private boolean mWaitingForSurface;
    private boolean mHaveEglContext;
    private boolean mHaveEglSurface;
    private boolean mShouldReleaseEglContext;
    private int mWidth;
    private int mHeight;
    private int mRenderMode;
    private boolean mRequestRender;
    private boolean mRenderComplete;
    private ArrayList<Runnable> mEventQueue = new ArrayList<Runnable>();
    private boolean mSizeChanged = true;

    // End of member variables protected by the RecordableTextureView.sGLThreadManager monitor.

    private EglHelper mEglHelper;

    /**
     * Set once at thread construction time, nulled out when the parent view is garbage
     * called. This weak reference allows the RecordableTextureView to be garbage collected while
     * the GLThread is still alive.
     */
    private WeakReference<RecordableTextureView> mGLSurfaceViewWeakRef;


    GLThread(WeakReference<RecordableTextureView> glSurfaceViewWeakRef) {
        super();
        mWidth = 0;
        mHeight = 0;
        mRequestRender = true;
        mRenderMode = RENDERMODE_CONTINUOUSLY;
        mGLSurfaceViewWeakRef = glSurfaceViewWeakRef;
    }

    @Override
    public void run() {
        setName("GLThread " + getId());
        if (LOG_THREADS) {
            Log.i("GLThread", "starting tid=" + getId());
        }

        try {
            guardedRun();
        } catch (InterruptedException e) {
            // fall thru and exit normally
        } finally {
            RecordableTextureView.sGLThreadManager.threadExiting(this);
        }
    }

    /*
     * This private method should only be called inside a
     * synchronized(RecordableTextureView.sGLThreadManager) block.
     */
    private void stopEglSurfaceLocked() {
        if (mHaveEglSurface) {
            mHaveEglSurface = false;
            mEglHelper.destroySurface();
        }
    }

    /*
     * This private method should only be called inside a
     * synchronized(RecordableTextureView.sGLThreadManager) block.
     */
    private void stopEglContextLocked() {
        if (mHaveEglContext) {
            mEglHelper.finish();
            mHaveEglContext = false;
            RecordableTextureView.sGLThreadManager.releaseEglContextLocked(this);
        }
    }

    private void guardedRun() throws InterruptedException {
        mEglHelper = new EglHelper(mGLSurfaceViewWeakRef);
        mHaveEglContext = false;
        mHaveEglSurface = false;
        try {
            GL10 gl = null;
            boolean createEglContext = false;
            boolean createEglSurface = false;
            boolean createGlInterface = false;
            boolean lostEglContext = false;
            boolean sizeChanged = false;
            boolean wantRenderNotification = false;
            boolean doRenderNotification = false;
            boolean askedToReleaseEglContext = false;
            boolean createRecordableSurface = false;
            boolean createImageReaderSurface = false;
            int w = 0;
            int h = 0;
            Runnable event = null;

            while (true) {
                synchronized (RecordableTextureView.sGLThreadManager) {
                    while (true) {
                        if (mShouldExit) {
                            return;
                        }

                        if (!mEventQueue.isEmpty()) {
                            event = mEventQueue.remove(0);
                            break;
                        }

                        // Update the pause state.
                        boolean pausing = false;
                        if (mPaused != mRequestPaused) {
                            pausing = mRequestPaused;
                            mPaused = mRequestPaused;
                            RecordableTextureView.sGLThreadManager.notifyAll();
                            if (LOG_PAUSE_RESUME) {
                                Log.i("GLThread", "mPaused is now " + mPaused + " tid=" + getId());
                            }
                        }

                        // Do we need to give up the EGL context?
                        if (mShouldReleaseEglContext) {
                            if (LOG_SURFACE) {
                                Log.i("GLThread", "releasing EGL context because asked to tid=" + getId());
                            }
                            stopEglSurfaceLocked();
                            stopEglContextLocked();
                            mShouldReleaseEglContext = false;
                            askedToReleaseEglContext = true;
                        }

                        // Have we lost the EGL context?
                        if (lostEglContext) {
                            stopEglSurfaceLocked();
                            stopEglContextLocked();
                            lostEglContext = false;
                        }

                        // When pausing, release the EGL surface:
                        if (pausing && mHaveEglSurface) {
                            if (LOG_SURFACE) {
                                Log.i("GLThread", "releasing EGL surface because paused tid=" + getId());
                            }
                            stopEglSurfaceLocked();
                        }

                        // When pausing, optionally release the EGL Context:
                        if (pausing && mHaveEglContext) {
                            RecordableTextureView view = mGLSurfaceViewWeakRef.get();
                            boolean preserveEglContextOnPause = view != null && view.mPreserveEGLContextOnPause;
                            if (!preserveEglContextOnPause || RecordableTextureView.sGLThreadManager.shouldReleaseEGLContextWhenPausing()) {
                                stopEglContextLocked();
                                if (LOG_SURFACE) {
                                    Log.i("GLThread", "releasing EGL context because paused tid=" + getId());
                                }
                            }
                        }

                        // When pausing, optionally terminate EGL:
                        if (pausing) {
                            if (RecordableTextureView.sGLThreadManager.shouldTerminateEGLWhenPausing()) {
                                mEglHelper.finish();
                                if (LOG_SURFACE) {
                                    Log.i("GLThread", "terminating EGL because paused tid=" + getId());
                                }
                            }
                        }

                        // Have we lost the SurfaceView surface?
                        if ((!mHasSurface) && (!mWaitingForSurface)) {
                            if (LOG_SURFACE) {
                                Log.i("GLThread", "noticed surfaceView surface lost tid=" + getId());
                            }
                            if (mHaveEglSurface) {
                                stopEglSurfaceLocked();
                            }
                            mWaitingForSurface = true;
                            mSurfaceIsBad = false;
                            RecordableTextureView.sGLThreadManager.notifyAll();
                        }

                        // Have we acquired the surface view surface?
                        if (mHasSurface && mWaitingForSurface) {
                            if (LOG_SURFACE) {
                                Log.i("GLThread", "noticed surfaceView surface acquired tid=" + getId());
                            }
                            mWaitingForSurface = false;
                            RecordableTextureView.sGLThreadManager.notifyAll();
                        }

                        if (doRenderNotification) {
                            if (LOG_SURFACE) {
                                Log.i("GLThread", "sending render notification tid=" + getId());
                            }
                            wantRenderNotification = false;
                            doRenderNotification = false;
                            mRenderComplete = true;
                            RecordableTextureView.sGLThreadManager.notifyAll();
                        }

                        // Ready to draw?
                        if (readyToDraw()) {

                            // If we don't have an EGL context, try to acquire one.
                            if (!mHaveEglContext) {
                                if (askedToReleaseEglContext) {
                                    askedToReleaseEglContext = false;
                                } else if (RecordableTextureView.sGLThreadManager.tryAcquireEglContextLocked(this)) {
                                    try {
                                        mEglHelper.start();
                                    } catch (RuntimeException t) {
                                        RecordableTextureView.sGLThreadManager.releaseEglContextLocked(this);
                                        throw t;
                                    }
                                    mHaveEglContext = true;
                                    createEglContext = true;

                                    RecordableTextureView.sGLThreadManager.notifyAll();
                                }
                            }

                            if (mHaveEglContext && !mHaveEglSurface) {
                                mHaveEglSurface = true;
                                createEglSurface = true;
                                createGlInterface = true;
                                sizeChanged = true;
                            }

                            if (mHaveEglSurface) {
                                if (mSizeChanged) {
                                    sizeChanged = true;
                                    w = mWidth;
                                    h = mHeight;
                                    wantRenderNotification = true;
                                    if (LOG_SURFACE) {
                                        Log.i("GLThread",
                                                "noticing that we want render notification tid="
                                                        + getId());
                                    }

                                    // Destroy and recreate the EGL surface.
                                    createEglSurface = true;
                                    RecordableTextureView view = mGLSurfaceViewWeakRef.get();
                                    if (view != null) {
                                        if (view.mRecorderController != null) {
                                            createRecordableSurface = true;
                                        }
                                        if (view.mImageReaderController != null) {
                                            createImageReaderSurface = true;
                                        }
                                    }
                                    mSizeChanged = false;
                                }
                                mRequestRender = false;
                                RecordableTextureView.sGLThreadManager.notifyAll();
                                break;
                            }

                            if (mHaveEglSurface) {
                                if (mEglHelper.mRecorderEglSurface == null) {
                                    RecordableTextureView view = mGLSurfaceViewWeakRef.get();
                                    if (view != null && view.mRecorderController != null) {
                                        createRecordableSurface = true;
                                    }
                                }
                                if (mEglHelper.mImageReaderEglSurface == null) {
                                    RecordableTextureView view = mGLSurfaceViewWeakRef.get();
                                    if (view != null && view.mImageReaderController != null) {
                                        createImageReaderSurface = true;
                                    }
                                }
                            }

                        }

                        // By design, this is the only place in a GLThread thread where we wait().
                        if (LOG_THREADS) {
                            Log.i("GLThread", "waiting tid=" + getId()
                                    + " mHaveEglContext: " + mHaveEglContext
                                    + " mHaveEglSurface: " + mHaveEglSurface
                                    + " mPaused: " + mPaused
                                    + " mHasSurface: " + mHasSurface
                                    + " mSurfaceIsBad: " + mSurfaceIsBad
                                    + " mWaitingForSurface: " + mWaitingForSurface
                                    + " mWidth: " + mWidth
                                    + " mHeight: " + mHeight
                                    + " mRequestRender: " + mRequestRender
                                    + " mRenderMode: " + mRenderMode);
                        }
                        RecordableTextureView.sGLThreadManager.wait();
                    } // end of inner looper
                } // end of synchronized(RecordableTextureView.sGLThreadManager)

                if (event != null) {
                    event.run();
                    event = null;
                    continue;
                }

                if (createEglSurface) {
                    if (LOG_SURFACE) {
                        Log.w("GLThread", "egl createSurface");
                    }
                    if (!mEglHelper.createSurface()) {
                        synchronized (RecordableTextureView.sGLThreadManager) {
                            mSurfaceIsBad = true;
                            RecordableTextureView.sGLThreadManager.notifyAll();
                        }
                        continue;
                    }
                    createEglSurface = false;
                }
                if (createRecordableSurface) {
                    mEglHelper.createRecordableSurface();
                    createRecordableSurface = false;
                }

                if (createImageReaderSurface) {
                    mEglHelper.createImageReaderSurface();
                    createImageReaderSurface = false;
                }
                if (!mEglHelper.makeCurrent(0)) {
                    synchronized (RecordableTextureView.sGLThreadManager) {
                        mSurfaceIsBad = true;
                        RecordableTextureView.sGLThreadManager.notifyAll();
                    }
                    continue;
                }

                if (createGlInterface) {
                    gl = (GL10) mEglHelper.createGL();

                    RecordableTextureView.sGLThreadManager.checkGLDriver(gl);
                    createGlInterface = false;
                }

                if (createEglContext) {
                    if (LOG_RENDERER) {
                        Log.w("GLThread", "onSurfaceCreated");
                    }
                    RecordableTextureView view = mGLSurfaceViewWeakRef.get();
                    if (view != null) {
                        view.mRenderer.onSurfaceCreated(gl, mEglHelper.mEglConfig);
                    }
                    createEglContext = false;
                }

                if (sizeChanged) {
                    if (LOG_RENDERER) {
                        Log.w("GLThread", "onSurfaceChanged(" + w + ", " + h + ")");
                    }
                    RecordableTextureView view = mGLSurfaceViewWeakRef.get();
                    if (view != null) {
                        view.mRenderer.onSurfaceChanged(gl, w, h);
                    }
                    sizeChanged = false;
                }

                if (LOG_RENDERER_DRAW_FRAME) {
                    Log.w("GLThread", "onDrawFrame tid=" + getId());
                }
                {
                    RecordableTextureView view = mGLSurfaceViewWeakRef.get();
                    if (view != null) {
                        boolean result;
                        //result = mEglHelper.makeCurrent(0);
                        {
                            view.mRenderer.onDrawFrame(gl);
                            int swapError = mEglHelper.swap(0);
                            switch (swapError) {
                                case EGL10.EGL_SUCCESS:
                                    break;
                                case EGL11.EGL_CONTEXT_LOST:
                                    if (LOG_SURFACE) {
                                        Log.i("GLThread", "egl context lost tid=" + getId());
                                    }
                                    lostEglContext = true;
                                    break;
                                default:
                                    // Other errors typically mean that the current surface is bad,
                                    // probably because the SurfaceView surface has been destroyed,
                                    // but we haven't been notified yet.
                                    // Log the error to help developers understand why rendering stopped.
                                    EglHelper.logEglErrorAsWarning("GLThread", "eglSwapBuffers", swapError);

                                    synchronized (RecordableTextureView.sGLThreadManager) {
                                        mSurfaceIsBad = true;
                                        RecordableTextureView.sGLThreadManager.notifyAll();
                                    }
                                    break;
                            }

                        }
                        if (view.mRecorderController != null && view.mRecorderController.isRecording()) {
                            result = mEglHelper.makeCurrent(1);
                            if (result) {
                                view.mRenderer.onDrawFrame(gl);
                                int swapError = mEglHelper.swap(1);
                                switch (swapError) {
                                    case EGL10.EGL_SUCCESS:
                                        break;
                                    case EGL11.EGL_CONTEXT_LOST:
                                        if (LOG_SURFACE) {
                                            Log.i("GLThread", "egl context lost tid=" + getId());
                                        }
                                        lostEglContext = true;
                                        break;
                                    default:
                                        // Other errors typically mean that the current surface is bad,
                                        // probably because the SurfaceView surface has been destroyed,
                                        // but we haven't been notified yet.
                                        // Log the error to help developers understand why rendering stopped.
                                        EglHelper.logEglErrorAsWarning("GLThread", "eglSwapBuffers", swapError);

                                        synchronized (RecordableTextureView.sGLThreadManager) {
                                            mSurfaceIsBad = true;
                                            RecordableTextureView.sGLThreadManager.notifyAll();
                                        }
                                        break;
                                }

                            }
                        }
                        if (view.mImageReaderController != null) {
                            result = mEglHelper.makeCurrent(2);
                            if (result) {
                                view.mRenderer.onDrawFrame(gl);
                                int swapError = mEglHelper.swap(2);
                                switch (swapError) {
                                    case EGL10.EGL_SUCCESS:
                                        break;
                                    case EGL11.EGL_CONTEXT_LOST:
                                        if (LOG_SURFACE) {
                                            Log.i("GLThread", "egl context lost tid=" + getId());
                                        }
                                        lostEglContext = true;
                                        break;
                                    default:
                                        // Other errors typically mean that the current surface is bad,
                                        // probably because the SurfaceView surface has been destroyed,
                                        // but we haven't been notified yet.
                                        // Log the error to help developers understand why rendering stopped.
                                        EglHelper.logEglErrorAsWarning("GLThread", "eglSwapBuffers", swapError);

                                        synchronized (RecordableTextureView.sGLThreadManager) {
                                            mSurfaceIsBad = true;
                                            RecordableTextureView.sGLThreadManager.notifyAll();
                                        }
                                        break;
                                }

                            }
                        }
                    }
                }

                if (wantRenderNotification) {
                    doRenderNotification = true;
                }
            } // end of outer looper

        } finally {
            /*
             * clean-up everything...
             */
            synchronized (RecordableTextureView.sGLThreadManager) {
                stopEglSurfaceLocked();
                stopEglContextLocked();
            }
        }
    }

    public boolean ableToDraw() {
        return mHaveEglContext && mHaveEglSurface && readyToDraw();
    }

    private boolean readyToDraw() {
        return (!mPaused) && mHasSurface && (!mSurfaceIsBad)
                && (mWidth > 0) && (mHeight > 0)
                && (mRequestRender || (mRenderMode == RENDERMODE_CONTINUOUSLY));
    }

    public void setRenderMode(int renderMode) {
        if (!((RENDERMODE_WHEN_DIRTY <= renderMode) && (renderMode <= RENDERMODE_CONTINUOUSLY))) {
            throw new IllegalArgumentException("renderMode");
        }
        synchronized (RecordableTextureView.sGLThreadManager) {
            mRenderMode = renderMode;
            RecordableTextureView.sGLThreadManager.notifyAll();
        }
    }

    public int getRenderMode() {
        synchronized (RecordableTextureView.sGLThreadManager) {
            return mRenderMode;
        }
    }

    public void requestRender() {
        synchronized (RecordableTextureView.sGLThreadManager) {
            mRequestRender = true;
            RecordableTextureView.sGLThreadManager.notifyAll();
        }
    }

    public void surfaceCreated() {
        synchronized (RecordableTextureView.sGLThreadManager) {
            if (LOG_THREADS) {
                Log.i("GLThread", "surfaceCreated tid=" + getId());
            }
            mHasSurface = true;
            RecordableTextureView.sGLThreadManager.notifyAll();
            while ((mWaitingForSurface) && (!mExited)) {
                try {
                    RecordableTextureView.sGLThreadManager.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void surfaceDestroyed() {
        synchronized (RecordableTextureView.sGLThreadManager) {
            if (LOG_THREADS) {
                Log.i("GLThread", "surfaceDestroyed tid=" + getId());
            }
            mHasSurface = false;
            RecordableTextureView.sGLThreadManager.notifyAll();
            while ((!mWaitingForSurface) && (!mExited)) {
                try {
                    RecordableTextureView.sGLThreadManager.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void onPause() {
        synchronized (RecordableTextureView.sGLThreadManager) {
            if (LOG_PAUSE_RESUME) {
                Log.i("GLThread", "onPause tid=" + getId());
            }
            mRequestPaused = true;
            RecordableTextureView.sGLThreadManager.notifyAll();
            while ((!mExited) && (!mPaused)) {
                if (LOG_PAUSE_RESUME) {
                    Log.i("Main thread", "onPause waiting for mPaused.");
                }
                try {
                    RecordableTextureView.sGLThreadManager.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void onResume() {
        synchronized (RecordableTextureView.sGLThreadManager) {
            if (LOG_PAUSE_RESUME) {
                Log.i("GLThread", "onResume tid=" + getId());
            }
            mRequestPaused = false;
            mRequestRender = true;
            mRenderComplete = false;
            RecordableTextureView.sGLThreadManager.notifyAll();
            while ((!mExited) && mPaused && (!mRenderComplete)) {
                if (LOG_PAUSE_RESUME) {
                    Log.i("Main thread", "onResume waiting for !mPaused.");
                }
                try {
                    RecordableTextureView.sGLThreadManager.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void onWindowResize(int w, int h) {
        synchronized (RecordableTextureView.sGLThreadManager) {
            mWidth = w;
            mHeight = h;
            mSizeChanged = true;
            mRequestRender = true;
            mRenderComplete = false;
            RecordableTextureView.sGLThreadManager.notifyAll();

            // Wait for thread to react to resize and render a frame
            while (!mExited && !mPaused && !mRenderComplete
                    && ableToDraw()) {
                if (LOG_SURFACE) {
                    Log.i("Main thread", "onWindowResize waiting for render complete from tid=" + getId());
                }
                try {
                    RecordableTextureView.sGLThreadManager.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void requestExitAndWait() {
        // don't call this from GLThread thread or it is a guaranteed
        // deadlock!
        synchronized (RecordableTextureView.sGLThreadManager) {
            mShouldExit = true;
            RecordableTextureView.sGLThreadManager.notifyAll();
            while (!mExited) {
                try {
                    RecordableTextureView.sGLThreadManager.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void requestReleaseEglContextLocked() {
        mShouldReleaseEglContext = true;
        RecordableTextureView.sGLThreadManager.notifyAll();
    }

    /**
     * Queue an "event" to be run on the GL rendering thread.
     *
     * @param r the runnable to be run on the GL rendering thread.
     */
    public void queueEvent(Runnable r) {
        if (r == null) {
            throw new IllegalArgumentException("r must not be null");
        }
        synchronized (RecordableTextureView.sGLThreadManager) {
            mEventQueue.add(r);
            RecordableTextureView.sGLThreadManager.notifyAll();
        }
    }



    public void deleteRecordableSurface() {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                mEglHelper.destroyRecorderSurface();
            }
        });
    }

    public void deleteImageReaderSurface() {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                mEglHelper.destroyImageReaderSurface();
            }
        });
    }
}