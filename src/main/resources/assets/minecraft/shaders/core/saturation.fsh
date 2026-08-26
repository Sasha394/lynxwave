#version 330

uniform sampler2D InSampler;

layout(std140) uniform SaturationConfig {
    vec4 Params;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float saturation = Params.x;
    vec4 color = texture(InSampler, texCoord);
    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    color.rgb = mix(vec3(gray), color.rgb, saturation);
    fragColor = color;
}
