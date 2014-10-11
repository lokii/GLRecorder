package com.research.GLRecorder.gles;

import android.graphics.SurfaceTexture;
import android.opengl.EGLExt;
import android.util.Log;
import android.view.Surface;

import javax.microedition.khronos.egl.*;
import javax.microedition.khronos.opengles.GL;

/**
 * Created by kanedong on 14-10-10.
 */
public class EglCore {
    private static final String TAG = "EglCore";
    private EGL10 mEgl;
    private final EGLContext mEGLContext;
//    private EGLDisplay mEGLDisplay;
    private EGLConfig mEGLConfig;

    public EglCore() {
        this(null);
    }

    public EglCore(EGLConfig config) {
        mEgl = (EGL10) EGLContext.getEGL();
//        mEGLDisplay = mEgl.eglGetCurrentDisplay();
        mEGLContext = mEgl.eglGetCurrentContext();
        if (null != config) {
            mEGLConfig = config;
        } /*else {
            int[] result = new int[1];
            mEgl.eglQueryContext(mEGLDisplay, mEGLContext, EGL11.EGL_CONFIG_ID, result);
            EGLConfig[] configs = new EGLConfig[1];
            int[] num_config = new int[1];
            mEgl.eglChooseConfig(mEGLDisplay,
                    new int[] { EGL11.EGL_CONFIG_ID, result[0]},
                    configs, 1, num_config);
            mEGLConfig = configs[0];
        }*/
    }

    public GL getGL() {
        return mEgl.eglGetCurrentContext().getGL();
    }

    /**
     * Destroys the specified surface.  Note the EGLSurface won't actually be destroyed if it's
     * still current in a context.
     */
    public void releaseSurface(EGLSurface eglSurface) {
        mEgl.eglDestroySurface(mEgl.eglGetCurrentDisplay(), eglSurface);
    }

    /**
     * Creates an EGL surface associated with a Surface.
     * <p>
     * If this is destined for MediaCodec, the EGLConfig should have the "recordable" attribute.
     */
    public EGLSurface createWindowSurface(Object surface) {
        if (!(surface instanceof Surface) && !(surface instanceof SurfaceTexture)) {
            throw new RuntimeException("invalid surface: " + surface);
        }

        // Create a window surface, and attach it to the Surface we received.
        int[] surfaceAttribs = {
                EGL10.EGL_NONE
        };
        EGLSurface eglSurface = mEgl.eglCreateWindowSurface(mEgl.eglGetCurrentDisplay(), mEGLConfig, surface,
                surfaceAttribs);
        checkEglError("eglCreateWindowSurface");
        if (eglSurface == null) {
            throw new RuntimeException("surface was null");
        }
        return eglSurface;
    }

    /**
     * Creates an EGL surface associated with an offscreen buffer.
     */
    public EGLSurface createOffscreenSurface(int width, int height) {
        int[] surfaceAttribs = {
                EGL11.EGL_WIDTH, width,
                EGL11.EGL_HEIGHT, height,
                EGL11.EGL_NONE
        };
        EGLSurface eglSurface = mEgl.eglCreatePbufferSurface(mEgl.eglGetCurrentDisplay(), mEGLConfig,
                surfaceAttribs);
        checkEglError("eglCreatePbufferSurface");
        if (eglSurface == null) {
            throw new RuntimeException("surface was null");
        }
        return eglSurface;
    }

    /**
     * Makes our EGL context current, using the supplied surface for both "draw" and "read".
     */
    public void makeCurrent(EGLSurface eglSurface) {
        EGLDisplay display = mEgl.eglGetCurrentDisplay();
        if (display == EGL11.EGL_NO_DISPLAY) {
            // called makeCurrent() before create?
            Log.d(TAG, "NOTE: makeCurrent w/o display");
        }
        if (!mEgl.eglMakeCurrent(display, eglSurface, eglSurface, mEgl.eglGetCurrentContext())) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    /**
     * Makes our EGL context current, using the supplied "draw" and "read" surfaces.
     */
    public void makeCurrent(EGLSurface drawSurface, EGLSurface readSurface) {
        EGLDisplay display = mEgl.eglGetCurrentDisplay();
        if (display == EGL11.EGL_NO_DISPLAY) {
            // called makeCurrent() before create?
            Log.d(TAG, "NOTE: makeCurrent w/o display");
        }
        if (!mEgl.eglMakeCurrent(display, drawSurface, readSurface, mEgl.eglGetCurrentContext())) {
            throw new RuntimeException("eglMakeCurrent(draw,read) failed");
        }
    }

    /**
     * Makes no context current.
     */
    public void makeNothingCurrent() {
        if (!mEgl.eglMakeCurrent(mEgl.eglGetCurrentDisplay(), EGL11.EGL_NO_SURFACE, EGL11.EGL_NO_SURFACE,
                EGL11.EGL_NO_CONTEXT)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    /**
     * Calls eglSwapBuffers.  Use this to "publish" the current frame.
     *
     * @return false on failure
     */
    public boolean swapBuffers(EGLSurface eglSurface) {
        return mEgl.eglSwapBuffers(mEgl.eglGetCurrentDisplay(), eglSurface);
    }

    /**
     * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
     */
    public void setPresentationTime(EGLSurface eglSurface, long nsecs) {
//        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, eglSurface, nsecs);
    }

    /**
     * Returns true if our context and the specified surface are current.
     */
    public boolean isCurrent(EGLSurface eglSurface) {
        return mEGLContext.equals(mEgl.eglGetCurrentContext()) &&
                eglSurface.equals(mEgl.eglGetCurrentSurface(EGL11.EGL_DRAW));
    }

    /**
     * Performs a simple surface query.
     */
    public int querySurface(EGLSurface eglSurface, int what) {
        int[] value = new int[1];
        mEgl.eglQuerySurface(mEgl.eglGetCurrentDisplay(), eglSurface, what, value);
        return value[0];
    }

    /**
     * Queries a string value.
     */
    public String queryString(int what) {
        return mEgl.eglQueryString(mEgl.eglGetCurrentDisplay(), what);
    }

    /**
     * Returns the GLES version this context is configured for (currently 2 or 3).
     */
    public int getGlVersion() {
        return 2;
    }

    /**
     * Writes the current display, context, and surface to the log.
     */
    public void logCurrent(String msg) {
        EGLDisplay display;
        EGLContext context;
        EGLSurface surface;

        display = mEgl.eglGetCurrentDisplay();
        context = mEgl.eglGetCurrentContext();
        surface = mEgl.eglGetCurrentSurface(EGL11.EGL_DRAW);
        Log.i(TAG, "Current EGL (" + msg + "): display=" + display + ", context=" + context +
                ", surface=" + surface);
    }

    public EGLSurface getCurrentSurface() {
        return mEgl.eglGetCurrentSurface(EGL11.EGL_DRAW);
    }

    /**
     * Checks for EGL errors.  Throws an exception if an error has been raised.
     */
    private void checkEglError(String msg) {
        int error;
        if ((error = mEgl.eglGetError()) != EGL11.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }
}
