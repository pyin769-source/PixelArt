package com.example.woolmapart.mixin;

import com.example.woolmapart.BetterMapTextures;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;

@Mixin(MapRenderer.class)
public class MapRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void woolmapart$useBetterTexture(
		MapId mapId, MapItemSavedData data, MapRenderState state, CallbackInfo callbackInfo) {
		BetterMapTextures.scan(Minecraft.getInstance());
		Identifier texture = BetterMapTextures.textureFor(mapId);
		if (texture != null) state.texture = texture;
	}
}
