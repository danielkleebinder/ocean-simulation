#version 400

out vec2 o_OutputX;
out vec2 o_OutputY;
out vec2 o_OutputZ;

// Pre-Computed textures
uniform sampler2D m_SpectrumTexture;
uniform sampler2D m_OmegaTexture;

// Parameters
uniform float m_Time;
uniform float m_Amplitude;

// Varying variables
smooth in vec3 v_TexCoord;

/**
 * Main method.
 */
void main(void) {
    // Do Transformation
    // Calculate pixel lookup vectors here
    vec2 texcoord = v_TexCoord.xy;

    // Pre compute K vector
    vec2 k = texcoord - 0.5;

    // 1: calculate height field Y
    // h(0) -> h(t)
    vec2 h0 = texture(m_SpectrumTexture, texcoord).rg * m_Amplitude;
    vec2 conH0 = texture(m_SpectrumTexture, 1.0 - texcoord).rg * m_Amplitude;
    float omega = texture(m_OmegaTexture, texcoord).r;
    float stepsize = omega * m_Time;

    float sinf = sin(stepsize);
    float cosf = cos(stepsize);

    vec2 ht = vec2((h0.r + conH0.r) * cosf - (h0.g + conH0.g) * sinf,
                       (h0.r - conH0.r) * sinf + (h0.g - conH0.g) * cosf);

    o_OutputY = vec2(ht.r, ht.g);

    // 2: calculate choppy X-Z field
    float squared = k.x * k.x + k.y * k.y;
    float rSquared = 0;
    if (squared > 1e-12) {
        rSquared = 1.0 / sqrt(squared);
    }
    k *= rSquared;

    vec4 choppy;
    choppy.x = ht.y * k.x;
    choppy.y = -ht.x * k.x;
    choppy.z = ht.y * k.y;
    choppy.w = -ht.x * k.y;

    o_OutputX = choppy.xy;
    o_OutputZ = choppy.zw;
}