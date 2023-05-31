package com.falch.textureview;

import static android.opengl.EGL14.EGL_DEFAULT_DISPLAY;
import static javax.microedition.khronos.egl.EGL10.EGL_NO_CONTEXT;
import static javax.microedition.khronos.egl.EGL10.EGL_SUCCESS;

import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class TestView extends LinearLayout implements TextureView.SurfaceTextureListener {
    class RenderThread extends Thread {
        private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private static final int EGL_OPENGL_ES2_BIT = 4;

        boolean isStopped = false;

        SurfaceTexture mSurface;

        RenderThread(SurfaceTexture surface) {
            mSurface = surface;
        }

        private int[] getConfig() {
            return new int[] {
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 0,
                    EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_NONE
            };
        }

        @Override
        public void run() {
            super.run();

            EGL10 egl = (EGL10) EGLContext.getEGL();
            EGLDisplay eglDisplay = egl.eglGetDisplay(EGL_DEFAULT_DISPLAY);

            int[] version = new int[2];
            egl.eglInitialize(eglDisplay, version);   // getting OpenGL ES 2
            EGLConfig eglConfig = chooseEglConfig(egl, eglDisplay);

            int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
            EGLContext eglContext = egl.eglCreateContext(eglDisplay, eglConfig,
                    EGL_NO_CONTEXT, attrib_list);

            EGLSurface eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, mSurface, null);

            float colorVelocity = 0.01f;
            float color = 0.5f;

            egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);

            while (!isStopped && egl.eglGetError() == EGL_SUCCESS) {

                if (color > 1 || color < 0) colorVelocity *= -1;
                color += colorVelocity;

                GLES20.glClearColor(color / 2, color, color, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                egl.eglSwapBuffers(eglDisplay, eglSurface);

                Log.i("SkiaBaseView", "Render using renderer thread.");

                try {
                    Thread.sleep((int) (1f / 60f * 1000f)); // in real life this sleep is more complicated
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            mSurface.release();
            egl.eglDestroyContext(eglDisplay, eglContext);
            egl.eglDestroySurface(eglDisplay, eglSurface);
        }

        private EGLConfig chooseEglConfig(EGL10 egl, EGLDisplay eglDisplay) {
            int[] configsCount = new int[1];
            EGLConfig[] configs = new EGLConfig[1];
            int[] configSpec = getConfig();

            if (!egl.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
                throw new IllegalArgumentException("eglChooseConfig failed " +
                        GLUtils.getEGLErrorString(egl.eglGetError()));
            } else if (configsCount[0] > 0) {
                return configs[0];
            }
            return null;
        }
    }

    private TextureView mTextureView;

    private Button mColorView;

    private RenderThread renderer;

    public TestView(Context context) {
        super(context);

        Button btn = new Button(context);
        btn.setText("Toggle");
        btn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTextureView == null) {
                    createTextureView();
                } else {
                    removeTextureView();
                }
            }
        });
        addView(btn);
    }

    void createTextureView() {
        mColorView = new Button(getContext().getApplicationContext());
        mColorView.setBackgroundColor(Color.parseColor("#FF0000"));
        addView(mColorView);

        mTextureView = new TextureView(getContext().getApplicationContext());
        addView(mTextureView);

        mTextureView.setSurfaceTextureListener(this);
        mTextureView.setOpaque(false);

        //mTextureView.layout(50, 50, 50, this.getMeasuredHeight()-50);
        //mColorView.layout(50, 50, 50, this.getMeasuredHeight()-50);
    }

    void removeTextureView() {
        removeView(mTextureView);
        removeView(mColorView);
        mTextureView = null;
        mColorView = null;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.i("SkiaBaseView", "onSurfaceTextAvailable - rendering OpenGL");
        renderer = new RenderThread(surface);
        renderer.start();
        Log.i("SkiaBaseView", "onSurfaceTextAvailable - done.");
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        renderer.isStopped = true;
        return false;                // surface.release() manually, after the last render
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // Nothing special to do here
    }
}
