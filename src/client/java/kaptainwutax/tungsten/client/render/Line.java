package kaptainwutax.tungsten.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;import kaptainwutax.tungsten.client.TungstenModDataContainer;
import net.minecraft.core.BlockPos;import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

public class Line extends Renderer {

    public Vec3 start;
    public Vec3 end;
    public Color color;

    public Line() {
        this(Vec3.ZERO, Vec3.ZERO, Color.WHITE);
    }

    public Line(Vec3 start, Vec3 end) {
        this(start, end, Color.WHITE);
    }

    public Line(Vec3 start, Vec3 end, Color color) {
        this.start = start;
        this.end = end;
        this.color = color;
    }

    @Override
    public void render() {
        if(TungstenModDataContainer.gameRenderer == null || this.start == null || this.end == null || this.color == null)return;
//        Vec3d camPos = TungstenModDataContainer.gameRenderer.getCamera().getCameraPos();
//        this.putVertex(builder, camPos, this.start);
//        this.putVertex(builder, camPos, this.end);
        Gizmos.line(this.start, this.end, this.color.toARGB(255)).setAlwaysOnTop();
    }

    protected void putVertex(BufferBuilder buffer, Vec3 camPos, Vec3 pos) {
        buffer.addVertex(
                (float) (pos.x() - camPos.x),
                (float) (pos.y() - camPos.y),
                (float) (pos.z() - camPos.z)
        ).setColor(
                this.color.getFRed(),
                this.color.getFGreen(),
                this.color.getFBlue(),
                1.0F
        );
    }

    @Override
    public BlockPos getPos() {
        double x = (this.end.x() - this.start.x()) / 2 + this.start.x();
        double y = (this.end.y() - this.start.y()) / 2 + this.start.y();
        double z = (this.end.z() - this.start.z()) / 2 + this.start.z();
        return new BlockPos((int) x, (int) y, (int) z);
    }

}
