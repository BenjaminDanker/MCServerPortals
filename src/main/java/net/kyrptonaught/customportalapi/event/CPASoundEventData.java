package net.kyrptonaught.customportalapi.event;

import net.minecraft.sounds.SoundEvent;

public record CPASoundEventData(SoundEvent sound, float pitch, float volume) {

}
