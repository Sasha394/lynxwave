#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 quadCoord;
in vec4 vertexColor;

layout(std140) uniform OutlineConfig {
    vec4 SizeParams;
    vec4 Radius;
    vec4 OutlineParams;
};

out vec4 fragColor;

float roundedDistance(vec2 pos, vec2 size, vec4 radius) {
    radius.xy = (pos.x > 0.0) ? radius.xy : radius.wz;
    radius.x = (pos.y > 0.0) ? radius.x : radius.y;

    vec2 v = abs(pos) - size + radius.x;
    return min(max(v.x, v.y), 0.0) + length(max(v, 0.0)) - radius.x;
}

void main() {
    vec2 size = SizeParams.xy;
    vec2 center = size * 0.5;
    float thickness = OutlineParams.x;
    vec2 smoothness = OutlineParams.yz;
    float dist = roundedDistance(center - (quadCoord * size), center - 1.0, Radius);
    float alpha = smoothstep(1.0 - thickness - smoothness.x - smoothness.y,
        1.0 - thickness - smoothness.y, dist);
    alpha *= 1.0 - smoothstep(1.0 - smoothness.y, 1.0, dist);
    vec4 color = vertexColor * ColorModulator;
    color *= alpha;

    if (color.a <= 0.001) {
        discard;
    }

    fragColor = color;
}
