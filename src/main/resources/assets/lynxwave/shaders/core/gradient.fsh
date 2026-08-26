#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 quadCoord;
in vec4 vertexColor;

layout(std140) uniform RoundedRectConfig {
    vec4 RectParams;
    vec4 Radius;
};

out vec4 fragColor;

float roundedDistance(vec2 pos, vec2 size, vec4 radius) {
    radius.xy = (pos.x > 0.0) ? radius.xy : radius.wz;
    radius.x = (pos.y > 0.0) ? radius.x : radius.y;

    vec2 v = abs(pos) - size + radius.x;
    return min(max(v.x, v.y), 0.0) + length(max(v, 0.0)) - radius.x;
}

float roundedAlpha(vec2 size, vec2 coord, vec4 radius, float smoothness) {
    vec2 center = size * 0.5;
    float dist = roundedDistance(center - (coord * size), center - 1.0, radius);
    return 1.0 - smoothstep(1.0 - smoothness, 1.0, dist);
}

void main() {
    vec2 size = RectParams.xy;
    float alpha = roundedAlpha(size, quadCoord, Radius, RectParams.z);
    vec4 color = vertexColor * ColorModulator;
    color *= alpha;

    if (color.a <= 0.001) {
        discard;
    }

    fragColor = color;
}
