#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform LiquidGlassConfig {
    vec4 SizeParams;
    vec4 GlassParams;
    vec4 FresnelColor;
    vec4 DistortParams;
    vec4 Radius;
};

uniform sampler2D Sampler0;

in vec2 screenTexCoord;
in vec2 quadCoord;
in vec4 vertexColor;
in float quadRadius;
in float globalAlpha;

out vec4 fragColor;

float roundedBoxSDF(vec2 p, vec2 b, vec4 r, float smoothnessPower) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x = (p.y > 0.0) ? r.x : r.y;
    vec2 q = abs(p) - b + r.x;
    vec2 qClamped = max(q, 0.0);
    float len = pow(pow(qClamped.x, smoothnessPower) + pow(qClamped.y, smoothnessPower), 1.0 / smoothnessPower);
    return min(max(q.x, q.y), 0.0) + len - r.x;
}

void main() {
    vec2 size = max(SizeParams.xy, vec2(1.0));
    float smoothness = clamp(SizeParams.z, 0.0, 4.0);
    float cornerSmoothness = clamp(SizeParams.w, 0.5, 20.0);
    float layerAlpha = clamp(GlassParams.x, 0.0, 1.0);
    float fresnelPower = max(GlassParams.y, 0.0);
    float baseAlpha = clamp(GlassParams.z, 0.0, 1.0);
    bool fresnelInvert = GlassParams.w > 0.5;
    float fresnelMix = clamp(DistortParams.x, 0.0, 1.0);
    float distortStrength = DistortParams.y;

    vec2 center = size * 0.5;
    vec2 boxHalfSize = max(center - 1.0, vec2(0.0));
    vec2 pos = (quadCoord * size) - center;

    float distance = roundedBoxSDF(-pos, boxHalfSize, Radius, cornerSmoothness);
    float alphaMask = 1.0 - smoothstep(1.0 - smoothness, 1.0, distance);

    float distToEdge = abs(roundedBoxSDF(pos, boxHalfSize, Radius, cornerSmoothness));
    float maxDistNorm = max(min(boxHalfSize.x, boxHalfSize.y), 0.0001);
    float edgeGradient = 1.0 - clamp(distToEdge / maxDistNorm, 0.0, 1.0);

    float fresnelBase = fresnelInvert ? edgeGradient : (1.0 - edgeGradient);
    float fresnel = (fresnelPower > 20.0)
            ? exp(fresnelPower * log(clamp(fresnelBase, 0.001, 1.0)))
            : pow(clamp(fresnelBase, 0.0, 1.0), fresnelPower);
    fresnel = clamp(fresnel, 0.0, 1.0);

    vec2 dir = (length(pos) > 0.0) ? normalize(pos) : vec2(0.0);
    vec2 distortedTexCoord = clamp(screenTexCoord + dir * fresnel * distortStrength, vec2(0.0), vec2(1.0));

    vec4 themed = vertexColor * ColorModulator;
    vec4 texColor = texture(Sampler0, distortedTexCoord) * themed;
    vec3 finalColor = mix(texColor.rgb, FresnelColor.rgb, fresnel * fresnelMix);
    float finalAlpha = mix(baseAlpha, FresnelColor.a, fresnel) * alphaMask * layerAlpha * themed.a;

    if (finalAlpha < 0.001) {
        discard;
    }

    fragColor = vec4(finalColor, finalAlpha);
}
