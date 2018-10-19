package com.example.android.opengl;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

//纹理四边形
public class CanvasRectangle {

    private Context mContext;
    int mProgram;//自定义渲染管线程序id
    int muMVPMatrixHandle;//总变换矩阵引用
    int maPositionHandle; //顶点位置属性引用
    int maTexCoorHandle; //顶点纹理坐标属性引用
    String mVertexShader;//顶点着色器代码脚本
    String mFragmentShader;//片元着色器代码脚本

    FloatBuffer mVertexBuffer;//顶点坐标数据缓冲
    FloatBuffer mTexCoorBuffer;//顶点纹理坐标数据缓冲
    int vCount = 0;

    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private int mGlTexture;
    private Canvas mSurfaceCanvas;

    private float width, height;

    private CameraController cameraController;

    public CanvasRectangle(Context context, int width, int height) {
        mContext = context;
        this.width = width;
        this.height = height;
        //初始化顶点数据的方法
        initVertexData();
        //初始化着色器的方法
        initShader(context.getResources());

        initCustomCanvasTexture();
    }

    private void initCustomCanvasTexture() {
        mGlTexture = createTexture();
        if (mGlTexture > 0){
            //attach the texture to a surface.
            //It's a clue class for rendering an android view to gl level
            //(2)创建SurfaceTexture，包含刚刚创建的texture
            mSurfaceTexture = new SurfaceTexture(mGlTexture);
            mSurfaceTexture.setDefaultBufferSize((int)width, (int)height);
            //(3) 创建Surface 跟SurfaceTexture结合
            mSurface = new Surface(mSurfaceTexture);
        }

        /*if (mSurface != null) {
            try {
                //跟SurfaceView的获得canvas的方法一样，通过lockCanvas获得Canvas
                mSurfaceCanvas = mSurface.lockCanvas(null);
                Paint paint = new Paint();
                paint.setColor(Color.RED);
                mSurfaceCanvas.drawRect(100, 200, 400, 300, paint);
                //然后通过unlockCanvasAndPost释放
                mSurface.unlockCanvasAndPost(mSurfaceCanvas);
            }catch (Exception e){
                Log.e(TAG, "error while rendering view to gl: " + e);
            }
        }*/
        cameraController = new CameraController(mContext);
        cameraController.setPreviewSurface(mSurface);
        cameraController.startBackgroundThread();
        cameraController.openCamera((int)width, (int)height);
    }

    //初始化顶点数据的方法
    public void initVertexData() {
        //顶点坐标数据的初始化================begin============================
        vCount = 6;
        float halfWidth = width * 0.5f;
        float halfHeight = height * 0.5f;
        float vertices[] = new float[]
                {
                        -1.f * halfWidth, 1.f * halfHeight, 0,
                        -1.f * halfWidth, -1.f * halfHeight, 0,
                        1.f * halfWidth, -1.f * halfHeight, 0,

                        1.f * halfWidth, -1.f * halfHeight, 0,
                        1.f * halfWidth, 1.f * halfHeight, 0,
                        -1.f * halfWidth, 1.f * halfHeight, 0
                };

        //创建顶点坐标数据缓冲
        //vertices.length*4是因为一个整数四个字节
        ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length * 4);
        vbb.order(ByteOrder.nativeOrder());//设置字节顺序
        mVertexBuffer = vbb.asFloatBuffer();//转换为Float型缓冲
        mVertexBuffer.put(vertices);//向缓冲区中放入顶点坐标数据
        mVertexBuffer.position(0);//设置缓冲区起始位置
        //特别提示：由于不同平台字节顺序不同数据单元不是字节的一定要经过ByteBuffer
        //转换，关键是要通过ByteOrder设置nativeOrder()，否则有可能会出问题
        //顶点坐标数据的初始化================end============================

        //顶点纹理坐标数据的初始化================begin============================
        float texCoor[] = new float[]//顶点颜色值数组，每个顶点4个色彩值RGBA
                {
                        0,0, 0,1, 1,1,
                        1,1, 1,0, 0,0
                };
        //创建顶点纹理坐标数据缓冲
        ByteBuffer cbb = ByteBuffer.allocateDirect(texCoor.length * 4);
        cbb.order(ByteOrder.nativeOrder());//设置字节顺序
        mTexCoorBuffer = cbb.asFloatBuffer();//转换为Float型缓冲
        mTexCoorBuffer.put(texCoor);//向缓冲区中放入顶点纹理数据
        mTexCoorBuffer.position(0);//设置缓冲区起始位置
        //特别提示：由于不同平台字节顺序不同数据单元不是字节的一定要经过ByteBuffer
        //转换，关键是要通过ByteOrder设置nativeOrder()，否则有可能会出问题
        //顶点纹理坐标数据的初始化================end============================

    }

    //初始化着色器
    public void initShader(Resources mRes) {
        //加载顶点着色器的脚本内容
        mVertexShader = GLUtil.loadFromAssetsFile("vertex.glsl", mRes);
        //加载片元着色器的脚本内容
        mFragmentShader = GLUtil.loadFromAssetsFile("canvas.frag", mRes);
        //基于顶点着色器与片元着色器创建程序
        mProgram = GLUtil.createProgram(mVertexShader, mFragmentShader);
        //获取程序中顶点位置属性引用
        maPositionHandle = GLES30.glGetAttribLocation(mProgram, "aPosition");
        //获取程序中顶点纹理坐标属性引用  
        maTexCoorHandle = GLES30.glGetAttribLocation(mProgram, "aTexCoor");
        //获取程序中总变换矩阵引用
        muMVPMatrixHandle = GLES30.glGetUniformLocation(mProgram, "uMVPMatrix");
    }

    public void drawSelf() {

        synchronized (this){
            // update texture。需要执行这个，才能更新最新的canvas
            // Huawei手机注意，申请canvas有关的texture，一定要在所有其他texture之前
            mSurfaceTexture.updateTexImage();
        }
        //指定使用某套shader程序
        GLES30.glUseProgram(mProgram);

        MatrixState.setInitStack();

        //将最终变换矩阵传入渲染管线
        GLES30.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, MatrixState.getFinalMatrix(), 0);
        //将顶点位置数据传送进渲染管线
        GLES30.glVertexAttribPointer
                (
                        maPositionHandle,
                        3,
                        GLES30.GL_FLOAT,
                        false,
                        3 * 4,
                        mVertexBuffer
                );
        //将顶点纹理坐标数据传送进渲染管线
        GLES30.glVertexAttribPointer
                (
                        maTexCoorHandle,
                        2,
                        GLES30.GL_FLOAT,
                        false,
                        2 * 4,
                        mTexCoorBuffer
                );
        //允许顶点位置数据数组
        GLES30.glEnableVertexAttribArray(maPositionHandle);//启用顶点位置数据
        GLES30.glEnableVertexAttribArray(maTexCoorHandle);//启用顶点纹理坐标数据
        //绑定纹理
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);//设置使用的纹理编号
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mGlTexture);//绑定指定的纹理id
        //以三角形的方式填充
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vCount);
    }

    private void releaseSurface(){
        if(mSurface != null){
            mSurface.release();
        }
        if(mSurfaceTexture != null){
            mSurfaceTexture.release();
        }
        mSurface = null;
        mSurfaceTexture = null;

    }

    public void onDestroy() {
        releaseSurface();
        cameraController.closeCamera();
        cameraController.stopBackgroundThread();
    }

    private static int createTexture(){
        int[] textures = new int[1];

        // Generate the texture to where android view will be rendered
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glGenTextures(1, textures, 0);
        GLUtil.checkGlError("Texture generate");

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
        GLUtil.checkGlError("Texture bind");

        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER,GL10.GL_LINEAR);
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        return textures[0];
    }
}
