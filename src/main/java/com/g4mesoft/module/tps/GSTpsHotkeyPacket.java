package com.g4mesoft.module.tps;

import java.io.IOException;

import com.g4mesoft.core.client.GSClientController;
import com.g4mesoft.core.server.GSServerController;
import com.g4mesoft.packet.GSIPacket;
import com.g4mesoft.util.GSDecodeBuffer;
import com.g4mesoft.util.GSEncodeBuffer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.network.ServerPlayerEntity;

public class GSTpsHotkeyPacket implements GSIPacket {

	private GSETpsHotkeyType type;
	
	public GSTpsHotkeyPacket() {
	}
	
	public GSTpsHotkeyPacket(GSETpsHotkeyType type) {
		this.type = type;
	}
	
	@Override
	public void read(GSDecodeBuffer buf) throws IOException {
		type = GSETpsHotkeyType.fromIndex((int)buf.readByte());
		if (type == null)
			throw new IOException("Invalid hotkey type");
	}

	@Override
	public void write(GSEncodeBuffer buf) throws IOException {
		buf.writeByte((byte)type.getIndex());
	}

	@Override
	public void handleOnServer(GSServerController controller, ServerPlayerEntity player) {
		controller.getTpsModule().onPlayerHotkey(player, type);
	}

	@Override
	@Environment(EnvType.CLIENT)
	public void handleOnClient(GSClientController controller) {
		controller.getTpsModule().performHotkeyAction(type);
	}
}
