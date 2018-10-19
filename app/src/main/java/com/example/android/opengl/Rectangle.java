package com.example.android.opengl;

import android.content.res.Resources;
import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

//�����ı���
public class Rectangle {
    int mProgram;//�Զ�����Ⱦ���߳���id
    int muMVPMatrixHandle;//�ܱ任��������
    int maPositionHandle; //����λ����������
    int maTexCoorHandle; //��������������������
    String mVertexShader;//������ɫ������ű�
    String mFragmentShader;//ƬԪ��ɫ������ű�

    FloatBuffer mVertexBuffer;//�����������ݻ���
    FloatBuffer mTexCoorBuffer;//���������������ݻ���
    int vCount = 0;

    private float width, height;

    public Rectangle(Resources res, int width, int height) {
        this.width = width;
        this.height = height;
        //��ʼ���������ݵķ���
        initVertexData();
        //��ʼ����ɫ���ķ���
        initShader(res);
    }

    //��ʼ���������ݵķ���
    public void initVertexData() {
        //�����������ݵĳ�ʼ��================begin============================
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

        //���������������ݻ���
        //vertices.length*4����Ϊһ�������ĸ��ֽ�
        ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length * 4);
        vbb.order(ByteOrder.nativeOrder());//�����ֽ�˳��
        mVertexBuffer = vbb.asFloatBuffer();//ת��ΪFloat�ͻ���
        mVertexBuffer.put(vertices);//�򻺳����з��붥����������
        mVertexBuffer.position(0);//���û�������ʼλ��
        //�ر���ʾ�����ڲ�ͬƽ̨�ֽ�˳��ͬ���ݵ�Ԫ�����ֽڵ�һ��Ҫ����ByteBuffer
        //ת�����ؼ���Ҫͨ��ByteOrder����nativeOrder()�������п��ܻ������
        //�����������ݵĳ�ʼ��================end============================

        //���������������ݵĳ�ʼ��================begin============================
        float texCoor[] = new float[]//������ɫֵ���飬ÿ������4��ɫ��ֵRGBA
                {
                        0,0, 0,1, 1,1,
                        1,1, 1,0, 0,0
                };
        //�������������������ݻ���
        ByteBuffer cbb = ByteBuffer.allocateDirect(texCoor.length * 4);
        cbb.order(ByteOrder.nativeOrder());//�����ֽ�˳��
        mTexCoorBuffer = cbb.asFloatBuffer();//ת��ΪFloat�ͻ���
        mTexCoorBuffer.put(texCoor);//�򻺳����з��붥����������
        mTexCoorBuffer.position(0);//���û�������ʼλ��
        //�ر���ʾ�����ڲ�ͬƽ̨�ֽ�˳��ͬ���ݵ�Ԫ�����ֽڵ�һ��Ҫ����ByteBuffer
        //ת�����ؼ���Ҫͨ��ByteOrder����nativeOrder()�������п��ܻ������
        //���������������ݵĳ�ʼ��================end============================

    }

    //��ʼ����ɫ��
    public void initShader(Resources res) {
        //���ض�����ɫ���Ľű�����
        mVertexShader = GLUtil.loadFromAssetsFile("vertex.glsl", res);
        //����ƬԪ��ɫ���Ľű�����
        mFragmentShader = GLUtil.loadFromAssetsFile("frag.glsl", res);
        //���ڶ�����ɫ����ƬԪ��ɫ����������
        mProgram = GLUtil.createProgram(mVertexShader, mFragmentShader);
        //��ȡ�����ж���λ����������
        maPositionHandle = GLES30.glGetAttribLocation(mProgram, "aPosition");
        //��ȡ�����ж�������������������  
        maTexCoorHandle = GLES30.glGetAttribLocation(mProgram, "aTexCoor");
        //��ȡ�������ܱ任��������
        muMVPMatrixHandle = GLES30.glGetUniformLocation(mProgram, "uMVPMatrix");
    }

    public void drawSelf(int texId) {
        //ָ��ʹ��ĳ��shader����
        GLES30.glUseProgram(mProgram);

        MatrixState.setInitStack();

        //�����ձ任��������Ⱦ����
        GLES30.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, MatrixState.getFinalMatrix(), 0);
        //������λ�����ݴ��ͽ���Ⱦ����
        GLES30.glVertexAttribPointer
                (
                        maPositionHandle,
                        3,
                        GLES30.GL_FLOAT,
                        false,
                        3 * 4,
                        mVertexBuffer
                );
        //�����������������ݴ��ͽ���Ⱦ����
        GLES30.glVertexAttribPointer
                (
                        maTexCoorHandle,
                        2,
                        GLES30.GL_FLOAT,
                        false,
                        2 * 4,
                        mTexCoorBuffer
                );
        //������λ����������
        GLES30.glEnableVertexAttribArray(maPositionHandle);//���ö���λ������
        GLES30.glEnableVertexAttribArray(maTexCoorHandle);//���ö���������������
        //������
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);//����ʹ�õ�������
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId);//��ָ��������id
        //�������εķ�ʽ���
         GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vCount);
    }
}
