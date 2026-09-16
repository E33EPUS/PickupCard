#version 150 core

// PickupCard 形状着色器（片段端）：SDF + 解析抗锯齿。
// 思路来自 ModernUI 的形状渲染（LGPL，仅借鉴算法：SDF 距离场 + fwidth aastep），
// 全部代码为本仓库清室重写。
//
// 模式：
//   0 = 圆角矩形填充（半径大即为胶囊/圆）
//   1 = 圆角矩形描边环
//   2 = 圆填充（圆心 = u_center 偏移）
//   3 = 圆描边环
//   4 = 月牙：大圆（圆心=quad 中心，半径 u_radius）减去切割圆（圆心 = u_center，半径 u_radius2）

in vec2 v_UV;
in vec4 v_Color;

uniform vec2 u_halfSize;
uniform float u_radius;
uniform float u_stroke;
uniform vec2 u_center;
uniform float u_radius2;
uniform int u_mode;
uniform vec4 ColorModulator;

out vec4 fragColor;

// 圆角盒 SDF（Inigo Quilez 公式，ModernUI 同款）：b = 半宽高，r = 圆角半径
float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float sdCircle(vec2 p, vec2 c, float r) {
    return length(p - c) - r;
}

// 解析抗锯齿：一个像素的 fwidth 定 smoothstep 宽度（ModernUI 用 0.7071 系数，同 SK_DistanceFieldAAFactor）
float aastep(float dis) {
    float afwidth = 0.7071 * length(vec2(dFdx(dis), dFdy(dis)));
    return 1.0 - smoothstep(-afwidth, afwidth, dis);
}

void main() {
    // UV (0..1) 重建局部像素坐标：中心为原点
    vec2 p = (v_UV - 0.5) * u_halfSize * 2.0;

    float dis;
    if (u_mode == 0) {
        dis = sdRoundBox(p, u_halfSize, u_radius);
    } else if (u_mode == 1) {
        dis = abs(sdRoundBox(p, u_halfSize, u_radius)) - u_stroke;
    } else if (u_mode == 2) {
        dis = sdCircle(p, u_center, u_radius2);
    } else if (u_mode == 3) {
        dis = abs(sdCircle(p, u_center, u_radius2)) - u_stroke;
    } else {
        // 月牙 = 大圆 减 切割圆（切割圆在 u_center）
        float base = length(p) - u_radius;
        float cut = sdCircle(p, u_center, u_radius2);
        dis = max(base, -cut);
    }

    float a = aastep(dis);
    fragColor = vec4(v_Color.rgb, v_Color.a * a) * ColorModulator;
    if (fragColor.a < 0.004) discard;
}
