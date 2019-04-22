#version 400

out vec4 o_NormalsFoldingMap;

uniform sampler2D m_DisplacementMap;

// Parameters
uniform vec2 m_ChoppyScale;
uniform float m_DistanceBetweenVertex;

// Varying variables
smooth in vec3 v_TexCoord;

void main(void) {
    vec3 choppyscale = vec3(m_ChoppyScale.x, 1.0, m_ChoppyScale.y);

    // Lookup displacements
    vec3 d0 = texture(m_DisplacementMap, v_TexCoord.st).xyz;
    vec3 dl = textureOffset(m_DisplacementMap, v_TexCoord.st, ivec2(-1, +0)).xyz;
    vec3 dr = textureOffset(m_DisplacementMap, v_TexCoord.st, ivec2(+1, +0)).xyz;
    vec3 db = textureOffset(m_DisplacementMap, v_TexCoord.st, ivec2(+0, -1)).xyz;
    vec3 df = textureOffset(m_DisplacementMap, v_TexCoord.st, ivec2(+0, +1)).xyz;

    // Calculate Normals
    vec3 s00 = d0 * choppyscale;
    vec3 s10 = vec3(m_DistanceBetweenVertex, 0.0, 0.0) + dr * choppyscale;
    vec3 s01 = vec3(0.0, 0.0, m_DistanceBetweenVertex) + df * choppyscale;
    vec3 normal = normalize(cross(s01 - s00, s10 - s00));

    // Calculate Folding
    vec2 dx = (dr.xz - dl.xz) * m_ChoppyScale;
    vec2 dy = (df.xz - db.xz) * m_ChoppyScale;
    float j = (1.0 + dx.x) * (1.0 + dy.y) - dx.y * dy.x;
    float fold = max(1.0 - j, 0.0);

    // Prevent out of bounds lookups
    if (v_TexCoord.x >= 0.99 || v_TexCoord.y >= 0.99
        || v_TexCoord.x <= 0.01 || v_TexCoord.y <= 0.01) {
        fold = 0.0;
    }

    // Write values
    o_NormalsFoldingMap = vec4((normal / 2.0 + 0.5), 1.0);
}