#version 300 es
#extension GL_OES_EGL_image_external : require
#extension GL_OES_EGL_image_external_essl3 : require

precision mediump float;
uniform samplerExternalOES sTexture;//������������
in vec2 vTextureCoord; //���մӶ�����ɫ�������Ĳ���
out vec4 fragColor;

void main()
{
   //�����������
   vec4 color1 = texture(sTexture, vTextureCoord);
   fragColor = vec4(1. - color1.rgb, 1.);
}