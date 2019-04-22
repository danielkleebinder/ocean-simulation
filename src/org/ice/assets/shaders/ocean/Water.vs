#version 330

// Author: Daniel Kleebinder

//Input vertex data
in vec4 i_Vertex;
in vec3 i_Normal;
in vec4 i_TexCoord;
in vec4 i_Color;

//Matrices
uniform mat4 m_ModelMatrix;
uniform mat4 m_ViewMatrix;
uniform mat4 m_ProjectionMatrix;

mat4 m_ModelViewMatrix = m_ViewMatrix * m_ModelMatrix;
mat4 m_ModelViewProjectionMatrix = m_ProjectionMatrix * m_ModelViewMatrix;
mat4 m_ViewProjectionMatrix = m_ProjectionMatrix * m_ViewMatrix;

mat3 m_ModelNormalMatrix = mat3(m_ModelMatrix);
mat3 m_ModelViewNormalMatrix = mat3(m_ModelViewMatrix);

// Varying variables
smooth out vec4 v_TexCoord;
smooth out vec4 v_Position;
smooth out vec4 v_Color;

void main(void) {
    v_TexCoord = i_TexCoord;
    v_Position = i_Vertex;
    v_Color    = i_Color;
}