package kassuk.addon.blackout.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalRunAway;
import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;
import kassuk.addon.blackout.enums.HoleType;
import kassuk.addon.blackout.enums.RotationType;
import kassuk.addon.blackout.managers.Managers;
import kassuk.addon.blackout.utils.*;
import kassuk.addon.blackout.utils.RaksuTone.RaksuPath;
import kassuk.addon.blackout.utils.RaksuTone.RaksuTone;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * @author OLEPOSSU
 */

public class AutoPvp extends BlackOutModule {
    public AutoPvp() {
        super(BlackOut.BLACKOUT, "自动PVP", "使用Baritone自动追踪玩家。专为crystalpvp.cc服务器优化");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSurround = settings.createGroup("Surround");
    private final SettingGroup sgTarget = settings.createGroup("Target");
    private final SettingGroup sgSuicide = settings.createGroup("Suicide");
    private final SettingGroup sgRotations = settings.createGroup("Rotations");
    private final SettingGroup sgEating = settings.createGroup("Eating");
    private final SettingGroup sgBaritone = settings.createGroup("Baritone");
    private final SettingGroup sgRaksu = settings.createGroup("Raksutone");

    //--------------------General--------------------//
    private final Setting<Boolean> autoMessage = sgGeneral.add(new BoolSetting.Builder()
        .name("自动消息")
        .description("重生时自动发送预设消息")
        .defaultValue(false)
        .build()
    );
    private final Setting<String> onSpawn = sgGeneral.add(new StringSetting.Builder()
        .name("重生消息")
        .description("重生时发送的预设消息内容")
        .defaultValue("/kit Blizzard")
        .build()
    );
    private final Setting<Integer> spawnRadius = sgTarget.add(new IntSetting.Builder()
        .name("重生点半径")
        .description("无目标时返回重生点的触发距离（格）")
        .defaultValue(50)
        .min(0)
        .sliderRange(0, 500)
        .build()
    );
    private final Setting<Boolean> baritone = sgGeneral.add(new BoolSetting.Builder()
        .name("Baritone")
        .description("使用Baritone进行移动。建议保持启用")
        .defaultValue(true)
        .build()
    );

    //--------------------General--------------------//
    //--------------------环绕设置--------------------//
    private final Setting<Boolean> surround = sgSurround.add(new BoolSetting.Builder()
        .name("自动环绕")
        .description("在目标附近自动构建防御性环绕结构")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> surroundMove = sgSurround.add(new BoolSetting.Builder()
        .name("环绕内移动")
        .description("在环绕结构内部移动以保持最佳防御位置")
        .defaultValue(false)
        .build()
    );

    //--------------------目标设置--------------------//
    private final Setting<Boolean> antiCamp = sgTarget.add(new BoolSetting.Builder()
        .name("防蹲守机制")
        .description("当接近目标时自动激活环绕防御")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> antiCampSeconds = sgTarget.add(new IntSetting.Builder()
        .name("蹲守判定时长(s)")
        .description("玩家静止超过此时长将被忽略")
        .defaultValue(30)
        .min(0)
        .sliderRange(0, 1000)
        .build()
    );
    private final Setting<Boolean> antiBurrow = sgTarget.add(new BoolSetting.Builder()
        .name("防潜地攻击")
        .description("忽略位于方块内部的敌对玩家")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> underY = sgTarget.add(new IntSetting.Builder()
        .name("高度上限")
        .description("仅追踪Y坐标低于此值的目标")
        .defaultValue(500)
        .min(0)
        .sliderRange(0, 500)
        .build()
    );
    private final Setting<Integer> yDiff = sgTarget.add(new IntSetting.Builder()
        .name("高度差容限")
        .description("忽略与自身Y坐标差异过大的目标")
        .defaultValue(500)
        .min(0)
        .sliderRange(0, 500)
        .build()
    );

    //--------------------自毁设置--------------------//
    private final Setting<Boolean> suicide = sgSuicide.add(new BoolSetting.Builder()
        .name("自动自毁")
        .description("物品数量低于阈值时自动执行自毁操作")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> totemAmount = sgSuicide.add(new IntSetting.Builder()
        .name("不死图腾数量")
        .description("当库存不死图腾数量低于此值时触发自毁")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 16)
        .build()
    );
    private final Setting<Integer> crystalAmount = sgSuicide.add(new IntSetting.Builder()
        .name("末影水晶数量")
        .description("当库存末影水晶数量低于此值时触发自毁")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 256)
        .build()
    );
    private final Setting<Integer> gappleAmount = sgSuicide.add(new IntSetting.Builder()
        .name("金苹果数量")
        .description("当库存附魔金苹果数量低于此值时触发自毁")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 256)
        .build()
    );
    private final Setting<Integer> expAmount = sgSuicide.add(new IntSetting.Builder()
        .name("经验瓶数量")
        .description("当库存经验瓶数量低于此值时触发自毁")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 256)
        .build()
    );
    private final Setting<Integer> obsidianAmount = sgSuicide.add(new IntSetting.Builder()
        .name("黑曜石数量")
        .description("当库存黑曜石数量低于此值时触发自毁")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 256)
        .build()
    );
    private final Setting<Boolean> eChests = sgSuicide.add(new BoolSetting.Builder()
        .name("统计末影箱")
        .description("将末影箱按1:8的比例折算为黑曜石数量")
        .defaultValue(true)
        .build()
    );

    //--------------------旋转设置--------------------//
    private final Setting<Boolean> rotate = sgRotations.add(new BoolSetting.Builder()
        .name("自动瞄准")
        .description("持续锁定目标敌人的头部位置")
        .defaultValue(true)
        .build()
    );

    //--------------------进食管理--------------------//
    private final Setting<Boolean> goldenApple = sgEating.add(new BoolSetting.Builder()
        .name("自动金苹果")
        .description("当生命值低于设定值时自动食用附魔金苹果")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> gappleHealth = sgEating.add(new IntSetting.Builder()
        .name("金苹果阈值")
        .description("触发金苹果食用的生命值百分比")
        .defaultValue(35)
        .min(0)
        .sliderRange(0, 36)
        .build()
    );
    private final Setting<Boolean> chorus = sgEating.add(new BoolSetting.Builder()
        .name("紫颂果应急")
        .description("在移动受阻时自动使用紫颂果脱困")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> chorusHealth = sgEating.add(new IntSetting.Builder()
        .name("紫颂果下限")
        .description("允许使用紫颂果的最低生命值")
        .defaultValue(14)
        .min(0)
        .sliderRange(0, 36)
        .build()
    );
    private final Setting<Integer> stuckTicks = sgEating.add(new IntSetting.Builder()
        .name("卡顿判定时长")
        .description("触发紫颂果使用的持续卡顿时间（刻）")
        .defaultValue(100)
        .min(0)
        .sliderRange(0, 1000)
        .build()
    );
    private final Setting<Boolean> speedPotion = sgEating.add(new BoolSetting.Builder()
        .name("速度药水")
        .description("自动饮用速度II药水增强机动性")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> speedHealth = sgEating.add(new IntSetting.Builder()
        .name("药水饮用阈值")
        .description("允许饮用速度药水的最低生命值")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 36)
        .build()
    );

    //--------------------Baritone--------------------//
    //--------------------路径计算--------------------//
    private final Setting<Boolean> assumeStep = sgBaritone.add(new BoolSetting.Builder()
        .name("自动跨越")
        .description("允许Baritone自动执行单格高度跨越")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> parkour = sgBaritone.add(new BoolSetting.Builder()
        .name("跑酷辅助")
        .description("启用Baritone的自动跑酷功能（跨越1-2格间隙）")
        .defaultValue(true)
        .build()
    );

    //--------------------Raksutone--------------------//
    private final Setting<Double> stepCooldown = sgRaksu.add(new DoubleSetting.Builder()
        .name("步进冷却")
        .description("两次连续步进动作之间的最小等待时间（秒）")
        .defaultValue(0.1)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );
    private final Setting<Double> rStepCooldown = sgRaksu.add(new DoubleSetting.Builder()
        .name("反向冷却")
        .description("两次反向步进动作之间的最小等待时间（秒）")
        .defaultValue(0.1)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    public static final String desc = "A setting for baritone. Updated on module activation.";

    private PlayerEntity target = null;
    private boolean inRange = false;

    private int stuckTimer = 0;
    private int eatingSlot = -1;
    private BlockPos lastPos = null;
    private final Map<PlayerEntity, Camp> camps = new HashMap<>();

    private long lastStep = 0;
    private long lastReverse = 0;

    private boolean shouldSuicide = false;
    private long lastRespawn = 0;

    private RaksuPath path = null;

    @Override
    public void onActivate() {
        camps.clear();
        settings();
    }

    @Override
    public void onDeactivate() {
        command("stop");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPacket(PacketEvent.Receive event) {
        if (autoMessage.get() && event.packet instanceof PlayerRespawnS2CPacket)
            ChatUtils.sendPlayerMsg(onSpawn.get());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onMove(PlayerMoveEvent event) {
        if (!inRange || shouldSuicide) {
            if (lastPos == null) lastPos = mc.player.getBlockPos();

            if (mc.player.getBlockPos().equals(lastPos)) stuckTimer++;
            else stuckTimer = 0;


            lastPos = mc.player.getBlockPos();

            if (path == null || path.path.isEmpty()) return;

            move(event.movement, path.path.get(0).pos().toCenterPos());
            return;
        }

        if (surroundMove.get()) {
            BlockPos walkPos = getSurroundWalk();

            if (walkPos != null) move(event.movement, walkPos.toCenterPos());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;


        mc.world.getPlayers().forEach(player -> {
            if (camps.containsKey(player)) {
                Camp camp = camps.get(player);

                if (player.getBlockPos().equals(camp.pos)) return;

                camps.remove(player);
            }

            camps.put(player, new Camp(player.getBlockPos(), System.currentTimeMillis()));
        });

        if (mc.currentScreen instanceof DeathScreen && System.currentTimeMillis() - lastRespawn > 1000) {
            mc.player.requestRespawn();
            lastRespawn = System.currentTimeMillis();
        }

        updateTarget();

        if (target == null) {
            if (Math.abs(mc.player.getBlockX()) > spawnRadius.get() || Math.abs(mc.player.getBlockZ()) > spawnRadius.get()) {
                if (baritone.get())
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(new BlockPos(0, 2, 0), 5));
            }
            stuckTimer = 0;
            return;
        }

        shouldSuicide = updateSuicide();

        if (shouldSuicide) {
            if (!Modules.get().isActive(Suicide.class))
                Modules.get().get(Suicide.class).toggle();
        } else {
            if (Modules.get().isActive(Suicide.class))
                Modules.get().get(Suicide.class).toggle();
        }

        eatUpdate();

        if (rotate.get()) {
            Managers.ROTATION.start(
                RotationUtils.getYaw(mc.player.getEyePos(), target.getEyePos()),
                RotationUtils.getPitch(mc.player.getEyePos(), target.getEyePos()),
                priority, RotationType.Other, Objects.hash(name + "stare"));
        }

        if (inRange && surround.get() && !shouldSuicide) {
            if (!Modules.get().isActive(SurroundPlus.class))
                Modules.get().get(SurroundPlus.class).toggle();
        } else if (Modules.get().isActive(SurroundPlus.class))
            Modules.get().get(SurroundPlus.class).toggle();

        if (shouldSuicide) {
            if (baritone.get())
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalRunAway(50, target.getBlockPos()));
            else path = RaksuTone.runAway(3, target.getBlockPos());

            return;
        }

        if (!inRange && (mc.player.getY() > 100 || baritone.get())) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(target.getBlockPos(), 3));
            path = null;
        } else {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(null);

            if (!baritone.get())
                path = RaksuTone.getPath(3, target.getBlockPos());
        }
    }

    private void move(Vec3d movement, Vec3d vec) {
        MovementUtils.moveTowards(movement, 0.2873, vec,
            System.currentTimeMillis() - lastStep > stepCooldown.get() * 1000 ? 2 : 0,
            System.currentTimeMillis() - lastReverse > rStepCooldown.get() * 1000 ? 3 : 0);

        if (movement.y >= 0.6) lastStep = System.currentTimeMillis();
        if (movement.y <= -0.6) lastReverse = System.currentTimeMillis();
    }

    private BlockPos getSurroundWalk() {
        Hole hole = getHole(mc.player.getBlockPos());

        if (hole == null) return null;

        BlockPos closest = null;

        for (BlockPos pos : hole.positions) {
            if (closest == null ||
                (target != null &&
                    pos.toCenterPos().distanceTo(target.getPos()) <
                        closest.toCenterPos().distanceTo(target.getPos()))) {
                closest = pos;
            }
        }
        return closest;
    }

    private boolean isCamper(PlayerEntity player) {
        return antiCamp.get() && camps.containsKey(player) && System.currentTimeMillis() - camps.get(player).time > antiCampSeconds.get() * 1000;
    }

    private void eatUpdate() {
        Predicate<ItemStack> food = getFood();

        if (food == null) {
            if (eatingSlot > -1) mc.options.useKey.setPressed(false);
            return;
        }

        int slot = InvUtils.findInHotbar(food).slot();

        if (Managers.HOLDING.slot != slot) InvUtils.swap(slot, false);

        if (eatingSlot != slot || (!mc.player.isUsingItem())) {
            eatingSlot = slot;
            mc.options.useKey.setPressed(true);
            Utils.rightClick();
        }
    }

    private Predicate<ItemStack> getFood() {
        if (shouldSuicide) return null;

        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (speedPotion.get() &&
            hp >= speedHealth.get() &&
            available(this::isSpeed) &&
            !mc.player.hasStatusEffect(StatusEffects.SPEED))
            return this::isSpeed;

        if (chorus.get() &&
            stuckTimer > stuckTicks.get() &&
            hp >= chorusHealth.get() &&
            available(i -> i.getItem() == Items.CHORUS_FRUIT))
            return i -> i.getItem() == Items.CHORUS_FRUIT;

        if (goldenApple.get() &&
            hp <= gappleHealth.get() &&
            available(OLEPOSSUtils::isGapple))
            return OLEPOSSUtils::isGapple;

        return null;
    }

    private boolean available(Predicate<ItemStack> predicate) {
        return InvUtils.findInHotbar(predicate).found();
    }

    private boolean isSpeed(ItemStack stack) {
        for (StatusEffectInstance instance : stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT).getEffects()) {
            if (instance.getEffectType() == StatusEffects.SPEED)
                return true;
        }
        return false;
    }

    private boolean inRange() {
        Goal goal = BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().getGoal();

        return goal == null ? Math.abs(mc.player.getBlockX() - target.getBlockX()) < 5 &&
            Math.abs(mc.player.getBlockZ() - target.getBlockZ()) < 5 &&
            Math.abs(mc.player.getBlockY() - target.getBlockY()) < 5 :
            goal.isInGoal(mc.player.getBlockPos());
    }

    private void command(String command) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(command);
    }

    private boolean updateSuicide() {
        if (!suicide.get()) return false;

        if (amountOf(i -> i.getItem() == Items.END_CRYSTAL) <= 0) return false;

        if (amountOf(OLEPOSSUtils::isGapple) <= gappleAmount.get()) return true;

        if (amountOf(i -> i.getItem() == Items.OBSIDIAN) + (eChests.get() ? amountOf(i -> i.getItem() == Items.ENDER_CHEST) * 8 : 0) <= obsidianAmount.get())
            return true;

        if (amountOf(i -> i.getItem() == Items.END_CRYSTAL) <= crystalAmount.get()) return true;

        if (amountOf(i -> i.getItem() == Items.EXPERIENCE_BOTTLE) <= expAmount.get()) return true;

        return amountOf(i -> i.getItem() == Items.TOTEM_OF_UNDYING) <= totemAmount.get();
    }

    private int amountOf(Predicate<ItemStack> predicate) {
        int a = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (!predicate.test(stack)) continue;

            a += stack.getCount();
        }
        return a;
    }

    private void updateTarget() {
        PlayerEntity closest = null;

        for (PlayerEntity pl : mc.world.getPlayers()) {
            if (pl == mc.player) continue;

            if (pl.isSpectator()) continue;

            if (Friends.get().isFriend(pl)) continue;

            if (pl.getHealth() <= 0) continue;

            if (antiBurrow.get() && OLEPOSSUtils.collidable(pl.getBlockPos())) continue;

            if (isCamper(pl)) continue;

            if (pl.getBlockY() > underY.get()) continue;

            if (pl.getBlockY() - mc.player.getBlockY() > yDiff.get()) continue;

            if (closest == null || mc.player.distanceTo(closest) > mc.player.distanceTo(pl)) closest = pl;
        }
        target = closest;

        inRange = target != null && inRange();
    }

    private void settings() {
        BaritoneAPI.getSettings().assumeStep.value = assumeStep.get();
        BaritoneAPI.getSettings().allowParkour.value = parkour.get();
        BaritoneAPI.getSettings().allowBreak.value = false;
        BaritoneAPI.getSettings().maxFallHeightNoWater.value = 1000; // cc doesn't have fall damage
        BaritoneAPI.getSettings().allowPlace.value = false;
        BaritoneAPI.getSettings().allowParkourPlace.value = false;

        BaritoneAPI.getSettings().logger.value = text -> {
        };
    }

    private Hole getHole(BlockPos pos) {
        if (HoleUtils.getHole(pos, 1).type == HoleType.Single) return null;

        // DoubleX
        if (HoleUtils.getHole(pos, 1).type == HoleType.DoubleX) return HoleUtils.getHole(pos, 1);

        if (HoleUtils.getHole(pos.add(-1, 0, 0), 1).type == HoleType.DoubleX)
            return HoleUtils.getHole(pos.add(-1, 0, 0), 1);

        // DoubleZ
        if (HoleUtils.getHole(pos, 1).type == HoleType.DoubleZ) return HoleUtils.getHole(pos, 1);

        if (HoleUtils.getHole(pos.add(0, 0, -1), 1).type == HoleType.DoubleZ)
            return HoleUtils.getHole(pos.add(0, 0, -1), 1);

        // Quad
        if (HoleUtils.getHole(pos, 1).type == HoleType.Quad) return HoleUtils.getHole(pos, 1);

        if (HoleUtils.getHole(pos.add(-1, 0, -1), 1).type == HoleType.Quad)
            return HoleUtils.getHole(pos.add(-1, 0, -1), 1);

        if (HoleUtils.getHole(pos.add(-1, 0, 0), 1).type == HoleType.Quad)
            return HoleUtils.getHole(pos.add(-1, 0, 0), 1);

        if (HoleUtils.getHole(pos.add(0, 0, -1), 1).type == HoleType.Quad)
            return HoleUtils.getHole(pos.add(0, 0, -1), 1);

        return null;
    }

    private record Camp(BlockPos pos, long time) {
    }
}
