package dev.visual.client

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.shader.Shader

internal object MenuGradient {
    private val shader = Shader(
        "assets/visual_client/shaders/menu_gradient.frag",
        "assets/visual_client/shaders/island_blur.vert"
    )
    private val projection = shader.uniforms.mat4("uProjection")
    private val dimensions = shader.uniforms.vec3("uPanel")
    private val accent = shader.uniforms.vec3("uAccent")
    private val motion = shader.uniforms.vec3("uMotion")
    private val mouse = shader.uniforms.vec2("uMouse")
    private val click = shader.uniforms.vec3("uClick")
    private val style = shader.uniforms.vec2("uStyle")
    private val panels = ArrayList<Panel>(3)

    init {
        shader.vertices.float(2, 0).float(2, 1)
    }

    fun queue(
        x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int,
        time: Float, grain: Boolean, mouseX: Float, mouseY: Float,
        clickX: Float, clickY: Float, clickAge: Float, gradient: Boolean
    ) {
        panels.add(Panel(x, y, width, height, radius, color, time, grain, mouseX, mouseY, clickX, clickY, clickAge, gradient))
    }

    fun flush() {
        if (panels.isEmpty()) return
        try {
            shader.attach()
            Luma.applyGameMatrix(shader.uniforms, projection)
            for (panel in panels) {
                shader.uniforms.vec3(dimensions, panel.width, panel.height, panel.radius)
                shader.uniforms.vec3(accent,
                    ((panel.color shr 16) and 255) / 255f,
                    ((panel.color shr 8) and 255) / 255f,
                    (panel.color and 255) / 255f)
                shader.uniforms.vec3(motion, panel.time, if (panel.grain) 0.08f else 0f, if (panel.time > 0) 1f else 0f)
                shader.uniforms.vec2(mouse, panel.mouseX, panel.mouseY)
                shader.uniforms.vec3(click, panel.clickX, panel.clickY, panel.clickAge)
                shader.uniforms.vec2(style, if (panel.gradient) 1f else 0f, 0f)
                val vertices = shader.vertices
                vertices.vec2(panel.x, panel.y).vec2(0f, 0f)
                vertices.vec2(panel.x, panel.y + panel.height).vec2(0f, 1f)
                vertices.vec2(panel.x + panel.width, panel.y + panel.height).vec2(1f, 1f)
                vertices.vec2(panel.x, panel.y).vec2(0f, 0f)
                vertices.vec2(panel.x + panel.width, panel.y + panel.height).vec2(1f, 1f)
                vertices.vec2(panel.x + panel.width, panel.y).vec2(1f, 0f)
                Luma.drawShader(shader)
            }
            shader.detach()
        } finally {
            panels.clear()
        }
    }

    fun close() {
        panels.clear()
        shader.close()
    }

    private data class Panel(
        val x: Float, val y: Float, val width: Float, val height: Float, val radius: Float,
        val color: Int, val time: Float, val grain: Boolean,
        val mouseX: Float, val mouseY: Float, val clickX: Float, val clickY: Float, val clickAge: Float,
        val gradient: Boolean
    )
}
