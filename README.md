# GLRecoder

A Video Recorder for OpenGL ES APP of Android.

Make sure your APP project compile with Android API level 18 or above.

## Usage:
1. Add this project as a library for your project.
1. Add below permissions in your AndroidManifest.xml:
```
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```
1. Import import com.research.GLRecorder.GLRecorder in your GLSurface Render.
1. Set EGLConfigChooser Provider by GLRecorder before setRender of GLSurfaceView:
```
        setEGLConfigChooser(GLRecorder.getEGLConfigChooser());
        setRenderer(YourRender);
```
1. Initialize GLRecorder at Surface size determine:
```
    private EGLConfig mEGLConfig;

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        mEGLConfig = config;
        // Some other code.
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLRecorder.init(width, height, mEGLConfig/*Assign in onSurfaceCreated method*/);
        GLRecorder.setRecordOutputFile("/sdcard/glrecord.mp4");     // Set output file path
        // Some other code.
}
```
1. Insert GLRecord.beginDraw() before your game begins drawing its frame, and GLRecord.endDraw() when your game has finished drawing its frame:
```
    GLRecorder.beginDraw();
    draw();     // Draw game frame
    GLRecorder.endDraw();
```
1. Start Recording and Stop it at appropriate time:
```
    GLRecorder.startRecording();    // Call it when game start, or specical user event trigger.
    
    GLRecorder.stopRecording();     // Call it when game over, or paused and so on.
```
1. At the last, your will find a output file in above initialize path.

## Have fun!
