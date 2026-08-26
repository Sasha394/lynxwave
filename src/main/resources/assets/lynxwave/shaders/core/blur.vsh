#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

out vec2 screenTexCoord;
out vec2 quadCoord;
out vec4 vertexColor;
out float quadRadius;
out float quadSmoothness;

const float PARAM_SCALE = 16.0;

float unpackCoord(int packedValue) {
    return float(packedValue & 1);
}

float unpackParam(int packedValue) {
    return float(packedValue >> 1) / PARAM_SCALE;
}

void main() {
    vec4 clipPosition = ProjMat * ModelViewMat * vec4(Position, 1.0);
    screenTexCoord = clipPosition.xy * 0.5 + 0.5;
    quadCoord = vec2(unpackCoord(UV2.x), unpackCoord(UV2.y));
    quadRadius = unpackParam(UV2.x);
    quadSmoothness = unpackParam(UV2.y);
    vertexColor = Color;

    gl_Position = clipPosition;
}
