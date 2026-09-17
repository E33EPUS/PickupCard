#version 150 core

// PickupCard 形状着色器（顶点端）。
// 一个形状 = 一个四边形；UV0 的四角为 (0,0)-(1,1)，片段端用它重建局部像素坐标。
// 参考 ModernUI 的 SDF 形状方案（LGPL，仅借鉴算法思路，未复制代码）。

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 v_UV;
out vec4 v_Color;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    v_UV = UV0;
    v_Color = Color;
}
