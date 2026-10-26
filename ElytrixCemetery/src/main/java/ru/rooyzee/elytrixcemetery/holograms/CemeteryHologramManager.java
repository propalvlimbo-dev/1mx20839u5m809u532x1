package ru.rooyzee.elytrixcemetery.holograms;

import eu.decentsoftware.holograms.api.DHAPI;
import ru.rooyzee.elytrixcemetery.Main;

/** Removes the retired event-level hologram; only per-grave holograms remain. */
public class CemeteryHologramManager {

    private static final String MAIN_HOLO_NAME = "cemetery_main_info";

    public CemeteryHologramManager(Main plugin) {
        // Keep the constructor signature used by Main; the manager no longer creates holograms.
    }

    /** Legacy entry point: the main cemetery hologram is intentionally disabled. */
    public void createOrUpdateMainHologram() {
        removeMainHologram();
    }

    public void refreshMainHologramNow() {
        removeMainHologram();
    }

    public void removeMainHologram() {
        try {
            if (DHAPI.getHologram(MAIN_HOLO_NAME) != null) {
                DHAPI.removeHologram(MAIN_HOLO_NAME);
            }
        } catch (Throwable ignored) {
        }
    }
}
