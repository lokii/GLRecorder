package com.research.GLRecorder;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.text.TextUtils;
import android.util.Log;
import com.research.GLRecorder.gles.*;

import javax.microedition.khronos.egl.*;
import java.io.File;
import java.io.IOException;

/**
 * Created by kanedong on 14-10-10.
 */
public class GLRecorder {
    private static final String TAG = "GLRecorder";

    // TODO: don't use hard code file name.
    public static final String RECORD_OUTPUT_FILE = "/sdcard/glrecord.mp4";
    // Extention for surface record
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;

    private static FullFrameRect mFullScreen;
    private static int mOffscreenTexture;
    private static int mFramebuffer;
    private static float[] mIdentityMatrix;

    private static EGLSurface mWindowSurface;
    private static TextureMovieEncoder2 mVideoEncoder;
    private static WindowSurface mInputWindowSurface;
    private static boolean mRecordingEnabled;
    public static EglCore mEglCore;
    private static File mOutputFile;
    // TODO: mActivity.findViewById(R.id.content).getSize
//    private static WeakReference<Activity> mActivityRef;

    private static int mWindowWidth, mWindowHeight;
    private static long mTick;
    private static String mOutputPath;

    // A simple EGL config chooser for get recordable config.
    private static GLSurfaceView.EGLConfigChooser mDefaultConfigChooser = new GLSurfaceView.EGLConfigChooser() {
        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int renderableType = 4;

            // The actual surface is generally RGBA or RGBX, so situationally omitting alpha
            // doesn't really help.  It can also lead to a huge performance hit on glReadPixels()
            // when reading into a GL_RGBA buffer.
            int[] attribList = {
                    EGL11.EGL_RED_SIZE, 8,
                    EGL11.EGL_GREEN_SIZE, 8,
                    EGL11.EGL_BLUE_SIZE, 8,
                    EGL11.EGL_ALPHA_SIZE, 8,
                    //EGL11.EGL_DEPTH_SIZE, 16,
                    //EGL11.EGL_STENCIL_SIZE, 8,
                    EGL11.EGL_RENDERABLE_TYPE, renderableType,
                    EGL_RECORDABLE_ANDROID, 1,      // set recordable [@-3]
                    EGL11.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!egl.eglChooseConfig(display, attribList, configs, configs.length,
                    numConfigs)) {
                return null;
            }
            return configs[0];
        }
    };

    public static void init(int w, int h, EGLConfig currentConfig) {
//        mActivityRef = new WeakReference<Activity>(activity);
        mEglCore = new EglCore(currentConfig);
        setup(w, h);
    }

    public static void beginDraw() {
        if (!mRecordingEnabled) return;
        // Make sure the video fps rate was half of game fps(usually about 30fps)
        if (mTick % 2 == 0) return;

        // Render offscreen.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFramebuffer);
        GlUtil.checkGlError("glBindFramebuffer");
    }

    public static void endDraw() {
        if (!mRecordingEnabled) return;
        if (mTick % 2 == 0) {
            ++mTick;
            return;
        }
        ++mTick;

        // Blit to display.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GlUtil.checkGlError("glBindFramebuffer");
        mFullScreen.drawFrame(mOffscreenTexture, mIdentityMatrix);

        // Blit to encoder.
        mVideoEncoder.frameAvailableSoon();
        mInputWindowSurface.makeCurrent();
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);    // again, only really need to
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);     //  clear pixels outside rect
        mFullScreen.drawFrame(mOffscreenTexture, mIdentityMatrix);
        mInputWindowSurface.setPresentationTime(System.nanoTime());    // TODO
        mInputWindowSurface.swapBuffers();

        mEglCore.makeCurrent(mWindowSurface);
    }

    public static void setRecordOutputFile(String filePath) {
        if (!TextUtils.isEmpty(filePath) && !filePath.equals(mOutputPath)) {
            mOutputPath = filePath;
            mOutputFile = new File(mOutputPath);
        }
    }

    public static void startRecording() {
        setRecordingEnabled(true);
    }

    public static void stopRecording() {
        setRecordingEnabled(false);
    }

    public static GLSurfaceView.EGLConfigChooser getEGLConfigChooser() {
        return mDefaultConfigChooser;
    }

    /**
     * Updates the recording state.  Stops or starts recording as needed.
     */
    private static void setRecordingEnabled(boolean enabled) {
        if (enabled == mRecordingEnabled) {
            return;
        }
        if (enabled) {
            startEncoder();
        } else {
            stopEncoder();
        }
        mRecordingEnabled = enabled;
    }

    private static void setup(int w, int h) {
        if (null != mIdentityMatrix) return;
        Log.d(TAG, "Setup GLRecorder");

        setRecordOutputFile(RECORD_OUTPUT_FILE);    // Set default output path.

        mIdentityMatrix = new float[16];
        Matrix.setIdentityM(mIdentityMatrix, 0);

        mWindowSurface = mEglCore.getCurrentSurface();

        // Used for blitting texture to FBO.
        mFullScreen = new FullFrameRect(
                new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D));

/*        Activity activity = mActivityRef.get();
        if (null != activity) {
            View contentView = activity.findViewById(android.R.id.content);
            int w = 0;
            int h = 0;
            if (null != contentView) {
                w = contentView.getWidth();
                h = contentView.getHeight();
            }
            if (0 == w || 0 == h) {
                w = activity.getResources().getDisplayMetrics().widthPixels;
                h = activity.getResources().getDisplayMetrics().heightPixels;
            }
        }*/
        mWindowWidth = w;
        mWindowHeight = h;
        prepareFramebuffer(w, h);
    }

    /**
     * Creates the video encoder object and starts the encoder thread.  Creates an EGL
     * surface for encoder input.
     */
    private static void startEncoder() {
        Log.d(TAG, "starting to record");
        // Record video size same as surface, regardless of the window dimensions.
        // The encoder may explode if given "strange" dimensions, e.g. a width that is not a multiple
        // of 16.  We can box it as needed to preserve dimensions.
        final int BIT_RATE = 4000000;   // 4Mbps
        final int VIDEO_WIDTH = mWindowWidth;
        final int VIDEO_HEIGHT = mWindowHeight;

        VideoEncoderCore encoderCore;
        try {
            encoderCore = new VideoEncoderCore(VIDEO_WIDTH, VIDEO_HEIGHT,
                    BIT_RATE, mOutputFile);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        mInputWindowSurface = new WindowSurface(mEglCore, encoderCore.getInputSurface(), true);
        mVideoEncoder = new TextureMovieEncoder2(encoderCore);
    }

    /**
     * Stops the video encoder if it's running.
     */
    private static void stopEncoder() {
        if (mVideoEncoder != null) {
            Log.d(TAG, "stopping recorder, mVideoEncoder=" + mVideoEncoder);
            mVideoEncoder.stopRecording();
            // TODO: wait (briefly) until it finishes shutting down so we know file is
            //       complete, or have a callback that updates the UI
            mVideoEncoder = null;
        }
        if (mInputWindowSurface != null) {
            mInputWindowSurface.release();
            mInputWindowSurface = null;
        }
    }

    /**
     * Prepares the off-screen framebuffer.
     */
    private static void prepareFramebuffer(int width, int height) {
        GlUtil.checkGlError("prepareFramebuffer start");

        int[] values = new int[1];

        // Create a texture object and bind it.  This will be the color buffer.
        GLES20.glGenTextures(1, values, 0);
        GlUtil.checkGlError("glGenTextures");
        mOffscreenTexture = values[0];   // expected > 0
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mOffscreenTexture);
        GlUtil.checkGlError("glBindTexture " + mOffscreenTexture);

        // Create texture storage.
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        // Set parameters.  We're probably using non-power-of-two dimensions, so
        // some values may not be available for use.
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GlUtil.checkGlError("glTexParameter");

        // Create framebuffer object and bind it.
        GLES20.glGenFramebuffers(1, values, 0);
        GlUtil.checkGlError("glGenFramebuffers");
        mFramebuffer = values[0];    // expected > 0
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFramebuffer);
        GlUtil.checkGlError("glBindFramebuffer " + mFramebuffer);

        // Create a depth buffer and bind it.
        GLES20.glGenRenderbuffers(1, values, 0);
        GlUtil.checkGlError("glGenRenderbuffers");
        int mDepthBuffer = values[0];
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, mDepthBuffer);
        GlUtil.checkGlError("glBindRenderbuffer " + mDepthBuffer);

        // Allocate storage for the depth buffer.
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16,
                width, height);
        GlUtil.checkGlError("glRenderbufferStorage");

        // Attach the depth buffer and the texture (color buffer) to the framebuffer object.
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                GLES20.GL_RENDERBUFFER, mDepthBuffer);
        GlUtil.checkGlError("glFramebufferRenderbuffer");
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mOffscreenTexture, 0);
        GlUtil.checkGlError("glFramebufferTexture2D");

        // See if GLES is happy with all this.
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer not complete, status=" + status);
        }

        // Switch back to the default framebuffer.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        GlUtil.checkGlError("prepareFramebuffer done");
    }
}
