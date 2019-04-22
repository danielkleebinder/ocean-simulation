#version 400

// Author: Daniel Kleebinder
// Fragment shader for the image based height map generator.

out vec2 o_OutputX;
out vec2 o_OutputY;
out vec2 o_OutputZ;
out vec3 o_Result;

// Uniforms
uniform sampler2D m_ButterflyTexture;
uniform float m_ButterflyIndex;

uniform float m_Dimension;
uniform float m_PatchSize;

uniform sampler2D m_HeightFieldX;
uniform sampler2D m_HeightFieldY;
uniform sampler2D m_HeightFieldZ;

uniform bool m_Vertical;
uniform bool m_LastPass;

// Varying variables
smooth in vec3 v_TexCoord;
smooth in vec3 v_Color;

/**
 * Computes the fast fourier transformation.
 *
 * @param sourceA Spectrum A.
 * @param sourceB Spectrum B.
 * @param weights Butterfly weights.
 * @return Fast fourier transformed complex number.
 */
vec2 fft(in vec2 sourceA, in vec2 sourceB, in vec2 weights) {
    vec2 weighted;
    weighted.r = weights.r * sourceB.r - weights.g * sourceB.g;
    weighted.g = weights.g * sourceB.r + weights.r * sourceB.g;
    return sourceA + weighted;
}

void main(void) {
    vec2 pixcoord = v_TexCoord.st * m_Dimension;

    vec4 lookup;
    vec2 indices, coordA, coordB;
    if (m_Vertical) {
        lookup = texture(m_ButterflyTexture, vec2(v_TexCoord.x, m_ButterflyIndex));
        indices = vec2(lookup.rg * m_Dimension);
        coordA = vec2(indices.x, pixcoord.y) / m_Dimension;
        coordB = vec2(indices.y, pixcoord.y) / m_Dimension;
    } else {
        lookup = texture(m_ButterflyTexture, vec2(v_TexCoord.y, m_ButterflyIndex));
        indices = vec2(lookup.rg * m_Dimension);
        coordA = vec2(pixcoord.x, indices.x) / m_Dimension;
        coordB = vec2(pixcoord.x, indices.y) / m_Dimension;
    }

    vec2 weights = lookup.ba;

    vec2 sourceXA = texture(m_HeightFieldX, coordA).rg;
    vec2 sourceXB = texture(m_HeightFieldX, coordB).rg;
    vec2 sourceYA = texture(m_HeightFieldY, coordA).rg;
    vec2 sourceYB = texture(m_HeightFieldY, coordB).rg;
    vec2 sourceZA = texture(m_HeightFieldZ, coordA).rg;
    vec2 sourceZB = texture(m_HeightFieldZ, coordB).rg;

    vec2 complex0 = fft(sourceXA, sourceXB, weights);
    vec2 complex1 = fft(sourceYA, sourceYB, weights);
    vec2 complex2 = fft(sourceZA, sourceZB, weights);

    // Store Data To Textures
    if (m_LastPass) {
        if ((int(pixcoord.x) + int(pixcoord.y)) % 2 == 1) {
            complex0.r = -complex0.r;
            complex1.r = -complex1.r;
            complex2.r = -complex2.r;
        }

        float d = 1.0 / (m_PatchSize * (m_PatchSize * 0.01));

        complex0.r *= d;
        complex1.r *= d;
        complex2.r *= d;

        o_Result = vec3(complex0.r, complex1.r, complex2.r);
    } else {
        o_OutputX = vec2(complex0);
        o_OutputY = vec2(complex1);
        o_OutputZ = vec2(complex2);
    }
}