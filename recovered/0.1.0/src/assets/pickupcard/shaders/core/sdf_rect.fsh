#version 150

// 圆角矩形的有符号距离场（Inigo Quilez 的 sdRoundedBox）。
// u_Rect   = (中心 x, 中心 y, 半宽, 半高)
// u_Border = (描边厚度, 是否描边) —— 描边画在内侧，跟 CSS 的 border-box 一致
// 屏幕→本地的逆仿射：local.x = u_LocalA.x*sx + u_LocalA.y*sy + u_LocalA.z（u_LocalB 同理 y）。
// 有了它，SDF 就能在"卡片自己的坐标系"里算 —— 卡片旋转时圆角与卡框才不会算歪。
uniform vec3 u_LocalA;
uniform vec3 u_LocalB;
// u_Rect = (中心x, 中心y, 半宽, 半高)，本地坐标
uniform vec4 u_Rect;
uniform float u_Radius;
uniform vec2 u_Border;
uniform vec4 u_BorderColor;
// u_Sheen = (中心 x, 半宽, 强度, 是否启用) —— 一道扫过卡面的光泽，坐标空间同 guiPos
uniform vec4 u_Sheen;
uniform vec3 u_SheenTint;
uniform vec4 ColorModulator;

in vec2 guiPos;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 local = vec2(u_LocalA.x * guiPos.x + u_LocalA.y * guiPos.y + u_LocalA.z,
                      u_LocalB.x * guiPos.x + u_LocalB.y * guiPos.y + u_LocalB.z);
    vec2 p = local - u_Rect.xy;
    vec2 q = abs(p) - u_Rect.zw + u_Radius;
    float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - u_Radius;

    // 抗锯齿宽度取自屏幕空间导数 —— 这样放大缩小都还是一像素软边，不会随 GUI scale 变糙
    float aa = fwidth(dist);
    float outer = 1.0 - smoothstep(-aa, 0.0, dist);
    float inner = 1.0 - smoothstep(-aa, 0.0, dist + u_Border.x);

    vec4 fill = vertexColor * ColorModulator;

    // 描边必须 source-over 叠在填充"之上"。直接把两者 mix 等于"用描边色替换填充" ——
    // 23% 白描边替换掉深色底之后，在亮地形上整条描边就隐形了（第一版就是这么翻的车）。
    float borderedA = u_BorderColor.a + fill.a * (1.0 - u_BorderColor.a);
    vec3 borderedRgb = borderedA <= 0.0
        ? fill.rgb
        : (u_BorderColor.rgb * u_BorderColor.a + fill.rgb * fill.a * (1.0 - u_BorderColor.a)) / borderedA;
    vec4 bordered = vec4(borderedRgb, borderedA);

    vec4 color = mix(fill, bordered, (1.0 - inner) * u_Border.y);
    color.a *= outer;

    if (u_Sheen.w > 0.5) {
        float band = 1.0 - smoothstep(0.0, max(u_Sheen.y, 0.001), abs(local.x - u_Sheen.x));
        color.rgb += band * u_Sheen.z * u_SheenTint;
    }

    if (color.a < 0.002) {
        discard;
    }
    fragColor = color;
}
