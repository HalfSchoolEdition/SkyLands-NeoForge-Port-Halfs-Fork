
package net.deepcanion.skylands.client.renderer;

import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;

import net.deepcanion.skylands.entity.model.IceBlobModel;
import net.deepcanion.skylands.entity.IceBlobEntity;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

public class IceBlobRenderer extends GeoEntityRenderer<IceBlobEntity> {
	public IceBlobRenderer(EntityRendererProvider.Context renderManager) {
		super(renderManager, new IceBlobModel());
		this.shadowRadius = 1f;
	}

	@Override
	public RenderType getRenderType(IceBlobEntity animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
		// Render the opaque inner cube first. The translucent outer shell uses
		// a separate buffer in renderRecursively, avoiding depth/sorting issues.
		return RenderType.entityCutoutNoCull(getTextureLocation(animatable));
	}

	@Override
	public void renderRecursively(PoseStack poseStack, IceBlobEntity animatable, GeoBone bone,
			RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
			boolean isReRender, float partialTick, int packedLight, int packedOverlay, int color) {
		// The face has fully opaque texture pixels. Keep it attached to the animated
		// gel bone, but render it with the inner body (cutout) rather than with the
		// translucent shell, which otherwise causes inconsistent depth sorting.
		if (bone.getName().equals("face")) {
			RenderType faceType = RenderType.entityCutoutNoCull(getTextureLocation(animatable));
			super.renderRecursively(poseStack, animatable, bone, faceType, bufferSource,
					bufferSource.getBuffer(faceType), isReRender, partialTick, packedLight, packedOverlay, color);
			return;
		}

		if (bone.getName().equals("gel")) {
			RenderType gelType = RenderType.entityTranslucentCull(getTextureLocation(animatable));
			super.renderRecursively(poseStack, animatable, bone, gelType, bufferSource,
					bufferSource.getBuffer(gelType), isReRender, partialTick, packedLight, packedOverlay, color);
			return;
		}

		super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource,
				buffer, isReRender, partialTick, packedLight, packedOverlay, color);
	}

	@Override
	public void preRender(PoseStack poseStack, IceBlobEntity entity, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int color) {
		float scale = 1f;
		this.scaleHeight = scale;
		this.scaleWidth = scale;
		super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, color);
	}
}
