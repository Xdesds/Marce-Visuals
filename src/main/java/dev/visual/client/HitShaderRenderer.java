package dev.visual.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.visual.client.mixin.RenderPipelinesInvoker;
import dev.visual.client.mixin.RenderTypeInvoker;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class HitShaderRenderer {
    private static final EnumMap<ClientSettings.HitShader, RenderPipeline> PIPELINES = new EnumMap<>(ClientSettings.HitShader.class);
    private static final Map<Key, RenderType> TYPES = new HashMap<>();

    private HitShaderRenderer() {}

    static void initialize() {
        for (var shader : ClientSettings.HitShader.values()) {
            RenderPipeline pipeline = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("visual_client", "pipeline/hit_" + shader.name().toLowerCase(java.util.Locale.ROOT)))
                .withVertexShader(Identifier.fromNamespaceAndPath("visual_client", "core/hit_color"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("visual_client", "core/hit_color"))
                .withShaderDefine("HIT_MODE", shader.ordinal())
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false).withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .build();
            PIPELINES.put(shader, RenderPipelinesInvoker.marce$register(pipeline));
        }
    }

    public static RenderType type(HitEffects.Snapshot hit, Identifier texture) {
        return TYPES.computeIfAbsent(new Key(hit.shader(), texture), key ->
            RenderTypeInvoker.marce$create("marce_hit_" + key.shader.name(), RenderSetup.builder(PIPELINES.get(key.shader))
                .withTexture("Sampler0", texture)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING_FORWARD)
                .sortOnUpload().createRenderSetup()));
    }

    static void clear() { TYPES.clear(); }
    private record Key(ClientSettings.HitShader shader, Identifier texture) {}
}
