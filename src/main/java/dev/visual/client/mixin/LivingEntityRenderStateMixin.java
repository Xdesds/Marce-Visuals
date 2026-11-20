package dev.visual.client.mixin;

import dev.visual.client.HitEffects;
import dev.visual.client.HitRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
abstract class LivingEntityRenderStateMixin implements HitRenderState {
    @Unique private HitEffects.Snapshot marce$hit;
    public HitEffects.Snapshot marce$getHit() { return marce$hit; }
    public void marce$setHit(HitEffects.Snapshot hit) { marce$hit = hit; }
}
