#version 150

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

in vec3 Position;
in vec4 Color;

out vec2 guiPos;
out vec4 vertexColor;

void main() {
    // guiPos 是烘焙后的顶点位置（已经乘过 pose），SDF 的矩形也必须在同一个空间里
    guiPos = Position.xy;
    vertexColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
