#version 330

// Порт эффектов ShaderHand из 1.21.4 (днтчччччч). Каждый режим воспроизводит
// соответствующий hand_*-шейдер: эффект считается так же, как в BlockHighLight,
// но перекрашивается в цвет клиента (BaseColor) по яркости эффекта и маскируется
// силуэтом руки (разница Sampler0/Sampler1). Режимы 1..12 совпадают с BlockHighLight.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform HandHighlightConfig {
    vec4 Params;       // x=time, y=threshold, z=opacity, w=mode
    vec4 BaseColor;    // rgb = цвет клиента
    vec4 Resolution;   // xy = размер фреймбуфера
};

uniform sampler2D Sampler0; // кадр с рукой
uniform sampler2D Sampler1; // кадр до руки

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

const float TAU = 6.28318530718;

vec2 handUv(vec2 uv) { return vec2(uv.x, 1.0 - uv.y); }

float handMaskAt(vec2 uv, float threshold) {
    vec4 withHand = texture(Sampler0, handUv(uv));
    vec4 beforeHand = texture(Sampler1, handUv(uv));
    vec3 diff3 = abs(withHand.rgb - beforeHand.rgb);
    float diff = max(diff3.r, max(diff3.g, diff3.b)) * 8.0;
    return smoothstep(threshold, threshold + 0.1, diff);
}

float handMask(float threshold) {
    return handMaskAt(texCoord0, threshold);
}

float max3(vec3 v) { return max(v.r, max(v.g, v.b)); }

// перекраска эффекта в цвет клиента (footer hand_bh*): mix к белому, яркость, BaseColor*1.8
vec4 tintFooter(vec3 shaderColor, float blendT, vec3 base, float mask, float opacity) {
    vec3 mixed = mix(shaderColor, vec3(1.0), blendT);
    float intensity = max3(mixed);
    vec3 rgb = clamp(base * intensity * 1.8, 0.0, 1.0);
    return vec4(rgb, mask * opacity);
}

// ───────── helpers ─────────
float h_random(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
vec2 h_random2(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}
float h_getGlow(float dist, float radius, float intensity) { return pow(radius / dist, intensity); }

float s2_Star(vec2 uv, float flare) {
    float d = length(uv);
    float m = sin(0.025 * 1.2) / max(d, 0.0015);
    float rays = max(0.0, 0.5 - abs(uv.x * uv.y * 1000.0));
    m += (rays * flare) * 2.0;
    m *= smoothstep(1.0, 0.1, d);
    return m;
}
float s2_Hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
vec3 s2_StarLayer(vec2 uv, float time) {
    vec3 col = vec3(0.0);
    vec2 gv = fract(uv);
    vec2 id = floor(uv);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offs = vec2(x, y);
            float n = s2_Hash21(id + offs);
            float size = fract(n);
            float star = s2_Star(gv - offs - vec2(n, fract(n * 34.0)) + 0.5, smoothstep(0.1, 0.9, size) * 0.46);
            vec3 color = sin(vec3(0.2, 0.3, 0.9) * fract(n * 2345.2) * TAU) * 0.25 + 0.75;
            color = color * vec3(0.9, 0.59, 0.9 + size);
            star *= sin(time * 0.6 + n * TAU) * 0.5 + 0.5;
            col += star * size * color;
        }
    }
    return col;
}

float bh1_field(vec3 p, float time) {
    float strength = 7.0 + 0.03 * log(1.0e-6 + fract(sin(time) * 4373.11));
    float accum = 0.0;
    float prev = 0.0;
    float tw = 0.0;
    float mag = dot(p, p);
    p = abs(p) / mag + vec3(-0.5, -0.8 + 0.1 * sin(time * 0.7 + 2.0), -1.1 + 0.3 * cos(time * 0.3));
    float w = exp(-0.0 / 7.0);
    accum += w * exp(-strength * pow(abs(mag - prev), 2.3));
    tw += w;
    prev = mag;
    return max(0.0, 5.0 * accum / tw - 0.7);
}

mat2 bh3_m(float a) { float c = cos(a), s = sin(a); return mat2(c, -s, s, c); }
float bh3_map(vec3 p, float time) {
    p.xz *= bh3_m(time * 0.4);
    p.xy *= bh3_m(time * 0.1);
    vec3 q = p * 2.0 + time;
    return length(p + vec3(sin(time * 0.7))) * log(length(p) + 1.0)
         + sin(q.x + sin(q.z + sin(q.y))) * 0.5 - 1.0;
}

float acid_hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float acid_noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(acid_hash(i), acid_hash(i + vec2(1, 0)), f.x),
               mix(acid_hash(i + vec2(0, 1)), acid_hash(i + vec2(1, 1)), f.x), f.y);
}
float acid_fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) { v += a * acid_noise(p); p = p * 2.1 + vec2(1.3, 0.7); a *= 0.5; }
    return v;
}
float fire_fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { v += a * acid_noise(p); p = p * 2.0 + vec2(0.7, 1.3); a *= 0.5; }
    return v;
}

float mtx_hash(float n) { return fract(sin(n) * 43758.5453); }

float sp_hash(vec3 p) { return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
float sp_noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(sp_hash(i), sp_hash(i + vec3(1, 0, 0)), f.x),
                   mix(sp_hash(i + vec3(0, 1, 0)), sp_hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(sp_hash(i + vec3(0, 0, 1)), sp_hash(i + vec3(1, 0, 1)), f.x),
                   mix(sp_hash(i + vec3(0, 1, 1)), sp_hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

// ───────── effect cores (vec3 shaderColor) for tinted modes 4..12 ─────────
vec3 core_nebula(vec2 fragCoord, vec2 res, float time) {
    const int iterations = 14;
    const float formuparam = 0.79;
    const int volsteps = 5;
    const float stepsize = 0.390;
    const float zoom = 0.900;
    const float tile = 0.850;
    const float brightness = 0.003;
    const float distfading = 0.560;
    const float saturation = 0.800;
    const float transverseSpeed = zoom * 2.0;
    const float cloud = 0.11;
    vec2 mouse = vec2(sin(time) / 48.0, cos(time) / 48.0);
    vec2 uv = 2.0 * fragCoord / res - 1.0;
    float time2 = time;
    float speed = 0.01 * cos(time2 * 0.02 + 3.1415926 / 4.0);
    float a_xz = 0.9;
    float a_yz = -0.6;
    float a_xy = 0.9 + time * 0.08;
    mat2 rot_xz = mat2(cos(a_xz), sin(a_xz), -sin(a_xz), cos(a_xz));
    mat2 rot_yz = mat2(cos(a_yz), sin(a_yz), -sin(a_yz), cos(a_yz));
    mat2 rot_xy = mat2(cos(a_xy), sin(a_xy), -sin(a_xy), cos(a_xy));
    float v2 = 1.0;
    vec3 dir = vec3(uv * zoom, 1.0);
    vec3 from = vec3(0.0);
    from.x -= 5.0 * (mouse.x - 0.5);
    from.y -= 5.0 * (mouse.y - 0.5);
    vec3 forward = vec3(0.0, 0.0, 1.0);
    from.x += transverseSpeed * cos(0.01 * time) + 0.001 * time;
    from.y += transverseSpeed * sin(0.01 * time) + 0.001 * time;
    from.z += 0.003 * time;
    dir.xy *= rot_xy; forward.xy *= rot_xy;
    dir.xz *= rot_xz; forward.xz *= rot_xz;
    dir.yz *= rot_yz; forward.yz *= rot_yz;
    from.xy *= -rot_xy;
    from.xz *= rot_xz;
    from.yz *= rot_yz;
    float zooom = (time2 - 3311.0) * speed;
    from += forward * zooom;
    float sampleShift = mod(zooom, stepsize);
    float zoffset = -sampleShift;
    sampleShift /= stepsize;
    float s = 0.24;
    float s3 = s + stepsize / 2.0;
    vec3 v = vec3(0.0);
    float t3 = 0.0;
    vec3 backCol2 = vec3(0.0);
    for (int r = 0; r < volsteps; r++) {
        vec3 p2 = from + (s + zoffset) * dir;
        vec3 p3 = from + (s3 + zoffset) * dir;
        p2 = abs(vec3(tile) - mod(p2, vec3(tile * 2.0)));
        p3 = abs(vec3(tile) - mod(p3, vec3(tile * 2.0)));
        t3 = bh1_field(p3, time);
        float pa = 0.0, a = 0.0;
        for (int i = 0; i < iterations; i++) {
            p2 = abs(p2) / dot(p2, p2) - formuparam;
            float D = abs(length(p2) - pa);
            a += i > 7 ? min(12.0, D) : D;
            pa = length(p2);
        }
        a *= a * a;
        float s1 = s + zoffset;
        float fade = pow(distfading, max(0.0, float(r) - sampleShift));
        v += fade;
        if (r == 0) fade *= (1.0 - sampleShift);
        if (r == volsteps - 1) fade *= sampleShift;
        v += vec3(s1, s1 * s1, s1 * s1 * s1 * s1) * a * brightness * fade;
        backCol2 += mix(0.11, 1.0, v2) * vec3(1.8 * t3 * t3 * t3, 1.4 * t3 * t3, t3) * fade;
        s += stepsize; s3 += stepsize;
    }
    v = mix(vec3(length(v)), v, saturation);
    vec3 forCol2 = v * 0.01;
    backCol2 *= cloud;
    backCol2.b *= 1.8;
    backCol2.r *= 0.05;
    backCol2.b = 0.5 * mix(backCol2.g, backCol2.b, 0.8);
    backCol2.g = 0.0;
    backCol2.bg = mix(backCol2.gb, backCol2.bg, 0.5 * (cos(time * 0.01) + 1.0));
    return forCol2 + backCol2;
}

vec3 core_fractal(vec2 fragCoord, vec2 res, float time) {
    vec2 uv = fragCoord / res - 0.5;
    uv.y *= res.y / res.x;
    vec3 dir = vec3(uv * 1.4, 1.0);
    vec3 from = vec3(1.25 * sin(time), -1.03 * time, -2.0);
    float s = 0.1;
    float fade = 0.07;
    vec3 v = vec3(0.4);
    for (int r = 0; r < 12; r++) {
        vec3 p = from + s * dir * 1.5;
        p = abs(vec3(0.750) - mod(p, vec3(0.750 * 2.0)));
        p.x += float(r * r) * 0.01;
        p.y += float(r) * 0.02;
        float pa = 0.0, a = 0.0;
        for (int i = 0; i < 15; i++) {
            p = abs(p) / dot(p, p) - 0.340;
            a += abs(length(p) - pa * 0.2);
            pa = length(p);
        }
        a *= a * a * 2.0;
        v += vec3(s, s * s, s * s * s * s) * a * 0.0017 * fade;
        fade *= 0.960;
        s += 0.110;
    }
    v = mix(vec3(length(v)), v, 0.8);
    return v * 0.01;
}

vec3 core_march(vec2 fragCoord, vec2 res, float time) {
    vec2 a = fragCoord / res.y - vec2(0.9, 0.5);
    vec3 cl = vec3(0.0);
    float d = 2.5;
    for (int i = 0; i <= 5; i++) {
        vec3 p = vec3(0.0, 0.0, 4.0) + normalize(vec3(a, -1.0)) * d;
        float rz = bh3_map(p, time);
        float f = clamp((rz - bh3_map(p + 0.1, time)) * 0.5, -0.1, 1.0);
        vec3 l = vec3(0.1, 0.3, 0.4) + vec3(5.0, 2.5, 3.0) * f;
        cl = cl * l + smoothstep(2.5, 0.0, rz) * 0.6 * l;
        d += min(rz, 1.0);
    }
    return cl;
}

vec3 core_kosmos(vec2 fragCoord, vec2 res, float time) {
    vec2 uv = fragCoord / res - 0.5;
    uv.y *= res.y / res.x;
    float mul = res.x / res.y;
    vec3 dir = vec3(uv * mul, 1.0);
    vec3 from = vec3(0.0025 * time, 0.03 * time, -2.0);
    float s = 0.3;
    float fade = 0.07;
    vec3 v = vec3(0.4);
    for (int r = 0; r < 8; r++) {
        vec3 p = from + s * dir * 1.5;
        p = abs(vec3(0.750) - mod(p, vec3(0.750 * 2.0)));
        p.x += float(r * r) * 0.01;
        p.y += float(r) * 0.02;
        float pa = 0.0, a = 0.0;
        for (int i = 0; i < 13; i++) {
            p = abs(p) / dot(p, p) - 0.340;
            a += abs(length(p) - pa * 0.2);
            pa = length(p);
        }
        a *= a * a * 8.0;
        v += vec3(s * s, s, s * s) * a * 0.0017 * fade;
        fade *= 0.960;
        s += 0.15;
    }
    v = mix(vec3(length(v)), v, 0.9);
    return v * 0.01;
}

vec3 core_plasma(vec2 fragCoord, vec2 res, float time) {
    vec2 uv = fragCoord / res;
    float v = 0.0;
    v += sin((uv.x * 10.0 + time));
    v += sin((uv.y * 10.0 + time));
    v += sin((uv.x * 5.0 + uv.y * 5.0 + time * 1.5));
    float cx = uv.x + 0.5 * sin(time * 0.5);
    float cy = uv.y + 0.5 * cos(time * 0.3);
    v += sin(sqrt(100.0 * (cx * cx + cy * cy) + 1.0) + time);
    vec3 col;
    col.r = sin(v * 3.14159);
    col.g = sin(v * 3.14159 + 2.094);
    col.b = sin(v * 3.14159 + 4.189);
    return col * 0.5 + 0.5;
}

vec3 core_acid(vec2 fragCoord, vec2 res, float time) {
    vec2 uv = fragCoord / res;
    uv = uv * 3.0 - 1.5;
    float t = time * 0.4;
    float n = acid_fbm(uv + vec2(t * 0.3, t * 0.2));
    float n2 = acid_fbm(uv * 1.5 - vec2(t * 0.2, t * 0.4) + n);
    float glow = pow(n2, 2.0);
    vec3 col = vec3(0.0, glow * 1.2, glow * 0.3) + vec3(0.0, 0.05, 0.02);
    col += vec3(0.0, 0.3, 0.1) * pow(acid_fbm(uv * 2.0 + t), 3.0);
    return col;
}

vec3 core_fire(vec2 fragCoord, vec2 res, float time) {
    vec2 uv = fragCoord / res;
    float t = time * 0.8;
    vec2 fuv = uv * vec2(2.5, 3.0);
    fuv.y -= t;
    float n = fire_fbm(fuv);
    float n2 = fire_fbm(fuv * 1.4 + vec2(n * 0.5, n));
    float fade = pow(1.0 - uv.y, 1.5);
    float fire = pow(n2, 1.5) * fade * 2.5;
    vec3 col = vec3(0.0);
    col += vec3(1.0, 0.1, 0.0) * fire;
    col += vec3(1.0, 0.5, 0.0) * fire * fire;
    col += vec3(1.0, 0.9, 0.3) * pow(fire, 4.0);
    return col;
}

vec3 core_matrix(vec2 fragCoord, vec2 res, float time) {
    vec2 uv = fragCoord / res;
    float cols = 40.0;
    float rows = 22.0;
    vec2 cell = vec2(floor(uv.x * cols), floor(uv.y * rows));
    float speed = 0.5 + mtx_hash(cell.x) * 1.5;
    float offset = mtx_hash(cell.x * 7.3);
    float t = time * speed + offset * 10.0;
    float head = mod(t, rows + 5.0) - 5.0;
    float dist = head - cell.y;
    float bright = 0.0;
    if (dist >= 0.0 && dist < 12.0) {
        bright = exp(-dist * 0.35);
        if (dist < 1.0) bright = 1.0;
    }
    float flicker = mtx_hash(cell.x * 3.7 + cell.y * 11.3 + floor(t));
    bright *= (0.7 + flicker * 0.3);
    if (dist < 1.0 && dist >= 0.0) return vec3(0.8, 1.0, 0.8) * bright;
    return vec3(0.0, bright, bright * 0.2);
}

vec3 core_space(vec2 fragCoord, vec2 res, float time) {
    // у руки нет WorldPos — строим экранно-пространственную позицию
    vec2 uv = (fragCoord - 0.5 * res) / res.y;
    vec3 worldPos = vec3(uv * 2.0, 0.0);
    vec3 p = worldPos * 8.0 + vec3(time * 0.06, time * 0.04, time * 0.02);
    float nebula = 0.0;
    float amp = 0.5;
    vec3 q = p;
    for (int i = 0; i < 5; i++) {
        nebula += amp * sp_noise(q);
        q = q * 2.1 + vec3(1.7, 0.9, 2.3);
        amp *= 0.5;
    }
    nebula = pow(nebula, 1.8);
    vec3 nebulaCol = vec3(0.02, 0.05, 0.35) * nebula * 4.0
                   + vec3(0.25, 0.0, 0.45) * pow(nebula, 2.5) * 3.0
                   + vec3(0.0, 0.15, 0.3) * pow(nebula, 3.0) * 2.0;
    vec3 sp = worldPos * 60.0;
    vec3 sc = floor(sp);
    float star = 0.0;
    float starH = sp_hash(sc);
    if (starH > 0.90) {
        vec3 sf = fract(sp) - 0.5;
        float d = length(sf);
        star = smoothstep(0.22, 0.0, d) * (starH - 0.90) * 10.0;
        star *= 0.5 + 0.5 * sin(time * (2.0 + starH * 10.0) + starH * 30.0);
    }
    return nebulaCol + vec3(0.9, 0.95, 1.0) * star * 1.5 + vec3(0.4 * nebula);
}

void main() {
    float time = Params.x;
    float threshold = Params.y;
    float opacity = Params.z;
    int mode = int(Params.w + 0.5);
    vec2 res = max(Resolution.xy, vec2(1.0));
    vec3 base = clamp(BaseColor.rgb, 0.0, 1.0);
    vec2 fragCoord = gl_FragCoord.xy;

    float mask = handMask(threshold);
    vec4 outCol;

    if (mode == 13 || mode == 14) {
        bool selectedSide = mode == 13
                ? fragCoord.x >= res.x * 0.5
                : fragCoord.x <= res.x * 0.5;
        if (!selectedSide) discard;

        vec2 pixel = 1.0 / res;
        vec2 px2 = pixel * 2.5;
        vec2 px4 = pixel * 4.0;
        float expanded = mask;
        expanded = max(expanded, handMaskAt(texCoord0 + vec2(px2.x, 0.0), threshold));
        expanded = max(expanded, handMaskAt(texCoord0 - vec2(px2.x, 0.0), threshold));
        expanded = max(expanded, handMaskAt(texCoord0 + vec2(0.0, px2.y), threshold));
        expanded = max(expanded, handMaskAt(texCoord0 - vec2(0.0, px2.y), threshold));
        expanded = max(expanded, handMaskAt(texCoord0 + px2, threshold));
        expanded = max(expanded, handMaskAt(texCoord0 - px2, threshold));
        expanded = max(expanded, handMaskAt(texCoord0 + vec2(px2.x, -px2.y), threshold));
        expanded = max(expanded, handMaskAt(texCoord0 + vec2(-px2.x, px2.y), threshold));
        expanded = max(expanded, handMaskAt(texCoord0 + vec2(px4.x, 0.0), threshold));
        expanded = max(expanded, handMaskAt(texCoord0 - vec2(px4.x, 0.0), threshold));
        expanded = max(expanded, handMaskAt(texCoord0 + vec2(0.0, px4.y), threshold));
        expanded = max(expanded, handMaskAt(texCoord0 - vec2(0.0, px4.y), threshold));

        float outline = smoothstep(0.04, 0.45, expanded - mask);
        outCol = vec4(vec3(1.0), outline * opacity);
    } else if (mode == 2) {
        if (mask <= 0.01) discard;
        // Волна
        vec2 I = fragCoord;
        vec4 O = vec4(0.0);
        float i = 0.0;
        float t = 0.0;
        float v = 0.0;
        for (O *= i; i++ < 50.0;) {
            vec3 p = t * normalize(vec3(I + I, 1.0) - res.xyy);
            float ca = cos(t * 0.15 + 0.0);
            float cb = cos(t * 0.15 + 11.0);
            float cc = cos(t * 0.15 + 33.0);
            float cd = cos(t * 0.15 + 0.0);
            mat2 rot = mat2(ca, cb, cc, cd);
            p.xy *= rot;
            p.z -= time;
            p = mod(p, 4.0) - 2.0;
            v = mix(abs(length(p) - 1.0), length(p.xz), 0.5 - 0.5 * cos(t)) + 0.01;
            t += v * 0.3;
            O += exp(sin(t + vec4(0.0, 2.0, 4.0, 0.0))) / v;
        }
        vec4 x = O / 2e2;
        O = x / (1.0 + abs(x));
        float intensity = pow(clamp(length(O.rgb) / 1.732, 0.0, 1.0), 0.85);
        outCol = vec4(base * intensity, mask * opacity * clamp(intensity * 1.1, 0.0, 1.0));
    } else if (mode == 3) {
        if (mask <= 0.01) discard;
        // Звёзды 2
        vec2 uv = (fragCoord - 0.5 * res) / res.y;
        vec2 M = vec2(0.0);
        M -= vec2(M.x + sin(time * 0.22), M.y - cos(time * 0.22));
        float t = time * 0.025;
        vec3 col = vec3(0.0);
        for (float i = 0.0; i < 1.0; i += 1.0 / 8.0) {
            float depth = fract(i + t);
            float scale = mix(20.0, 0.5, depth);
            float fade = depth * smoothstep(1.0, 0.9, depth);
            col += s2_StarLayer(uv * scale + i * 453.2 - time * 0.05 + M, time) * fade;
        }
        float intensity = clamp(max3(col), 0.0, 1.0);
        outCol = vec4(base * intensity, mask * opacity * clamp(intensity * 1.4, 0.0, 1.0));
    } else if (mode == 4) {
        if (mask <= 0.01) discard;
        outCol = tintFooter(core_nebula(fragCoord, res, time), 0.2, base, mask, opacity);
    } else if (mode == 5) {
        if (mask <= 0.01) discard;
        outCol = tintFooter(core_fractal(fragCoord, res, time), 0.2, base, mask, opacity);
    } else if (mode == 6) {
        if (mask <= 0.01) discard;
        outCol = tintFooter(core_march(fragCoord, res, time), 0.2, base, mask, opacity);
    } else if (mode == 7) {
        if (mask <= 0.01) discard;
        outCol = tintFooter(core_kosmos(fragCoord, res, time), 0.2, base, mask, opacity);
    } else if (mode == 8) {
        if (mask <= 0.01) discard;
        outCol = tintFooter(core_plasma(fragCoord, res, time), 0.25, base, mask, opacity);
    } else if (mode == 9) {
        if (mask <= 0.01) discard;
        outCol = tintFooter(core_acid(fragCoord, res, time), 0.2, base, mask, opacity);
    } else if (mode == 10) {
        if (mask <= 0.01) discard;
        outCol = tintFooter(core_fire(fragCoord, res, time), 0.2, base, mask, opacity);
    } else if (mode == 11) {
        if (mask <= 0.01) discard;
        outCol = tintFooter(core_matrix(fragCoord, res, time), 0.2, base, mask, opacity);
    } else if (mode == 12) {
        if (mask <= 0.01) discard;
        outCol = tintFooter(core_space(fragCoord, res, time), 0.15, base, mask, opacity);
    } else {
        if (mask <= 0.01) discard;
        // Звёзды (hand_highlight) — режим 1 / по умолчанию
        float t = 1.0 + time * 0.06;
        float layers = 10.0;
        float scale = 32.0;
        float rotationAngle = time * -0.1;
        float del = 1.0 / layers;
        vec2 rot = vec2(cos(t), sin(t));
        mat2 rotation = mat2(cos(rotationAngle), -sin(rotationAngle), sin(rotationAngle), cos(rotationAngle));
        vec3 col = vec3(0.0);
        for (float i = 0.0; i <= 1.0; i += del) {
            float depth = fract(i + t);
            vec2 centre = rot * 0.2 * depth + 0.5;
            vec2 uvR = centre - fragCoord / res.x;
            uvR *= rotation;
            uvR *= mix(scale, 0.0, depth);
            vec2 fl = floor(uvR);
            vec2 local_uv = uvR - fl - 0.5;
            for (float j = -1.0; j <= 1.0; j++) {
                for (float k = -1.0; k <= 1.0; k++) {
                    vec2 cell = vec2(j, k);
                    vec2 index = fl + cell;
                    vec2 seed = 128.0 * i + index;
                    vec2 pos = cell + 0.9 * (h_random2(seed) - 0.5);
                    float phase = 128.0 * h_random(seed);
                    float variation = 0.15 * (h_random(seed + 3.0) - 0.5);
                    vec3 tone = clamp(base + vec3(variation), 0.0, 1.0);
                    float size = (0.1 + 0.5 + 0.5 * sin(phase * t)) * depth;
                    float glow = size * h_getGlow(length(local_uv - pos), 0.07, 2.5);
                    col += 5.0 * vec3(0.02 * glow) + tone * glow;
                }
            }
        }
        col = 1.0 - exp(-col);
        col = pow(col, vec3(0.4545));
        outCol = vec4(col, mask * opacity * clamp(length(col) * 0.8, 0.0, 1.0));
    }

    vec4 themed = vertexColor * ColorModulator;
    fragColor = vec4(outCol.rgb * themed.rgb, outCol.a * themed.a);
    if (fragColor.a <= 0.001) discard;
}
