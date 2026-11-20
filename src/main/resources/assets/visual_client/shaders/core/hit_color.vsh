#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;
out vec3 surface;
out vec3 surfaceNormal;
out vec4 tint;
out vec2 texCoord;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    surface = Position;
    surfaceNormal = Normal;
    tint = Color * ColorModulator;
    texCoord = UV0;
}
