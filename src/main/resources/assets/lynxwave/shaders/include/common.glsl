float rdist(vec2 pos, vec2 size, vec4 radius) {
    radius.xy = (pos.x > 0.0) ? radius.xy : radius.wz;
    radius.x  = (pos.y > 0.0) ? radius.x : radius.y;

    vec2 v = abs(pos) - size + radius.x;
    return min(max(v.x, v.y), 0.0) + length(max(v, 0.0)) - radius.x;
}

vec4 gradient(vec2 coords, vec4 color1, vec4 color2, vec4 color3, vec4 color4) {
    vec4 color = mix(mix(color1, color2, coords.y), mix(color3, color4, coords.y), coords.x);
    color += mix(0.0019607843, -0.0019607843, fract(sin(dot(coords.xy, vec2(12.9898, 78.233))) * 43758.5453));
    return color;
}

float ralpha(vec2 size, vec2 coord, vec4 radius, float Smoothness) {
    vec2 center = size * 0.5;
    float dist = rdist(center - (coord * size), center - 1.0, radius);
    return 1.0 - smoothstep(1.0 - Smoothness, 1.0, dist);
}

const vec2[4] RECT_VERTICES_COORDS = vec2[] (
vec2(0.0, 0.0),
vec2(0.0, 1.0),
vec2(1.0, 1.0),
vec2(1.0, 0.0)
);

vec2 rvertexcoord(int id) {
    return RECT_VERTICES_COORDS[id % 4];
}