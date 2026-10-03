package it.coralmc.duelli.objects;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

public class SavedInventory {

    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offhand;
    private final Location location;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final int level;
    private final float exp;
    private final List<PotionEffect> potionEffects;

    public SavedInventory(Player player) {
        PlayerInventory inv = player.getInventory();
        
        this.contents = inv.getContents().clone();
        this.armor = inv.getArmorContents().clone();
        this.offhand = inv.getItemInOffHand().clone();
        
        this.location = player.getLocation().clone();
        this.health = player.getHealth();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.level = player.getLevel();
        this.exp = player.getExp();
        
        this.potionEffects = new ArrayList<>(player.getActivePotionEffects());
    }

    public void restore(Player player) {
        PlayerInventory inv = player.getInventory();
        
        inv.setContents(contents);
        inv.setArmorContents(armor);
        inv.setItemInOffHand(offhand);
        
        player.setHealth(Math.min(health, player.getMaxHealth()));
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setLevel(level);
        player.setExp(exp);
        
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : potionEffects) {
            player.addPotionEffect(effect, true);
        }
        
        player.updateInventory();
    }

    public Location getLocation() { return location; }
    public double getHealth() { return health; }
    public int getFoodLevel() { return foodLevel; }
    public ItemStack[] getContents() { return contents; }
    public ItemStack[] getArmor() { return armor; }
    public ItemStack getOffhand() { return offhand; }
}