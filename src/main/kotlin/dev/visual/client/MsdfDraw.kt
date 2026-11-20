package dev.visual.client

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import sweetie.evaware.luma.Luma
import sweetie.evaware.luma.shader.Shader
import sweetie.evaware.luma.texture.TextureHandle
import sweetie.evaware.luma.texture.TextureUploader
import sweetie.evaware.renderutil.helper.ColorUtil
import javax.imageio.ImageIO

internal object MsdfDraw {
    private const val ASSETS = "assets/visual_client"
    private val inter = Atlas("inter")
    private val symbols = Atlas("symbols")
    private val shader = Shader("$ASSETS/shaders/msdf.frag", "$ASSETS/shaders/msdf.vert")
    private val projection = shader.uniforms.mat4("uProjection")
    private val unitRange = shader.uniforms.vec2("uUnitRange")
    private val atlasSampler = shader.uniforms.int1("uAtlas")

    init {
        shader.vertices.float(2, 0).float(2, 1).float(4, 2)
    }

    enum class Icon(val codepoint: Int) {
        CANCEL(0xE888),
        WIFI(0xE63E),
        MANAGE_SEARCH(0xF02F),
        MUSIC(0xE405),
        BRUSH(0xE3AE),
        SETTINGS(0xE8B8),
        CHECK_SMALL(0xF88B),
        NOTIFICATIONS(0xE7F5),
        TUNE(0xE429),
        ISLAND(0xE8E9),
        SPEED(0xE9E4),
        BOLT(0xEA0B)
    }

    fun width(text: String, size: Float): Float {
        var advance = 0f
        var offset = 0
        while (offset < text.length) {
            val codepoint = text.codePointAt(offset)
            advance += inter.glyph(codepoint).advance
            offset += Character.charCount(codepoint)
        }
        return advance * size
    }

    fun ellipsize(text: String, size: Float, maxWidth: Float): String {
        if (width(text, size) <= maxWidth) return text
        val limit = maxWidth - width("…", size)
        var advance = 0f
        var offset = 0
        while (offset < text.length) {
            val codepoint = text.codePointAt(offset)
            advance += inter.glyph(codepoint).advance * size
            if (advance > limit) break
            offset += Character.charCount(codepoint)
        }
        return text.substring(0, offset).trimEnd() + "…"
    }

    fun text(text: String, x: Float, centerY: Float, size: Float, color: Int) {
        val baseline = centerY + inter.centerToBaseline * size
        var left = x
        var offset = 0
        while (offset < text.length) {
            val codepoint = text.codePointAt(offset)
            val glyph = inter.glyph(codepoint)
            inter.quad(glyph, left, baseline, size, color)
            left += glyph.advance * size
            offset += Character.charCount(codepoint)
        }
    }

    fun icon(icon: Icon, centerX: Float, centerY: Float, size: Float, color: Int) {
        val glyph = symbols.glyphs.getValue(icon.codepoint)
        val bounds = checkNotNull(glyph.plane)
        val left = centerX - (bounds.left + bounds.right) * 0.5f * size
        val baseline = centerY + (bounds.top + bounds.bottom) * 0.5f * size
        symbols.quad(glyph, left, baseline, size, color)
    }

    fun hasPending(): Boolean = inter.quads.isNotEmpty() || symbols.quads.isNotEmpty()

    fun flush() {
        try {
            draw(inter)
            draw(symbols)
        } finally {
            inter.quads.clear()
            symbols.quads.clear()
        }
    }

    fun close() {
        inter.close()
        symbols.close()
        shader.close()
    }

    private fun draw(atlas: Atlas) {
        if (atlas.quads.isEmpty()) return

        Luma.bindTexture(atlas.texture())
        shader.attach()
        Luma.applyGameMatrix(shader.uniforms, projection)
        shader.uniforms.int1(atlasSampler, 0)
        shader.uniforms.vec2(unitRange, atlas.range / atlas.width, atlas.range / atlas.height)

        val vertices = shader.vertices
        vertices.reserveVertices(atlas.quads.size * 6)
        for (quad in atlas.quads) {
            val (left, top, right, bottom) = quad.screen
            val (uLeft, vTop, uRight, vBottom) = quad.uv
            val red = ColorUtil.redf(quad.color)
            val green = ColorUtil.greenf(quad.color)
            val blue = ColorUtil.bluef(quad.color)
            val alpha = ColorUtil.alphaf(quad.color)
            vertices.vec2(left, top).vec2(uLeft, vTop).vec4(red, green, blue, alpha)
            vertices.vec2(left, bottom).vec2(uLeft, vBottom).vec4(red, green, blue, alpha)
            vertices.vec2(right, bottom).vec2(uRight, vBottom).vec4(red, green, blue, alpha)
            vertices.vec2(left, top).vec2(uLeft, vTop).vec4(red, green, blue, alpha)
            vertices.vec2(right, bottom).vec2(uRight, vBottom).vec4(red, green, blue, alpha)
            vertices.vec2(right, top).vec2(uRight, vTop).vec4(red, green, blue, alpha)
        }
        Luma.drawShader(shader)
        shader.detach()
    }

    private class Atlas(name: String) {
        private val path = "/$ASSETS/msdf/$name"
        private val metadata = resource("$path.json").bufferedReader(Charsets.UTF_8).use {
            JsonParser.parseReader(it).asJsonObject
        }
        private val dimensions = metadata.getAsJsonObject("atlas")
        val width = dimensions["width"].asFloat
        val height = dimensions["height"].asFloat
        val range = dimensions["distanceRange"].asFloat
        val centerToBaseline = metadata.getAsJsonObject("metrics").let {
            (it["ascender"].asFloat + it["descender"].asFloat) * 0.5f
        }
        val glyphs = metadata.getAsJsonArray("glyphs").associate { element ->
            val entry = element.asJsonObject
            val plane = entry.getAsJsonObject("planeBounds")?.let(::bounds)
            val uv = entry.getAsJsonObject("atlasBounds")?.let(::bounds)?.let {
                // Atlas metadata uses a bottom origin. TextureUploader uploads the top image row to v=0.
                Bounds(it.left / width, 1f - it.top / height, it.right / width, 1f - it.bottom / height)
            }
            entry["unicode"].asInt to Glyph(entry["advance"].asFloat, plane, uv)
        }
        val quads = ArrayList<Quad>()
        private var texture: TextureHandle? = null

        fun glyph(codepoint: Int): Glyph = glyphs[codepoint] ?: glyphs.getValue('?'.code)

        fun quad(glyph: Glyph, x: Float, baseline: Float, size: Float, color: Int) {
            val plane = glyph.plane ?: return
            val uv = glyph.uv ?: return
            quads.add(Quad(
                Bounds(
                    x + plane.left * size,
                    baseline - plane.top * size,
                    x + plane.right * size,
                    baseline - plane.bottom * size
                ),
                uv,
                color
            ))
        }

        fun texture(): TextureHandle {
            texture?.let { return it }
            val image = resource("$path.png").use { ImageIO.read(it) }
            check(image.width.toFloat() == width && image.height.toFloat() == height) {
                "MSDF image size does not match $path.json"
            }
            return TextureUploader.upload(image, mipmap = false).also { texture = it }
        }

        fun close() {
            quads.clear()
            texture?.close()
            texture = null
        }
    }

    private fun resource(path: String) = checkNotNull(MsdfDraw::class.java.getResourceAsStream(path)) {
        "Missing MSDF resource: $path"
    }

    private fun bounds(json: JsonObject) = Bounds(
        json["left"].asFloat, json["top"].asFloat, json["right"].asFloat, json["bottom"].asFloat
    )

    private data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)
    private data class Glyph(val advance: Float, val plane: Bounds?, val uv: Bounds?)
    private data class Quad(val screen: Bounds, val uv: Bounds, val color: Int)
}
