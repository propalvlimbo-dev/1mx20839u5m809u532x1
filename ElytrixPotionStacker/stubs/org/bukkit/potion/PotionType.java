package org.bukkit.potion;

import java.util.Collections;
import java.util.List;

public enum PotionType {
    WATER,
    MUNDANE,
    THICK,
    AWKWARD;

    public List<PotionEffect> getPotionEffects() { return Collections.emptyList(); }
}
