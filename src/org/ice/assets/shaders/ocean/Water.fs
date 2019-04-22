#version 400

#include "org/ice/assets/shaders/libraries/FragmentCore.glsl"
#include "org/ice/assets/shaders/libraries/PerlinNoise.glsl"

// Author: Daniel Kleebinder
// Fragment shader for shading the ocean.

// Uniforms
uniform mat3 m_ModelNormalMatrix;

uniform sampler2D m_NormalsFoldingMap;
uniform sampler2D m_FoamMap;

//uniform samplerCube m_SkyBox;

uniform vec4 m_LightColor;
uniform vec4 m_DiffuseColor;
uniform vec4 m_LowWaterColor;
uniform vec4 m_HighWaterColor;

uniform vec3 m_CameraPosition;
uniform vec3 m_LightPosition;

uniform float m_LightShininess;
uniform float m_Reflection;
uniform float m_Transparency;
uniform float m_Foam;
uniform float m_FoamHeightModification;

// Varying variables
smooth in vec4 e_TexCoord;
smooth in vec4 e_Position;
smooth in vec4 v_Color;

void main(void) {
    // Pre-Calculated Often Used Values
    vec3 normal = normalize(m_ModelNormalMatrix * texture(m_NormalsFoldingMap, e_TexCoord.st).xyz);
    float folding = texture(m_NormalsFoldingMap, e_TexCoord.st).a;

    float fraction = (e_Position.y + 4.0) * 0.1;
    vec3 incident = normalize(e_Position.xyz - m_LightPosition);

    vec3 reflected = reflect(incident, normal);

    // Height Adjustment
    vec4 heightAdjustment = mix(m_LowWaterColor, m_HighWaterColor, fraction);

    // Sky Box Effects
    vec4 reflection = vec4(0.1, 0.3, 0.5, 1.0);//texture(m_SkyBox, reflected);
    vec3 refracted = normalize(refract(incident, normal, 1.0));
    vec4 refraction = vec4(0.1, 0.3, 0.5, 1.0);//textureCube(m_SkyBox, refracted);

    // Specular Lighting
    vec4 specular = m_LightColor
                    * pow(clamp(dot(normalize(m_CameraPosition - e_Position.xyz), reflected), 0.0175, 0.975)
                    , 0.3 * m_LightShininess) * 3.0;

    // Diffuse Lighting
    //vec4 diffuse = m_LightColor * dot(normalize(m_LightPosition - e_Position.xyz), normal) * 0.025;
    vec4 diffuse = m_LightColor * dot(normalize(m_LightPosition - e_Position.xyz), normal) * 0.05;
    //diffuse = clamp(diffuse, vec4(0.0, 0.0, 0.0, 0.0), vec4(0.1, 0.1, 0.1, 0.1));

    // Fresnel Term
    float fa = distance(m_CameraPosition, e_Position.xyz) / 48.0;
    float fs = clamp(fa * fa, 0.0, 1.25);

    // Put All Together
    vec4 color0 =  mix(mix(heightAdjustment, reflection, m_Reflection), refraction, m_Reflection);
    vec4 color1 = texture(m_FoamMap, e_TexCoord.st * 6.0);
    float foamFactor = folding * m_Foam + 0.0 * m_FoamHeightModification;
    vec4 water = mix(color0, color1, clamp(foamFactor, 0.0, 1.0)) * m_DiffuseColor;

    vec4 result = diffuse + water + specular * fs;

    // Store Data To Textures
    fragdata(vec4(result.rgb, m_Transparency), e_Position, normal);
}