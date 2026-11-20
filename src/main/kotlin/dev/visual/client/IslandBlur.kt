package dev.visual.client

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.shader.Shader
import sweetie.evaware.luma.texture.TextureHandle
import kotlin.math.ceil
import kotlin.math.floor

internal object IslandBlur {
    private const val ASSETS = "assets/visual_client/shaders"
    private val shader = Shader("$ASSETS/island_blur.frag", "$ASSETS/island_blur.vert")
    private val projection = shader.uniforms.mat4("uProjection")
    private val sampler = shader.uniforms.int1("uBackdrop")
    private val copyOrigin = shader.uniforms.vec2("uCopyOrigin")
    private val copySize = shader.uniforms.vec2("uCopySize")
    private val textureSize = shader.uniforms.vec2("uTextureSize")
    private val radiusPixels = shader.uniforms.vec2("uRadiusPixels")
    private val shapeSize = shader.uniforms.vec2("uShapeSize")
    private val corners = shader.uniforms.vec2("uCorners")
    private val wave = shader.uniforms.vec3("uWave")
    private val opacity = shader.uniforms.float1("uOpacity")
    private val viewport = IntArray(4)
    private var texture: TextureHandle? = null
    private val shells = ArrayList<Shell>(2)

    init {
        shader.vertices.float(2, 0).float(2, 1)
    }

    fun queue(
        x: Float, y: Float, width: Float, height: Float, top: Float, bottom: Float,
        motion: Float, beat: Float, phase: Float, opacity: Float
    ) {
        shells.add(Shell(x, y, width, height, top, bottom, motion, beat, phase, opacity))
    }

    fun hasPending() = shells.isNotEmpty()

    fun flush() {
        if (shells.isEmpty()) return
        try {
            if (!Luma.platform.getViewport(viewport) || viewport[2] <= 0 || viewport[3] <= 0) return
            val scaleX = viewport[2] / Luma.platform.getGuiScaledWidth()
            val scaleY = viewport[3] / Luma.platform.getGuiScaledHeight()
            for (shell in shells) {
                draw(shell, scaleX, scaleY)
            }
        } finally {
            shells.clear()
        }
    }

    private fun draw(shape: Shell, scaleX: Float, scaleY: Float) {
        val reach = 2.6f * shape.motion + 1.45f * shape.beat
        val padding = if (reach > 0.001f) ceil(reach) + 1f else 0f
        val margin = 8 + padding
        val left = floor((shape.x - margin) * scaleX).toInt().coerceIn(0, viewport[2])
        val right = ceil((shape.x + shape.width + margin) * scaleX).toInt().coerceIn(0, viewport[2])
        val bottom = floor(viewport[3] - (shape.y + shape.height + margin) * scaleY).toInt().coerceIn(0, viewport[3])
        val top = ceil(viewport[3] - (shape.y - 8) * scaleY).toInt().coerceIn(0, viewport[3])
        val width = right - left
        val height = top - bottom
        if (width <= 0 || height <= 0) return

        val backdrop = ensureTexture(width, height)
        Luma.bindTexture(backdrop)
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, left, bottom, width, height)
        shader.attach()
        Luma.applyGameMatrix(shader.uniforms, projection)
        shader.uniforms.int1(sampler, 0)
        shader.uniforms.vec2(copyOrigin, left.toFloat(), bottom.toFloat())
        shader.uniforms.vec2(copySize, width.toFloat(), height.toFloat())
        shader.uniforms.vec2(textureSize, backdrop.width.toFloat(), backdrop.height.toFloat())
        shader.uniforms.vec2(radiusPixels, 5 * scaleX, 5 * scaleY)
        shader.uniforms.vec2(shapeSize, shape.width, shape.height)
        shader.uniforms.vec2(corners, shape.top, shape.bottom)
        shader.uniforms.vec3(wave, shape.motion, shape.beat, shape.phase)
        shader.uniforms.float1(opacity, shape.opacity)

        val x = shape.x
        val y = shape.y
        val w = shape.width
        val h = shape.height
        val vertices = shader.vertices
        vertices.vec2(x - padding, y).vec2(-padding, 0f)
        vertices.vec2(x - padding, y + h + padding).vec2(-padding, h + padding)
        vertices.vec2(x + w + padding, y + h + padding).vec2(w + padding, h + padding)
        vertices.vec2(x - padding, y).vec2(-padding, 0f)
        vertices.vec2(x + w + padding, y + h + padding).vec2(w + padding, h + padding)
        vertices.vec2(x + w + padding, y).vec2(w + padding, 0f)
        Luma.drawShader(shader)
        shader.detach()
    }

    private fun ensureTexture(width: Int, height: Int): TextureHandle {
        val previous = texture
        if (previous != null && previous.width >= width && previous.height >= height) return previous
        val capacityWidth = maxOf(previous?.width ?: 0, ((width + 127) / 128) * 128)
        val capacityHeight = maxOf(previous?.height ?: 0, ((height + 63) / 64) * 64)
        previous?.close()
        val handle = TextureHandle(GL11.glGenTextures(), capacityWidth, capacityHeight)
        texture = handle
        Luma.bindTexture(handle)
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, capacityWidth, capacityHeight,
            0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        return handle
    }

    fun close() {
        shells.clear()
        texture?.close()
        texture = null
        shader.close()
    }

    private data class Shell(
        val x: Float, val y: Float, val width: Float, val height: Float,
        val top: Float, val bottom: Float, val motion: Float, val beat: Float, val phase: Float, val opacity: Float
    )
}
