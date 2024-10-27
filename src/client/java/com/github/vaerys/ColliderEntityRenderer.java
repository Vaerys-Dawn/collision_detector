package com.github.vaerys;

import com.mojang.blaze3d.platform.GlStateManager;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.BlazeEntityRenderer;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class ColliderEntityRenderer<T extends CollisionEntity> extends EmptyEntityRenderer {

    public ColliderEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(Entity entity, float yaw, float tickDelta, MatrixStack matrix, VertexConsumerProvider vertexProvider, int light) {
        super.render(entity, yaw, tickDelta, matrix, vertexProvider, light);

        boolean showBounding = MinecraftClient.getInstance().getEntityRenderDispatcher().shouldRenderHitboxes();
        if (!showBounding) return;

        matrix.push();
        matrix.translate(-entity.getX(), -entity.getY(), -entity.getZ());
        matrix.translate(0.01, 0.01, 0.01);
        VertexConsumer vertex = vertexProvider.getBuffer(RenderLayer.LINES);
        CollisionEntity collider = (CollisionEntity) entity;
        Vector3f pos1 = collider.getDataTracker().get(CollisionEntity.POS1);
        Vector3f pos2 = collider.getDataTracker().get(CollisionEntity.POS2);
        Box box = new Box(new Vec3d(pos1), new Vec3d(pos2));
        box = box.shrink(0.02, 0.02, 0.02);
        WorldRenderer.drawBox(matrix, vertex, box, 0, 1.0f, 1.0f, 1.0f);

        VertexConsumer vertex2 = vertexProvider.getBuffer(RenderLayer.getDebugFilledBox());
        WorldRenderer.renderFilledBox(matrix, vertex2, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 0, 1.0f, 1.0f, 0.25f);
        matrix.pop();
    }
}
