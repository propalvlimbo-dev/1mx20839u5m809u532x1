package ru.rooyzee.elytrixcasino.automate;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixcasino.util.Text;

public final class SlotSymbol {

    private final String id;
    private final Material material;
    private final int weight;
    private final String name;
    private final double pay3;
    private final double pay4;
    private final double pay5;

    public SlotSymbol(String id, Material material, int weight, String name,
                      double pay3, double pay4, double pay5) {
        this.id = id;
        this.material = material;
        this.weight = Math.max(0, weight);
        this.name = name == null ? "&f?" : name;
        this.pay3 = Math.max(0.0D, pay3);
        this.pay4 = Math.max(0.0D, pay4);
        this.pay5 = Math.max(0.0D, pay5);
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public int getWeight() {
        return weight;
    }

    public String getName() {
        return name;
    }

    public double pay(int count) {
        if (count >= 5) {
            return pay5;
        }
        if (count == 4) {
            return pay4;
        }
        if (count == 3) {
            return pay3;
        }
        return 0.0D;
    }

    public ItemStack icon() {
        return icon(false, false);
    }

    public ItemStack icon(boolean blur, boolean glow) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (blur) {
                meta.setDisplayName(" ");
            } else {
                meta.setDisplayName(Text.color(name));
            }
            if (glow) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
