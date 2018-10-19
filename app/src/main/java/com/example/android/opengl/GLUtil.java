package com.example.android.opengl;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

//���ض���Shader��ƬԪShader�Ĺ�����
public class GLUtil {
    //�����ƶ�shader�ķ���
    public static int loadShader
    (
            int shaderType, //shader������  GLES30.GL_VERTEX_SHADER   GLES30.GL_FRAGMENT_SHADER
            String source   //shader�Ľű��ַ���
    ) {
        //����һ����shader
        int shader = GLES30.glCreateShader(shaderType);
        //�������ɹ������shader
        if (shader != 0) {
            //����shader��Դ����
            GLES30.glShaderSource(shader, source);
            //����shader
            GLES30.glCompileShader(shader);
            //��ű���ɹ�shader����������
            int[] compiled = new int[1];
            //��ȡShader�ı������
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {//������ʧ������ʾ������־��ɾ����shader
                Log.e("ES30_ERROR", "Could not compile shader " + shaderType + ":");
                Log.e("ES30_ERROR", GLES30.glGetShaderInfoLog(shader));
                GLES30.glDeleteShader(shader);
                shader = 0;
            }
        }
        return shader;
    }

    //����shader����ķ���
    public static int createProgram(String vertexSource, String fragmentSource) {
        //���ض�����ɫ��
        int vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }

        //����ƬԪ��ɫ��
        int pixelShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) {
            return 0;
        }

        //��������
        int program = GLES30.glCreateProgram();
        //�����򴴽��ɹ���������м��붥����ɫ����ƬԪ��ɫ��
        if (program != 0) {
            //������м��붥����ɫ��
            GLES30.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            //������м���ƬԪ��ɫ��
            GLES30.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");
            //���ӳ���
            GLES30.glLinkProgram(program);
            //������ӳɹ�program����������
            int[] linkStatus = new int[1];
            //��ȡprogram���������
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0);
            //������ʧ���򱨴�ɾ������
            if (linkStatus[0] != GLES30.GL_TRUE) {
                Log.e("ES30_ERROR", "Could not link program: ");
                Log.e("ES30_ERROR", GLES30.glGetProgramInfoLog(program));
                GLES30.glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }

    //���ÿһ�������Ƿ��д���ķ���
    public static void checkGlError(String op) {
        int error;
        while ((error = GLES30.glGetError()) != GLES30.GL_NO_ERROR) {
            Log.e("ES30_ERROR", op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }

    //��sh�ű��м���shader���ݵķ���
    public static String loadFromAssetsFile(String fname, Resources r) {
        String result = null;
        try {
            InputStream in = r.getAssets().open(fname);
            int ch = 0;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while ((ch = in.read()) != -1) {
                baos.write(ch);
            }
            byte[] buff = baos.toByteArray();
            baos.close();
            in.close();
            result = new String(buff, "UTF-8");
            result = result.replaceAll("\\r\\n", "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static int initTexture(Resources res,  int rid) {
        //��������ID
        int[] textures = new int[1];
        GLES30.glGenTextures
                (
                        1,          //����������id������
                        textures,   //����id������
                        0           //ƫ����
                );
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0]);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        //ͨ������������ͼƬ===============begin===================
        Bitmap bitmapTmp = BitmapFactory.decodeResource(res, rid);
        //ͨ������������ͼƬ===============end=====================

        //ʵ�ʼ���������Դ�
        GLUtils.texImage2D
                (
                        GLES30.GL_TEXTURE_2D, //��������
                        0,                      //����Ĳ�Σ�0��ʾ����ͼ��㣬�������Ϊֱ����ͼ
                        bitmapTmp,              //����ͼ��
                        0                      //����߿�ߴ�
                );
        bitmapTmp.recycle();          //������سɹ����ͷ��ڴ��е�����ͼ
        return textures[0];
    }

    public static int initTexture(Bitmap bitmapTmp) {
        //��������ID
        int[] textures = new int[1];
        GLES30.glGenTextures
                (
                        1,          //����������id������
                        textures,   //����id������
                        0           //ƫ����
                );
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0]);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

        //ʵ�ʼ���������Դ�
        GLUtils.texImage2D
                (
                        GLES30.GL_TEXTURE_2D, //��������
                        0,                      //����Ĳ�Σ�0��ʾ����ͼ��㣬�������Ϊֱ����ͼ
                        bitmapTmp,              //����ͼ��
                        0                      //����߿�ߴ�
                );
        return textures[0];
    }

    public static void copyTexture(int srcTexId, int dstTexId, int width, int height) {
        int[] fboId = new int[1];
        GLES30.glGenFramebuffers(1, fboId, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId[0]);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, srcTexId, 0);

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dstTexId);
        GLES30.glCopyTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 0, 0, width, height, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    public static int initEmptyTexture(int width, int height) {
        int[] textures = new int[1];
        GLES30.glGenTextures
                (
                        1,          //����������id������
                        textures,   //����id������
                        0           //ƫ����
                );
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0]);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        return textures[0];
    }
}
