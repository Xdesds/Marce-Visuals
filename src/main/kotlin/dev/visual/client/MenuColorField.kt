package dev.visual.client

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.shader.Shader

internal object MenuColorField {
    private const val SHADERS = "assets/visual_client/shaders"
    private val shader = Shader("$SHADERS/menu_color.frag", "$SHADERS/island_blur.vert")
    private val projection = shader.uniforms.mat4("uProjection")
    private val state = shader.uniforms.vec3("uHsv")
    private val dimensions = shader.uniforms.vec3("uField")
    private val fields = ArrayList<Field>(2)

    init {
        shader.vertices.float(2, 0).float(2, 1)
    }

    fun queue(x: Float, y: Float, width: Float, height: Float, hue: Float, saturation: Float, value: Float, hueBar: Boolean) {
        fields.add(Field(x, y, width, height, hue, saturation, value, hueBar))
    }

    fun flush() {
        if (fields.isEmpty()) return
        try {
            shader.attach()
            Luma.applyGameMatrix(shader.uniforms, projection)
            for (field in fields) {
                shader.uniforms.vec3(state, field.hue, field.saturation, field.value)
                shader.uniforms.vec3(dimensions, field.width, field.height, if (field.hueBar) 1f else 0f)
                val right = field.x + field.width
                val bottom = field.y + field.height
                val vertices = shader.vertices
                vertices.vec2(field.x, field.y).vec2(0f, 0f)
                vertices.vec2(field.x, bottom).vec2(0f, 1f)
                vertices.vec2(right, bottom).vec2(1f, 1f)
                vertices.vec2(field.x, field.y).vec2(0f, 0f)
                vertices.vec2(right, bottom).vec2(1f, 1f)
                vertices.vec2(right, field.y).vec2(1f, 0f)
                Luma.drawShader(shader)
            }
            shader.detach()
        } finally {
            fields.clear()
        }
    }

    fun close() {
        fields.clear()
        shader.close()
    }

    private data class Field(
        val x: Float, val y: Float, val width: Float, val height: Float,
        val hue: Float, val saturation: Float, val value: Float, val hueBar: Boolean
    )
}
