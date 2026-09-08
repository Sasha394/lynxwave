#version 330

layout(std140) uniform SkyShaderData {
    vec4 uResolution;
    vec4 uSettings;
    vec4 uCameraDir;
    vec4 uSkyZenith;
    vec4 uSkyHorizon;
    vec4 uNebColor1;
    vec4 uNebColor2;
    vec4 uStarColor;
    vec4 uFogColor;
    vec4 uFogRange;
};

out vec3 Direction;
out vec4 vColor;

mat3 cameraRotation() {
    vec3 qv = uCameraDir.xyz;
    float qw = uCameraDir.w;
    return mat3(
        1.0 - 2.0 * (qv.y * qv.y + qv.z * qv.z),
        2.0 * (qv.x * qv.y + qv.z * qw),
        2.0 * (qv.x * qv.z - qv.y * qw),
        2.0 * (qv.x * qv.y - qv.z * qw),
        1.0 - 2.0 * (qv.x * qv.x + qv.z * qv.z),
        2.0 * (qv.y * qv.z + qv.x * qw),
        2.0 * (qv.x * qv.z + qv.y * qw),
        2.0 * (qv.y * qv.z - qv.x * qw),
        1.0 - 2.0 * (qv.x * qv.x + qv.y * qv.y)
    );
}

void main() {
    vec2 positions[3] = vec2[](
        vec2(-1.0, -1.0),
        vec2( 3.0, -1.0),
        vec2(-1.0,  3.0)
    );

    vec2 clip = positions[gl_VertexID];
    gl_Position = vec4(clip, 0.0, 1.0);

    float aspect = uResolution.x / max(uResolution.y, 1.0);
    float tanV = tan(radians(uSettings.z) * 0.5);
    vec3 cameraRay = vec3(clip.x * tanV * aspect, clip.y * tanV, -1.0);
    Direction = cameraRotation() * cameraRay;
    vColor = vec4(1.0);
}
