#version 330
#extension GL_ARB_separate_shader_objects : require

// Title-screen code rain (25w14craftmine parity): the snapshot's own fragment
// math (16px cells driven by the physical framebuffer size, per-column glyph
// runners with the pow-8 head), with uniforms delivered through the standard
// 26.3 UBO blocks instead of the snapshot's named uniforms. Block layouts
// mirror minecraft:shaders/include/dynamictransforms.glsl and globals.glsl
// exactly so the frontend binds the same UBOs by block name - the same
// proven shape as core/rain (the in-game "Dev Mode" sky).

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    float GlintAlpha;
    vec3 CameraOffset;
    float GameTime;
    vec2 ScreenSize;
    int MenuBlurRadius;
    int UseRgss;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

float random (float v) {
    return fract(sin(v) * 43758.5453123);
}

void main() {
    float columns = floor(ScreenSize.x / 16.0);
    float rows = floor(ScreenSize.y / 16.0);

    vec2 uv = texCoord0;

    float column = floor(uv.x * columns);
    float row = floor(uv.y * rows);

    float runners = (1.0 + 3.0 * random(column));
    float runner_phase = random(3.6 * column + 0.4231) * 2.0 + (GameTime * 300.0);

    float cells_per_runner = floor(rows / runners);
    float runner = floor(row / cells_per_runner);

    float runner_start = cells_per_runner * runner - runner_phase;
    float cell_to_runner_start = row - runner_start;

    float brightness = 1.0 - fract(cell_to_runner_start / cells_per_runner);
    const float e = 8.0;
    float green = pow(brightness, e);
    float not_green = pow(brightness, e * 4.0);

    vec4 glow = vec4(not_green, green, not_green, 1.0);

    const float glyph_width = 128.0 / 8.0;
    const float glyph_height = 128.0 / 8.0;

    float glyph_row = floor(random(3 * column + 5 * row) * 8.0);
    float glyph_column = floor(random(7 * column + 9 * row) * 17.0);

    float shift_x = fract(uv.x * columns);
    float shift_y = 1.0 - fract(uv.y * rows);
    vec4 glyph_color = texture(Sampler0, vec2((glyph_column + shift_x) / glyph_width, (glyph_row + shift_y) / glyph_height));

    fragColor = glow * glyph_color * ColorModulator;
}
