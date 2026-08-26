#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform BlurConfig {
    vec4 BlurParams;
    vec4 SizeParams;
    vec4 Radius;
};

uniform sampler2D Sampler0;

in vec2 quadCoord;
in vec4 vertexColor;

out vec4 fragColor;

#define TAU 6.28318530718

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
    vec2 inputResolution = max(BlurParams.xy, vec2(1.0));
    float quality = max(BlurParams.z, 0.0);
    float smoothness = max(BlurParams.w, 0.75);
    vec2 size = max(SizeParams.xy, vec2(1.0));

    vec2 uv = clamp(gl_FragCoord.xy / inputResolution, vec2(0.0), vec2(1.0));
    vec2 sampleRadius = quality / inputResolution;

    vec4 blur = texture(Sampler0, uv);
    float samples = 1.0;

    if (quality > 0.0) {
        float angleStep = TAU / 16.0;
        for (float direction = 0.0; direction < TAU; direction += angleStep) {
            vec2 offsetDirection = vec2(cos(direction), sin(direction)) * sampleRadius;
            for (float strength = 0.2; strength <= 1.0001; strength += 0.2) {
                blur += texture(Sampler0, clamp(uv + offsetDirection * strength, vec2(0.0), vec2(1.0)));
                samples += 1.0;
            }
        }
    }

    blur /= max(samples, 1.0);

    float alpha = roundedAlpha(size, quadCoord, Radius, smoothness);
    vec4 themed = vertexColor * ColorModulator;
    vec4 color = vec4(blur.rgb * themed.rgb, themed.a * alpha);

    if (color.a <= 0.001) {
        discard;
    }

    fragColor = color;
}
