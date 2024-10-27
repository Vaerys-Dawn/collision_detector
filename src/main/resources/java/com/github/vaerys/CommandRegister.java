package com.github.vaerys;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.command.FunctionCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class CommandRegister {

    private static final String ENTITY_TAG = "Entity";
    private static final String COMMAND_TAG = "Command";
    private static final String POS_1_TAG = "Pos1";
    private static final String POS_2_TAG = "Pos2";
    private static final String COOLDOWN_TAG = "Cooldown";

    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            LiteralCommandNode<ServerCommandSource> spawnColDetector = literal("collider").requires(source -> source.hasPermissionLevel(2)).build();

            spawnColDetector.addChild(literal("create")
                    .then(argument(POS_1_TAG, BlockPosArgumentType.blockPos())
                            .then(argument(POS_2_TAG, BlockPosArgumentType.blockPos())
                                    .then(argument(COMMAND_TAG, StringArgumentType.greedyString()).suggests(FunctionCommand.SUGGESTION_PROVIDER)
                                            .executes(context ->
                                                    buildEntity(context,
                                                            BlockPosArgumentType.getBlockPos(context, POS_1_TAG),
                                                            BlockPosArgumentType.getBlockPos(context, POS_2_TAG),
                                                            StringArgumentType.getString(context, COMMAND_TAG)))
                                    ))).build());
            LiteralCommandNode<ServerCommandSource> remove = literal("remove")
                    .then(argument(ENTITY_TAG, EntityArgumentType.entity()).suggests(CommandRegister::findColliders)
                            .executes(context -> removeViaEntity(context, EntityArgumentType.getEntity(context, ENTITY_TAG)))).build();
            spawnColDetector.addChild(remove);


            LiteralArgumentBuilder<ServerCommandSource> edit = literal("edit");

            ArgumentCommandNode<ServerCommandSource, EntitySelector> entityArg = argument(ENTITY_TAG, EntityArgumentType.entity()).suggests(CommandRegister::findColliders).build();
            edit.then(entityArg);

            entityArg.addChild(literal("command")
                    .then(argument(COMMAND_TAG, StringArgumentType.greedyString()).suggests(FunctionCommand.SUGGESTION_PROVIDER)
                            .executes(context ->
                                    editViaEntity(context, EntityArgumentType.getEntity(context, ENTITY_TAG),
                                            StringArgumentType.getString(context, COMMAND_TAG)))).build());
            entityArg.addChild(literal("cooldown")
                    .then(argument(COOLDOWN_TAG, IntegerArgumentType.integer(0, Integer.MAX_VALUE))
                            .executes(context ->
                                    editViaEntity(context, EntityArgumentType.getEntity(context, ENTITY_TAG),
                                            IntegerArgumentType.getInteger(context, COOLDOWN_TAG)))).build());
            entityArg.addChild(literal("position")
                    .then(argument(POS_1_TAG, BlockPosArgumentType.blockPos())
                            .then(argument(POS_2_TAG, BlockPosArgumentType.blockPos())
                                    .executes(context ->
                                            editViaEntity(context, EntityArgumentType.getEntity(context, ENTITY_TAG),
                                                    BlockPosArgumentType.getBlockPos(context, POS_1_TAG),
                                                    BlockPosArgumentType.getBlockPos(context, POS_2_TAG))))).build());
            spawnColDetector.addChild(edit.build());

            dispatcher.getRoot().addChild(spawnColDetector);
        });
    }

    private static CompletableFuture<Suggestions> findColliders(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        if (!context.getSource().isExecutedByPlayer()) return builder.buildFuture();
        ServerPlayerEntity player = context.getSource().getPlayer();
        EntityHitResult result = raycastEntity(player, 20);
        if (result != null) builder.suggest(result.getEntity().getUuid().toString());

        return builder.buildFuture();
    }

    private static int editViaEntity(CommandContext<ServerCommandSource> context, Entity entity, BlockPos pos1, BlockPos pos2) {
        if (pos1.getY() < -64 || pos2.getY() < -64) {
            context.getSource().sendError(Text.literal("You cannot set a position below y=-64"));
            return 0;
        }
        if (!entity.getType().equals(CollisionDetector.COLLISION_ENTITY_ENTITY_TYPE)) {
            context.getSource().sendError(Text.of("This entity is not of type: " + EntityType.getId(CollisionDetector.COLLISION_ENTITY_ENTITY_TYPE).toString()));
            return 0;
        }
        CollisionEntity collider = (CollisionEntity) entity;
        collider.setCollider(pos1.toCenterPos().toVector3f().floor(), pos2.toCenterPos().toVector3f().floor());
        context.getSource().sendFeedback(() -> Text.literal("Modified position of Collider Entity."), false);
        return 1;
    }

    private static int editViaEntity(CommandContext<ServerCommandSource> context, Entity entity, String command) {
        if (entity.getType() != CollisionDetector.COLLISION_ENTITY_ENTITY_TYPE) {
            context.getSource().sendError(Text.of("This entity is not of type: " + EntityType.getId(CollisionDetector.COLLISION_ENTITY_ENTITY_TYPE).toString()));
            return 0;
        }
        CollisionEntity collider = (CollisionEntity) entity;
        collider.setCommand(command);
        context.getSource().sendFeedback(() -> Text.literal("Modified command of Collider Entity."), false);
        return 1;
    }

    private static int editViaEntity(CommandContext<ServerCommandSource> context, Entity entity, int cooldown) {
        if (entity.getType() != CollisionDetector.COLLISION_ENTITY_ENTITY_TYPE) {
            context.getSource().sendError(Text.of("This entity is not of type: " + EntityType.getId(CollisionDetector.COLLISION_ENTITY_ENTITY_TYPE).toString()));
            return 0;
        }
        CollisionEntity collider = (CollisionEntity) entity;
        collider.setCooldown(cooldown);
        context.getSource().sendFeedback(() -> Text.literal("Modified cooldown of Collider Entity."), false);
        return 1;
    }

    private static int removeViaEntity(CommandContext<ServerCommandSource> context, Entity entity) {
        if (!entity.getType().equals(CollisionDetector.COLLISION_ENTITY_ENTITY_TYPE)) {
            context.getSource().sendError(Text.of("This entity is not of type: " + EntityType.getId(CollisionDetector.COLLISION_ENTITY_ENTITY_TYPE).toString()));
            return 0;
        }
        entity.remove(Entity.RemovalReason.DISCARDED);
        context.getSource().sendFeedback(() -> Text.literal("Removed Collider Entity."), false);
        return 1;
    }


    private static CompletableFuture<Suggestions> getIDProvider(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        context.getSource().getServer();
        return null;
    }

    private static int buildEntity(CommandContext<ServerCommandSource> context, BlockPos pos1, BlockPos pos2, String command) {
        if (pos1.getY() < -64 || pos2.getY() < -64) {
            context.getSource().sendError(Text.literal("You cannot set a position below y=-64"));
            return 0;
        }
        ServerWorld world = context.getSource().getWorld();
        CollisionEntity entity = new CollisionEntity(CollisionDetector.COLLISION_ENTITY_ENTITY_TYPE, world);
        entity.setCommand(command);
        entity.setCollider(pos1.toCenterPos().toVector3f().floor(), pos2.toCenterPos().toVector3f().floor());
        world.spawnEntity(entity);
        context.getSource().sendFeedback(() -> Text.literal("Created new Collider Entity"), false);
        return 1;
    }


    public static EntityHitResult raycastEntity(ServerPlayerEntity player, double maxDistance) {
        Entity cameraEntity = player.getCameraEntity();
        if (cameraEntity != null) {
            Vec3d cameraPos = player.getCameraPosVec(1.0f);
            Vec3d rot = player.getRotationVec(1.0f);
            Vec3d rayCastContext = cameraPos.add(rot.x * maxDistance, rot.y * maxDistance, rot.z * maxDistance);
            Box box = cameraEntity.getBoundingBox().stretch(rot.multiply(maxDistance)).expand(1d, 1d, 1d);
            return ProjectileUtil.raycast(cameraEntity, cameraPos, rayCastContext, box, (entity -> /* any custom parameters here */ !entity.isSpectator()), maxDistance);
        }
        return null;
    }
}
