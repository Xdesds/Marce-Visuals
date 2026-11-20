package dev.visual.client

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.shader.Shader
import sweetie.evaware.luma.texture.TextureHandle
import sweetie.evaware.luma.texture.TextureUploader
import sweetie.evaware.renderutil.helper.ColorUtil
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.sin

internal object MarceLogo {
    private const val ASSETS = "assets/visual_client"
    private const val SOURCE_SIZE = 1254f
    private const val LEFT = 53f
    private const val TOP = 170f
    private const val RIGHT = 1196f
    private const val BOTTOM = 1123f

    enum class Layer { FULL, UPPER, LOWER, ORBIT }

    private val shader = Shader("$ASSETS/shaders/logo.frag", "$ASSETS/shaders/logo.vert")
    private val projection = shader.uniforms.mat4("uProjection")
    private val sampler = shader.uniforms.int1("uLogo")
    private var texture: TextureHandle? = null

    init {
        shader.vertices.float(2, 0).float(2, 1).float(2, 2).float(4, 3)
    }

    fun layer(layer: Layer, centerX: Float, centerY: Float, width: Float, angle: Float, opacity: Float, tint: Int) {
        if (opacity <= 0f) return
        val scale = width / (RIGHT - LEFT)
        val halfWidth = width * 0.5f
        val halfHeight = (BOTTOM - TOP) * scale * 0.5f
        val rotationX = cos(angle)
        val rotationY = sin(angle)
        val vertices = shader.vertices

        fun vertex(x: Float, y: Float, u: Float, v: Float) {
            vertices
                .vec2(centerX + rotationX * x - rotationY * y, centerY + rotationY * x + rotationX * y)
                .vec2(u / SOURCE_SIZE, v / SOURCE_SIZE)
                .vec2(layer.ordinal.toFloat(), opacity)
                .vec4(ColorUtil.redf(tint), ColorUtil.greenf(tint), ColorUtil.bluef(tint), ColorUtil.alphaf(tint))
        }

        vertex(-halfWidth, -halfHeight, LEFT, TOP)
        vertex(-halfWidth, halfHeight, LEFT, BOTTOM)
        vertex(halfWidth, halfHeight, RIGHT, BOTTOM)
        vertex(-halfWidth, -halfHeight, LEFT, TOP)
        vertex(halfWidth, halfHeight, RIGHT, BOTTOM)
        vertex(halfWidth, -halfHeight, RIGHT, TOP)
    }

    fun hasPending() = shader.vertices.hasVertices()

    fun flush() {
        if (!hasPending()) return
        Luma.bindTexture(texture ?: loadTexture().also { texture = it })
        shader.attach()
        Luma.applyGameMatrix(shader.uniforms, projection)
        shader.uniforms.int1(sampler, 0)
        Luma.drawShader(shader)
        shader.detach()
    }

    private fun loadTexture(): TextureHandle {
        val stream = checkNotNull(MarceLogo::class.java.getResourceAsStream("/$ASSETS/marce-logo.png"))
        return TextureUploader.upload(stream.use { ImageIO.read(it) }, mipmap = true)
    }

    fun close() {
        texture?.close()
        texture = null
        shader.close()
    }
}
