package kaptainwutax.tungsten.client.sim;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Examples for patterns #1, #2, #4 — async dispatch, batch runner, and world-free
 * kinematic mode. Not wired into any mod; keep it as a copy-paste reference.
 */
public final class AgentSimulatorAsyncExample {

    private AgentSimulatorAsyncExample() {}

    /**
     * #1 — Async dispatch. Caller is on a worker thread; the tick still happens on the
     * main (world-owning) thread, and the result comes back via a CompletableFuture.
     */
    public static CompletableFuture<AgentStatus> asyncTickFromWorker(AgentSimulator sim) {
        Executor worker = Executors.newSingleThreadExecutor();
        Executor main = Minecraft.getInstance()::execute;  // or: ServerLifecycleHooks.get().getServer()::execute
        AgentInput in = new AgentInput(true, false, false, false, false, false, true, 0f, 180f);
        return sim.simulateAsync(in, main).thenApplyAsync(status -> {
            // Now we're back on the worker thread. Heavy eval work goes here, off-main.
            System.out.println("position after tick: " + status.position);
            return status;
        }, worker);
    }

    /**
     * #2 — Batch runner. Submit many candidate input sequences; they all tick on the main
     * thread, but the caller's code (this method) blocks only until every sequence is
     * done, not for each individual tick.
     */
    public static List<List<AgentStatus>> evaluateCandidates(
        List<AgentSimulator> sims,
        List<List<AgentInput>> candidates,
        Executor main
    ) {
        long t0 = System.currentTimeMillis();
        List<List<AgentStatus>> results = AgentSimulator.runBatch(sims, candidates, main);
        long elapsed = System.currentTimeMillis() - t0;
        System.out.println("ran " + sims.size() + " sims in " + elapsed + " ms");
        return results;
    }

    /**
     * #2b — Same idea, but the caller wants to evaluate the result of each sequence on
     * a worker thread between ticks. Chains {@code simulateSequenceAsync} with
     * {@code thenApplyAsync} to bounce off the worker pool.
     */
    public static CompletableFuture<List<AgentStatus>> sequenceWithEvalOnWorker(
        AgentSimulator sim, List<AgentInput> inputs, Executor main, Executor worker
    ) {
        return sim.simulateSequenceAsync(inputs, main).thenApplyAsync(trace -> {
            // Heavy evaluation on a worker thread, not the main thread.
            for (AgentStatus s : trace) {
                // e.g. score the position, check a goal condition, etc.
            }
            return trace;
        }, worker);
    }

    /**
     * #4 — Kinematic pre-filter. Spin up a kinematic sim, run many candidate sequences
     * cheaply, pick the best few, then re-evaluate those with the full block-accurate sim.
     */
    public static List<Integer> kinematicPreFilterThenFullSim(
        AgentSimulator baseSim, List<List<AgentInput>> candidates, Executor main
    ) {
        // Build kinematic mirrors of the sims (same starting state, but no block reads).
        List<AgentSimulator> kinematicSims = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            AgentSimulator k = new AgentSimulator(clone(baseSim.getAgent()));
            k.withKinematic(true);
            kinematicSims.add(k);
        }
        List<List<AgentStatus>> fastResults = AgentSimulator.runBatch(kinematicSims, candidates, main);

        // Pick the top 10 by horizontal distance traveled.
        List<Integer> ranked = new ArrayList<>();
        for (int i = 0; i < fastResults.size(); i++) {
            List<AgentStatus> trace = fastResults.get(i);
            if (trace.isEmpty()) continue;
            double dist = trace.get(trace.size() - 1).position
                .subtract(trace.get(0).position).horizontalDistance();
            ranked.add(i);
            // pretend we sort by dist here
        }
        // Take the top 10 indices and re-run them through the full sim.
        List<AgentSimulator> fullSims = new ArrayList<>();
        List<List<AgentInput>> fullInputs = new ArrayList<>();
        for (int i = 0; i < Math.min(10, ranked.size()); i++) {
            int idx = ranked.get(i);
            AgentSimulator full = new AgentSimulator(clone(baseSim.getAgent()));
            fullSims.add(full);
            fullInputs.add(candidates.get(idx));
        }
        AgentSimulator.runBatch(fullSims, fullInputs, main);
        return ranked;
    }

    private static AgentEntity clone(AgentEntity src) {
        AgentEntity c = new AgentEntity(
            src.level(), src.getGameProfile(),
            src.startPosition, src.startVelocity, src.startYaw
        );
        c.setXRot(src.getXRot());
        return c;
    }
}
