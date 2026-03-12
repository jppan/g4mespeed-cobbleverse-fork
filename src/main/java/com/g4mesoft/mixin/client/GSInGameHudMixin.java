package com.g4mesoft.mixin.client;

import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.g4mesoft.core.client.GSClientController;
import com.g4mesoft.module.tps.GSTpsModule;
import com.g4mesoft.module.translation.GSTranslationModule;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;

@Mixin(InGameHud.class)
public abstract class GSInGameHudMixin {

	private static final int TPS_LABEL_MAGIN = 5;

	private static final int LABEL_BACKGROUND_COLOR = 0x60333333;
	private static final int LABEL_ENABLED_COLOR = 0xFF55DD55;

	@Shadow @Final private DebugHud debugHud;

	@Shadow @Final private MinecraftClient client;
	
	@Shadow public abstract TextRenderer getTextRenderer();

	@Inject(
		method = "renderBossBarHud",
		at = @At(
			value = "INVOKE",
			shift = Shift.BEFORE,
			target =
				"Lnet/minecraft/client/gui/hud/BossBarHud;render(" +
					"Lnet/minecraft/client/gui/DrawContext;" +
				")V"
		)
	)
	private void onRenderBeforeBossBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (GSClientController.getInstance().getTpsModule().cTpsLabel.get() == GSTpsModule.TPS_LABEL_TOP_CENTER) {
			Matrix3x2fStack matrixStack = context.getMatrices();
			matrixStack.pushMatrix();
			matrixStack.translate(0.0f, client.textRenderer.fontHeight + 5);
		}
	}

	@Inject(
		method = "renderBossBarHud",
		at = @At(
			value = "INVOKE",
			shift = Shift.AFTER,
			target =
				"Lnet/minecraft/client/gui/hud/BossBarHud;render(" +
					"Lnet/minecraft/client/gui/DrawContext;" +
				")V"
		)
	)
	private void onRenderAfterBossBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (GSClientController.getInstance().getTpsModule().cTpsLabel.get() == GSTpsModule.TPS_LABEL_TOP_CENTER)
			context.getMatrices().popMatrix();
	}
	
	@Inject(
		method = "render",
		at = @At(
			value = "INVOKE",
			shift = Shift.BEFORE, 
			target =
				"Lnet/minecraft/client/gui/hud/InGameHud;renderSubtitlesHud(" +
					"Lnet/minecraft/client/gui/DrawContext;" +
					"Z" +
				")V"
		)
	)
	private void onRenderBeforeSubtitles(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		GSClientController controller = GSClientController.getInstance();
		GSTpsModule tpsModule = controller.getTpsModule();
		
		int labelLocation = tpsModule.cTpsLabel.get();
		if (!debugHud.shouldShowDebugHud() && labelLocation != GSTpsModule.TPS_LABEL_DISABLED) {
			if (!tpsModule.isSpeedupEnabled())
				return;

			TextRenderer font = getTextRenderer();
			GSTranslationModule translationModule = controller.getTranslationModule();
			String speedupMultiplier = GSTpsModule.TPS_FORMAT.format(tpsModule.sHotkeySpeedupAmount.get());
			String indicator = translationModule.getFormattedTranslation("play.info.speedupIndicatorValue", speedupMultiplier);
			int indicatorW = font.getWidth(indicator);
			
			int lx;
			int ly = TPS_LABEL_MAGIN;
			int lw = indicatorW;
			int lh = font.fontHeight;

			switch (labelLocation) {
			case GSTpsModule.TPS_LABEL_TOP_CENTER:
				lx = (context.getScaledWindowWidth() - lw) / 2;
				break;
			case GSTpsModule.TPS_LABEL_TOP_RIGHT:
				lx = context.getScaledWindowWidth() - lw - TPS_LABEL_MAGIN + 1;
				break;
			case GSTpsModule.TPS_LABEL_TOP_LEFT:
			default:
				lx = TPS_LABEL_MAGIN;
				break;
				}
				
			context.fill(lx - 1, ly - 1, lx + lw, ly + lh, LABEL_BACKGROUND_COLOR);
			context.drawText(font, indicator, lx, ly, LABEL_ENABLED_COLOR, false);
		}
	}
}
