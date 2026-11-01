package com.example.woolmapart;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class PaintingTablePayloads {
	private static final Identifier SELECT_ID =
		Identifier.fromNamespaceAndPath("woolmapart", "painting_table_select");
	private static final Identifier STATUS_ID =
		Identifier.fromNamespaceAndPath("woolmapart", "painting_table_status");

	public record Select(String filename) implements CustomPacketPayload {
		public static final Type<Select> TYPE = CustomPacketPayload.createType("woolmapart/painting_table_select");
		public static final StreamCodec<RegistryFriendlyByteBuf, Select> CODEC =
			StreamCodec.of((buf, value) -> buf.writeUtf(value.filename(), 256),
				buf -> new Select(buf.readUtf(256)));

		@Override
		public Type<Select> type() {
			return TYPE;
		}
	}

	public record Status(int required, int remaining, String message) implements CustomPacketPayload {
		public static final Type<Status> TYPE = CustomPacketPayload.createType("woolmapart/painting_table_status");
		public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC =
			StreamCodec.of((buf, value) -> {
					buf.writeInt(value.required());
					buf.writeInt(value.remaining());
					buf.writeUtf(value.message(), 256);
				},
				buf -> new Status(buf.readInt(), buf.readInt(), buf.readUtf(256)));

		@Override
		public Type<Status> type() {
			return TYPE;
		}
	}

	private PaintingTablePayloads() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Select.TYPE, Select.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Status.TYPE, Status.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Select.TYPE, (payload, context) ->
			context.server().execute(() -> {
				if (context.player().containerMenu instanceof PaintingTableMenu menu) {
					WoolMapArt.selectPainting(context.player(), menu, payload.filename());
				}
			}));
	}

	public static void sendStatus(ServerPlayer player, int required, int remaining, String message) {
		ServerPlayNetworking.send(player, new Status(required, remaining, message));
	}
}
