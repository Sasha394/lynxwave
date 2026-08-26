#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 texCoord0;
in vec4 vertexColor;

uniform sampler2D Sampler0;

layout(std140) uniform MsdfConfig {
    vec4 FontParams;
    vec4 OutlineColor;
};

out vec4 fragColor;

float median(vec3 color) {
    return max(min(color.r, color.g), min(max(color.r, color.g), color.b));
}

void main() {
    float range = max(FontParams.x, 1.0);
    float thickness = FontParams.y;
    float smoothness = max(FontParams.z, 0.001);
    float outlineThickness = max(FontParams.w, 0.0);

    float dist = median(texture(Sampler0, texCoord0).rgb) - 0.5 + thickness;
    vec2 derivatives = vec2(dFdx(texCoord0.x), dFdy(texCoord0.y)) * vec2(textureSize(Sampler0, 0));
    float pixels = range * inversesqrt(max(dot(derivatives, derivatives), 0.0001));
    float alpha = smoothstep(-smoothness, smoothness, dist * pixels);

    vec4 fillColor = vertexColor * ColorModulator;
    vec4 color = vec4(fillColor.rgb, fillColor.a * alpha);

    if (outlineThickness > 0.0) {
        float outlineAlpha = smoothstep(-smoothness, smoothness, (dist + outlineThickness) * pixels);
        vec4 outlineColor = OutlineColor * ColorModulator;
        color = mix(outlineColor, fillColor, alpha);
        color.a *= outlineAlpha;
    }

    if (color.a <= 0.001) {
        discard;
    }

    fragColor = color;
}
