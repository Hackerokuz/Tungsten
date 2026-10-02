package kaptainwutax.tungsten.client.sim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Single-step driver for an {@link AgentEntity}.
 *
 * <p>This is the public face of the simulator: call {@link #simulate(AgentInput)} (or the
 * batch overload) to advance the agent one MC tick and get back an {@link AgentStatus}
 * snapshot. Construction accepts either an existing {@link AgentEntity} or a live
 * {@link LocalPlayer} (a fresh entity mirroring the player is built for you).
 *
 * <h2>Threading</h2>
 * Must run on the thread that owns the underlying {@link Level}. On SP/integrated servers
 * that's the client thread for a {@code ClientLevel}, or the server thread when bound to a
 * {@link ServerLevel} (preferred — block physics and chunks page in natively). MP clients
 * only have the client world available, so chunk reads are best-effort there.
 *
 * <h2>Standalone</h2>
 * This class (and everything it pulls in) has zero dependencies on the rest of the
 * ParkourCalculatorMod repo. Only Minecraft and the Java standard library.
 */
public final class AgentSimulator {

    private final AgentEntity agent;
    private boolean chunkPreload = true;

    public AgentSimulator(AgentEntity agent) {
        this.agent = Objects.requireNonNull(agent, "agent");
    }

    /**
     * Builds a fresh {@link AgentEntity} mirroring the given player, then wraps it.
     * Prefers the integrated server's {@link ServerLevel} when running on its thread so
     * block reads and chunk loads behave like the real server. Falls back to the client
     * world on dedicated multiplayer.
     */
    public static AgentSimulator fromLocalPlayer(LocalPlayer player, Vec3 startVelocity) {
        Objects.requireNonNull(player, "player");
        Minecraft mc = Minecraft.getInstance();
        Level clientWorld = mc.level;
        if (clientWorld == null) {
            throw new IllegalStateException("Cannot seed agent: client world not loaded");
        }
        Level simWorld = clientWorld;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null && server.isSameThread()) {
            ServerLevel serverWorld = server.getLevel(clientWorld.dimension());
            if (serverWorld != null) simWorld = serverWorld;
        }
        Vec3 start = player.position();
        Vec3 vel = startVelocity != null ? startVelocity : Vec3.ZERO;
        AgentEntity entity = new AgentEntity(simWorld, player.getGameProfile(), start, vel, player.getYRot());
        // Copy attribute base values so movement-speed buffs / sprint modifiers are honest.
        entity.getAttributes().assignBaseValues(player.getAttributes());
        entity.setXRot(player.getXRot());
        entity.setOnGround(player.onGround());
        entity.horizontalCollision = player.horizontalCollision;
        entity.minorHorizontalCollision = player.minorHorizontalCollision;
        entity.setSprinting(player.isSprinting());
        entity.setSwimming(player.isSwimming());
        return new AgentSimulator(entity);
    }

    public static AgentSimulator fromLocalPlayer(LocalPlayer player) {
        return fromLocalPlayer(player, Vec3.ZERO);
    }

    /** Toggle chunk preloading around the agent before each tick (default true). */
    public AgentSimulator withChunkPreload(boolean enabled) {
        this.chunkPreload = enabled;
        return this;
    }

    public AgentEntity getAgent() {
        return agent;
    }

    // ------------------------------------------------------------------
    // Core step
    // ------------------------------------------------------------------

    /** Feed the input, tick the agent once, return the resulting status. */
    public AgentStatus simulate(AgentInput input) {
        Objects.requireNonNull(input, "input");
        agent.feed(input);
        if (chunkPreload) preloadChunksAround(agent);
        agent.tick();
        return agent.snapshot();
    }

    /**
     * Runs {@link #simulate(AgentInput)} once per input and returns every status in order.
     * The final status reflects the last input's tick.
     */
    public List<AgentStatus> simulate(List<AgentInput> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        List<AgentStatus> out = new ArrayList<>(inputs.size());
        for (AgentInput in : inputs) {
            out.add(simulate(in));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Async dispatch + batch (#1 + #2)
    // ------------------------------------------------------------------
    //
    // The actual tick() must run on the world-owning thread (Minecraft is single-threaded
    // for entity + world mutations), so the async API simply hops to the supplied tick
    // executor and returns a CompletableFuture. The caller's code is free to be on any
    // thread, including a worker pool doing AI / search.

    /**
     * Submit one tick of work to {@code tickThread} and return a future that completes with
     * the resulting status. The future completes on {@code tickThread}; chain with
     * {@code .thenApplyAsync(... , workerPool)} to continue work off-thread.
     *
     * <p>The wrapped {@link AgentEntity} is single-threaded — never call
     * {@code simulateAsync} on the same simulator from multiple threads in parallel.
     * Serialize calls via {@link #simulateSequenceAsync(List, Executor)} or
     * {@link #runBatch(List, List, Executor)} instead.
     *
     * <p>For a Fabric client, a typical {@code tickThread} is
     * {@code Minecraft.getInstance()::execute}. For a dedicated server, use
     * {@code server::execute} or {@code server.submit(...)}.
     */
    public CompletableFuture<AgentStatus> simulateAsync(AgentInput input, Executor tickThread) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(tickThread, "tickThread");
        CompletableFuture<AgentStatus> result = new CompletableFuture<>();
        tickThread.execute(() -> {
            try {
                result.complete(simulate(input));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result;
    }

    /**
     * Chain a sequence of ticks on the agent. Each tick is dispatched to
     * {@code tickThread} after the previous one completes, so the sequence runs
     * sequentially on the tick thread. Returns a future that completes with the full
     * status list once the last input has been ticked.
     */
    public CompletableFuture<List<AgentStatus>> simulateSequenceAsync(
        List<AgentInput> inputs, Executor tickThread
    ) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(tickThread, "tickThread");
        CompletableFuture<List<AgentStatus>> result = new CompletableFuture<>();
        List<AgentStatus> out = new ArrayList<>(inputs.size());
        chainAsync(inputs, 0, out, tickThread, result);
        return result;
    }

    private void chainAsync(
        List<AgentInput> inputs,
        int idx,
        List<AgentStatus> out,
        Executor tickThread,
        CompletableFuture<List<AgentStatus>> result
    ) {
        if (idx >= inputs.size()) {
            result.complete(out);
            return;
        }
        // whenComplete runs on whichever thread completed the future — which is the tick
        // thread, since that's where we called result.complete(...). That means the
        // recursive enqueue below also fires on the tick thread, which is exactly the
        // single-threaded ordering we want.
        simulateAsync(inputs.get(idx), tickThread).whenComplete((status, err) -> {
            if (err != null) {
                result.completeExceptionally(err);
                return;
            }
            out.add(status);
            chainAsync(inputs, idx + 1, out, tickThread, result);
        });
    }

    /**
     * Run N independent simulations in parallel. Each {@link AgentSimulator} gets its own
     * sequence; ticks from all sequences are interleaved on {@code tickThread}, which
     * remains the serialization point. The caller's thread blocks on
     * {@link CompletableFuture#allOf} until every sequence is done.
     *
     * <p>Best use case: many candidate input sequences evaluated in parallel from a
     * worker thread, while {@code tickThread} (the main client/server thread) handles
     * the actual ticks one at a time.
     */
    public static List<List<AgentStatus>> runBatch(
        List<AgentSimulator> sims,
        List<List<AgentInput>> inputLists,
        Executor tickThread
    ) {
        if (sims == null || inputLists == null) {
            throw new IllegalArgumentException("sims and inputLists must be non-null");
        }
        if (sims.size() != inputLists.size()) {
            throw new IllegalArgumentException(
                "sims.size() (" + sims.size() + ") != inputLists.size() (" + inputLists.size() + ")");
        }
        if (sims.isEmpty()) return List.of();

        List<CompletableFuture<List<AgentStatus>>> futures = new ArrayList<>(sims.size());
        for (int i = 0; i < sims.size(); i++) {
            futures.add(sims.get(i).simulateSequenceAsync(inputLists.get(i), tickThread));
        }
        // Block until every sequence is done. Since allOf.join() waits for completion, the
        // per-future join() below is immediate.
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<List<AgentStatus>> out = new ArrayList<>(futures.size());
        for (CompletableFuture<List<AgentStatus>> f : futures) {
            out.add(f.join());
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Kinematic mode (#4)
    // ------------------------------------------------------------------

    /**
     * Enable world-free kinematic travel. Useful as a fast pre-filter on a worker thread
     * before running the full block-accurate sim. See {@link AgentEntity#kinematicTravel}
     * for the caveats.
     *
     * <p>Note: kinematic mode does NOT make the entity thread-safe. The actual
     * {@code tick()} must still run on the world-owning thread (use
     * {@link #simulateAsync(AgentInput, Executor)}). Kinematic mode just removes the
     * <em>block reads</em> inside {@code travel()}, so a world with no chunks around the
     * agent won't crash the sim.
     */
    public AgentSimulator withKinematic(boolean enabled) {
        agent.setKinematic(enabled);
        return this;
    }

    public boolean isKinematic() {
        return agent.isKinematic();
    }

    /** Captures the agent's current state without advancing it. */
    public AgentStatus snapshot() {
        return agent.snapshot();
    }

    // ------------------------------------------------------------------
    // Reset / restore
    // ------------------------------------------------------------------

    /** Resets the agent to its captured start position/velocity/yaw. */
    public void reset() {
        agent.resetPlayer();
    }

    /** Teleports the agent, zeroes transient flags, and clears input. */
    public void reset(Vec3 position, Vec3 velocity, float yaw) {
        agent.teleportRest(position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
        agent.setYRot(yaw);
    }

    /** Restores from a previously captured {@link AgentEntity.Checkpoint}. */
    public void reset(AgentEntity.Checkpoint checkpoint) {
        agent.restoreCheckpoint(checkpoint);
    }

    // ------------------------------------------------------------------
    // Potion effects
    // ------------------------------------------------------------------

    /**
     * Apply Speed / Jump Boost effects matching the given amplifier (vanilla level - 1).
     * Pass 0 to remove. Mirrors what {@code FabricSimulator.applyTickEffects} does.
     */
    public void applyPotionEffects(int speedAmplifier, int jumpBoostAmplifier) {
        agent.removeAllEffects();
        if (speedAmplifier > 0) {
            agent.addEffect(new MobEffectInstance(MobEffects.SPEED, 2, speedAmplifier - 1));
        }
        if (jumpBoostAmplifier > 0) {
            agent.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 2, jumpBoostAmplifier - 1));
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * 1-chunk-radius preload policy. {@code hasChunk} short-circuits the common case;
     * {@code getChunk(FULL, true)} only fires on a miss. No-op on ClientLevel (can't load
     * chunks from there).
     */
    private static void preloadChunksAround(AgentEntity e) {
        Level world = e.level();
        if (!(world instanceof ServerLevel serverWorld)) return;
        Vec3 pos = e.position();
        int cx = Mth.floor(pos.x) >> 4;
        int cz = Mth.floor(pos.z) >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = cx + dx, z = cz + dz;
                if (!serverWorld.hasChunk(x, z)) {
                    serverWorld.getChunkSource().getChunk(x, z, ChunkStatus.FULL, true);
                }
            }
        }
    }
}
