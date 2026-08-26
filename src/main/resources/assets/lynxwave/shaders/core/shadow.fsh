#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 quadCoord;
in vec4 vertexColor;

layout(std140) uniform EffectConfig {
    vec4 EffectParams;
    vec4 Radius;
    vec4 Reserved;
};

out vec4 fragColor;

float roundedDistance(vec2 pos, vec2 size, vec4 radius) {
    radius.xy = (pos.x > 0.0) ? radius.xy : radius.wz;
    radius.x = (pos.y > 0.0) ? radius.x : radius.y;

    vec2 v = abs(pos) - size + radius.x;
    return min(max(v.x, v.y), 0.0) + length(max(v, 0.0)) - radius.x;
}

void main() {
    vec2 size = EffectParams.xy;
    float softness = EffectParams.z;
    float effectRadius = EffectParams.w;
    vec2 center = size * 0.5;
    float dist = roundedDistance(center - (quadCoord * size), center - 1.0 - effectRadius, Radius);
    if (dist <= 0.0) {
        discard;
    }

    float alpha = 1.0 - smoothstep(-softness, effectRadius, dist);
    vec4 color = vertexColor * ColorModulator;
    color.a *= alpha;

    if (color.a <= 0.001) {
        discard;
    }

    fragColor = color;
}
