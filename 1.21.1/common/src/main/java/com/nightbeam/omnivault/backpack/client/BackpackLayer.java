package com.nightbeam.omnivault.backpack.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nightbeam.omnivault.backpack.BackpackItem;
import com.nightbeam.omnivault.backpack.BackpackTier;
import com.nightbeam.omnivault.client.ClientPlatform;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/** Draws the backpack worn in the chest slot on the player's back, using the same model as the block. */
public class BackpackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};
    /** Model-space (pixels) point that sits on the player's back just below the neck: top of the lid, back of the straps. */
    private static final float MODEL_TOP = 13.0f / 16;
    private static final float MODEL_BACK = 13.0f / 16;
    private static final float SCALE = 0.85f;
    private final RandomSource random = RandomSource.create();

    public BackpackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack stack = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(stack.getItem() instanceof BackpackItem item) || player.isInvisible()) {
            return;
        }
        BakedModel model = ClientPlatform.INSTANCE.getStandaloneModel(BackpackClient.model(item.tier()));
        int tint = item.tier() == BackpackTier.LEATHER ? DyedItemColor.getOrDefault(stack, DyedItemColor.LEATHER_COLOR) : -1;
        float r = (tint >> 16 & 0xFF) / 255f;
        float g = (tint >> 8 & 0xFF) / 255f;
        float b = (tint & 0xFF) / 255f;

        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        // Just below the neck, against the back of the torso (+Z is the player's back in model space).
        pose.translate(0, 1.0f / 16, 2.0f / 16);
        pose.mulPose(Axis.XP.rotationDegrees(180));
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-0.5f, -MODEL_TOP, -MODEL_BACK);
        VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());
        renderQuads(pose, consumer, model, r, g, b, light);
        if (stack.hasFoil()) {
            renderQuads(pose, buffers.getBuffer(RenderType.entityGlint()), model, 1, 1, 1, light);
        }
        pose.popPose();
    }

    private void renderQuads(PoseStack pose, VertexConsumer consumer, BakedModel model, float r, float g, float b, int light) {
        for (Direction side : SIDES) {
            random.setSeed(42L);
            for (BakedQuad quad : model.getQuads(null, side, random)) {
                boolean tinted = quad.isTinted();
                consumer.putBulkData(pose.last(), quad, tinted ? r : 1f, tinted ? g : 1f, tinted ? b : 1f, 1f, light, OverlayTexture.NO_OVERLAY);
            }
        }
    }
}
