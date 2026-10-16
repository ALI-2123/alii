package dev.blade;

import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;

/** Coloured dust particle (Minecraft 1.21.4). */
final class Dust {
    private Dust() {}

    static ParticleEffect of(int rgb, float scale) {
        return new DustParticleEffect(rgb, scale);
    }
}
