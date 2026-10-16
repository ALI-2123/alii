package dev.blade;

import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import org.joml.Vector3f;

/** Coloured dust particle (Minecraft 1.21.1). */
final class Dust {
    private Dust() {}

    static ParticleEffect of(int rgb, float scale) {
        return new DustParticleEffect(new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), scale);
    }
}
