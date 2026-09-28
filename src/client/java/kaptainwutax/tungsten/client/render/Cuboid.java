package kaptainwutax.tungsten.client.render;


import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.swing.*;

public class Cuboid extends Renderer {

    public Vec3 start;
    public Vec3 size;

    private Line[] edges = new Line[12];

    public Cuboid() {
        this(Vec3.ZERO, Vec3.ZERO, Color.WHITE);
    }

    public Cuboid(Vec3 pos) {
        this(pos, new Vec3(1, 1, 1), Color.WHITE);
    }

    public Cuboid(AABB box, Color color) {
        this(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ), color);
    }

    public Cuboid(Vec3 start, Vec3 size, Color color) {
        this.start = start;
        this.size = size;
        this.edges[0] = new Line(this.start, this.start.add(this.size.x(), 0, 0), color);
        this.edges[1] = new Line(this.start, this.start.add(0, this.size.y(), 0), color);
        this.edges[2] = new Line(this.start, this.start.add(0, 0, this.size.z()), color);
        this.edges[3] = new Line(this.start.add(this.size.x(), 0, this.size.z()), this.start.add(this.size.x(), 0, 0), color);
        this.edges[4] = new Line(this.start.add(this.size.x(), 0, this.size.z()), this.start.add(this.size.x(), this.size.y(), this.size.z()), color);
        this.edges[5] = new Line(this.start.add(this.size.x(), 0, this.size.z()), this.start.add(0, 0, this.size.z()), color);
        this.edges[6] = new Line(this.start.add(this.size.x(), this.size.y(), 0), this.start.add(this.size.x(), 0, 0), color);
        this.edges[7] = new Line(this.start.add(this.size.x(), this.size.y(), 0), this.start.add(0, this.size.y(), 0), color);
        this.edges[8] = new Line(this.start.add(this.size.x(), this.size.y(), 0), this.start.add(this.size.x(), this.size.y(), this.size.z()), color);
        this.edges[9] = new Line(this.start.add(0, this.size.y(), this.size.z()), this.start.add(0, 0, this.size.z()), color);
        this.edges[10] = new Line(this.start.add(0, this.size.y(), this.size.z()), this.start.add(0, this.size.y(), 0), color);
        this.edges[11] = new Line(this.start.add(0, this.size.y(), this.size.z()), this.start.add(this.size.x(), this.size.y(), this.size.z()), color);
    }

    @Override
    public void render(BufferBuilder builder) {
        if(this.start == null || this.size == null || this.edges == null)return;

        for(Line edge: this.edges) {
            if(edge == null)continue;
            edge.render(builder);
        }
    }

    @Override
    public BlockPos getPos() {
    	Vec3 vec = this.start.add(this.size.x() / 2, this.size.y() / 2, this.size.z() / 2);
        return new BlockPos((int) vec.x, (int) vec.y, (int) vec.z);
    }

}
