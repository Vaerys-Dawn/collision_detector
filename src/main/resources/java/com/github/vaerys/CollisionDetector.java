package com.github.vaerys;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CollisionDetector implements ModInitializer {
	public static final String MOD_ID = "collision_detector";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final EntityType<CollisionEntity> COLLISION_ENTITY_ENTITY_TYPE = Registry.register(
			Registries.ENTITY_TYPE, Identifier.of(MOD_ID, "collision_detector"),
			CollisionEntity.Builder.create(CollisionEntity::new).build("collision_detector"));

	@Override
	public void onInitialize() {
		CommandRegister.registerCommands();
	}


}