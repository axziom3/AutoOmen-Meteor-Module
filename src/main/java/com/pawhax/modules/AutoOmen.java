package com.pawhax.modules;

import com.pawhax.PawHax;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AutoOmen extends Module {

    // Ticks to hold right-click to fully drink the bottle (drinking animation = 32 ticks)
    private static final int DRINK_TICKS = 35;

    private int drinkTimer = 0;
    private boolean isDrinking = false;
    private int savedSlot = -1;

    public AutoOmen() {
        super(PawHax.CATEGORY, "AutoOmen", "Automatically drinks the highest-level ominous bottle to maintain Bad Omen through raids.");
    }

    @Override
    public void onActivate() {
        drinkTimer = 0;
        isDrinking = false;
        savedSlot = -1;
    }

    @Override
    public void onDeactivate() {
        // Swap back to sword if we got disabled mid-drink
        if (isDrinking) {
            finishDrinking();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // If we're currently mid-drink, keep holding right-click until done
        if (isDrinking) {
            drinkTimer--;
            mc.options.useKey.setPressed(true);

            if (drinkTimer <= 0) {
                mc.options.useKey.setPressed(false);
                finishDrinking();
            }
            return;
        }

        boolean hasRaidOmen = mc.player.hasStatusEffect(StatusEffects.RAID_OMEN);
        boolean hasBadOmen  = mc.player.hasStatusEffect(StatusEffects.BAD_OMEN);

        // Only act when we have Raid Omen but not Bad Omen (raid finished / between raids)
        // OR when we have neither effect at all (first activation, no raid started yet)
        // We do NOT drink if a raid is actively in progress (Raid Omen is present)
        if (hasRaidOmen) return;
        if (hasBadOmen)  return;

        // Find the best ominous bottle in the full inventory (prefer highest amplifier)
        int bestSlot      = -1;
        int bestAmplifier = -1;
        boolean inHotbar  = false;

        // Check full inventory: slots 0-8 are hotbar, 9-35 are main inventory
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isOf(Items.OMINOUS_BOTTLE)) continue;

            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (contents == null) continue;

            // Ominous bottles use BAD_OMEN potion effect; amplifier is on the effect instance
            // getEffects() returns Iterable, not Collection, so we loop manually
            int amplifier = -1;
            for (var effect : contents.getEffects()) {
                if (effect.getEffectType().equals(StatusEffects.BAD_OMEN)) {
                    amplifier = Math.max(amplifier, effect.getAmplifier());
                }
            }
            if (amplifier < 0) continue; // no BAD_OMEN effect on this bottle

            if (amplifier > bestAmplifier) {
                bestAmplifier = amplifier;
                bestSlot      = i;
                inHotbar      = (i < 9);
            }
        }

        if (bestSlot == -1) {
            // No ominous bottles found — nothing to do
            return;
        }

        // Save the current hotbar slot so we can restore it afterward
        savedSlot = mc.player.getInventory().selectedSlot;

        if (inHotbar) {
            // Bottle is already in the hotbar — just switch to it
            mc.player.getInventory().selectedSlot = bestSlot;
        } else {
            // Bottle is in main inventory — swap it into the current hotbar slot
            InvUtils.move().from(bestSlot).toHotbar(savedSlot);
        }

        // Begin drinking
        isDrinking  = true;
        drinkTimer  = DRINK_TICKS;
        mc.options.useKey.setPressed(true);
    }

    private void finishDrinking() {
        mc.options.useKey.setPressed(false);
        isDrinking = false;
        drinkTimer = 0;

        if (mc.player == null) return;

        // Switch back to the sword / previously held slot
        if (savedSlot >= 0 && savedSlot < 9) {
            mc.player.getInventory().selectedSlot = savedSlot;
        }
        savedSlot = -1;
    }
}
