package com.example.android.opengl;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class SceneRenderer implements GLSurfaceView.Renderer {

    private Context context;
    private CanvasRectangle texRect;//纹理三角形对象引用
    private int textureId;

    public SceneRenderer(Context context) {
        this.context = context;
    }

    public void onDrawFrame(GL10 gl) {
        Log.d("aaron", "onDrawFrame");
        //清除深度缓冲与颜色缓冲
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT | GLES30.GL_COLOR_BUFFER_BIT);
        //绘制纹理三角形
        if (texRect != null) texRect.drawSelf();
        GLUtil.checkGlError("onDrawFrame Error");
    }

    public void onSurfaceChanged(GL10 gl, int width, int height) {
        //设置视窗大小及位置
        GLES30.glViewport(0, 0, width, height);
        //计算GLSurfaceView的宽高比
        float ratio = (float) width / height;
        //调用此方法计算产生透视投影矩阵
        MatrixState.setOrthoFrustum(-(width >> 1), (width >> 1), -(height >> 1), height >> 1, 1, 10);
        //调用此方法产生摄像机9参数位置矩阵
        MatrixState.setCamera(0, 0, 3, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
        texRect = new CanvasRectangle(context, width, height);
    }

    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        //设置屏幕背景色RGBA
        GLES30.glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
        //创建三角形对对象
        //打开深度检测
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        //初始化纹理
        /*Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.mipmap.image);
        int srcTexture = GLUtil.initTexture(bitmap);
        int dstTexture = GLUtil.initEmptyTexture(bitmap.getWidth(), bitmap.getHeight());
        GLUtil.copyTexture(srcTexture, dstTexture, bitmap.getWidth(), bitmap.getHeight());
        bitmap.recycle();
        textureId = dstTexture;*/
        //关闭背面剪裁
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLUtil.checkGlError("onSurfaceCreated Error");
    }
}