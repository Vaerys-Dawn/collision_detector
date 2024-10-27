package com.github.vaerys;

import com.google.common.collect.ImmutableSet;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.entity.*;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.Util;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


// TODO: 22/10/2024 Rework entire resizing system, it should only happen on create or on edit via the commands
// TODO: 22/10/2024 This means that when the nbt is read it should just read the nbt and build the bounding box
// TODO: 22/10/2024 also, cooldown should be per player, easiest and least laggy way is to check timestamps
// TODO: 22/10/2024 this will mean cooldowns will not be tick based but server time based, maybe change cooldown to seconds or ms?
// TODO: 22/10/2024 when checking if the bounding box should be checked and activated, check if cooldowns.get(playerid) + cooldown <= currentTime
// TODO: 22/10/2024 it's shrimple really how easy it is

public class CollisionEntity extends Entity {


    public static Vector3f ERROR_POS = new Vector3f(0, 0, 0);

    public static final TrackedData<Vector3f> POS1 = DataTracker.registerData(CollisionEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
    public static final TrackedData<Vector3f> POS2 = DataTracker.registerData(CollisionEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
    private static final TrackedData<Float> WIDTH = DataTracker.registerData(CollisionEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> HEIGHT = DataTracker.registerData(CollisionEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<String> FUNCTION = DataTracker.registerData(CollisionEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> COOLDOWN = DataTracker.registerData(CollisionEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private Map<String, Long> playerCooldowns = new HashMap<>();
    private Box box;

    public CollisionEntity(EntityType<? extends Entity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(WIDTH, 1.0f);
        this.dataTracker.startTracking(HEIGHT, 1.0f);
        this.dataTracker.startTracking(FUNCTION, "");
        this.dataTracker.startTracking(COOLDOWN, 20);
        this.dataTracker.startTracking(POS1, ERROR_POS);
        this.dataTracker.startTracking(POS2, ERROR_POS);
    }

    @Override
    public Iterable<ItemStack> getArmorItems() {
        return List.of();
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("Function")) dataTracker.set(FUNCTION, nbt.getString("Function"));
        else dataTracker.set(FUNCTION, "");
        if (nbt.contains("Pos1")) dataTracker.set(POS1, deSerializeVec(nbt.getCompound("Pos1")));
        else dataTracker.set(POS1, ERROR_POS);
        if (nbt.contains("Pos2")) dataTracker.set(POS2, deSerializeVec(nbt.getCompound("Pos2")));
        else dataTracker.set(POS2, ERROR_POS);
        if (nbt.contains("Height")) dataTracker.set(HEIGHT, nbt.getFloat("Height"));
        else dataTracker.set(POS2, ERROR_POS);
        if (nbt.contains("Width")) dataTracker.set(WIDTH, nbt.getFloat("Width"));
        else dataTracker.set(POS2, ERROR_POS);
        if (nbt.contains("Cooldown")) dataTracker.set(COOLDOWN, nbt.getInt("Cooldown"));
        else dataTracker.set(COOLDOWN, 20);
        if (dataTracker.get(COOLDOWN) < 0) dataTracker.set(COOLDOWN, 0);
        if (nbt.contains("PlayerCooldowns")) playerCooldowns = deSerializePlayers(nbt.getCompound("PlayerCooldowns"));
        else playerCooldowns = new HashMap<>();
//        updateBoundingBox();
    }

    private Map<String, Long> deSerializePlayers(NbtCompound playerCooldowns) {
        Map<String, Long> cooldowns = new HashMap<>();
        Set<String> playerIds = playerCooldowns.getKeys();
        for (String playerId : playerIds) {
            cooldowns.put(playerId, playerCooldowns.getLong(playerId));
        }
        return cooldowns;
    }

    private NbtCompound serializePlayers() {
        NbtCompound nbt = new NbtCompound();
        playerCooldowns.forEach(nbt::putLong);
        return nbt;
    }


    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack) {
        // do noting
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("Function", dataTracker.get(FUNCTION));
        nbt.putInt("Cooldown", dataTracker.get(COOLDOWN));
        if (!dataTracker.get(POS1).equals(ERROR_POS)) nbt.put("Pos1", serializeVec(dataTracker.get(POS1)));
        if (!dataTracker.get(POS2).equals(ERROR_POS)) nbt.put("Pos2", serializeVec(dataTracker.get(POS2)));
        nbt.putFloat("Height", dataTracker.get(HEIGHT));
        nbt.putFloat("Width", dataTracker.get(WIDTH));
        nbt.put("PlayerCooldowns", serializePlayers());
    }

    private NbtCompound serializeVec(Vector3f vector3f) {
        NbtCompound nbt = new NbtCompound();
        nbt.putFloat("x", vector3f.x);
        nbt.putFloat("y", vector3f.y);
        nbt.putFloat("z", vector3f.z);
        return nbt;
    }

    private Vector3f deSerializeVec(NbtCompound compound) {
        return new Vector3f(compound.getFloat("x"), compound.getFloat("y"), compound.getFloat("z"));
    }

    @Override
    public void tick() {

    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (POS1.equals(data) || POS2.equals(data)) {
            updateBoundingBox();
            updateCollider();
        }
    }

    public void setCollider(Vector3f pos1, Vector3f pos2) {
        fixPoints(pos1, pos2);
        updateCollider();
    }

    private void updateCollider() {
        Vector3f pos1 = dataTracker.get(POS1);
        Vector3f pos2 = dataTracker.get(POS2);

        // sets y pos to smaller y cord
        float yPos = pos2.y;
        float xLength = pos1.x - pos2.x;
        float yLength = pos1.y - pos2.y;
        float zLength = pos1.z - pos2.z;

        float width = Math.max(xLength, zLength);

        float xPos = pos2.x + (xLength / 2);
        float zPos = pos2.z + (zLength / 2);

        dataTracker.set(WIDTH, width);
        dataTracker.set(HEIGHT, yLength);
        setPosition(xPos, yPos, zPos);
    }

    private void fixPoints(Vector3f pos1, Vector3f pos2) {
        float[] val1 = new float[]{pos1.x, pos1.y, pos1.z};
        float[] val2 = new float[]{pos2.x, pos2.y, pos2.z};
        // val1 = higher
        // val2 = lower

        for (int i = 0; i < 3; i++) {
            if (val2[i] > val1[i]) {
                float temp = val2[i];
                val2[i] = val1[i];
                val1[i] = temp;
            }
        }
        dataTracker.set(POS1, new Vector3f(val1[0] + 1, val1[1] + 1, val1[2] + 1));
        dataTracker.set(POS2, new Vector3f(val2[0], val2[1], val2[2]));
    }

    private void updateBoundingBox() {
        if (dataTracker.get(POS1).equals(ERROR_POS) || dataTracker.get(POS2).equals(ERROR_POS)) return;
        Vec3d corner1 = new Vec3d(dataTracker.get(POS1));
        Vec3d corner2 = new Vec3d(dataTracker.get(POS2));
        this.box = new Box(corner1, corner2);
    }

    @Override
    public void onPlayerCollision(PlayerEntity player) {
        super.onPlayerCollision(player);
        if (player.getEntityWorld().isClient) return;
        if (player.getServer() == null) return;
        if (box == null) return;

        List<Entity> entities = player.getEntityWorld().getOtherEntities(this, box);

        if (entities.contains(player)) {
            long timestampTick = (System.currentTimeMillis() * 20) / 1000;

            long cooldown = dataTracker.get(COOLDOWN);
            String uuid = player.getUuidAsString();

            if (dataTracker.get(COOLDOWN) != 0) {
                if (playerCooldowns.containsKey(uuid)) {
                    long coolDownTick = playerCooldowns.get(uuid);
                    long diff = timestampTick - coolDownTick;
                    if (diff < cooldown) {
                        return;
                    }
                }
                playerCooldowns.put(player.getUuidAsString(), timestampTick);
            }
            String function = dataTracker.get(FUNCTION);
            CollisionDetector.LOGGER.info("Interacting with player: {}, and executing function: {}", player.getDisplayName().getString(), function);
            player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(),
                    "execute as " + player.getDisplayName().getString() + " run function " + function);
        }
    }

    @Override
    public PistonBehavior getPistonBehavior() {
        return PistonBehavior.IGNORE;
    }

    @Override
    public boolean canAvoidTraps() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    private EntityDimensions getDimensions() {
        if (dataTracker.get(POS1).equals(ERROR_POS) || dataTracker.get(POS2).equals(ERROR_POS))
            return EntityDimensions.changing(1, 1);
        else return EntityDimensions.changing(dataTracker.get(WIDTH), dataTracker.get(HEIGHT));
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        return this.getDimensions();
    }

    @Override
    protected Box calculateBoundingBox() {
        return this.getDimensions().getBoxAt(this.getPos());
    }

    public void setCommand(String command) {
        dataTracker.set(FUNCTION, command);
    }

    public void setCooldown(int cooldown) {
        dataTracker.set(COOLDOWN, cooldown);
    }

    public static class Builder<T extends Entity> {
        private final EntityType.EntityFactory<T> factory;

        public Builder(EntityType.EntityFactory factory) {
            this.factory = factory;
        }

        public static <T extends Entity> Builder<T> create(EntityType.EntityFactory<T> factory) {
            return new Builder(factory);
        }

        public EntityType<T> build(String id) {
            Util.getChoiceType(TypeReferences.ENTITY_TREE, id);
            return new EntityType<>(factory,
                    SpawnGroup.MISC,
                    true,
                    true,
                    true,
                    true,
                    ImmutableSet.of(),
                    EntityDimensions.changing(1.0f, 1.0f),
                    5,
                    3,
                    FeatureFlags.VANILLA_FEATURES);
        }
    }
}
