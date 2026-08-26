#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform HandBlurConfig {
    vec4 Params;
    vec4 TintColor;
};

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

const float PI2 = 6.28318530718;
const float STEP = PI2 / 16.0;

vec2 handUv(vec2 uv) {
    return vec2(uv.x, 1.0 - uv.y);
}

void main() {
    vec4 withHand = texture(Sampler0, handUv(texCoord0));
    vec4 beforeHand = texture(Sampler1, handUv(texCoord0));

    vec3 diff3 = abs(withHand.rgb - beforeHand.rgb);
    float diff = max(diff3.r, max(diff3.g, diff3.b)) * 8.0;
    float threshold = Params.y;
    float mask = smoothstep(threshold, threshold + 0.1, diff);
    if (mask <= 0.01) {
        discard;
    }

    float blurRadius = max(Params.x, 0.0);
    vec2 multiplier = blurRadius / vec2(textureSize(Sampler1, 0));
    vec3 blurredBg = vec3(0.0);
    float total = 0.0;

    for (float d = 0.0; d < PI2; d += STEP) {
        vec2 dir = vec2(cos(d), sin(d));
        for (float i = 0.2; i <= 1.0; i += 0.2) {
            vec2 sampleUv = clamp(texCoord0 + dir * multiplier * i, vec2(0.0), vec2(1.0));
            blurredBg += texture(Sampler1, handUv(sampleUv)).rgb;
            total += 1.0;
        }
    }

    blurredBg /= max(total, 1.0);
    vec3 tinted = mix(blurredBg, TintColor.rgb, clamp(TintColor.a, 0.0, 1.0));
    vec3 composed = mix(withHand.rgb, tinted, clamp(TintColor.a, 0.0, 1.0));
    vec4 themed = vertexColor * ColorModulator;
    fragColor = vec4(composed * themed.rgb, mask * themed.a);
}
