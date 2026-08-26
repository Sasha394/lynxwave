#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
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

    vec2 q = abs(pos) - size + radius.x;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius.x;
}

void main() {
    vec2 size = RectParams.xy;
    vec2 pixel = quadCoord * size;
    vec2 halfSize = size * 0.5;
    vec2 local = pixel - halfSize;
    vec2 bounds = max(halfSize - vec2(1.0), vec2(0.0));

    float dist = roundedDistance(local, bounds, Radius);
    float aa = max(fwidth(dist) * RectParams.z, 1e-4);
    float alpha = 1.0 - smoothstep(-aa, aa, dist);
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    color.a *= alpha;

    if (color.a <= 0.001) {
        discard;
    }

    fragColor = color;
}
