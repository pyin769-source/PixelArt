package com.example.woolmapart;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class PaintingTableClientNetworking {
	private PaintingTableClientNetworking() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PaintingTablePayloads.Status.TYPE, (payload, context) ->
			context.client().execute(() ->
				PaintingTableScreen.receiveStatus(payload.required(), payload.remaining(), payload.message())));
	}
}
