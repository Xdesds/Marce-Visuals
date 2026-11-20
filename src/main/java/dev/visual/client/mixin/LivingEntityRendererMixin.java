package dev.visual.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.visual.client.HitEffects;
import dev.visual.client.HitRenderState;
import dev.visual.client.HitShaderRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @Shadow protected EntityModel<LivingEntityRenderState> model;
    @Shadow public abstract Identifier getTextureLocation(LivingEntityRenderState state);

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void marce$extract(LivingEntity entity, LivingEntityRenderState state, float partial, CallbackInfo ci) {
        HitEffects.Snapshot hit = HitEffects.snapshot(entity, partial);
        ((HitRenderState) state).marce$setHit(hit);
        if (HitEffects.replacesRedOverlay(entity)) state.hasRedOverlay = false;
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void marce$submit(LivingEntityRenderState state, PoseStack poses, SubmitNodeCollector collector,
                              CameraRenderState camera, CallbackInfo ci) {
        HitEffects.Snapshot hit = ((HitRenderState) state).marce$getHit();
        if (hit == null || state.isInvisible) return;
        collector.order(1).submitModel(model, state, poses, HitShaderRenderer.type(hit, getTextureLocation(state)),
            state.lightCoords, OverlayTexture.NO_OVERLAY, hit.color(), null, 0, null);
    }
}
