package dev.visual.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class VisualClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        var settings = ClientSettings.load();
        var discord = new DiscordRpc();
        var music = new WindowsMusic();
        var island = new DynamicIsland(settings, music);
        VisualDraw.initialize();
        HitEffects.initialize(settings);
        HitShaderRenderer.initialize();
        var hitboxes = new CustomHitboxes(settings);
        WorldRenderEvents.END_EXTRACTION.register(hitboxes::extract);
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (world.isClientSide() && target instanceof LivingEntity living) HitEffects.attack(player, living);
            return InteractionResult.PASS;
        });

        var key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.visual_client.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("visual_client", "controls"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            discord.update(settings.discordRpc, client.level == null ? "В главном меню"
                : client.hasSingleplayerServer() ? "Одиночная игра" : "Мультиплеер");
            HitEffects.tick(client);
            music.setEnabled(client.player != null && settings.island && settings.music);
            while (key.consumeClick()) {
                if (client.screen == null && client.level != null) {
                    client.setScreen(new VisualScreen(settings, island));
                }
            }
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("visual_client", "dynamic_island"),
            (graphics, delta) -> island.render(graphics));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> island.joined());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            music.close();
            island.reset();
            HitEffects.reset();
            HitShaderRenderer.clear();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            discord.close();
            music.close();
            VisualDraw.close();
        });
    }
}
