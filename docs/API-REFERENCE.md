# Clockwork 1.21.1 移植 · API 参考（证据版）
> 目标：**MC 1.21.1 / NeoForge 21.1.228**，前置 Create `6.0.10` + Sable `2.0.5` + Create: Aeronautics(Simulated) `1.3.2`。
> 所有签名取自编译期真实二进制的 `javap`，或真实源码 `文件:行号`。无证据者标 `未核实：<原因>`，绝不臆造。
## 0. 证据来源与工具
**库**（`clockwork_unoffical/libs/`）：`create-1.21.1-6.0.10.jar`、`sable-neoforge-1.21.1-2.0.5.jar`、
`sable-companion-common-1.21.1-1.6.0.jar`、`dev.simulated_team.simulated.simulated-neoforge-1.21.1-1.3.2.jar`、
`veil-neoforge-1.21.1-4.4.1.jar`、`Registrate-MC1.21-1.3.0+67.jar`、`flywheel-neoforge-api-1.21.1-1.0.6-44.jar`。

**`net.minecraft.**`** 在 ModDevGradle 产物：`clockwork_unoffical/build/moddev/artifacts/neoforge-21.1.228.jar`（MC+NeoForge 合并，23.5MB）、同目录 `-sources.jar`。
**FML 类**（`EventBusSubscriber`、`ModContainer`）在 `C:\Users\yql11\.gradle\caches\modules-2\files-2.1\net.neoforged.fancymodloader\loader\4.0.44\63433608b68302a442e3dba87880e54f42ea0583\loader-4.0.44.jar`。

```powershell
$javap = "C:\Users\yql11\java\dragonwell-21.0.8.0.8+9-GA\bin\javap.exe"
$jar   = "C:\Users\yql11\java\dragonwell-21.0.8.0.8+9-GA\bin\jar.exe"
$libs  = "C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\libs"
$mc    = "C:\Users\yql11\OneDrive\Desktop\mod\clockwork_1211fork\clockwork_unoffical\build\moddev\artifacts\neoforge-21.1.228.jar"

& $javap -classpath "$libs\sable-neoforge-1.21.1-2.0.5.jar" -public <全类名>   # -p 看 private
& $jar tf "$libs\sable-neoforge-1.21.1-2.0.5.jar" | Select-String "SubLevelAssembly"   # 找不到类先列 jar
```

> `javap` 会打印**协变桥接方法**。同一方法出现两条（如 `Pose3d logicalPose()` 与 `Pose3dc logicalPose()`）
> 表示真实实现返回 `Pose3d`、接口声明是 `Pose3dc`；只出现一条时那条就是声明类型。

**标记**：✅已核实（有 javap 或源码行号）/ ⚠️部分核实（间接证据，正文说明）/ ❌未核实（禁止照抄）。
## 1. Sable 装配
### 1.1 `SubLevelAssemblyHelper` ✅已核实
`& $javap -classpath "$libs\sable-neoforge-1.21.1-2.0.5.jar" -public dev.ryanhcode.sable.api.SubLevelAssemblyHelper`

```text
public class dev.ryanhcode.sable.api.SubLevelAssemblyHelper {
  public dev.ryanhcode.sable.api.SubLevelAssemblyHelper();   public static dev.ryanhcode.sable.sublevel.ServerSubLevel assembleBlocks(net.minecraft.server.level.ServerLevel, net.minecraft.core.BlockPos, java.lang.Iterable<net.minecraft.core.BlockPos>, dev.ryanhcode.sable.companion.math.BoundingBox3ic);
  public static void kickFromContainingSubLevel(net.minecraft.server.level.ServerLevel, dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem, dev.ryanhcode.sable.api.physics.PhysicsPipeline, dev.ryanhcode.sable.sublevel.ServerSubLevel, dev.ryanhcode.sable.sublevel.SubLevel);
  public static dev.ryanhcode.sable.api.SubLevelAssemblyHelper$GatherResult gatherConnectedBlocks(net.minecraft.core.BlockPos, net.minecraft.server.level.ServerLevel, int, dev.ryanhcode.sable.api.SubLevelAssemblyHelper$FrontierPredicate);
  public static void moveTrackingPoints(net.minecraft.server.level.ServerLevel, dev.ryanhcode.sable.companion.math.BoundingBox3ic, dev.ryanhcode.sable.sublevel.ServerSubLevel, dev.ryanhcode.sable.api.SubLevelAssemblyHelper$AssemblyTransform);
  public static void moveOtherStuff(net.minecraft.server.level.ServerLevel, dev.ryanhcode.sable.api.SubLevelAssemblyHelper$AssemblyTransform, java.lang.Iterable<net.minecraft.core.BlockPos>, dev.ryanhcode.sable.companion.math.BoundingBox3ic);
  public static void moveBlocks(net.minecraft.server.level.ServerLevel, dev.ryanhcode.sable.api.SubLevelAssemblyHelper$AssemblyTransform, java.lang.Iterable<net.minecraft.core.BlockPos>);
  public static void markAndNotifyBlock(net.minecraft.world.level.Level, net.minecraft.core.BlockPos, net.minecraft.world.level.chunk.LevelChunk, net.minecraft.world.level.block.state.BlockState, net.minecraft.world.level.block.state.BlockState, int, int);
}
```

坑：① `assembleBlocks` 参数序是 `(level, anchor, blocks, bounds)`，`anchor` 是锚点方块，不是包围盒；
② `gatherConnectedBlocks` 是 `(origin, level, maxBlocks, predicate)`，**与前者顺序相反**；
③ `moveTrackingPoints` 第 3 参 Simulated 传 `null`（`SimAssemblyHelper.java:133`），可空；
④ 私有 `needsBitSet(...)` 非 API，故意不列。
### 1.2 嵌套类型 ✅已核实
```text
public final class SubLevelAssemblyHelper$GatherResult extends java.lang.Record {
  public SubLevelAssemblyHelper$GatherResult(java.util.Set<BlockPos>, int, BoundingBox3i, SubLevelAssemblyHelper$GatherResult$State);
  public java.util.Set<net.minecraft.core.BlockPos> blocks();   public int checkedBlocks();   public dev.ryanhcode.sable.companion.math.BoundingBox3i boundingBox();   public SubLevelAssemblyHelper$GatherResult$State assemblyState();
}
public interface SubLevelAssemblyHelper$FrontierPredicate {
  public abstract boolean isValidConnection(net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState, net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState, net.minecraft.core.Direction);
}
public class SubLevelAssemblyHelper$AssemblyTransform {
  public SubLevelAssemblyHelper$AssemblyTransform(net.minecraft.core.BlockPos, net.minecraft.core.BlockPos, int, net.minecraft.world.level.block.Rotation, net.minecraft.server.level.ServerLevel);
  public net.minecraft.world.phys.Vec3 apply(net.minecraft.world.phys.Vec3);   public net.minecraft.core.BlockPos apply(net.minecraft.core.BlockPos);
  public net.minecraft.world.level.block.state.BlockState apply(net.minecraft.world.level.block.state.BlockState);   public net.minecraft.server.level.ServerLevel getLevel();   public net.minecraft.world.level.block.Rotation getRotation();
}
public final class SubLevelAssemblyHelper$GatherResult$State extends java.lang.Enum<...> {
  public static final ... SUCCESS;  public static final ... TOO_MANY_BLOCKS;  public static final ... NO_BLOCKS;   public final java.lang.String errorKey;
}
```
### 1.3 `SubLevel` / `ServerSubLevel` ✅已核实
```text
public abstract class dev.ryanhcode.sable.sublevel.SubLevel implements dev.ryanhcode.sable.companion.SubLevelAccess {
  public void tick();  public void updateBoundingBox();  public void forceUpdateGlobalBounds();   public net.minecraft.world.level.Level getLevel();
  public dev.ryanhcode.sable.companion.math.Pose3d  logicalPose();   // 协变实现
  public dev.ryanhcode.sable.companion.math.Pose3dc logicalPose();   // 桥接 = 接口声明
  public dev.ryanhcode.sable.companion.math.Pose3dc lastPose();   public dev.ryanhcode.sable.companion.math.BoundingBox3dc boundingBox();
  public dev.ryanhcode.sable.sublevel.plot.LevelPlot getPlot();   public void onRemove();  public boolean isRemoved();  public void markRemoved();   public void setUniqueId(java.util.UUID);  public java.util.UUID getUniqueId();
  public java.lang.String getName();  public void setName(java.lang.String);   // 省略 onPlotBoundsChanged/updateLastPose
}
public class dev.ryanhcode.sable.sublevel.ServerSubLevel extends dev.ryanhcode.sable.sublevel.SubLevel
        implements dev.ryanhcode.sable.api.physics.PhysicsPipelineBody {
  public final org.joml.Vector3d latestLinearVelocity;   public final org.joml.Vector3d latestAngularVelocity;   public ServerSubLevel(net.minecraft.server.level.ServerLevel, int, int, dev.ryanhcode.sable.companion.math.Pose3d);
  public int getRuntimeId();                                 // PhysicsPipelineBody
  public net.minecraft.server.level.ServerLevel getLevel();   // 协变窄化
  public dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot getPlot();
  public dev.ryanhcode.sable.api.physics.mass.MassData getMassTracker();    // ← 声明 MassData！
  public dev.ryanhcode.sable.api.physics.mass.MassTracker getSelfMassTracker();   public void buildMassTracker();   public void updateMergedMassData(float);   public void tick();
  public void applyQueuedForces(dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem, dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle, double);   public void prePhysicsTick(dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem, dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle, double);
  public dev.ryanhcode.sable.api.physics.force.QueuedForceGroup getOrCreateQueuedForceGroup(dev.ryanhcode.sable.api.physics.force.ForceGroup);   public it.unimi.dsi.fastutil.objects.Object2ObjectMap<ForceGroup, QueuedForceGroup> getQueuedForceGroups();
  public boolean isTrackingIndividualQueuedForces();  public void enableIndividualQueuedForcesTracking(boolean);   public foundry.veil.api.network.VeilPacketManager$PacketSink playerSink();
  public java.util.Collection<java.util.UUID> getTrackingPlayers();   public net.minecraft.nbt.CompoundTag getUserDataTag();  public void setUserDataTag(net.minecraft.nbt.CompoundTag);
  // 省略：getHeatMapManager / getFloatingBlockController / getReactionWheelManager / prePhysicsTickBegin /
  //       setSplitFrom / getSplitFromSubLevel / getSplitFromPose / clearSplitFrom / deleteAllEntities / setName
}
```

`getMassTracker()` **声明返回 `MassData`**（不是 `MergedMassTracker`），运行期实例是 `MergedMassTracker`：
`sable-main/common/src/main/java/dev/ryanhcode/sable/sublevel/ServerSubLevel.java:100`（`private MergedMassTracker massTracker;`）、
`:489`（`public MassData getMassTracker()`）。**按 `MassData` 用，别强转。**
`SubLevel.boundingBox()` 只列出一条 `BoundingBox3dc`，**无协变桥接**。
### 1.4 真实调用范例
`Simulated-Project-main/simulated/common/src/main/java/dev/simulated_team/simulated/util/SimAssemblyHelper.java:141-158`

```java
final Collection<BlockPos> blocks = contraption.getBlocks();
if (!blocks.isEmpty()) {
    final BoundingBox3i bounds = BoundingBox3i.from(blocks);            // static from(Iterable<BlockPos>) ✅
    final BlockPos anchor = blocks.stream().findFirst().get();
    final SubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks((ServerLevel) level, anchor, blocks, bounds);
    if (subLevel != null) {                                            // 返回值必须判空
        final BlockPos offsetBlocks = subLevel.getPlot().getCenterBlock().subtract(anchor);
    }
}
```

`BoundingBox3i`（`sable-companion-common` jar）✅：

```text
public final class dev.ryanhcode.sable.companion.math.BoundingBox3i implements dev.ryanhcode.sable.companion.math.BoundingBox3ic {
  public static final dev.ryanhcode.sable.companion.math.BoundingBox3ic EMPTY;   public BoundingBox3i(int, int, int, int, int, int);   public BoundingBox3i(net.minecraft.core.BlockPos, net.minecraft.core.BlockPos);   public BoundingBox3i(dev.ryanhcode.sable.companion.math.BoundingBox3ic);
  public static dev.ryanhcode.sable.companion.math.BoundingBox3i from(java.lang.Iterable<net.minecraft.core.BlockPos>);
}
```

拆解 + 踢出（`SimAssemblyHelper.java:53-54, :130, :133`；`SwivelBearingBlockEntity.java:463`）：

```java
final BoundingBox3i plotBounds = new BoundingBox3i(toDisassemble.getPlot().getBoundingBox());  // (BoundingBox3ic) ✅
final SubLevelAssemblyHelper.AssemblyTransform transform = new SubLevelAssemblyHelper.AssemblyTransform(
        subLevelAnchor, disassemblyGoal,
        rotation == Rotation.NONE ? 0 : (4 - rotation.ordinal()),   // 第 3 参是 int（90° 步数）
        rotation, (ServerLevel) level);
SubLevelAssemblyHelper.moveBlocks((ServerLevel) level, transform, blocks);
SubLevelAssemblyHelper.moveTrackingPoints((ServerLevel) level, plotBounds, null, transform);
SubLevelAssemblyHelper.kickFromContainingSubLevel(level, physicsSystem, pipeline, assembledSubLevel, containingSubLevel);
```
## 2. Sable 物理施力
### 2.1 `RigidBodyHandle` ✅已核实
```text
public class dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle {
  public static RigidBodyHandle of(net.minecraft.server.level.ServerLevel, dev.ryanhcode.sable.api.physics.PhysicsPipelineBody);   public static RigidBodyHandle of(dev.ryanhcode.sable.sublevel.ServerSubLevel);
  public RigidBodyHandle(dev.ryanhcode.sable.api.physics.PhysicsPipelineBody, dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem);   public void applyImpulseAtPoint(org.joml.Vector3dc, org.joml.Vector3dc);   public void applyImpulseAtPoint(net.minecraft.world.phys.Vec3, net.minecraft.world.phys.Vec3);
  public void applyLinearAndAngularImpulse(org.joml.Vector3dc, org.joml.Vector3dc);   public void applyLinearAndAngularImpulse(org.joml.Vector3dc, org.joml.Vector3dc, boolean);
  public void applyLinearImpulse(org.joml.Vector3dc);   public void applyAngularImpulse(org.joml.Vector3dc);   public void applyTorqueImpulse(org.joml.Vector3dc);
  public org.joml.Vector3dc getLinearVelocity();   public org.joml.Vector3dc getAngularVelocity();   public org.joml.Vector3d  getLinearVelocity(org.joml.Vector3d);   public org.joml.Vector3d getAngularVelocity(org.joml.Vector3d);
  public void applyForcesAndReset(dev.ryanhcode.sable.api.physics.force.ForceTotal);   public void addLinearAndAngularVelocity(org.joml.Vector3dc, org.joml.Vector3dc);   public void teleport(org.joml.Vector3dc, org.joml.Quaterniondc);   public boolean isValid();
}
```

> **没有 `of(SubLevel)` 重载** → 拿到 `SubLevel` 必须先强转：
> `RigidBodyHandle.of((ServerSubLevel) subLevel)`（`WheelMountBlockEntity.java:234`）。
### 2.2 `ForceTotal` / `QueuedForceGroup` / `ForceGroup(s)` ✅已核实
```text
public class dev.ryanhcode.sable.api.physics.force.ForceTotal {
  public ForceTotal();
  public void applyForces(dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle);   // @ApiStatus.Internal
  public void reset();   public void applyForceTotal(dev.ryanhcode.sable.api.physics.force.ForceTotal);   public void applyLinearAndAngularImpulse(org.joml.Vector3dc, org.joml.Vector3dc);   public void applyLinearImpulse(org.joml.Vector3dc);   public void applyAngularImpulse(org.joml.Vector3dc);
  public void applyTorqueImpulse(org.joml.Vector3dc);   public void applyImpulseAtPoint(dev.ryanhcode.sable.api.physics.mass.MassData,   org.joml.Vector3dc, org.joml.Vector3dc);   public void applyImpulseAtPoint(dev.ryanhcode.sable.sublevel.ServerSubLevel,     org.joml.Vector3dc, org.joml.Vector3dc);
  public void applyImpulseAtPoint(dev.ryanhcode.sable.api.physics.mass.MassTracker, net.minecraft.world.phys.Vec3, net.minecraft.world.phys.Vec3);   public org.joml.Vector3d getLocalForce();   public org.joml.Vector3d getLocalTorque();
}
```

**关键语义**（源码 `sable-main/common/src/main/java/dev/ryanhcode/sable/api/physics/force/ForceTotal.java:95-119`）：

```java
/** @param position the position inside the plot to apply the force at [m] */
public void applyImpulseAtPoint(final MassData massTracker, final Vector3dc position, final Vector3dc force) {
    this.localForce.add(force);
    position.sub(massTracker.getCenterOfMass(), this.temp);
    this.localTorque.add(this.temp.cross(force));
}
public void applyImpulseAtPoint(final ServerSubLevel massTracker, final Vector3dc position, final Vector3dc force) {
    this.applyImpulseAtPoint(massTracker.getMassTracker(), position, force);
}
```

① `position` 是 **plot 内部局部坐标**，不是世界坐标；② `force` 也是局部向量；③ 力矩由
`(position - centerOfMass) × force` 自动算。`applyForces`（`:25-37`）推完后把 `localForce/localTorque` 清零
→ **`ForceTotal` 可跨 tick 复用**。

```text
public class QueuedForceGroup {
  public QueuedForceGroup(dev.ryanhcode.sable.sublevel.ServerSubLevel);   public dev.ryanhcode.sable.api.physics.force.ForceTotal getForceTotal();   public void applyAndRecordPointForce(org.joml.Vector3dc, org.joml.Vector3dc);   public void recordPointForce(org.joml.Vector3dc, org.joml.Vector3dc);
  public java.util.List<QueuedForceGroup$PointForce> getRecordedPointForces();   public void reset();
}
public final class QueuedForceGroup$PointForce extends java.lang.Record {
  public QueuedForceGroup$PointForce(org.joml.Vector3dc, org.joml.Vector3dc);   public org.joml.Vector3dc point();   public org.joml.Vector3dc force();
}
public final class ForceGroup extends java.lang.Record {
  public ForceGroup(net.minecraft.network.chat.Component, net.minecraft.network.chat.Component, int, boolean);   public net.minecraft.network.chat.Component name();   public net.minecraft.network.chat.Component description();   public int color();   public boolean defaultDisplayed();
}
public class ForceGroups {
  public static final net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<ForceGroup>> REGISTRY_KEY;   public static final net.minecraft.core.Registry<ForceGroup> REGISTRY;
  public static final foundry.veil.platform.registry.RegistryObject<ForceGroup> GRAVITY, DRAG, LEVITATION,
      BALLOON_LIFT, PROPULSION, LIFT, MAGNETIC_FORCE;    // 实为 7 个独立 public static final 字段
  public static void register();   public static int count();
}
```

用法（`EndSeaPhysics.java:82-83`）：`serverSubLevel.getOrCreateQueuedForceGroup(ForceGroups.DRAG.get())`。
只想「每 tick 推一把」用 `ForceTotal` + `handle.applyForcesAndReset(...)` 即可。
### 2.3 `PhysicsPipeline` / `PhysicsPipelineBody` / `PhysicsPipelineProvider` ✅已核实
```text
public interface dev.ryanhcode.sable.api.physics.PhysicsPipeline {
  void init(org.joml.Vector3dc, double);   void dispose();   void tick();   void prePhysicsTicks();   void physicsTick(double);   void postPhysicsTicks();   void add(dev.ryanhcode.sable.sublevel.ServerSubLevel, dev.ryanhcode.sable.companion.math.Pose3dc);   void remove(dev.ryanhcode.sable.sublevel.ServerSubLevel);
  void add(dev.ryanhcode.sable.api.sublevel.KinematicContraption);   void remove(dev.ryanhcode.sable.api.sublevel.KinematicContraption);   dev.ryanhcode.sable.companion.math.Pose3d readPose(dev.ryanhcode.sable.sublevel.ServerSubLevel, dev.ryanhcode.sable.companion.math.Pose3d);
  dev.ryanhcode.sable.api.physics.object.rope.RopeHandle addRope(dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject);
  dev.ryanhcode.sable.api.physics.object.box.BoxHandle   addBox(dev.ryanhcode.sable.api.physics.object.box.BoxPhysicsObject);   void handleChunkSectionAddition(net.minecraft.world.level.chunk.LevelChunkSection, int, int, int, boolean);
  void handleBlockChange(net.minecraft.core.SectionPos, net.minecraft.world.level.chunk.LevelChunkSection, int, int, int, BlockState, BlockState);
  default void onStatsChanged(dev.ryanhcode.sable.sublevel.ServerSubLevel);   void teleport(dev.ryanhcode.sable.api.physics.PhysicsPipelineBody, org.joml.Vector3dc, org.joml.Quaterniondc);   void applyImpulse(dev.ryanhcode.sable.api.physics.PhysicsPipelineBody, org.joml.Vector3dc, org.joml.Vector3dc);
  void applyLinearAndAngularImpulse(dev.ryanhcode.sable.api.physics.PhysicsPipelineBody, org.joml.Vector3dc, org.joml.Vector3dc, boolean);   default void addLinearAndAngularVelocity(dev.ryanhcode.sable.api.physics.PhysicsPipelineBody, org.joml.Vector3dc, org.joml.Vector3dc);
  default org.joml.Vector3d getLinearVelocity(dev.ryanhcode.sable.api.physics.PhysicsPipelineBody, org.joml.Vector3d);   default org.joml.Vector3d getAngularVelocity(dev.ryanhcode.sable.api.physics.PhysicsPipelineBody, org.joml.Vector3d);
  void wakeUp(dev.ryanhcode.sable.api.physics.PhysicsPipelineBody);   // 省略 handleChunkSectionRemoval / resetVelocity
  default <T extends dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle> T addConstraint(
          dev.ryanhcode.sable.api.physics.PhysicsPipelineBody, dev.ryanhcode.sable.api.physics.PhysicsPipelineBody,
  dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration<T>);   default void updateConfigFrom(dev.ryanhcode.sable.physics.config.PhysicsConfigData);   int getNextRuntimeID();
}
public interface dev.ryanhcode.sable.api.physics.PhysicsPipelineBody {
  public static final int NULL_RUNTIME_ID;   int getRuntimeId();   dev.ryanhcode.sable.api.physics.mass.MassData getMassTracker();   boolean isRemoved();
}
public interface dev.ryanhcode.sable.api.physics.PhysicsPipelineProvider {
  public static final dev.ryanhcode.sable.api.physics.PhysicsPipelineProvider INSTANCE;   // ServiceLoader
  dev.ryanhcode.sable.api.physics.PhysicsPipeline createPipeline(net.minecraft.server.level.ServerLevel);
}
```

`addConstraint` 是泛型 default 方法，`T` 由第三参推断，可直接赋给具体 `*Handle`。
### 2.4 `SubLevelPhysicsSystem` ✅已核实
```text
public class dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem implements dev.ryanhcode.sable.api.sublevel.SubLevelObserver {
  public static final int DEFAULT_RESIDENT_CAPACITY;   public static final boolean USE_TICKETS_FOR_QUERIES;   public static boolean IN_PHYSICS_STEP;   public static SubLevelPhysicsSystem currentlySteppingSystem;   public SubLevelPhysicsSystem(net.minecraft.server.level.ServerLevel);
  public static SubLevelPhysicsSystem get(net.minecraft.world.level.Level);   public static SubLevelPhysicsSystem require(net.minecraft.world.level.Level);
  public static SubLevelPhysicsSystem getCurrentlySteppingSystem();   public void initialize();   public void onConfigUpdated();   public void tick(dev.ryanhcode.sable.api.sublevel.SubLevelContainer);
  public void updatePose(dev.ryanhcode.sable.sublevel.ServerSubLevel);   public boolean recoverSubLevel(dev.ryanhcode.sable.sublevel.ServerSubLevel);   public boolean tryPunch(java.util.UUID, int);
  public dev.ryanhcode.sable.api.physics.PhysicsPipeline getPipeline();   public dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle getPhysicsHandle(dev.ryanhcode.sable.sublevel.ServerSubLevel);
  public void wakeUpObjectsAt(int, int, int);   public double getPartialPhysicsTick();   public boolean getPaused();   public void setPaused(boolean);   public java.lang.Iterable<dev.ryanhcode.sable.sublevel.SubLevel> queryIntersecting(dev.ryanhcode.sable.companion.math.BoundingBox3dc);
  public dev.ryanhcode.sable.physics.config.PhysicsConfigData getConfig();   public java.lang.Iterable<dev.ryanhcode.sable.api.physics.object.ArbitraryPhysicsObject> getArbitraryObjects();
  public net.minecraft.server.level.ServerLevel getLevel();      // ← Simulated 的 onPhysicsTick 全靠它
  public void addObject(dev.ryanhcode.sable.api.physics.object.ArbitraryPhysicsObject);   public int getNextRuntimeID();
  // 省略：removeObject / handleBlockChange / updateMassDataFromBlockChange / getTicketManager / onSubLevelAdded / onSubLevelRemoved
}
```

> ✅ `getPhysicsHandle(ServerSubLevel)` 等价于 `RigidBodyHandle.of(...)`。
> 省略成员：`onSubLevelAdded/onSubLevelRemoved`（`SubLevelObserver` 默认实现）、
> `handleBlockChange` / `updateMassDataFromBlockChange` / `getTicketManager`（内部用）。
### 2.5 `api.event` ✅已核实
```text
public interface SablePrePhysicsTickEvent  { void prePhysicsTick(SubLevelPhysicsSystem, double); }
public interface SablePostPhysicsTickEvent { void postPhysicsTick(SubLevelPhysicsSystem, double); }
public interface SableSubLevelContainerReadyEvent { void onSubLevelContainerReady(net.minecraft.world.level.Level, SubLevelContainer); }
public class dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent extends net.neoforged.bus.api.Event {
  public ForgeSablePrePhysicsTickEvent(dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem, double);   public dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem getPhysicsSystem();   public double getTimeStep();
}
public class dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent extends net.neoforged.bus.api.Event { /* 同上 */ }
```

⚠️ 前三个是「**回调接口**」，不是 NeoForge 事件类；后两个才是 NeoForge 事件。推荐用
`SableEventPlatform.INSTANCE.onPhysicsTick(...)`（§5.2）。
## 3. Sable 方块侧接口（`dev.ryanhcode.sable.api.block.**`）
全部 ✅已核实；下列为 `javap -public` 逐字输出（省略 `Compiled from` 行）。
### 3.1 `BlockEntitySubLevelActor` —— 方块实体物理 tick 的正门
```text
public interface dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor {
  public default void sable$tick(dev.ryanhcode.sable.sublevel.ServerSubLevel);   public default void sable$physicsTick(dev.ryanhcode.sable.sublevel.ServerSubLevel, dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle, double);
  public default java.lang.Iterable<dev.ryanhcode.sable.sublevel.SubLevel> sable$getLoadingDependencies();   public default java.lang.Iterable<dev.ryanhcode.sable.sublevel.SubLevel> sable$getConnectionDependencies();
}
```

真实实现（`HandleBlockEntity.java:36, :84-90`）：

```java
public class HandleBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor {
    @Override
    public void sable$physicsTick(final ServerSubLevel subLevel, final RigidBodyHandle handle, final double timeStep) {
        this.checkPlayers();
        for (final HandleConstraint constraint : this.players.values()) {
            constraint.physicsTick(subLevel, handle);
        }
    }
}
```

依赖项可返回 `null`：`SwivelBearingPlateBlockEntity.java:133-144` 无父级时 `return null;`。
### 3.2 `BlockSubLevelLiftProvider` —— 升力 / 阻力
```text
public interface BlockSubLevelLiftProvider {
  public static final net.minecraft.core.Direction[] DIRECTIONS;
  public static final org.joml.Vector3d LIFT_FORCE;   /* 另有 LIFT_POS, LIFT_NORMAL, LIFT_VELO, DRAG, TEMP，共 6 个同型字段 */
  public static void resetVectors();   public static java.util.List<LiftProviderGroup> groupLiftProviders(java.util.Collection<LiftProviderContext>);   public abstract net.minecraft.core.Direction sable$getNormal(net.minecraft.world.level.block.state.BlockState);
  public default float sable$getParallelDragScalar();   public default float sable$getDirectionlessDragScalar();   public default float sable$getLiftScalar();
  public default void sable$contributeLiftAndDrag(LiftProviderContext, dev.ryanhcode.sable.sublevel.ServerSubLevel,
          dev.ryanhcode.sable.companion.math.Pose3d, double, org.joml.Vector3dc, org.joml.Vector3dc,
  org.joml.Vector3d, org.joml.Vector3d, LiftProviderGroup);
}
public final class BlockSubLevelLiftProvider$LiftProviderContext extends java.lang.Record {
  public LiftProviderContext(net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState, net.minecraft.world.phys.Vec3);   public net.minecraft.core.BlockPos pos();   public net.minecraft.world.level.block.state.BlockState state();   public net.minecraft.world.phys.Vec3 dir();
}
```

❌ `LiftProviderGroup` 成员**未核实**（未单独 javap）。要用先执行：
`& $javap -classpath "$libs\sable-neoforge-1.21.1-2.0.5.jar" -public 'dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider$LiftProviderGroup'`
### 3.3 `BlockEntitySubLevelReactionWheel`
```text
public interface BlockEntitySubLevelReactionWheel {
  public abstract void sable$getAngularVelocity(org.joml.Vector3d);   public abstract net.minecraft.world.level.block.state.BlockState getBlockState();
}
```
### 3.4 螺旋桨
```text
public interface dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller {
  public abstract net.minecraft.core.Direction getBlockDirection();   public abstract double getAirflow();   public abstract double getThrust();   public abstract boolean isActive();   public default double getScaledThrust();   public default double getCurrentAirPressure();   public default double getAirflowScaling();
  public abstract net.minecraft.world.level.Level getLevel();   public abstract net.minecraft.core.BlockPos getBlockPos();
}
public interface dev.ryanhcode.sable.api.block.propeller.BlockEntitySubLevelPropellerActor
        extends dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor {
  public static final org.joml.Vector3d THRUST_VECTOR;   public static final org.joml.Vector3d THRUST_POSITION;   public abstract dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller getPropeller();
  public default void sable$physicsTick(dev.ryanhcode.sable.sublevel.ServerSubLevel, dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle, double);   public default void applyForces(dev.ryanhcode.sable.sublevel.ServerSubLevel, net.minecraft.world.phys.Vec3, double);
}
```

后者的 `sable$physicsTick` / `applyForces` **都有默认实现**，只需实现 `getPropeller()` 即得自动推力。
范例：`SmartPropellerBlockEntity.java:80`。
### 3.5 碰撞 / 质心 / 动态碰撞体 / 装配监听
```text
public interface BlockSubLevelCollisionShape {
  public abstract net.minecraft.world.phys.shapes.VoxelShape getSubLevelCollisionShape(net.minecraft.world.level.BlockGetter, BlockState);
}
public interface BlockSubLevelCustomCenterOfMass {
  public abstract org.joml.Vector3dc getCenterOfMass(net.minecraft.world.level.BlockGetter, BlockState);
}
public interface BlockSubLevelDynamicCollider { public abstract void buildBoxes(dev.ryanhcode.sable.api.physics.collider.VoxelColliderData); }
public interface BlockSubLevelAssemblyListener {
  public default void beforeMove(net.minecraft.server.level.ServerLevel, net.minecraft.server.level.ServerLevel, BlockState, net.minecraft.core.BlockPos, net.minecraft.core.BlockPos);
  public abstract void afterMove(net.minecraft.server.level.ServerLevel, net.minecraft.server.level.ServerLevel, BlockState, net.minecraft.core.BlockPos, net.minecraft.core.BlockPos);
}
public interface BlockWithSubLevelCollisionCallback {
  public static dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback sable$getCallback(BlockState);   public static boolean hasCallback(BlockState);   public abstract dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback sable$getCallback();
}
// 记忆点：CollisionShape/CustomCenterOfMass 都收 (BlockGetter, BlockState)；AssemblyListener 只 before/afterMove 两个钩子
```

> `BlockWithSubLevelCollisionCallback` 同时有静态与实例 `sable$getCallback()`，实现类只写**无参**那个。
### 3.6 `api/block` 完整清单
```text
dev.ryanhcode.sable.api.block.{BlockEntitySubLevelActor, BlockEntitySubLevelReactionWheel,
  BlockSubLevelAssemblyListener, BlockSubLevelCollisionShape, BlockSubLevelCustomCenterOfMass,
  BlockSubLevelDynamicCollider, BlockSubLevelLiftProvider, BlockWithSubLevelCollisionCallback}
dev.ryanhcode.sable.api.block.propeller.{BlockEntityPropeller, BlockEntitySubLevelPropellerActor}
```

命令：`& $jar tf "$libs\sable-neoforge-1.21.1-2.0.5.jar" | Select-String "dev/ryanhcode/sable/api/block"`（共 10 条，无其他）。
## 4. Sable 质量 / 关节
### 4.1 `api.physics.mass.**` ✅已核实
```text
public interface MassData {
  double getMass();   double getInverseMass();   org.joml.Matrix3dc getInertiaTensor();   org.joml.Matrix3dc getInverseInertiaTensor();   org.joml.Vector3dc getCenterOfMass();   default boolean isInvalid();   default double getInverseNormalMass(org.joml.Vector3dc, org.joml.Vector3dc);
}
public class MassTracker implements MassData {
  public static java.util.function.BiFunction<net.minecraft.world.level.BlockGetter,
  net.minecraft.world.level.block.state.BlockState, org.joml.Vector3dc> BLOCK_CENTER_OF_MASS;   public MassTracker();   public static MassTracker build(net.minecraft.world.level.BlockGetter, dev.ryanhcode.sable.companion.math.BoundingBox3ic);
  public void addBlockMass(net.minecraft.world.level.BlockGetter, BlockState, net.minecraft.core.BlockPos, double, net.minecraft.world.phys.Vec3);   public void moveCenterOfMass(org.joml.Vector3d);   public double getMass();   public org.joml.Matrix3dc getInertiaTensor();
  public double getInverseMass();   public org.joml.Matrix3dc getInverseInertiaTensor();   public org.joml.Vector3dc getCenterOfMass();
}
public class MergedMassTracker implements MassData {
  public MergedMassTracker(dev.ryanhcode.sable.sublevel.ServerSubLevel, MassTracker);   public void update(float);   public double getMass();   public double getInverseMass();   public org.joml.Matrix3dc getInertiaTensor();
  public org.joml.Matrix3dc getInverseInertiaTensor();   public org.joml.Vector3dc getCenterOfMass();   public MassTracker getSelfMassTracker();
}
```

「本方块自己的」→ `ServerSubLevel.getSelfMassTracker()`（`MassTracker`）；「含连接结构的合并」→
`ServerSubLevel.getMassTracker()`（声明 `MassData`，运行期 `MergedMassTracker`）。
❌ `MassTracker.BLOCK_CENTER_OF_MASS` 的读写时机、`BlockSubLevelCustomCenterOfMass` 如何被消费**未核实**（未读 Sable 内部源码）。
### 4.2 `api.physics.constraint.**` ✅已核实
```text
public interface PhysicsConstraintHandle {
  void getJointImpulses(org.joml.Vector3d, org.joml.Vector3d);   void setContactsEnabled(boolean);   void setMotor(ConstraintJointAxis axis, double target, double stiffness, double damping, boolean hasMaxForce, double maxForce);   void remove();   boolean isValid();
}
public interface PhysicsConstraintConfiguration<T extends PhysicsConstraintHandle> {
  static void validateAnchors(ServerSubLevelContainer, PhysicsPipelineBody, PhysicsPipelineBody, org.joml.Vector3dc, org.joml.Vector3dc);   default void validate(ServerSubLevelContainer, PhysicsPipelineBody, PhysicsPipelineBody);
}
public final class ConstraintJointAxis extends java.lang.Enum<...> {
  public static final ... LINEAR_X, LINEAR_Y, LINEAR_Z, ANGULAR_X, ANGULAR_Y, ANGULAR_Z;   public static final ConstraintJointAxis[] ALL, LINEAR, ANGULAR;
}
public final class FreeConstraintConfiguration extends java.lang.Record implements PhysicsConstraintConfiguration<FreeConstraintHandle> {
  public FreeConstraintConfiguration(org.joml.Vector3dc, org.joml.Vector3dc, org.joml.Quaterniondc);   public org.joml.Vector3dc pos1();   public org.joml.Vector3dc pos2();   public org.joml.Quaterniondc orientation();
}
public interface FreeConstraintHandle extends PhysicsConstraintHandle { }
public final class RotaryConstraintConfiguration extends java.lang.Record implements PhysicsConstraintConfiguration<RotaryConstraintHandle> {
  public RotaryConstraintConfiguration(org.joml.Vector3dc, org.joml.Vector3dc, org.joml.Vector3dc, org.joml.Vector3dc);
  public org.joml.Vector3dc pos1(), pos2(), normal1(), normal2();   // 4 个访问器
}
public interface RotaryConstraintHandle extends PhysicsConstraintHandle {
  public static final ConstraintJointAxis DEFAULT_AXIS;
  public default void setServoCoefficients(double, double, double);   // @deprecated → setMotor(DEFAULT_AXIS, …)
}
public final class GenericConstraintConfiguration extends java.lang.Record implements PhysicsConstraintConfiguration<GenericConstraintHandle> {
  public GenericConstraintConfiguration(org.joml.Vector3dc, org.joml.Vector3dc, org.joml.Quaterniondc, org.joml.Quaterniondc);   public GenericConstraintConfiguration(org.joml.Vector3dc, org.joml.Vector3dc, org.joml.Quaterniondc, org.joml.Quaterniondc, java.util.Set<ConstraintJointAxis>);
  public org.joml.Vector3dc pos1(), pos2();   public org.joml.Quaterniondc orientation1(), orientation2();   public java.util.Set<ConstraintJointAxis> lockedAxes();
}
public interface GenericConstraintHandle extends PhysicsConstraintHandle {
  void setFrame1(org.joml.Vector3dc, org.joml.Quaterniondc);   void setFrame2(org.joml.Vector3dc, org.joml.Quaterniondc);   void setLimit(ConstraintJointAxis axis, double min, double max);   void lockAxes(ConstraintJointAxis...);
}
public final class FixedConstraintConfiguration extends java.lang.Record implements PhysicsConstraintConfiguration<FixedConstraintHandle> {
  public FixedConstraintConfiguration(org.joml.Vector3dc, org.joml.Vector3dc, org.joml.Quaterniondc);
}
public interface FixedConstraintHandle extends PhysicsConstraintHandle { }
```

关于任务书问的 **`setMotor` / limit**：

- `setMotor` 在 **基接口 `PhysicsConstraintHandle`**，6 参（✅javap + 源码 javadoc `PhysicsConstraintHandle.java:31`）。
- **基接口没有 `setLimit`**；`setLimit(ConstraintJointAxis, double, double)` 只在
  **`GenericConstraintHandle`**（✅javap + `GenericConstraintHandle.java:36`）。
- `RotaryConstraintHandle.setServoCoefficients(angle, stiffness, damping)` 已 `@deprecated`
  （`RotaryConstraintHandle.java:16-20`，默认轴 `DEFAULT_AXIS`）。

```java
RotaryConstraintHandle h = pipeline.addConstraint(bodyA, bodyB,
        new RotaryConstraintConfiguration(pos1, pos2, normal1, normal2));
h.setMotor(RotaryConstraintHandle.DEFAULT_AXIS, angle, stiffness, damping, false, 0.0);
```
## 5. Sable 事件平台 + Simulated / Aeronautics 初始化
### 5.1 关键更正：`SableEventPlatform` 在 **Sable** 里，不在 Simulated 里 ✅已核实
任务书写的 `dev.simulated_team.simulated.platform.SableEventPlatform` **不存在**：

```powershell
& $jar tf "$libs\dev.simulated_team.simulated.simulated-neoforge-1.21.1-1.3.2.jar" | Select-String "platform|Platform"
# 仅 2 条：dev/simulated_team/simulated/neoforge/service/NeoForgeSimPlatformService.class
#          dev/simulated_team/simulated/service/SimPlatformService.class
```

真实位置：`dev.ryanhcode.sable.platform.SableEventPlatform`（对外门面）、
`dev.ryanhcode.sable.platform.SableEventPublishPlatform`（`@ApiStatus.Internal` 发布者）、
`dev.ryanhcode.sable.neoforge.platform.SableEventPlatformImpl` / `SableEventPublishPlatformImpl`。
### 5.2 `SableEventPlatform` 签名与语义 ✅已核实
```text
public interface dev.ryanhcode.sable.platform.SableEventPlatform {
  public static final dev.ryanhcode.sable.platform.SableEventPlatform INSTANCE;   public abstract void onSubLevelContainerReady(dev.ryanhcode.sable.api.event.SableSubLevelContainerReadyEvent);
  public abstract void onPhysicsTick(dev.ryanhcode.sable.api.event.SablePrePhysicsTickEvent);   public abstract void onPostPhysicsTick(dev.ryanhcode.sable.api.event.SablePostPhysicsTickEvent);
}
```

**这三个方法是「注册监听器」，不是「发布事件」**。实现证据
`sable-main/neoforge/src/main/java/dev/ryanhcode/sable/neoforge/platform/SableEventPlatformImpl.java:16-29`：

```java
@Override
public void onPhysicsTick(final SablePrePhysicsTickEvent event) {
    NeoForge.EVENT_BUS.<ForgeSablePrePhysicsTickEvent>addListener(
            forgeEvent -> event.prePhysicsTick(forgeEvent.getPhysicsSystem(), forgeEvent.getTimeStep()));
}
```

传入一个 lambda（函数式接口），Sable 把它桥接到 NeoForge 事件总线；因为是 `addListener`，**可注册多个**。

⚠️ **陷阱**：`INSTANCE` 由 `sable-main/common/src/main/java/dev/ryanhcode/sable/platform/SablePlatformUtil.java:6-10` 决定：

```java
public static <T> T load(Class<T> clazz) {
    return ServiceLoader.load(clazz, SablePlatformUtil.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
}
```

Sable 自己已占用 `META-INF/services/dev.ryanhcode.sable.platform.SableEventPlatform`，`findFirst()` + 顺序不确定
⇒ **不要自己 provide service 文件去「替换」实现**，只用 `INSTANCE.onPhysicsTick(...)` 注册回调。
### 5.3 用法（真实代码）
**（a）注册** —— `Simulated.java:58-59`、`Aeronautics.java:65-66`、`Offroad.java:62`

```java
SableEventPlatform.INSTANCE.onPhysicsTick(SimulatedCommonEvents::onPhysicsTick);
SableEventPlatform.INSTANCE.onPostPhysicsTick(SimulatedCommonEvents::onPostPhysicsTick);
SableEventPlatform.INSTANCE.onSubLevelContainerReady(AeronauticsCommonEvents::onSubLevelContainerReady);
```

**（b）回调体** —— `SimulatedCommonEvents.java:109-144`（签名必须与 `prePhysicsTick(SubLevelPhysicsSystem, double)` 一致）

```java
public static void onPhysicsTick(final SubLevelPhysicsSystem physicsSystem, final double timeStep) {
    final ServerLevel level = physicsSystem.getLevel();
    EndSeaPhysicsData.physicsTick(timeStep, level);
    ServerLevelRopeManager.getOrCreate(level).physicsTick(physicsSystem, timeStep);
}
public static void onPostPhysicsTick(final SubLevelPhysicsSystem physicsSystem, final double timeStep) {
    DiagramEntity.postPhysicsTick(physicsSystem.getLevel());
}
private static void onContainerReady(final Level level, final SubLevelContainer subLevelContainer) {
    if (!(subLevelContainer instanceof final ServerSubLevelContainer serverContainer)) {
        return;
    }
    serverContainer.addObserver(new PhysicsStaffSubLevelObserver(serverContainer.getLevel()));
    serverContainer.trackingSystem().addTrackingPlugin(new ServerRopeTrackingSystem(serverContainer.getLevel()));
}
```

**（c）容器 API** ✅已核实：

```text
public class ServerSubLevelContainer extends SubLevelContainer {
  public void initialize();   public void tick();   public void takePhysicsSystem(dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem);   public void takeTrackingSystem(dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem);   public dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem physicsSystem();
  public dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem trackingSystem();   public java.util.List<dev.ryanhcode.sable.sublevel.ServerSubLevel> getAllSubLevels();
  public net.minecraft.server.level.ServerLevel getLevel();   public void removeSubLevel(int, int, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason);
  public <T> boolean addForceLoadTicket(dev.ryanhcode.sable.sublevel.ServerSubLevel, SubLevelLoadingTicketType<T>, T);   public <T> boolean removeForceLoadTicket(dev.ryanhcode.sable.sublevel.ServerSubLevel, SubLevelLoadingTicketType<T>, T);
}
public abstract class SubLevelContainer {
  public static SubLevelContainer       getContainer(net.minecraft.world.level.Level);   public static ServerSubLevelContainer getContainer(net.minecraft.server.level.ServerLevel);
  public static ClientSubLevelContainer getContainer(net.minecraft.client.multiplayer.ClientLevel);   public void addObserver(dev.ryanhcode.sable.api.sublevel.SubLevelObserver);   public dev.ryanhcode.sable.sublevel.SubLevel getSubLevel(int, int);   public dev.ryanhcode.sable.sublevel.SubLevel getSubLevel(java.util.UUID);
  public dev.ryanhcode.sable.sublevel.SubLevel allocateNewSubLevel(dev.ryanhcode.sable.companion.math.Pose3d);   public java.util.List<? extends dev.ryanhcode.sable.sublevel.SubLevel> getAllSubLevels();
  public java.lang.Iterable<dev.ryanhcode.sable.sublevel.SubLevel> queryIntersecting(dev.ryanhcode.sable.companion.math.BoundingBox3dc);   public boolean inBounds(net.minecraft.core.BlockPos);   public int getLoadedCount();   public void tick();   public void processSubLevelRemovals();
}
public interface SubLevelObserver {
  public default void onSubLevelAdded(dev.ryanhcode.sable.sublevel.SubLevel);   public default void onSubLevelRemoved(dev.ryanhcode.sable.sublevel.SubLevel, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason);   public default void tick(dev.ryanhcode.sable.api.sublevel.SubLevelContainer);
}
```

**备选：直接听 NeoForge 事件**（✅类存在，见 §2.5）：

```java
@EventBusSubscriber(modid = Clockwork.MOD_ID)   // 默认 = GAME bus
public final class ClockworkSableEvents {
    @SubscribeEvent
    public static void onPrePhysics(final ForgeSablePrePhysicsTickEvent event) {
        final SubLevelPhysicsSystem sys = event.getPhysicsSystem();
        final double dt = event.getTimeStep();
    }
}
```

```text
public interface net.neoforged.fml.common.EventBusSubscriber extends java.lang.annotation.Annotation {
  net.neoforged.api.distmarker.Dist[] value();   java.lang.String modid();   net.neoforged.fml.common.EventBusSubscriber$Bus bus();
}
public final class EventBusSubscriber$Bus extends java.lang.Enum<...> { GAME; MOD; }
```
### 5.4 `Sable` 主类与「找包含该方块的 sub-level」✅已核实
```text
public final class dev.ryanhcode.sable.Sable {
  public static final java.lang.String MOD_NAME, MOD_ID, ISSUE_TRACKER_URL;   public static final org.slf4j.Logger LOGGER;   public static final dev.ryanhcode.sable.ActiveSableCompanion HELPER;   public static void init();
  public static net.minecraft.resources.ResourceLocation sablePath(java.lang.String);   public static void defaultSubLevelContainerInitializer(net.minecraft.world.level.Level, dev.ryanhcode.sable.api.sublevel.SubLevelContainer);
}
public class dev.ryanhcode.sable.ActiveSableCompanion implements dev.ryanhcode.sable.companion.SableCompanion {
  public dev.ryanhcode.sable.sublevel.SubLevel getContaining(net.minecraft.world.level.block.entity.BlockEntity);   public dev.ryanhcode.sable.sublevel.SubLevel getContaining(net.minecraft.world.level.Level, net.minecraft.core.Vec3i);
  public dev.ryanhcode.sable.sublevel.SubLevel getContaining(net.minecraft.world.level.Level, org.joml.Vector3dc);   public dev.ryanhcode.sable.sublevel.SubLevel getContaining(net.minecraft.world.entity.Entity);
  public dev.ryanhcode.sable.sublevel.ClientSubLevel getContainingClient(net.minecraft.world.level.block.entity.BlockEntity);   public net.minecraft.world.phys.Vec3 projectOutOfSubLevel(net.minecraft.world.level.Level, net.minecraft.world.phys.Vec3);
  public net.minecraft.world.phys.Vec3 getVelocityRelativeToAir(net.minecraft.world.level.Level, net.minecraft.world.phys.Vec3);
}
```

```java
final SubLevel subLevel = Sable.HELPER.getContaining(this);          // this = BlockEntity
if (subLevel == null) return;
final RigidBodyHandle handle = RigidBodyHandle.of((ServerSubLevel) subLevel);   // WheelMountBlockEntity.java:228,234
```
### 5.5 Simulated 主类初始化 ✅已核实
`Simulated.java:25-84`：

```java
public final class Simulated {
    public static final String MOD_ID = "simulated";
    private static final NonNullSupplier<SimulatedRegistrate> REGISTRATE = NonNullSupplier.lazy(() ->
            (SimulatedRegistrate) new SimulatedRegistrate(path("simulated"), MOD_ID).defaultCreativeTab((ResourceKey) null));

    public static void init() {
        setTooltips();
        getRegistrate().addDataGenerator(ProviderType.LANG, SimLang::registrateLang);
        SimRegistries.register();   SimTags.register();      SimBlocks.register();
        SimItems.register();        SimBlockEntityTypes.register();
        SimParticleTypes.register(); SimSoundEvents.init();  SimSpriteShifts.init();
        SimPacketManager.init();    SimEntityTypes.register(); SimMenuTypes.register();
        SimNavigationTargets.register(); SimDataComponents.register(); SimItemAttributeTypes.init();
        SimulatedCommonEvents.register(); SimBlockMovementChecks.register();
        SimAssemblyHelper.register();  SimModCompatibilityService.initLoaded();

        SableEventPlatform.INSTANCE.onPhysicsTick(SimulatedCommonEvents::onPhysicsTick);
        SableEventPlatform.INSTANCE.onPostPhysicsTick(SimulatedCommonEvents::onPostPhysicsTick);
    }
    public static ResourceLocation path(final String path) { return ResourceLocation.tryBuild(MOD_ID, path); }
}
```

`SimulatedNeoForge.java:24-55`：

```java
@Mod(Simulated.MOD_ID)
public final class SimulatedNeoForge {
    public static final CreativeModeTab TAB = CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + Simulated.MOD_ID + ".group"))
            .icon(() -> new ItemStack(SimBlocks.PHYSICS_ASSEMBLER.get()))
            .build();

    public SimulatedNeoForge(final IEventBus modEventBus, final ModContainer modContainer) {
        final DeferredRegister<CreativeModeTab> tabRegister =
                DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, Simulated.MOD_ID);
        tabRegister.register("main_tab", () -> TAB);
        tabRegister.register(modEventBus);

        NeoForge.EVENT_BUS.register(SimNeoForgeCommonEvents.class);
        modEventBus.register(SimNeoForgeCommonEvents.ModBusEvents.class);
        Simulated.getRegistrate().registerEventListeners(modEventBus);   // Registrate 接管 mod bus

        Simulated.init();
        NeoForgeSimConfigService.register(ModLoadingContext.get(), modContainer);
    }
}
```

```java
@Mod(value = Simulated.MOD_ID, dist = Dist.CLIENT)      // SimulatedNeoForgeClient.java:14-25
public class SimulatedNeoForgeClient {
	public SimulatedNeoForgeClient(final IEventBus modEventBus, final ModContainer container) {
		container.registerExtensionPoint(IConfigScreenFactory.class, ((c, l) -> new BaseConfigScreen(l, Simulated.MOD_ID)));
		NeoForge.EVENT_BUS.register(SimNeoForgeClientEvents.class);
		modEventBus.register(SimNeoForgeClientEvents.ModBusEvents.class);
		SimulatedClient.init();
	}
}
```

配套签名 ✅：

```text
public class net.neoforged.neoforge.registries.DeferredRegister<T> {
  public static <T> DeferredRegister<T> create(net.minecraft.core.Registry<T>, java.lang.String);   public <I extends T> DeferredHolder<T, I> register(java.lang.String, java.util.function.Supplier<? extends I>);   public void register(net.neoforged.bus.api.IEventBus);
}
public abstract class net.neoforged.fml.ModContainer {
  public void registerConfig(net.neoforged.fml.config.ModConfig$Type, net.neoforged.fml.config.IConfigSpec);   public <T extends net.neoforged.fml.IExtensionPoint> void registerExtensionPoint(java.lang.Class<T>, T);
}
```
## 6. Simulated 的注册范式（可照抄）
### 6.1 自定义 Registrate ✅已核实
`registrate/SimulatedRegistrate.java:31, :41-45, :88-99`

```java
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.tterrag.registrate.builders.Builder;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.neoforged.neoforge.registries.DeferredHolder;

public class SimulatedRegistrate extends CreateRegistrate {
    public SimulatedRegistrate(final ResourceLocation initialSection, final String modId) {
        super(modId);
        this.currentSection = initialSection;
        MODS.add(modId);
    }
    @Override
    protected <R, T extends R> @NotNull RegistryEntry<R, T> accept(final String name,
            final ResourceKey<? extends Registry<R>> type, final Builder<R, T, ?, ?> builder,
            final NonNullSupplier<? extends T> creator,
            final NonNullFunction<DeferredHolder<R, T>, ? extends RegistryEntry<R, T>> entryFactory) {
        final RegistryEntry<R, T> entry = super.accept(name, type, builder, creator, entryFactory);
        if (type.equals(Registries.ITEM)) { /* … */ }
        return entry;
    }
}
```

```text
public class com.simibubi.create.foundation.data.CreateRegistrate
        extends com.tterrag.registrate.AbstractRegistrate<com.simibubi.create.foundation.data.CreateRegistrate> {
  public static CreateRegistrate create(java.lang.String);   public CreateRegistrate registerEventListeners(net.neoforged.bus.api.IEventBus);   public CreateRegistrate setTooltipModifierFactory(java.util.function.Function<Item, com.simibubi.create.foundation.item.TooltipModifier>);
  public CreateRegistrate setCreativeTab(net.neoforged.neoforge.registries.DeferredHolder<CreativeModeTab, CreativeModeTab>);   public <T extends BlockEntity> CreateBlockEntityBuilder<T, CreateRegistrate> blockEntity(java.lang.String, BlockEntityBuilder$BlockEntityFactory<T>);
  public <T extends DisplaySource> com.simibubi.create.api.registry.registrate.SimpleBuilder<DisplaySource, T, CreateRegistrate>
  displaySource(java.lang.String, java.util.function.Supplier<T>);
  public <T extends DisplayTarget> com.simibubi.create.api.registry.registrate.SimpleBuilder<DisplayTarget, T, CreateRegistrate>
  displayTarget(java.lang.String, java.util.function.Supplier<T>);
}
```
### 6.2 方块链 ✅已核实（`index/SimBlocks.java:95, :101, :290-315`）
```java
import static com.simibubi.create.api.behaviour.movement.MovementBehaviour.movementBehaviour;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.tterrag.registrate.util.entry.BlockEntry;

private static final SimulatedRegistrate REGISTRATE = Simulated.getRegistrate();

public static final BlockEntry<AugerShaftBlock> AUGER_SHAFT =
        REGISTRATE.block("auger_shaft", AugerShaftBlock::new)
                .initialProperties(SharedProperties::softMetal)
                .blockstate(SimBlockStateService.INSTANCE.augerShaftGenerate("auger_shaft", false))
                .transform(pickaxeOnly())
                .transform(SimStress.setImpact(0.5))
                .recipe((c, p) -> { /* ShapelessRecipeBuilder… .save(p); */ })
                .properties(p -> p.sound(SoundType.NETHERITE_BLOCK))
                .properties(BlockBehaviour.Properties::noOcclusion)
                .addLayer(() -> RenderType::cutoutMipped)
                .transform(DisplaySource.displaySource(SimDisplaySources.AUGER_DISPLAY))
                .item().transform(customItemModel())
                .register();
```

带运动行为（`SimBlocks.java:464-465`）：

```java
.transform(DisplaySource.displaySource(SimDisplaySources.ALTITUDE_SENSOR_DISPLAY))
.onRegister(movementBehaviour(new AltitudeSensorMovementBehaviour()))
```

> `movementBehaviour(...)` 返回 `NonNullConsumer` → 配 `.onRegister(...)`；
> `DisplaySource.displaySource(...)` 返回 `NonNullUnaryOperator` → 配 `.transform(...)`。**别互换。**

```text
public <T extends Block> com.tterrag.registrate.builders.BlockBuilder<T, S> block(java.lang.String, NonNullFunction<BlockBehaviour$Properties, T>);
public S defaultCreativeTab(net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab>);
public com.tterrag.registrate.builders.NoConfigBuilder<CreativeModeTab, CreativeModeTab, S> defaultCreativeTab();
public S registerEventListeners(net.neoforged.bus.api.IEventBus);
```
### 6.3 方块实体链 ✅已核实（`index/SimBlockEntityTypes.java:78-98`）
```java
import com.tterrag.registrate.util.entry.BlockEntityEntry;

public static final BlockEntityEntry<PhysicsAssemblerBlockEntity> PHYSICS_ASSEMBLER = REGISTRATE
        .blockEntity("physics_assembler", PhysicsAssemblerBlockEntity::new)
        .validBlocks(SimBlocks.PHYSICS_ASSEMBLER)
        .renderer(() -> PhysicsAssemblerRenderer::new)
        .register();

public static final BlockEntityEntry<AnalogTransmissionBlockEntity> SIMPLE_BE = REGISTRATE
        .blockEntity("simple", AnalogTransmissionBlockEntity::new)
        .visual(() -> AnalogTransmissionVisual::new)
        .validBlocks(SimBlocks.ANALOG_TRANSMISSION)
        .renderer(() -> AnalogTransmissionRenderer::new)
        .register();
```

```text
public class com.simibubi.create.foundation.data.CreateBlockEntityBuilder<T extends BlockEntity, P>
        extends com.tterrag.registrate.builders.BlockEntityBuilder<T, P> {
  public CreateBlockEntityBuilder<T, P> validBlocksDeferred(NonNullSupplier<? extends java.util.Collection<NonNullSupplier<? extends Block>>>);
  public CreateBlockEntityBuilder<T, P> displaySource(RegistryEntry<DisplaySource, ? extends DisplaySource>);   public CreateBlockEntityBuilder<T, P> displayTarget(RegistryEntry<DisplayTarget, ? extends DisplayTarget>);
  public CreateBlockEntityBuilder<T, P> visual(NonNullSupplier<dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer$Factory<T>>);   public CreateBlockEntityBuilder<T, P> visual(NonNullSupplier<...>, boolean);   public CreateBlockEntityBuilder<T, P> visual(NonNullSupplier<...>, java.util.function.Predicate<T>);
}
```

❌ `.renderer(...)` / `.validBlocks(...)` / `.validBlock(...)` / `.onRegister(...)`（来自 Registrate `BlockEntityBuilder`）
**签名未核实**，但 Simulated 源码大量实测使用。
### 6.4 `DisplaySource` 注册 ✅已核实（`index/SimDisplaySources.java:11-27`）
```java
public class SimDisplaySources {
    public static final RegistryEntry<DisplaySource, ItemThroughputDisplaySource> AUGER_DISPLAY =
            simple("auger_throughput", ItemThroughputDisplaySource::new);
    private static <T extends DisplaySource> RegistryEntry<DisplaySource, T> simple(final String name, final Supplier<T> supplier) {
        return Simulated.getRegistrate().displaySource(name, supplier).register();
    }
}
```
### 6.5 创造模式标签 ✅已核实
原生写法见 §5.5（`DeferredRegister<CreativeModeTab>` + `CreativeModeTab.builder()`）。Simulated 另有
「自定义 tab 子类 + Registrate 钩子」：`SimulatedCreativeTab.java:130-143` 遍历 `SimulatedRegistrate.TAB_ITEMS`
（`:94` 在 `accept()` 中对 `Registries.ITEM` 自动登记）。**移植建议**：Clockwork 用原生 `DeferredRegister<CreativeModeTab>` 即可。
### 6.6 配置 ✅已核实（`NeoForgeSimConfigService.java:57-81`）
```java
import net.createmod.catnip.config.ConfigBase;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

private static <T extends ConfigBase> T register(final Supplier<T> factory, final ModConfig.Type side) {
    final Pair<T, ModConfigSpec> specPair = (new ModConfigSpec.Builder()).configure((builder) -> {
        final T config = factory.get();
        config.registerAll(builder);
        return config;
    });
    final T config = specPair.getLeft();
    config.specification = specPair.getRight();
    CONFIGS.put(side, config);
    return config;
}
public static void register(final ModLoadingContext context, final ModContainer container) {
    server = register(SimServer::new, ModConfig.Type.SERVER);
    client = register(SimClient::new, ModConfig.Type.CLIENT);
    for (final Map.Entry<ModConfig.Type, ConfigBase> e : CONFIGS.entrySet()) {
        container.registerConfig(e.getKey(), e.getValue().specification);
    }
    final CStress stress = SimConfigService.INSTANCE.server().kinetics.stressValues;
    BlockStressValues.IMPACTS.registerProvider(stress::getImpact);
    BlockStressValues.CAPACITIES.registerProvider(stress::getCapacity);
}
```
### 6.7 网络（Veil）✅已核实
`network/SimPacketManager.java:24-33`：

```java
import foundry.veil.api.network.VeilPacketManager;

public class SimPacketManager {
    public static final VeilPacketManager INSTANCE = VeilPacketManager.create(Simulated.MOD_ID, "0.1");
    public static void init() {
        INSTANCE.registerServerbound(AssemblePacket.TYPE, AssemblePacket.CODEC, AssemblePacket::handle);
        INSTANCE.registerClientbound(PhysicsStaffBeamPacket.TYPE, PhysicsStaffBeamPacket.CODEC, PhysicsStaffBeamPacket::handle);
    }
}
```

包体 `network/packets/AssemblePacket.java:14-27`（全文件）：

```java
import foundry.veil.api.network.handler.ServerPacketContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AssemblePacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<AssemblePacket> TYPE = new Type<>(Simulated.path("assemble"));
    public static final StreamCodec<ByteBuf, AssemblePacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, packet -> packet.pos,
            AssemblePacket::new);
    @Override public Type<AssemblePacket> type() { return TYPE; }
    public void handle(final ServerPacketContext context) { final ServerPlayer player = context.player(); }
}
```

> `Type` 无限定符时指 `CustomPacketPayload.Type`。移植时建议显式写 `CustomPacketPayload.Type<AssemblePacket>`。
## 7. Create 6.0.10（1.21.1）关键 API 现状
### 7.0 包名总表（先列 jar 再断言）
```powershell
& $jar tf "$libs\create-1.21.1-6.0.10.jar" | Select-String "SmartBlockEntity|KineticBlockEntity|BlockEntityBehaviour|ValueSettingsBehaviour|IControlContraption|ContraptionType|CreateBuiltInRegistries|BlockStressValues|DisplaySource|MovementBehaviour|BoilerHeat|AllBlocks"
```

| 概念 | 6.0.10 真实 FQN（**按这个 import**） | 状态 |
|---|---|---|
| `SmartBlockEntity` | `com.simibubi.create.foundation.blockEntity.SmartBlockEntity` | ✅ |
| `KineticBlockEntity` | `com.simibubi.create.content.kinetics.base.KineticBlockEntity` | ✅ |
| `BlockEntityBehaviour` / `ValueSettingsBehaviour` | `com.simibubi.create.foundation.blockEntity.behaviour.{BlockEntityBehaviour,ValueSettingsBehaviour}` | ✅ |
| `IControlContraption` | `com.simibubi.create.content.contraptions.IControlContraption` | ✅ |
| `ContraptionType` / `AllContraptionTypes` | `com.simibubi.create.api.contraption.ContraptionType` / `com.simibubi.create.AllContraptionTypes` | ✅ |
| `CreateBuiltInRegistries` | `com.simibubi.create.api.registry.CreateBuiltInRegistries` | ✅ |
| `BlockStressValues` | `com.simibubi.create.api.stress.BlockStressValues` | ✅ |
| `DisplaySource` / `DisplayTarget` | `com.simibubi.create.api.behaviour.display.{DisplaySource,DisplayTarget}` | ✅ |
| `MovementBehaviour` | `com.simibubi.create.api.behaviour.movement.MovementBehaviour` | ✅ |
| `BoilerHeater` | `com.simibubi.create.api.boiler.BoilerHeater` | ✅ |
| `AllBlocks` / `AllBlockEntityTypes` / `AllDisplaySources` | `com.simibubi.create.{AllBlocks,AllBlockEntityTypes,AllDisplaySources}` | ✅ |

> Create 6.x 把部分通用工具搬去 `net.createmod.catnip.**`（如 `net.createmod.catnip.config.ConfigBase`、
> `net.createmod.catnip.lang.FontHelper`），但**上表这些类仍在 `com.simibubi.create` 下**（已核实）。
### 7.1 `SmartBlockEntity` ✅已核实
```text
public abstract class com.simibubi.create.foundation.blockEntity.SmartBlockEntity
        extends com.simibubi.create.foundation.blockEntity.CachedRenderBBBlockEntity
        implements com.simibubi.create.api.schematic.nbt.PartialSafeNBT, com.simibubi.create.foundation.utility.IInteractionChecker,
                   com.simibubi.create.api.schematic.requirement.SpecialBlockEntityItemRequirement, net.createmod.ponder.api.VirtualBlockEntity {
  public SmartBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState);   public abstract void addBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour>);
  public void addBehavioursDeferred(java.util.List<BlockEntityBehaviour>);   public void initialize();   public void tick();   public void lazyTick();
  public void writeSafe(net.minecraft.nbt.CompoundTag, net.minecraft.core.HolderLookup$Provider);   public void onChunkUnloaded();   public final void setRemoved();   public void invalidate();   public void remove();   public void destroy();
  public final void saveAdditional(net.minecraft.nbt.CompoundTag, net.minecraft.core.HolderLookup$Provider);   public final void readClient(net.minecraft.nbt.CompoundTag, net.minecraft.core.HolderLookup$Provider);
  public final net.minecraft.nbt.CompoundTag writeClient(net.minecraft.nbt.CompoundTag, net.minecraft.core.HolderLookup$Provider);
  public <T extends BlockEntityBehaviour> T getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<T>);   public void forEachBehaviour(java.util.function.Consumer<BlockEntityBehaviour>);
  public java.util.Collection<BlockEntityBehaviour> getAllBehaviours();   public void attachBehaviourLate(BlockEntityBehaviour);   public void removeBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<?>);
  public void setLazyTickRate(int);   public void markVirtual();   public boolean isVirtual();   public boolean isChunkUnloaded();   public boolean canPlayerUse(net.minecraft.world.entity.player.Player);   public void sendToMenu(net.minecraft.network.RegistryFriendlyByteBuf);   public void refreshBlockState();
  // 省略：addBehavioursDeferred / registerAwardables / award / awardIfNear / getRequiredItems
}
```

**已核实的破坏性变化**：

- ❌ **不存在 `read(CompoundTag, boolean)` / `write(CompoundTag, boolean)`**；改为 `readClient` / `writeClient`
  （均 `final`，带 `HolderLookup.Provider`），持久化走 `saveAdditional`（也 `final`）。
  → 旧代码 `@Override public void read(...)` 直接编译失败。
- ✅ `addBehaviours(List<BlockEntityBehaviour>)` 仍 **abstract**，子类必须实现。
- ⚠️ 6.0.10 **只有 `getBehaviour(BehaviourType<T>)`**，全量 public 列表里**没有** `getBehaviour(Class)`。
### 7.2 `BlockEntityBehaviour` / `ValueSettingsBehaviour` ✅已核实
```text
public abstract class BlockEntityBehaviour {
  public com.simibubi.create.foundation.blockEntity.SmartBlockEntity blockEntity;             // public 字段
  public BlockEntityBehaviour(com.simibubi.create.foundation.blockEntity.SmartBlockEntity);    // 唯一构造器
  public abstract com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<?> getType();   public void initialize();   public void tick();   public void read(net.minecraft.nbt.CompoundTag, net.minecraft.core.HolderLookup$Provider, boolean);
  public void write(net.minecraft.nbt.CompoundTag, net.minecraft.core.HolderLookup$Provider, boolean);   public void writeSafe(net.minecraft.nbt.CompoundTag, net.minecraft.core.HolderLookup$Provider);
  public boolean isSafeNBT();   public com.simibubi.create.content.schematics.requirement.ItemRequirement getRequiredItems();   public void onBlockChanged(BlockState);   public void onNeighborChanged(net.minecraft.core.BlockPos);
  public void unload();   public void destroy();   public void setLazyTickRate(int);   public void lazyTick();   public net.minecraft.core.BlockPos getPos();   public net.minecraft.world.level.Level getWorld();
  public static <T extends BlockEntityBehaviour> T get(net.minecraft.world.level.BlockGetter, net.minecraft.core.BlockPos, BehaviourType<T>);   public static <T extends BlockEntityBehaviour> T get(net.minecraft.world.level.block.entity.BlockEntity, BehaviourType<T>);
}
```

- `read`/`write` 是 **3 参**（`…, boolean clientPacket`）；1.20.1 的 2 参签名不存在。
- ❌ 静态工厂 `BlockEntityBehaviour.addTo(...)` / `addToSelf(...)` **未出现**在 javap 全量 public 列表中。
  ⚠️ 不能 100% 断定已移除（未读 6.0.10 源码），但**不要照抄**；改用 `SmartBlockEntity.addBehaviours` / `attachBehaviourLate`。

```text
public interface ValueSettingsBehaviour extends com.simibubi.create.content.equipment.clipboard.ClipboardCloneable {
  public abstract boolean testHit(net.minecraft.world.phys.Vec3);   public abstract boolean isActive();   public default boolean onlyVisibleWithWrench();
  public default void newSettingHovered(ValueSettingsBehaviour$ValueSettings);   public abstract com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform getSlotPositioning();
  public abstract com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard createBoard(net.minecraft.world.entity.player.Player, net.minecraft.world.phys.BlockHitResult);   public abstract void setValueSettings(net.minecraft.world.entity.player.Player, ValueSettingsBehaviour$ValueSettings, boolean);
  public abstract ValueSettingsBehaviour$ValueSettings getValueSettings();   public default boolean acceptsValueSettings();   public default java.lang.String getClipboardKey();   public default boolean writeToClipboard(net.minecraft.core.HolderLookup$Provider, net.minecraft.nbt.CompoundTag, net.minecraft.core.Direction);
  public default boolean readFromClipboard(net.minecraft.core.HolderLookup$Provider, net.minecraft.nbt.CompoundTag, net.minecraft.world.entity.player.Player, net.minecraft.core.Direction, boolean);   public default void playFeedbackSound(BlockEntityBehaviour);
  public default void onShortInteract(net.minecraft.world.entity.player.Player, net.minecraft.world.InteractionHand, net.minecraft.core.Direction, net.minecraft.world.phys.BlockHitResult);
  public default boolean bypassesInput(net.minecraft.world.item.ItemStack);   public default boolean mayInteract(net.minecraft.world.entity.player.Player);   public default int netId();
}
public final class ValueSettingsBoard extends java.lang.Record {
  public ValueSettingsBoard(net.minecraft.network.chat.Component, int, int, java.util.List<net.minecraft.network.chat.Component>,
  com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter);   public net.minecraft.network.chat.Component title();   public int maxValue();   public int milestoneInterval();   public java.util.List<net.minecraft.network.chat.Component> rows();
  public com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter formatter();
}
```

❌ `ValueSettingsBehaviour$ValueSettings` 成员**未核实**：
`& $javap -classpath "$libs\create-1.21.1-6.0.10.jar" -public 'com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour$ValueSettings'`
### 7.3 `KineticBlockEntity` ✅已核实
```text
public class com.simibubi.create.content.kinetics.base.KineticBlockEntity extends SmartBlockEntity
        implements com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation, com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation {
  public java.lang.Long network;                 // ← Long（可空），不是 long
  public net.minecraft.core.BlockPos source;   public boolean networkDirty;   public boolean updateSpeed;   public int preventSpeedUpdate;   public com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity$SequenceContext sequenceContext;
  public KineticBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.core.BlockPos, BlockState);   public void initialize();   public void tick();   public void updateFromNetwork(float, float, int);   public float calculateStressApplied();   public float calculateAddedStressCapacity();
  public void onSpeedChanged(float);   public void remove();   public boolean needsSpeedUpdate();   public float getGeneratedSpeed();   public boolean isSource();   public float getSpeed();   public float getTheoreticalSpeed();   public void setSpeed(float);
  public boolean hasSource();   public void setSource(net.minecraft.core.BlockPos);   public void removeSource();   public void setNetwork(java.lang.Long);
  public com.simibubi.create.content.kinetics.KineticNetwork getOrCreateNetwork();   public boolean hasNetwork();   public void attachKinetics();   public void detachKinetics();
  public boolean isSpeedRequirementFulfilled();   public static void switchToBlockState(net.minecraft.world.level.Level, net.minecraft.core.BlockPos, BlockState);   public void addBehaviours(java.util.List<BlockEntityBehaviour>);   public boolean addToTooltip(java.util.List<net.minecraft.network.chat.Component>, boolean);
  public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component>, boolean);   public void clearKineticInformation();   public void warnOfMovement();   public int getFlickerScore();   public static float convertToDirection(float, net.minecraft.core.Direction);
  public static float convertToLinear(float);   public static float convertToAngular(float);   public boolean isOverStressed();   public float propagateRotationTo(KineticBlockEntity, BlockState, BlockState, net.minecraft.core.BlockPos, boolean, boolean);
  public java.util.List<net.minecraft.core.BlockPos> addPropagationLocations(com.simibubi.create.content.kinetics.base.IRotate, BlockState, java.util.List<net.minecraft.core.BlockPos>);
  public boolean isCustomConnection(KineticBlockEntity, BlockState, BlockState);   public int getRotationAngleOffset(net.minecraft.core.Direction$Axis);
  // 省略：requestModelDataUpdate / tickAudio / getFrictionCoefficient…（javap 全量列表无 getStress）
}
```

- ⚠️ **全量 public 列表里没有 `getStress()`** → 应力通过 `calculateStressApplied()` / `calculateAddedStressCapacity()` 拿。
- `convertToDirection/Linear/Angular` 都是 **static**；旧调用
  `Clockwork-1.20.1-main/.../MixinSteamEngineBlockEntity.java:90` 的 `GeneratingKineticBlockEntity.convertToDirection(1, facing)` 仍可用 ✅。
### 7.4 `IControlContraption` ✅已核实
```text
public interface com.simibubi.create.content.contraptions.IControlContraption {
  public abstract boolean isAttachedTo(com.simibubi.create.content.contraptions.AbstractContraptionEntity);
  public abstract void attach(com.simibubi.create.content.contraptions.ControlledContraptionEntity);   public abstract void onStall();   public abstract boolean isValid();   public abstract net.minecraft.core.BlockPos getBlockPosition();
}
```

只有 5 个抽象方法；`isAttachedTo` 收 **`AbstractContraptionEntity`**，`attach` 收 `ControlledContraptionEntity`。
❌ `startMoving` / `stopMoving` / `getControlTarget` **未在 javap 中出现**，6.0.10 挪去哪**未核实**。
### 7.5 `ContraptionType` 注册 —— **`BY_LEGACY_NAME` 仍然存在**（更正任务书）✅已核实
> 任务书假设 `AllContraptionTypes.BY_LEGACY_NAME` 已不存在 —— **错的**，6.0.10 里它在，别据此删代码。

```text
public class com.simibubi.create.AllContraptionTypes {
  public static final java.util.Map<java.lang.String, com.simibubi.create.api.contraption.ContraptionType> BY_LEGACY_NAME;
  public static final net.minecraft.core.Holder$Reference<ContraptionType> PISTON, BEARING, PULLEY, CLOCKWORK,
      MOUNTED, STABILIZED, GANTRY, CARRIAGE, ELEVATOR;      // 实为 9 个独立 public static final 字段
  public static void init();
}
public final class com.simibubi.create.api.contraption.ContraptionType {
  public final java.util.function.Supplier<? extends com.simibubi.create.content.contraptions.Contraption> factory;   public final net.minecraft.core.Holder$Reference<com.simibubi.create.api.contraption.ContraptionType> holder;
  public ContraptionType(java.util.function.Supplier<? extends com.simibubi.create.content.contraptions.Contraption>);   public boolean is(net.minecraft.tags.TagKey<com.simibubi.create.api.contraption.ContraptionType>);   public static com.simibubi.create.content.contraptions.Contraption fromType(java.lang.String);
}
public class com.simibubi.create.api.registry.CreateBuiltInRegistries {
  public static final net.minecraft.core.Registry<com.simibubi.create.api.contraption.ContraptionType> CONTRAPTION_TYPE;   public static final net.minecraft.core.Registry<com.simibubi.create.api.behaviour.display.DisplaySource> DISPLAY_SOURCE;
  public static final net.minecraft.core.Registry<com.simibubi.create.api.behaviour.display.DisplayTarget> DISPLAY_TARGET;   public static final net.minecraft.core.Registry<com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType> ARM_INTERACTION_POINT_TYPE;
  public static final net.minecraft.core.Registry<com.simibubi.create.content.kinetics.fan.processing.FanProcessingType> FAN_PROCESSING_TYPE;
  /* 其余：ITEM_ATTRIBUTE_TYPE / MOUNTED_ITEM_STORAGE_TYPE / MOUNTED_FLUID_STORAGE_TYPE /
     PACKAGE_PORT_TARGET_TYPE / POTATO_PROJECTILE_RENDER_MODE / _ENTITY_HIT_ACTION / _BLOCK_HIT_ACTION */
  public static void init();
}
```

**注册写法**（除 `RegisterEvent` 外签名均已核实）：

```java
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.resources.ResourceLocation;

@SubscribeEvent
public static void onRegister(final RegisterEvent event) {
    event.register(CreateBuiltInRegistries.CONTRAPTION_TYPE,
            ResourceLocation.fromNamespaceAndPath(Clockwork.MOD_ID, "my_type"),
            () -> new ContraptionType(MyContraption::new));
}
```

❌ `RegisterEvent.register(...)` 重载**未核实**，定稿前先执行
`& $javap -classpath "$mc" -public net.neoforged.neoforge.registries.RegisterEvent`。
`ContraptionType` 构造器是 **public**，`holder` 由注册框架填充 → **别自己 new 完不注册**。
### 7.6 `BlockStressValues`（**旧静态 API 已不存在**）✅已核实
```text
public class com.simibubi.create.api.stress.BlockStressValues {
  public static final com.simibubi.create.api.registry.SimpleRegistry<Block, java.util.function.DoubleSupplier> IMPACTS;   public static final com.simibubi.create.api.registry.SimpleRegistry<Block, java.util.function.DoubleSupplier> CAPACITIES;
  public static final com.simibubi.create.api.registry.SimpleRegistry<Block, BlockStressValues$GeneratedRpm> RPM;   public static double getImpact(Block);   public static double getCapacity(Block);
  public static com.tterrag.registrate.util.nullness.NonNullConsumer<Block> setGeneratorSpeed(int);   public static com.tterrag.registrate.util.nullness.NonNullConsumer<Block> setGeneratorSpeed(int, boolean);
}
public final class BlockStressValues$GeneratedRpm extends java.lang.Record {
  public BlockStressValues$GeneratedRpm(int, boolean);   public int value();   public boolean mayGenerateLess();
}
```

**破坏性变化**：全量 public 列表里**没有** `BlockStressValues.registerProvider(String, IStressValueProvider)`
这类静态方法 → 旧代码
`Clockwork-1.20.1-main/forge/src/main/java/org/valkyrienskies/clockwork/forge/config/AllClockworkConfigs.java:45`
（`BlockStressValues.registerProvider(context.getActiveNamespace(), …)`，当前是注释）照抄会**编译失败**。
正确姿势是操作 `SimpleRegistry` 字段：

```text
public interface SimpleRegistry<K, V> {
  void register(K, V);   void registerProvider(SimpleRegistry$Provider<K, V>);   void invalidate();   V get(K);   V get(net.minecraft.world.level.block.state.StateHolder<K, ?>);   static <K, V> SimpleRegistry<K, V> create();
}
public interface SimpleRegistry$Multi<K, V> extends SimpleRegistry<K, java.util.List<V>> {
  void add(K, V);   void addProvider(SimpleRegistry$Provider<K, V>);   java.util.List<V> get(K);   static <K, V> SimpleRegistry$Multi<K, V> create();
}
public interface SimpleRegistry$Provider<K, V> {
  V get(K);   default void onRegister(java.lang.Runnable);   static <V> SimpleRegistry$Provider<Block, V> forBlockTag(net.minecraft.tags.TagKey<Block>, V);
  static <V> SimpleRegistry$Provider<BlockEntityType<?>, V> forBlockEntityTag(net.minecraft.tags.TagKey<BlockEntityType<?>>, V);   static <V> SimpleRegistry$Provider<Item, V> forItemTag(net.minecraft.tags.TagKey<Item>, V);
  static <V> SimpleRegistry$Provider<EntityType<?>, V> forEntityTag(net.minecraft.tags.TagKey<EntityType<?>>, V);   static <V> SimpleRegistry$Provider<Fluid, V> forFluidTag(net.minecraft.tags.TagKey<Fluid>, V);
}
```

即 `BlockStressValues.IMPACTS.register(block, () -> 4.0)` 或
`BlockStressValues.IMPACTS.registerProvider(stress::getImpact)`（Simulated 写法，见 §6.6）。
### 7.7 `DisplaySource` ✅已核实
```text
public abstract class com.simibubi.create.api.behaviour.display.DisplaySource {
  public static final com.simibubi.create.api.registry.SimpleRegistry$Multi<Block, DisplaySource> BY_BLOCK;   public static final com.simibubi.create.api.registry.SimpleRegistry$Multi<BlockEntityType<?>, DisplaySource> BY_BLOCK_ENTITY;
  public static final java.util.List<net.minecraft.network.chat.MutableComponent> EMPTY;   public static final net.minecraft.network.chat.MutableComponent EMPTY_LINE, WHITESPACE;
  public abstract java.util.List<net.minecraft.network.chat.MutableComponent> provideText(
          com.simibubi.create.content.redstone.displayLink.DisplayLinkContext,
          com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats);
  public void transferData(DisplayLinkContext, DisplayTarget, int);
  public void onSignalReset(DisplayLinkContext);   public void populateData(DisplayLinkContext);   public int getPassiveRefreshTicks();   public boolean shouldPassiveReset();   public net.minecraft.network.chat.Component getName();
  public void loadFlapDisplayLayout(DisplayLinkContext, com.simibubi.create.content.trains.display.FlapDisplayBlockEntity, com.simibubi.create.content.trains.display.FlapDisplayLayout, int);
  public java.util.List<java.util.List<net.minecraft.network.chat.MutableComponent>> provideFlapDisplayText(DisplayLinkContext, DisplayTargetStats);   public void initConfigurationWidgets(DisplayLinkContext, com.simibubi.create.foundation.gui.ModularGuiLineBuilder, boolean);
  public static <B extends Block, P> com.tterrag.registrate.util.nullness.NonNullUnaryOperator<com.tterrag.registrate.builders.BlockBuilder<B, P>>
         displaySource(com.tterrag.registrate.util.entry.RegistryEntry<DisplaySource, ? extends DisplaySource>);
  public static DisplaySource get(net.minecraft.resources.ResourceLocation);   public static java.util.List<DisplaySource> getAll(net.minecraft.world.level.LevelAccessor, net.minecraft.core.BlockPos);
}
```
- **唯一抽象方法**是 `provideText(DisplayLinkContext, DisplayTargetStats)` → 子类只写这一个。
- 注册：`CreateRegistrate.displaySource(name, supplier)`（§6.4 ✅）；`BY_BLOCK` / `BY_BLOCK_ENTITY` 是 `SimpleRegistry.Multi` → 绑定用 `add(block, source)`。
- ❌ 「`CreateBuiltInRegistries.DISPLAY_SOURCE` 是否由 `RegisterEvent` 填充」**未核实**（未读 Create 源码）→ 优先用 Registrate 路径。
### 7.8 `DisplayTarget` ✅已核实
```text
public abstract class com.simibubi.create.api.behaviour.display.DisplayTarget {
  public static final SimpleRegistry<Block, DisplayTarget> BY_BLOCK;   public static final SimpleRegistry<BlockEntityType<?>, DisplayTarget> BY_BLOCK_ENTITY;
  public abstract void acceptText(int, java.util.List<net.minecraft.network.chat.MutableComponent>, DisplayLinkContext);
  public abstract com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats provideStats(DisplayLinkContext);
  public net.minecraft.world.phys.AABB getMultiblockBounds(net.minecraft.world.level.LevelAccessor, net.minecraft.core.BlockPos);
  public net.minecraft.network.chat.Component getLineOptionText(int);
  public static void reserve(int, BlockEntity, DisplayLinkContext);   public boolean isReserved(int, BlockEntity, DisplayLinkContext);   public boolean requiresComponentSanitization();
  public static <B extends Block, P> NonNullUnaryOperator<BlockBuilder<B, P>> displayTarget(RegistryEntry<DisplayTarget, ? extends DisplayTarget>);
  public static DisplayTarget get(net.minecraft.resources.ResourceLocation);   public static DisplayTarget get(net.minecraft.world.level.LevelAccessor, net.minecraft.core.BlockPos);
}
```
### 7.9 `MovementBehaviour` ✅已核实
```text
public interface com.simibubi.create.api.behaviour.movement.MovementBehaviour {
  public static final com.simibubi.create.api.registry.SimpleRegistry<Block, MovementBehaviour> REGISTRY;   public static <B extends Block> com.tterrag.registrate.util.nullness.NonNullConsumer<? super B> movementBehaviour(MovementBehaviour);
  public default boolean isActive(com.simibubi.create.content.contraptions.behaviour.MovementContext);   public default void tick(MovementContext);   public default void startMoving(MovementContext);
  public default void visitNewPosition(MovementContext, net.minecraft.core.BlockPos);   public default net.minecraft.world.phys.Vec3 getActiveAreaOffset(MovementContext);
  public default net.minecraft.world.item.ItemStack canBeDisabledVia(MovementContext);   public default void onDisabledByControls(MovementContext);   public default boolean mustTickWhileDisabled();
  public default void dropItem(MovementContext, net.minecraft.world.item.ItemStack);   public default void collectOrDropItem(MovementContext, net.minecraft.world.item.ItemStack);
  public default void onSpeedChanged(MovementContext, net.minecraft.world.phys.Vec3, net.minecraft.world.phys.Vec3);   public default void stopMoving(MovementContext);   public default void cancelStall(MovementContext);   public default void writeExtraData(MovementContext);   public default boolean disableBlockEntityRendering();
  public default void renderInContraption(MovementContext, VirtualRenderWorld, ContraptionMatrices, net.minecraft.client.renderer.MultiBufferSource);
  public default com.simibubi.create.content.contraptions.render.ActorVisual createVisual(
  dev.engine_room.flywheel.api.visualization.VisualizationContext, VirtualRenderWorld, MovementContext);
  // 上两行原文用的是全限定名 com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld / …render.ContraptionMatrices
}
```

**所有方法都有 default** → 只覆盖需要的。注册两条路：
`.onRegister(movementBehaviour(new MyBehaviour()))`（返回 `NonNullConsumer`，**不能**配 `.transform`）或
`MovementBehaviour.REGISTRY.register(block, behaviour)`。
### 7.10 `BoilerHeater`（**旧 API 已不存在**）✅已核实
```text
public interface com.simibubi.create.api.boiler.BoilerHeater {
  public static final int PASSIVE_HEAT, NO_HEAT;   public static final BoilerHeater PASSIVE, BLAZE_BURNER;   public static final com.simibubi.create.api.registry.SimpleRegistry<Block, BoilerHeater> REGISTRY;
  public static float findHeat(net.minecraft.world.level.Level, net.minecraft.core.BlockPos, BlockState);   public abstract float getHeat(net.minecraft.world.level.Level, net.minecraft.core.BlockPos, BlockState);
}
public class com.simibubi.create.content.fluids.tank.BoilerHeaters {
  public static void registerDefaults();   public static int passive(net.minecraft.world.level.Level, net.minecraft.core.BlockPos, BlockState);   public static int blazeBurner(net.minecraft.world.level.Level, net.minecraft.core.BlockPos, BlockState);
}
```

**破坏性变化**：`BoilerHeaters` **只有 `registerDefaults/passive/blazeBurner`，没有 `registerHeater(...)`**。
旧 Clockwork `ClockworkModFabric.java:69`（`ClockworkBoilerHeaters.INSTANCE.init();`）对应实现改为：

```java
BoilerHeater.REGISTRY.register(MyBlocks.MY_HEATER.get(), (level, pos, state) -> 1.0f);
```

`getHeat` 返回 **`float`**（`findHeat` 也是 float）。
### 7.11 `AllBlocks` / `AllBlockEntityTypes` / `AllDisplaySources` ✅已核实（存在性 + 条目类型）
```text
public class com.simibubi.create.AllBlocks { /* 大量 public static final BlockEntry<…>，如 ANDESITE_CASING / SHAFT / COGWHEEL */ }
public class com.simibubi.create.AllBlockEntityTypes { /* BlockEntityEntry<…> */ }
public class com.simibubi.create.AllDisplaySources {
  public static final com.tterrag.registrate.util.entry.RegistryEntry<DisplaySource, com.simibubi.create.content.redstone.displayLink.source.KineticSpeedDisplaySource> KINETIC_SPEED;
  /* 另有 DEATH_COUNT / KINETIC_STRESS / … 共 26 条同型字段 */
  public static final java.util.Map<java.lang.String, RegistryEntry<DisplaySource, ? extends DisplaySource>> LEGACY_NAMES;   public static void register();
}
```

`AllBlocks` 字段类型是 `BlockEntry<…>`（实证：`SimBlocks.java:116, :140` 把 `AllBlocks.ANDESITE_CASING` /
`INDUSTRIAL_IRON_BLOCK` 直接喂给 `RegistrateRecipeProvider.has(...)`）。
`AllDisplaySources` 的条目正好能喂 `DisplaySource.displaySource(entry)` ✅。
### 7.12 `Registrate` 链式写法 ✅已核实
```text
public class com.tterrag.registrate.Registrate extends com.tterrag.registrate.AbstractRegistrate<com.tterrag.registrate.Registrate> {
  public static com.tterrag.registrate.Registrate create(java.lang.String);
}
public abstract class com.tterrag.registrate.AbstractRegistrate<S extends AbstractRegistrate<S>> {
  public <T extends Block> com.tterrag.registrate.builders.BlockBuilder<T, S> block(java.lang.String, NonNullFunction<BlockBehaviour$Properties, T>);   public <T extends BlockEntity> com.tterrag.registrate.builders.BlockEntityBuilder<T, S> blockEntity(java.lang.String, BlockEntityBuilder$BlockEntityFactory<T>);
  public <T extends Item> com.tterrag.registrate.builders.ItemBuilder<T, S> item(java.lang.String, NonNullFunction<Item$Properties, T>);
  public <R, T extends R> RegistryEntry<R, T> simple(java.lang.String, ResourceKey<? extends Registry<R>>, NonNullSupplier<T>);   public S defaultCreativeTab(net.minecraft.resources.ResourceKey<CreativeModeTab>);
  public NoConfigBuilder<CreativeModeTab, CreativeModeTab, S> defaultCreativeTab(java.util.function.Consumer<CreativeModeTab$Builder>);   public S registerEventListeners(net.neoforged.bus.api.IEventBus);   public S addDataGenerator(ProviderType<? extends T>, NonNullConsumer<? extends T>);
  public net.minecraft.network.chat.MutableComponent addLang(java.lang.String, ResourceLocation, java.lang.String);   public java.lang.String getModid();   public net.neoforged.bus.api.IEventBus getModEventBus();
}
```

```java
import com.tterrag.registrate.Registrate;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public static final Registrate REGISTRATE = Registrate.create(Clockwork.MOD_ID);

public static final BlockEntry<Block> MY_BLOCK = REGISTRATE
        .block("my_block", Block::new)                      // ✅ block(String, NonNullFunction<Properties, T>) → BlockBuilder<T,S>
        .initialProperties(() -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL))
        .simpleItem()
        .register();                                        // → BlockEntry
```

❌ `initialProperties(...)` / `.simpleItem()` / `.register()` 的**逐条签名未核实**（未 dump `BlockBuilder`）；
但 `.initialProperties(SharedProperties::wooden)` 与 `.register()` 在 `SimBlocks.java:104, :119` 实测可用。要严格核实：

```powershell
& $javap -classpath "$libs\Registrate-MC1.21-1.3.0+67.jar" -public com.tterrag.registrate.builders.BlockBuilder
```
## 8. 最小可用模板：方块实体每物理 tick 给所在 sub-level 施加一个力
### 8.1 每个调用的证据
| 调用 | 证据 |
|---|---|
| `SmartBlockEntity(BlockEntityType<?>, BlockPos, BlockState)` | §7.1 javap ✅ |
| `addBehaviours(List<BlockEntityBehaviour>)`（abstract） | §7.1 javap ✅ |
| `BlockEntitySubLevelActor#sable$physicsTick(ServerSubLevel, RigidBodyHandle, double)` | §3.1 javap ✅ |
| `SubLevel#logicalPose()` → `Pose3dc` | §1.3 javap ✅ |
| `Pose3dc#transformPositionInverse(Vec3)` → `Vec3`；`transformNormalInverse(Vector3dc, Vector3d)` → `Vector3d` | sable-companion javap ✅ |
| `RigidBodyHandle#isValid()` / `#applyForcesAndReset(ForceTotal)` | §2.1 javap ✅ |
| `ForceTotal()` / `#applyImpulseAtPoint(ServerSubLevel, Vector3dc, Vector3dc)` | §2.2 javap ✅ |
| 「力必须乘 `timeStep`」 | `SpringBlockEntity.java:280` `.mul(timeStep)` ✅ |
| `Vec3.atCenterOf(Vec3i)`；`BlockEntity.level`/`.worldPosition`；`Level.isClientSide` | vanilla javap ✅ |

```text
$ javap -classpath "$mc" -public net.minecraft.world.phys.Vec3
  public static net.minecraft.world.phys.Vec3 atCenterOf(net.minecraft.core.Vec3i);
$ javap -classpath "$mc" -protected net.minecraft.world.level.block.entity.BlockEntity
  protected net.minecraft.world.level.Level level;   protected final net.minecraft.core.BlockPos worldPosition;
$ javap -classpath "$mc" -public net.minecraft.world.level.Level
  public final boolean isClientSide;
```
### 8.2 完整代码（可直接复制）
```java
package org.valkyrienskies.clockwork.content.thruster;   // ← 换成你的包

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.List;

/** 每物理 tick 给所在 sub-level 施加世界 +Y 方向的推力。物理 tick 每游戏刻跑多次，力必须乘 timeStep。 */
public class ThrusterBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor {

    private static final double THRUST = 2400.0;   // 示例值

    /** 跨 tick 复用安全：ForceTotal#applyForces（ForceTotal.java:25-37）推给 handle 后会清零 localForce/localTorque */
    private final ForceTotal forceTotal = new ForceTotal();

    public ThrusterBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        // abstract 必须实现；本模板不需要 behaviour
    }

    @Override
    public void sable$physicsTick(final ServerSubLevel subLevel, final RigidBodyHandle handle, final double timeStep) {
        if (this.level == null || this.level.isClientSide) {
            return;                       // 物理只在服务端跑
        }
        if (!handle.isValid()) {
            return;                       // 刚 remove / 尚未进管线的句柄要跳过
        }

        final Pose3dc pose = subLevel.logicalPose();

        // (1) 世界坐标 → plot 内部局部坐标（applyImpulseAtPoint 要的是 plot 局部坐标！）
        final Vec3 localCenter = pose.transformPositionInverse(Vec3.atCenterOf(this.worldPosition));
        final Vector3d localPos = new Vector3d(localCenter.x, localCenter.y, localCenter.z);
        // (2) 世界方向 → plot 局部方向
        final Vector3d localThrust = pose.transformNormalInverse(new Vector3d(0.0, THRUST, 0.0), new Vector3d());
        // (3) 施力（乘 timeStep，见类注释）
        this.forceTotal.applyImpulseAtPoint(subLevel, localPos, localThrust.mul(timeStep));
        // (4) 推入刚体并复位 forceTotal
        handle.applyForcesAndReset(this.forceTotal);
    }
}
```

```java
// 配套注册（Registrate，见 §6.3 / §7.12）
public static final BlockEntityEntry<ThrusterBlockEntity> THRUSTER = REGISTRATE
        .blockEntity("thruster", ThrusterBlockEntity::new)
        .validBlocks(MyBlocks.THRUSTER)
        .register();
```
### 8.3 变体 B：在全局物理 tick 里遍历（Simulated 的组织方式）
```java
SableEventPlatform.INSTANCE.onPhysicsTick(MyEvents::onPhysicsTick);      // 初始化时注册一次

public static void onPhysicsTick(final SubLevelPhysicsSystem physicsSystem, final double timeStep) {
    final ServerLevel level = physicsSystem.getLevel();                               // ✅ §2.4
    final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);   // ✅ 返回 ServerSubLevelContainer
    final PhysicsPipeline pipeline = physicsSystem.getPipeline();                      // ✅ §2.4
    for (final ServerSubLevel subLevel : container.getAllSubLevels()) {                // ✅ List<ServerSubLevel>
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);       // ✅ §2.4
        // …按需施力
    }
}
```
### 8.4 变体 C：装配（签名全部已核实）
```java
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;

public static ServerSubLevel assemble(final ServerLevel level, final BlockPos anchor,
                                      final Collection<BlockPos> blocks) {
    final BoundingBox3i bounds = BoundingBox3i.from(blocks);                            // ✅ §1.4
    final ServerSubLevel subLevel =
            SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds);       // ✅ §1.1
    if (subLevel == null) {                                                             // 调用点惯例判空
        return null;
    }
    return subLevel;
}
```
## 9. 网络：NeoForge 1.21.1 原生 `CustomPacketPayload`
**结论：原生 API 完全可用，不必退回 Veil。** 编译期二进制：
`~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/21.1.228/{userdev,sources,universal}.jar`，
含 patched vanilla 的是 `build/moddev/artifacts/neoforge-21.1.228.jar`（`& $jar tf "$mc" | Select-String "CustomPacketPayload"` 命中 6 条）。
### 9.1 `CustomPacketPayload` ✅已核实
```text
public interface net.minecraft.network.protocol.common.custom.CustomPacketPayload {
  public abstract CustomPacketPayload$Type<? extends CustomPacketPayload> type();
  public static <B extends io.netty.buffer.ByteBuf, T extends CustomPacketPayload>
  net.minecraft.network.codec.StreamCodec<B, T> codec(StreamMemberEncoder<B, T>, StreamDecoder<B, T>);   public static <T extends CustomPacketPayload> CustomPacketPayload$Type<T> createType(java.lang.String);
  public default net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket toVanillaClientbound();   public default net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket toVanillaServerbound();
}
public final class CustomPacketPayload$Type<T extends CustomPacketPayload> extends java.lang.Record {
  public CustomPacketPayload$Type(net.minecraft.resources.ResourceLocation);   public net.minecraft.resources.ResourceLocation id();
}
```
### 9.2 `RegisterPayloadHandlersEvent` + `PayloadRegistrar` ✅已核实
```text
public class net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
        extends net.neoforged.bus.api.Event implements net.neoforged.fml.event.IModBusEvent {
  public net.neoforged.neoforge.network.registration.PayloadRegistrar registrar(java.lang.String);   // 只有单参重载
}
public class net.neoforged.neoforge.network.registration.PayloadRegistrar {
  public PayloadRegistrar(java.lang.String);   public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload$Type<T>, StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, T>, IPayloadHandler<T>);
  public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload$Type<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>, IPayloadHandler<T>);
  public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(CustomPacketPayload$Type<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>, IPayloadHandler<T>);
  public <T extends CustomPacketPayload> PayloadRegistrar configurationToClient(CustomPacketPayload$Type<T>, StreamCodec<? super net.minecraft.network.FriendlyByteBuf, T>, IPayloadHandler<T>);
  // 同构族（各 3 个，StreamCodec 均用 FriendlyByteBuf）：
  //   configuration{ToServer,Bidirectional} / common{ToClient,ToServer,Bidirectional}   public PayloadRegistrar executesOn(net.neoforged.neoforge.network.registration.HandlerThread);   public PayloadRegistrar versioned(java.lang.String);   public PayloadRegistrar optional();
}
```

> ⚠️ **没有** `common(...)` / `clientbound(...)` / `serverbound(...)` 这些旧方法名（1.20.4 时代 API）。
> 只有 `play*` / `configuration*` / `common*` 三族。
### 9.3 `IPayloadContext` / `IPayloadHandler` / `PacketDistributor` ✅已核实
```text
public interface IPayloadHandler<T extends CustomPacketPayload> { void handle(T, IPayloadContext); }
public interface net.neoforged.neoforge.network.handling.IPayloadContext {
  public abstract net.neoforged.neoforge.common.extensions.ICommonPacketListener listener();   public default net.minecraft.network.Connection connection();
  public abstract net.minecraft.world.entity.player.Player player();   public default void reply(CustomPacketPayload);   public default void disconnect(net.minecraft.network.chat.Component);   public abstract java.util.concurrent.CompletableFuture<java.lang.Void> enqueueWork(java.lang.Runnable);
  public abstract <T> java.util.concurrent.CompletableFuture<T> enqueueWork(java.util.function.Supplier<T>);   public abstract net.minecraft.network.protocol.PacketFlow flow();   public default net.minecraft.network.ConnectionProtocol protocol();   public default void handle(net.minecraft.network.protocol.Packet<?>);
  public abstract void handle(CustomPacketPayload);   public abstract void finishCurrentTask(net.minecraft.server.network.ConfigurationTask$Type);   public default io.netty.channel.ChannelHandlerContext channelHandlerContext();
}
public final class net.neoforged.neoforge.network.PacketDistributor {
  public static void sendToServer(CustomPacketPayload, CustomPacketPayload...);   public static void sendToPlayer(net.minecraft.server.level.ServerPlayer, CustomPacketPayload, CustomPacketPayload...);
  public static void sendToPlayersInDimension(net.minecraft.server.level.ServerLevel, CustomPacketPayload, CustomPacketPayload...);   public static void sendToPlayersNear(net.minecraft.server.level.ServerLevel, net.minecraft.server.level.ServerPlayer, double, double, double, double, CustomPacketPayload, CustomPacketPayload...);
  public static void sendToAllPlayers(CustomPacketPayload, CustomPacketPayload...);   public static void sendToPlayersTrackingEntity(net.minecraft.world.entity.Entity, CustomPacketPayload, CustomPacketPayload...);
  public static void sendToPlayersTrackingEntityAndSelf(net.minecraft.world.entity.Entity, CustomPacketPayload, CustomPacketPayload...);   public static void sendToPlayersTrackingChunk(net.minecraft.server.level.ServerLevel, net.minecraft.world.level.ChunkPos, CustomPacketPayload, CustomPacketPayload...);
}
```

> `PacketDistributor` 的 8 个方法签名**尾参一律是 `CustomPacketPayload...`**，所以一次可带多个包。
### 9.4 最小可编译注册片段
```java
package org.valkyrienskies.clockwork.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Clockwork.MOD_ID, bus = EventBusSubscriber.Bus.MOD)   // ✅ §5.3
public final class ClockworkPackets {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {       // ✅ §9.2
        final PayloadRegistrar registrar = event.registrar("1");                  // 只有 registrar(String)
        registrar.playToServer(SyncThingPacket.TYPE, SyncThingPacket.STREAM_CODEC, SyncThingPacket::handle);
        registrar.playToClient(ClientSyncPacket.TYPE, ClientSyncPacket.STREAM_CODEC, ClientSyncPacket::handle);
    }

    public record SyncThingPacket(int id) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncThingPacket> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Clockwork.MOD_ID, "sync_thing"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncThingPacket> STREAM_CODEC =
                StreamCodec.composite(
                        net.minecraft.network.codec.ByteBufCodecs.VAR_INT, SyncThingPacket::id,
                        SyncThingPacket::new);

        @Override
        public CustomPacketPayload.Type<SyncThingPacket> type() { return TYPE; }

        public static void handle(final SyncThingPacket packet, final IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                    // 主线程处理逻辑
                }
            });
        }
    }
}
```

```java
import net.neoforged.neoforge.network.PacketDistributor;

PacketDistributor.sendToServer(new SyncThingPacket(7));                  // 客户端 → 服务端
PacketDistributor.sendToPlayer(serverPlayer, new ClientSyncPacket());    // 服务端 → 单玩家
PacketDistributor.sendToPlayersTrackingChunk(serverLevel, chunkPos, new ClientSyncPacket());
```

`StreamCodec` / `ByteBufCodecs` 类存在性已核实（`& $jar tf "$mc" | Select-String "ByteBufCodecs"`）。
❌ `StreamCodec.composite` 各元数重载**未逐个 javap**；上面 2 元写法与 `AssemblePacket.java:17-19` 一致，**实测可用**。
### 9.5 Veil 备选（Simulated 实际在用）✅已核实
```text
public interface foundry.veil.api.network.VeilPacketManager {
  public static final VeilPacketManager$Factory FACTORY;   public static VeilPacketManager create(java.lang.String, java.lang.String);
  public default  <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload$Type<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>, VeilPacketManager$PacketHandler<foundry.veil.api.network.handler.ClientPacketContext, T>);
  public abstract <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload$Type<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>, VeilPacketManager$PacketHandler<ClientPacketContext, T>, boolean);
  public default  <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload$Type<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>, VeilPacketManager$PacketHandler<foundry.veil.api.network.handler.ServerPacketContext, T>);
  public abstract <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload$Type<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>, VeilPacketManager$PacketHandler<ServerPacketContext, T>, boolean);
  public static VeilPacketManager$PacketSink server();   public static VeilPacketManager$PacketSink player(net.minecraft.server.level.ServerPlayer);   public static VeilPacketManager$PacketSink level(net.minecraft.server.level.ServerLevel);
  public static VeilPacketManager$PacketSink around(net.minecraft.server.level.ServerPlayer, net.minecraft.server.level.ServerLevel, double, double, double, double);
  public static VeilPacketManager$PacketSink all(net.minecraft.server.MinecraftServer);   public static VeilPacketManager$PacketSink tracking(net.minecraft.world.entity.Entity);
  public static VeilPacketManager$PacketSink trackingAndSelf(net.minecraft.world.entity.Entity);   public static VeilPacketManager$PacketSink tracking(net.minecraft.server.level.ServerLevel, net.minecraft.world.level.ChunkPos);
  public static VeilPacketManager$PacketSink tracking(net.minecraft.server.level.ServerLevel, net.minecraft.core.BlockPos);   public static VeilPacketManager$PacketSink tracking(net.minecraft.server.level.ServerLevel, net.minecraft.core.BlockPos, net.minecraft.core.BlockPos);
  public static VeilPacketManager$PacketSink tracking(net.minecraft.world.level.block.entity.BlockEntity);
}
public interface VeilPacketManager$PacketSink {
  public default void sendPacket(net.minecraft.network.protocol.common.custom.CustomPacketPayload...);   public abstract void sendPacket(net.minecraft.network.protocol.Packet<?>);
}
```

> Veil 的 `handle` 收 **`ServerPacketContext` / `ClientPacketContext`**（不是 `IPayloadContext`）；
> 包体定义完全通用，只有 handler 参数类型不同。
> Sable 侧现成能力：`ServerSubLevel.playerSink()` → `VeilPacketManager$PacketSink`（§1.3 ✅），
> 可精准发给「看得到该 sub-level 的玩家」。

**方案对比**：原生 NeoForge ✅**推荐**（§9.1–9.4 全部核实）；Veil ✅可用（Simulated/Sable 在用，
多一层依赖 + 上下文类型不同）。
## 10. 汇总
### 10.1 已核实主题
| # | 主题 | 证据 |
|---|---|---|
| 1 | `SubLevelAssemblyHelper` 全部 public 方法 + `GatherResult`/`FrontierPredicate`/`AssemblyTransform`/`State` | javap |
| 1 | `SubLevel` / `ServerSubLevel`（`logicalPose`/`getMassTracker`/`boundingBox`/`isRemoved`…） | javap |
| 1 | 装配/拆解真实范例（参数顺序、返回值判空） | 源码 `SimAssemblyHelper.java` |
| 2 | `RigidBodyHandle` / `ForceTotal` / `QueuedForceGroup` / `PointForce` / `ForceGroup(s)` | javap |
| 2 | `applyImpulseAtPoint` 用的是 **plot 局部坐标** | 源码 javadoc + 实现 |
| 2 | `PhysicsPipeline` / `PhysicsPipelineBody` / `PhysicsPipelineProvider` / `SubLevelPhysicsSystem` | javap |
| 2 | `api.event` 三接口 + `ForgeSable*PhysicsTickEvent` | javap |
| 3 | `api/block/**` 全部 10 个接口的完整签名 | javap |
| 4 | `MassTracker` / `MassData` / `MergedMassTracker` | javap |
| 4 | 全部 constraint 配置/句柄类、`ConstraintJointAxis`、`setMotor`、`setLimit` | javap + 源码 javadoc |
| 5 | **`SableEventPlatform` 在 Sable 而非 Simulated**（更正任务书） | jar 清单 + javap + 源码 |
| 5 | `onPhysicsTick` = 注册监听器；`ServiceLoader.findFirst()` 陷阱 | 源码 |
| 5 | `Simulated`/`Aeronautics`/`Offroad` 初始化全流程；`DeferredRegister` / `ModContainer` | 源码 + javap |
| 6 | Registrate 子类 / 方块链 / 方块实体链 / DisplaySource / 创造标签 / 配置 / Veil 网络 | 源码逐行 |
| 7 | §7.0 全部 FQN + `SmartBlockEntity`/`KineticBlockEntity`/`BlockEntityBehaviour`/`ValueSettingsBehaviour`/`IControlContraption`/`ContraptionType`/`CreateBuiltInRegistries`/`BlockStressValues`/`DisplaySource`/`DisplayTarget`/`MovementBehaviour`/`BoilerHeater`/`AllBlocks`/`AllDisplaySources` | javap |
| 7 | **`AllContraptionTypes.BY_LEGACY_NAME` 仍然存在**（更正任务书假设） | javap |
| 7 | `BlockStressValues` 无静态 `registerProvider`；`BoilerHeaters` 无 `registerHeater`；`SmartBlockEntity` 无 `read/write` | javap 全量 public 列表 |
| 7 | `Registrate.create`/`block`/`blockEntity`/`item`/`simple`/`defaultCreativeTab`/`registerEventListeners` | javap |
| 8 | 模板里每个调用（含 `Vec3.atCenterOf`、`BlockEntity.level/worldPosition`、`Level.isClientSide`） | javap |
| 9 | 原生 `CustomPacketPayload`/`RegisterPayloadHandlersEvent`/`PayloadRegistrar`/`IPayloadContext`/`IPayloadHandler`/`PacketDistributor` | javap |
| 9 | Veil `VeilPacketManager` 完整 public 面 + `PacketSink` | javap |
| 9 | `@EventBusSubscriber` FQN + `Bus.GAME/MOD` + `ModContainer.registerConfig` | javap |
### 10.2 未核实清单（**不要照抄**）
| 项 | 原因 |
|---|---|
| `dev.simulated_team.simulated.platform.SableEventPlatform` | **不存在**（任务书笔误）；真名 `dev.ryanhcode.sable.platform.SableEventPlatform` |
| `BlockSubLevelLiftProvider$LiftProviderGroup`、`ValueSettingsBehaviour$ValueSettings` 成员 | 未单独 javap |
| `Registrate` 的 `BlockBuilder.initialProperties/simpleItem/properties/register` 逐条签名 | 只核实了 `block(...)` 返回 `BlockBuilder`；链上方法靠 Simulated 实测调用佐证 |
| `BlockEntityBuilder.validBlocks/validBlock/renderer/onRegister` | 未 javap `BlockEntityBuilder` |
| `RegisterEvent.register(...)` 重载 | 未 javap；§7.5 注册代码需先补这一条 |
| `IControlContraption` 的移动入口（旧 `startMoving`/`stopMoving`/`getControlTarget`） | javap 全量 public 里没有；6.0.10 挪去哪未追 |
| `BlockEntityBehaviour.addTo(...)` 静态工厂 | javap 全量 public 里没有（未在 6.0.10 源码确认） |
| `StreamCodec.composite` 各元数重载 | 只核实类存在 + 2 元写法实测可用 |
| `MassTracker.BLOCK_CENTER_OF_MASS` 读写时机、`BlockSubLevelCustomCenterOfMass` 如何被消费 | 需读 Sable 内部源码 |
| `CreateBuiltInRegistries.DISPLAY_SOURCE` 是否由 `RegisterEvent` 填充 | 需读 Create 源码 |
| `SablePlatformUtil.load` 多模组同时 provide 同一 service 时的确定性 | `ServiceLoader` 顺序未实测 |
| 旧 1.20.1 的 `ClockworkDisplaySources` / `ClockworkBoilerHeaters` 实现细节 | 本仓库 `Clockwork-1.20.1-main/` 里**只有调用点，无类文件** |
| Create 1.20.1 的 jar | gradle 缓存中不存在 → 无法逐条二进制对比；§7 的破坏性变化结论均基于「6.0.10 全量 public 列表中不存在」这一可证伪事实 |
### 10.3 移植最容易踩的 3 个坑
**坑 1 —— `ForceTotal.applyImpulseAtPoint` 要的是 plot 局部坐标，不是世界坐标。**
`ForceTotal.java:96-105` javadoc 明确写 `position the position inside the plot`，并且内部拿它减
`getCenterOfMass()` 算力矩。直接传 `Vec3.atCenterOf(worldPosition)` 或 `BlockPos`，会得到一个巨大的、
随 sub-level 位置漂移的力矩 → 表现为「一施力就疯狂自旋」。正确做法：

```java
final Vec3 local = subLevel.logicalPose().transformPositionInverse(Vec3.atCenterOf(this.worldPosition));
```

（参照 `LaunchedPlungerEntity.java:336`、`SpringBlockEntity.java:545`。）

**坑 2 —— 物理 tick ≠ 游戏 tick（力必须乘 `timeStep`）；且 `RigidBodyHandle.of` 没有 `SubLevel` 重载。**
`SableEventPlatform.java:17-25` javadoc 明说「一个游戏刻内会跑多次物理 tick，取决于子步配置」，
`SpringBlockEntity.java:280` 的 `.mul(timeStep)` 就是这个原因 —— 漏乘会让载具物理表现随 Sable
`PhysicsConfigData` 变化而突变。另外 `RigidBodyHandle` 只有
`of(ServerLevel, PhysicsPipelineBody)` 与 `of(ServerSubLevel)` 两个静态重载（§2.1），
而 `Sable.HELPER.getContaining(...)` 返回 `SubLevel` → **必须先 `(ServerSubLevel)` 强转**
（`WheelMountBlockEntity.java:234`）。

**坑 3 —— Create 6.0.10 的 NBT 与注册 API 已按 1.21.1 重写，旧覆写会直接编译失败，但别按印象批量改。**
- `SmartBlockEntity` **没有** `read(CompoundTag, boolean)` / `write(CompoundTag, boolean)`，改为
  `final readClient(CompoundTag, HolderLookup.Provider)` / `final writeClient(...)` / `final saveAdditional(...)`（§7.1）
  → 所有 1.20.1 的 `@Override protected void read(...)` 都是编译错误。
- `BlockEntityBehaviour.read/write` 是 **3 参**（多一个 `boolean clientPacket`），
  `createBoard` / `writeToClipboard` 等也新增了 `HolderLookup.Provider`。
- 反直觉的好消息：`AllContraptionTypes.BY_LEGACY_NAME` **仍然存在**（§7.5）—— 别按「旧 API 一定没了」的直觉删代码；
  但 `BlockStressValues.registerProvider(String, …)`、`BoilerHeaters.registerHeater(...)` **确实没了**
  （§7.6 / §7.10），必须改成 `SimpleRegistry.register(...)` / `registerProvider(...)`。
  **结论：逐个 javap 确认，不要按印象批量改。**

---

*生成方式：对 `clockwork_unoffical/libs/*.jar`、`build/moddev/artifacts/neoforge-21.1.228.jar`、
FML `loader-4.0.44.jar` 逐个 `javap -public`，并交叉核对 `Simulated-Project-main/`、`sable-main/`、
`Clockwork-1.20.1-main/` 的真实源码行。*


