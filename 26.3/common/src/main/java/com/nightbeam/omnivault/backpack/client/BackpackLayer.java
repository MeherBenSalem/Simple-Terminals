package com.nightbeam.omnivault.backpack.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nightbeam.omnivault.backpack.BackpackItem;
import com.nightbeam.omnivault.backpack.BackpackTier;
import com.nightbeam.omnivault.client.ClientPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.List;

/** Draws the backpack worn in the chest slot on the player's back, using the same model as the block. */
public class BackpackLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Model-space (pixels) point that sits on the player's back just below the neck: top of the lid, back of the straps. */
    private static final float MODEL_TOP = 13.0f / 16;
    private static final float MODEL_BACK = 13.0f / 16;
    private static final float SCALE = 0.85f;

    public BackpackLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        ItemStack stack = chestItem(state);
        if (!(stack.getItem() instanceof BackpackItem item) || state.isInvisible) {
            return;
        }
        List<BlockStateModelPart> model = List.of(ClientPlatform.INSTANCE.getStandaloneModel(BackpackClient.model(item.tier())));
        int tint = item.tier() == BackpackTier.LEATHER ? DyedItemColor.getOrDefault(stack, DyedItemColor.LEATHER_COLOR) : -1;
        int[] tints = {0xFF000000 | tint};

        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        // Just below the neck, against the back of the torso (+Z is the player's back in model space).
        pose.translate(0, 1.0f / 16, 2.0f / 16);
        pose.rotateDegrees(Axis.XP, 180);
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-0.5f, -MODEL_TOP, -MODEL_BACK);
        // The glint is part of the render type.
        collector.submitBlockModel(pose, stack.hasFoil() ? Sheets.cutoutBlockItemGlintSheet() : Sheets.cutoutBlockItemSheet(), model, tints, light,
                OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
    }

    /** The avatar's chest slot. The render state only carries chest items that have an armour model, so look it up. */
    private static ItemStack chestItem(AvatarRenderState state) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && level.getEntity(state.id) instanceof LivingEntity entity ? entity.getItemBySlot(EquipmentSlot.CHEST) : ItemStack.EMPTY;
    }
}
