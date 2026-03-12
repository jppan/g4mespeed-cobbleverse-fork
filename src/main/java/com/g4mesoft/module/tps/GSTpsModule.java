package com.g4mesoft.module.tps;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import com.g4mesoft.G4mespeedMod;
import com.g4mesoft.GSExtensionInfo;
import com.g4mesoft.access.client.GSIAbstractClientPlayerEntityAccess;
import com.g4mesoft.access.common.GSIServerTickManagerAccess;
import com.g4mesoft.core.GSCoreExtension;
import com.g4mesoft.core.GSIModule;
import com.g4mesoft.core.GSIModuleManager;
import com.g4mesoft.core.client.GSClientController;
import com.g4mesoft.core.client.GSIClientModuleManager;
import com.g4mesoft.core.server.GSServerController;
import com.g4mesoft.hotkey.GSEKeyEventType;
import com.g4mesoft.hotkey.GSKeyManager;
import com.g4mesoft.setting.GSISettingChangeListener;
import com.g4mesoft.setting.GSSetting;
import com.g4mesoft.setting.GSSettingCategory;
import com.g4mesoft.setting.GSSettingManager;
import com.g4mesoft.setting.types.GSBooleanSetting;
import com.g4mesoft.setting.types.GSFloatSetting;
import com.g4mesoft.setting.types.GSIntegerSetting;
import com.g4mesoft.ui.util.GSMathUtil;
import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.ServerTickManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.GameMode;
import net.minecraft.world.tick.TickManager;

public class GSTpsModule implements GSIModule {

	public static final float DEFAULT_TPS = 20.0f;
	public static final float MIN_TPS = 0.01f;
	public static final float MAX_TPS = Float.MAX_VALUE;
	public static final float MS_PER_SEC = 1000.0f;

	private static final long SERVER_TPS_INTERVAL = 2000L;
	
	private static final float HOTKEY_SPEEDUP_AMOUNT_MIN = 1.10f;
	private static final float HOTKEY_SPEEDUP_AMOUNT_MAX = 10.0f;
	private static final float HOTKEY_SPEEDUP_AMOUNT_STEP = 0.05f;
	
	public static final GSSettingCategory TPS_CATEGORY = new GSSettingCategory("tps");
	public static final GSSettingCategory BETTER_PISTONS_CATEGORY = new GSSettingCategory("betterPistons");

	public static final String KEY_CATEGORY = "tps";
	
	public static final int PISTON_ANIM_PAUSE_END = 0;
	public static final int PISTON_ANIM_PAUSE_MIDDLE = 1;
	public static final int PISTON_ANIM_PAUSE_BEGINNING = 2;
	public static final int PISTON_ANIM_NO_PAUSE = 3;
	
	public static final int AUTOMATIC_PISTON_RENDER_DISTANCE = -1;
	
	private static final int HOTKEY_MODE_DISABLED = 0;
	private static final int HOTKEY_MODE_CREATIVE = 1;
	private static final int HOTKEY_MODE_ALL      = 2;
	
	private static final int HOTKEY_FEEDBACK_DISABLED = 0;
	private static final int HOTKEY_FEEDBACK_STATUS   = 1;
	private static final int HOTKEY_FEEDBACK_CHAT     = 2;
	
	public static final int TPS_LABEL_DISABLED   = 0;
	public static final int TPS_LABEL_TOP_LEFT   = 1;
	public static final int TPS_LABEL_TOP_CENTER = 2;
	public static final int TPS_LABEL_TOP_RIGHT  = 3;
	
	public static final int PRETTY_SAND_DISABLED         = 0;
	public static final int PRETTY_SAND_BEST_PERFORMANCE = 1;
	public static final int PRETTY_SAND_MOVE_ON_SERVER   = 2;
	public static final int PRETTY_SAND_FIDELITY         = PRETTY_SAND_MOVE_ON_SERVER;
	
	public static final DecimalFormat TPS_FORMAT = new DecimalFormat("0.0##", new DecimalFormatSymbols(Locale.ENGLISH));
	
	private static final String TPS_CACHE_FILE_NAME = "tps_cache.txt";
	
	private float tps;
	private final List<GSITpsDependant> listeners;

	private int serverSyncTimer;
	private GSTpsMonitor serverTpsMonitor;
	private long lastServerTpsTime;
	
	private boolean fixedMovementOnDefaultTps = false;
	private float serverTps = Float.NaN;
	private final GSServerTickTimer serverTimer = new GSServerTickTimer(this);
	
	private boolean sprinting = false;

	private GSIModuleManager manager;

	public final GSBooleanSetting cShiftPitch;
	public final GSBooleanSetting cSyncTick;
	public final GSIntegerSetting sSyncPacketInterval;
	public final GSIntegerSetting sTpsHotkeyMode;
	public final GSIntegerSetting sTpsHotkeyFeedback;
	public final GSFloatSetting sHotkeySpeedupAmount;
	public final GSBooleanSetting sRequireOP;
	public final GSBooleanSetting cNormalMovement;
	public final GSBooleanSetting cTweakerooFreecamHack;
	public final GSIntegerSetting cTpsLabel;
	public final GSBooleanSetting sBroadcastTps;
	public final GSBooleanSetting sRestoreTickrate;
	public final GSIntegerSetting sPrettySand;

	public final GSIntegerSetting cPistonAnimationType;
	public final GSBooleanSetting cCorrectPistonPushing;
	public final GSBooleanSetting cMovingLightSources;
	public final GSIntegerSetting cPistonRenderDistance;
	public final GSIntegerSetting sBlockEventDistance;
	public final GSBooleanSetting sParanoidMode;
	public final GSBooleanSetting sImmediateBlockBroadcast;
	
	public GSTpsModule() {
		tps = DEFAULT_TPS;
		listeners = new ArrayList<>();

		serverSyncTimer = 0;
		serverTpsMonitor = new GSTpsMonitor();
		lastServerTpsTime = Util.getMeasuringTimeMs();
		
		manager = null;
	
		cShiftPitch = new GSBooleanSetting("shiftPitch", true);
		cSyncTick = new GSBooleanSetting("syncTick", true);
		sSyncPacketInterval = new GSIntegerSetting("syncPacketInterval", 10, 1, 20);
		sTpsHotkeyMode = new GSIntegerSetting("hotkeyMode", HOTKEY_MODE_ALL, 0, 2);
		sTpsHotkeyFeedback = new GSIntegerSetting("hotkeyFeedback", HOTKEY_FEEDBACK_STATUS, 0, 2);
		sHotkeySpeedupAmount = new GSFloatSetting("hotkeySpeedupAmount", 2.0f, HOTKEY_SPEEDUP_AMOUNT_MIN, HOTKEY_SPEEDUP_AMOUNT_MAX, HOTKEY_SPEEDUP_AMOUNT_STEP);
		sRequireOP = new GSBooleanSetting("requireOP", true);
		cNormalMovement = new GSBooleanSetting("normalMovement", true);
		cTweakerooFreecamHack = new GSBooleanSetting("tweakerooFreecamHack", true);
		cTpsLabel = new GSIntegerSetting("tpsLabel", TPS_LABEL_DISABLED, 0, 3);
		sBroadcastTps = new GSBooleanSetting("broadcastTps", true);
		sRestoreTickrate = new GSBooleanSetting("restoreTickrate", false);
		sPrettySand = new GSIntegerSetting("prettySand", PRETTY_SAND_BEST_PERFORMANCE, 0, 2);
		
		cPistonAnimationType = new GSIntegerSetting("pistonAnimationType", PISTON_ANIM_PAUSE_END, 0, 3);
		cCorrectPistonPushing = new GSBooleanSetting("correctPistonPushing", false);
		cMovingLightSources = new GSBooleanSetting("movingLightSources", true);
		cPistonRenderDistance = new GSIntegerSetting("pistonRenderDistance", AUTOMATIC_PISTON_RENDER_DISTANCE, -1, 32);
		sBlockEventDistance = new GSIntegerSetting("blockEventDistance", 4, 0, 32);
		sParanoidMode = new GSBooleanSetting("paranoidMode", false);
		sImmediateBlockBroadcast = new GSBooleanSetting("immediateBlockBroadcast", false);
	}
	
	@Override
	public void init(GSIModuleManager manager) {
		this.manager = manager;
		
		resetTps();
		serverTpsMonitor.reset();
		
		manager.runOnServer(managerServer -> {
			if (sRestoreTickrate.get()) {
				try {
					setTps(readTps(getTpsCacheFile()));
				} catch (IOException e) {
					G4mespeedMod.GS_LOGGER.warn("Unable to read tps from cache.");
				}
			}
		});
	}

	@Override
	public void onClose() {
		clearTpsListeners();
		
		manager.runOnServer(serverManager -> {
			if (sRestoreTickrate.get()) {
				try {
					writeTps(tps, getTpsCacheFile());
				} catch (IOException e) {
					G4mespeedMod.GS_LOGGER.warn("Unable to write tps to cache.");
				}
			}
		});
		
		manager = null;
	}
	
	@Override
	public void registerClientSettings(GSSettingManager settings) {
		settings.registerSettings(TPS_CATEGORY,
			cShiftPitch,
			cSyncTick,
			cNormalMovement,
			G4mespeedMod.getTweakerooCompat().isCameraEntityRetreived() ? cTweakerooFreecamHack : null,
			cTpsLabel
		);

		settings.registerSettings(BETTER_PISTONS_CATEGORY,
			cPistonAnimationType,
			cCorrectPistonPushing,
			cMovingLightSources,
			cPistonRenderDistance
		);
		
		settings.addChangeListener(new GSISettingChangeListener() {
			@Override
			public void onSettingChanged(GSSettingCategory category, GSSetting<?> setting) {
				if (setting == cNormalMovement) {
					sendFixedMovementPacket();
					cTweakerooFreecamHack.setEnabledInGui(cNormalMovement.get());
				}
			}
		});
		// Tweakeroo hack is only enabled for normal movement setting.
		cTweakerooFreecamHack.setEnabledInGui(cNormalMovement.get());
	}

	@Override
	public void registerHotkeys(GSKeyManager keyManager) {
		keyManager.registerKey("toggleSpeedup", KEY_CATEGORY, GLFW.GLFW_KEY_M, 
				GSETpsHotkeyType.TOGGLE_SPEEDUP, this::onClientHotkey, GSEKeyEventType.PRESS);
	}
	
	@Override
	public void registerGlobalServerSettings(GSSettingManager settings) {
		settings.registerSettings(TPS_CATEGORY,
			sRequireOP,
			sSyncPacketInterval,
			sBroadcastTps,
			sTpsHotkeyMode,
			sTpsHotkeyFeedback,
			sHotkeySpeedupAmount,
			sRestoreTickrate,
			sPrettySand
		);
		settings.registerSettings(BETTER_PISTONS_CATEGORY,
			sBlockEventDistance,
			sParanoidMode,
			sImmediateBlockBroadcast
		);
		settings.addChangeListener(new GSISettingChangeListener() {
			@Override
			public void onSettingChanged(GSSettingCategory category, GSSetting<?> setting) {
				if (setting == sRequireOP) {
					// Send the command tree, since the tps command might no
					// longer be available an vice versa.
					manager.runOnServer(managerServer -> {
						PlayerManager playerManager = managerServer.getServer().getPlayerManager();
						for (ServerPlayerEntity player : playerManager.getPlayerList()) {
							// The command tree can only change for non-OP players.
							if (!player.hasPermissionLevel(GSServerController.OP_PERMISSION_LEVEL))
								playerManager.sendCommandTree(player);
						}
					});
				} else if (setting == sHotkeySpeedupAmount && isSpeedupEnabled()) {
					setTps(getSpeedupTps());
				}
			}
		});
	}
	
	@Override
	public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
		GSTpsCommand.registerCommand(dispatcher);
	}
	
	@Override
	public void tick(boolean paused) {
		manager.runOnServer(managerServer -> {
			if (!paused && !isSprinting()) {
				serverSyncTimer++;
				
				int syncInterval = sSyncPacketInterval.get();
				if (serverSyncTimer >= syncInterval) {
					managerServer.sendPacketToAll(new GSServerSyncPacket(syncInterval));
					serverSyncTimer = 0;
				}
			}
			
			serverTpsMonitor.update(1);
			
			if (sBroadcastTps.get()) {
				long now = Util.getMeasuringTimeMs();
				
				// Note that the interval may be less than zero in case of the
				// first tick or in case of overflow / underflow.
				long serverTpsInterval = now - lastServerTpsTime;
				if (serverTpsInterval < 0L || serverTpsInterval > SERVER_TPS_INTERVAL) {
					float averageTps = serverTpsMonitor.getAverageTps();
					managerServer.sendPacketToAll(new GSServerTpsPacket(averageTps));
					lastServerTpsTime = now;
				}
			}
		});
	}
	
	@Override
	public void onJoinG4mespeedServer(GSExtensionInfo coreInfo) {
		sendFixedMovementPacket();
	}
	
	public void onServerTps(float serverTps) {
		this.serverTps = serverTps;
		lastServerTpsTime = Util.getMeasuringTimeMs();
	}
	
	private void onClientHotkey(GSETpsHotkeyType hotkeyType) {
		manager.runOnClient(new Consumer<GSIClientModuleManager>() {
			
			@Override
			@Environment(EnvType.CLIENT)
			public void accept(GSIClientModuleManager managerClient) {
				MinecraftClient client = MinecraftClient.getInstance();
				if (client.currentScreen instanceof GameMenuScreen)
					return;
				
				if (managerClient.isG4mespeedServer()) {
					if (sTpsHotkeyMode.get() != HOTKEY_MODE_DISABLED) {
						// Only send the hotkey packet when the server
						// allows us to use hotkey controls.
						managerClient.sendPacket(new GSTpsHotkeyPacket(hotkeyType));
					}
				} else if (client.interactionManager != null) { 
					if (isGameModeAllowingHotkeys(client.interactionManager.getCurrentGameMode())) {
						performHotkeyAction(hotkeyType);
					}
				}
			}
		});
	}
	
	public void onPlayerHotkey(ServerPlayerEntity player, GSETpsHotkeyType type) {
		if (sTpsHotkeyMode.get() != HOTKEY_MODE_DISABLED && isPlayerAllowedTpsChange(player)) {
			if (isGameModeAllowingHotkeys(player.interactionManager.getGameMode())) {
				performHotkeyAction(type);
			} else {
				sendHotkeyFeedback(player, Text.translatable("play.info.hotkeysDisallowed"));
			}
		}
	}
	
	private void sendHotkeyFeedback(ServerPlayerEntity player, Text feedbackText) {
		switch (sTpsHotkeyFeedback.get()) {
		case HOTKEY_FEEDBACK_DISABLED:
			break;
		case HOTKEY_FEEDBACK_STATUS:
			player.sendMessage(feedbackText, true);
			break;
		case HOTKEY_FEEDBACK_CHAT:
			player.sendMessage(feedbackText, false);
			break;
		default:
			break;
		}
	}
	
	public boolean performHotkeyAction(GSETpsHotkeyType type) {
		if (type == GSETpsHotkeyType.TOGGLE_SPEEDUP) {
			if (isSpeedupEnabled()) {
				resetTps();
				return false;
			}
			setTps(getSpeedupTps());
			return true;
		}
		return isSpeedupEnabled();
	}

	private float getSpeedupTps() {
		return DEFAULT_TPS * sHotkeySpeedupAmount.get();
	}

	public boolean isSpeedupEnabled() {
		return GSMathUtil.equalsApproximate(tps, getSpeedupTps());
	}

	private Text getClientSpeedupStatusText() {
		if (isSpeedupEnabled())
			return Text.translatable("play.info.clientSpeedupEnabled");
		return Text.translatable("play.info.clientSpeedupDisabled");
	}

	@Override
	public void onDisconnectServer() {
		resetTps();
		
		serverTps = Float.NaN;
		sprinting = false;
	}

	public void addTpsListener(GSITpsDependant listener) {
		if (listener == null)
			throw new IllegalArgumentException("listener is null");
		synchronized(listeners) {
			listeners.add(listener);
			listener.tpsChanged(tps, 0.0f);
		}
	}

	public void removeTpsListener(GSITpsDependant listener) {
		synchronized(listeners) {
			listeners.remove(listener);
		}
	}
	
	private void clearTpsListeners() {
		synchronized(listeners) {
			listeners.clear();
		}
	}
	
	public void resetTps() {
		setTps(DEFAULT_TPS);
	}
	
	public void setTps(float tps) {
		tps = GSMathUtil.clamp(tps, MIN_TPS, MAX_TPS);
		
		if (!GSMathUtil.equalsApproximate(tps, this.tps)) {
			float oldTps = this.tps;
			this.tps = tps;
			
			synchronized(listeners) {
				for (GSITpsDependant listener : listeners)
					listener.tpsChanged(tps, oldTps);
			}
			
			manager.runOnServer(managerServer -> {
				MinecraftServer server = managerServer.getServer();
				ServerTickManager tickManager = server.getTickManager();
				
				if (!((GSIServerTickManagerAccess)tickManager).gs_isUpdatingTps()) {
					// Actually update the tps. This also sends a packet to the clients.
					tickManager.setTickRate(this.tps);
				}
				
				// Setup sync timer so it will send sync in the 
				// next tick (this ensures that the client had
				// time to react to the previous packet).
				serverSyncTimer = sSyncPacketInterval.get();

				// Reset the tps monitor. This should only happen
				// on the server, since it would otherwise create
				// a de-sync with the server tick cycle.
				serverTpsMonitor.reset();

				lastServerTpsTime = Util.getMeasuringTimeMs();
			});
		}
	}
	
	public boolean isGameModeAllowingHotkeys(GameMode gameMode) {
		switch (sTpsHotkeyMode.get()) {
		case HOTKEY_MODE_CREATIVE:
			return (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR || gameMode == GameMode.SURVIVAL);
		case HOTKEY_MODE_ALL:
			return true;
		case HOTKEY_MODE_DISABLED:
		default:
			return false;
		}
	}

	public boolean isPlayerAllowedTpsChange(PlayerEntity player) {
		if (sRequireOP.get()) {
			if (player instanceof ServerPlayerEntity serverPlayer && serverPlayer.hasPermissionLevel(GSServerController.OP_PERMISSION_LEVEL))
				return true;
			if (player instanceof ServerPlayerEntity serverPlayer) {
				return GSServerController.getInstance().isExtensionInstalled(serverPlayer, GSCoreExtension.UID);
			}
			return false;
		}
		return true;
	}
	
	private void sendFixedMovementPacket() {
		manager.runOnClient(clientManager -> {
			clientManager.sendPacket(new GSPlayerFixedMovementPacket(cNormalMovement.get()));
		});
	}

	public float getMsPerTick() {
		return MS_PER_SEC / tps;
	}

	public float getTps() {
		return tps;
	}
	
	public boolean isDefaultTps() {
		return GSMathUtil.equalsApproximate(tps, DEFAULT_TPS);
	}

	@Environment(EnvType.CLIENT)
	private TickManager getClientTickManager() {
		if (!manager.isClient())
			throw new IllegalStateException();
		MinecraftClient client = MinecraftClient.getInstance();
		return (client.world != null) ? client.world.getTickManager() : null;
	}
	
	@Environment(EnvType.CLIENT)
	private float getVanillaClientTps() {
		TickManager tm = getClientTickManager();
		return (tm != null) ? tm.getTickRate() : DEFAULT_TPS;
	}
	
	public boolean isSameTpsAsServer() {
		return GSMathUtil.equalsApproximate(tps, getVanillaClientTps());
	}
	
	public boolean isFrozen() {
		TickManager tm = getClientTickManager();
		if (tm == null)
			return false;
		return tm.isFrozen();
	}

	public boolean isStepping() {
		TickManager tm = getClientTickManager();
		if (tm == null)
			return false;
		return tm.isStepping();
	}

	public boolean isSprinting() {
		return sprinting;
	}
	
	private float readTps(File file) throws IOException {
		try (BufferedReader br = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			String line;
			if ((line = br.readLine()) != null)
				return Float.parseFloat(line);

			throw new IOException("Tps file is empty");
		} catch (NumberFormatException e) {
			throw new IOException("Invalid tps format", e);
		}
	}

	private void writeTps(float tps, File file) throws IOException {
		try (BufferedWriter bw = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
			bw.write(Float.toString(tps));
		}
	}
	
	private File getTpsCacheFile() {
		return new File(manager.getCacheFile(), TPS_CACHE_FILE_NAME);
	}
	
	@Environment(EnvType.CLIENT)
	public void onServerSyncPacket(int packetInterval) {
		serverTimer.onSyncPacket(packetInterval);
		
		// This is only for approximating the server tps
		serverTpsMonitor.update(packetInterval);
	}
	
	@Environment(EnvType.CLIENT)
	public float getServerTps() {
		if (sBroadcastTps.get() && Float.isFinite(serverTps))
			return serverTps;
		return serverTpsMonitor.getAverageTps();
	}

	@Environment(EnvType.CLIENT)
	public GSServerTickTimer getServerTimer() {
		return serverTimer;
	}
	
	@Environment(EnvType.CLIENT)
	public boolean isMainPlayerFixedMovement() {
		if (shouldUseFixedMovementCompensation()) {
			PlayerEntity player = GSClientController.getInstance().getPlayer();
			// Do not enable fixed movement if player has a vehicle.
			if (player != null && !player.hasVehicle())
				return true;
		}
		
		return false;
	}

	@Environment(EnvType.CLIENT)
	public boolean isPlayerFixedMovement(AbstractClientPlayerEntity player) {
		if (shouldUseFixedMovementCompensation()) {
			GSClientController controller = GSClientController.getInstance();
		
			// Check if is is the main player.
			if (player == controller.getPlayer())
				return isMainPlayerFixedMovement();
		
			if (!controller.isG4mespeedServer())
				return GSMathUtil.equalsApproximate(getVanillaClientTps(), DEFAULT_TPS);
			return ((GSIAbstractClientPlayerEntityAccess)player).gs_isFixedMovement();
		}
		
		return false;
	}

	@Environment(EnvType.CLIENT)
	private boolean shouldUseFixedMovementCompensation() {
		if (!cNormalMovement.get())
			return false;
		if (isSpeedupEnabled())
			return false;
		return !isDefaultTps() || fixedMovementOnDefaultTps;
	}
	
	public boolean isFixedMovementOnDefaultTps() {
		return fixedMovementOnDefaultTps;
	}

	public void setFixedMovementOnDefaultTps(boolean fixedMovementOnDefaultTps) {
		this.fixedMovementOnDefaultTps = fixedMovementOnDefaultTps;
	}
	
	@Environment(EnvType.CLIENT)
	public int getMovingBlockLuminance(BlockState state, BlockView world, BlockPos pos) {
		if (cMovingLightSources.get() && state.isOf(Blocks.MOVING_PISTON)) {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity instanceof PistonBlockEntity)
				return ((PistonBlockEntity)blockEntity).getPushedBlock().getLuminance();
		}
		return state.getLuminance();
	}
	
	@Environment(EnvType.CLIENT)
	public void onClientGameModeChanged(GameMode gameMode) {
		GSClientController controller = GSClientController.getInstance();
		if (controller.isConnectedToServer() && !controller.isG4mespeedServer() && !isGameModeAllowingHotkeys(gameMode)) {
			// User is connected to a non-g4mespeed server, and changed to a game mode that
			// does not allow client tps changes. Ensure that the player can not cheat by
			// resetting to default tps here.
			setTps(getVanillaClientTps());
		}
	}

	public void onTickSprintChanged(boolean sprinting) {
		if (sprinting != this.sprinting) {
			this.sprinting = sprinting;
			
			manager.runOnServer((managerServer) -> {
				managerServer.sendPacketToAll(new GSTickSprintUpdatePacket(sprinting));
			});
		}
	}
}
