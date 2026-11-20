package dev.visual.client

import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.shader.Shader
import sweetie.evaware.luma.texture.TextureHandle
import sweetie.evaware.luma.texture.TextureUploader
import java.awt.image.BufferedImage

internal object AlbumArt {
    private const val SHADERS = "assets/visual_client/shaders"
    private val shader = Shader("$SHADERS/album_art.frag", "$SHADERS/island_blur.vert")
    private val projection = shader.uniforms.mat4("uProjection")
    private val sampler = shader.uniforms.int1("uCover")
    private val crop = shader.uniforms.vec2("uCrop")
    private val opacity = shader.uniforms.float1("uOpacity")
    private var pending: Cover? = null
    private var image: BufferedImage? = null
    private var texture: TextureHandle? = null

    init {
        shader.vertices.float(2, 0).float(2, 1)
    }

    fun draw(image: BufferedImage, x: Float, y: Float, size: Float, opacity: Float) {
        pending = Cover(image, x, y, size, opacity)
    }

    fun flush() {
        val cover = pending ?: return
        pending = null
        if (image !== cover.image) {
            texture?.close()
            texture = TextureUploader.upload(cover.image, mipmap = true)
            image = cover.image
        }
        val handle = checkNotNull(texture)
        Luma.bindTexture(handle)
        shader.attach()
        Luma.applyGameMatrix(shader.uniforms, projection)
        shader.uniforms.int1(sampler, 0)
        val side = minOf(handle.width, handle.height).toFloat()
        shader.uniforms.vec2(crop, side / handle.width, side / handle.height)
        shader.uniforms.float1(opacity, cover.opacity)

        val x = cover.x
        val y = cover.y
        val right = x + cover.size
        val bottom = y + cover.size
        val vertices = shader.vertices
        vertices.vec2(x, y).vec2(0f, 0f)
        vertices.vec2(x, bottom).vec2(0f, 1f)
        vertices.vec2(right, bottom).vec2(1f, 1f)
        vertices.vec2(x, y).vec2(0f, 0f)
        vertices.vec2(right, bottom).vec2(1f, 1f)
        vertices.vec2(right, y).vec2(1f, 0f)
        Luma.drawShader(shader)
        shader.detach()
    }

    fun close() {
        pending = null
        image = null
        texture?.close()
        texture = null
        shader.close()
    }

    private data class Cover(
        val image: BufferedImage, val x: Float, val y: Float, val size: Float, val opacity: Float
    )
}
