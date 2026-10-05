package it.xxmauro.duelli.managers;

import it.xxmauro.duelli.DuelliPlugin;
import it.xxmauro.duelli.utils.ItemBuilder;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class KitManager {

    private final DuelliPlugin plugin;
    private final Map<String, Kit> kits = new HashMap<>();
    private String defaultKit;

    public KitManager(DuelliPlugin plugin) {
        this.plugin = plugin;
    }

    public record ArmorSet(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots) {}

    public void loadKits() {
        kits.clear();
        FileConfiguration config = plugin.getConfigManager().getKitsConfig();
        ConfigurationSection kitsSection = config.getConfigurationSection("kits");
        
        if (kitsSection == null) {
            plugin.getLogger().warning("Nessun kit trovato in kits.yml!");
            return;
        }

        for (String key : kitsSection.getKeys(false)) {
            ConfigurationSection kitSection = kitsSection.getConfigurationSection(key);
            if (kitSection != null) {
                Kit kit = parseKit(key, kitSection);
                if (kit != null) {
                    kits.put(key.toLowerCase(), kit);
                }
            }
        }

        this.defaultKit = config.getString("default-kit", "guerriero").toLowerCase();
        if (!kits.containsKey(defaultKit)) {
            this.defaultKit = kits.keySet().iterator().next();
        }

        plugin.getLogger().info("Caricati " + kits.size() + " kit. Default: " + defaultKit);
    }

    private Kit parseKit(String id, ConfigurationSection section) {
        try {
            String name = section.getString("name", id);
            String permission = section.getString("permission", "duelli.kit." + id);
            String iconStr = section.getString("icon", "DIAMOND_SWORD");
            Material icon = Material.getMaterial(iconStr.toUpperCase());
            if (icon == null) icon = Material.DIAMOND_SWORD;

            List<String> description = section.getStringList("description");

            Map<Integer, ItemStack> items = new HashMap<>();
            ConfigurationSection itemsSection = section.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String itemKey : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemKey);
                    if (itemSection != null) {
                        ItemStack item = parseItem(itemSection);
                        if (item != null) {
                            int slot = itemSection.getInt("slot", 0);
                            items.put(slot, item);
                        }
                    }
                }
            }

            ArmorSet armor = parseArmor(section.getConfigurationSection("armor"));

            List<PotionEffect> effects = new ArrayList<>();
            ConfigurationSection effectsSection = section.getConfigurationSection("potion-effects");
            if (effectsSection != null) {
                for (String effectKey : effectsSection.getKeys(false)) {
                    ConfigurationSection effectSection = effectsSection.getConfigurationSection(effectKey);
                    if (effectSection != null) {
                        PotionEffect effect = parsePotionEffect(effectSection);
                        if (effect != null) effects.add(effect);
                    }
                }
            }

            return new Kit(id.toLowerCase(), name, permission, icon, description, items, armor, effects);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Errore caricamento kit: " + id, e);
            return null;
        }
    }

    private ItemStack parseItem(ConfigurationSection section) {
        String materialStr = section.getString("material");
        if (materialStr == null) return null;
        
        Material material = Material.getMaterial(materialStr.toUpperCase());
        if (material == null) return null;

        int amount = section.getInt("amount", 1);
        ItemStack item = new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            String name = section.getString("name");
            if (name != null) meta.setDisplayName(plugin.getMessageUtil().colorize(name));
            
            List<String> lore = section.getStringList("lore");
            if (!lore.isEmpty()) {
                meta.setLore(lore.stream().map(plugin.getMessageUtil()::colorize).toList());
            }
            
            ConfigurationSection enchSection = section.getConfigurationSection("enchantments");
            if (enchSection != null) {
                for (String enchKey : enchSection.getKeys(false)) {
                    Enchantment enchantment = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(enchKey.toLowerCase()));
                    if (enchantment != null) {
                        int level = enchSection.getInt(enchKey);
                        meta.addEnchant(enchantment, level, true);
                    }
                }
            }
            
            item.setItemMeta(meta);
        }

        if (material == Material.SPLASH_POTION || material == Material.POTION || material == Material.LINGERING_POTION) {
            String potionTypeStr = section.getString("potion-type");
            int potionLevel = section.getInt("potion-level", 1);
            if (potionTypeStr != null) {
                try {
                    PotionType potionType = PotionType.valueOf(potionTypeStr.toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }
        }

        return item;
    }

    private ArmorSet parseArmor(ConfigurationSection section) {
        if (section == null) return new ArmorSet(null, null, null, null);
        
        ItemStack helmet = parseArmorPiece(section.getConfigurationSection("helmet"));
        ItemStack chestplate = parseArmorPiece(section.getConfigurationSection("chestplate"));
        ItemStack leggings = parseArmorPiece(section.getConfigurationSection("leggings"));
        ItemStack boots = parseArmorPiece(section.getConfigurationSection("boots"));
        
        return new ArmorSet(helmet, chestplate, leggings, boots);
    }

    private ItemStack parseArmorPiece(ConfigurationSection section) {
        if (section == null) return null;
        
        String materialStr = section.getString("material");
        if (materialStr == null) return null;
        
        Material material = Material.getMaterial(materialStr.toUpperCase());
        if (material == null) return null;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            String name = section.getString("name");
            if (name != null) meta.setDisplayName(plugin.getMessageUtil().colorize(name));
            
            String colorStr = section.getString("color");
            if (colorStr != null && meta instanceof LeatherArmorMeta leatherMeta) {
                try {
                    Color color = Color.fromRGB(Integer.parseInt(colorStr.replace("#", ""), 16));
                    leatherMeta.setColor(color);
                } catch (NumberFormatException ignored) {}
            }
            
            ConfigurationSection enchSection = section.getConfigurationSection("enchantments");
            if (enchSection != null) {
                for (String enchKey : enchSection.getKeys(false)) {
                    Enchantment enchantment = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(enchKey.toLowerCase()));
                    if (enchantment != null) {
                        int level = enchSection.getInt(enchKey);
                        meta.addEnchant(enchantment, level, true);
                    }
                }
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }

    private PotionEffect parsePotionEffect(ConfigurationSection section) {
        String typeStr = section.getString("type");
        if (typeStr == null) return null;
        
        PotionEffectType type = PotionEffectType.getByName(typeStr.toUpperCase());
        if (type == null) return null;
        
        int amplifier = section.getInt("amplifier", 0);
        int duration = section.getInt("duration", 999999);
        
        return new PotionEffect(type, duration * 20, amplifier, false, false, true);
    }

    public Kit getKit(String id) {
        return kits.get(id.toLowerCase());
    }

    public Kit getDefaultKit() {
        return kits.get(defaultKit);
    }

    public Map<String, Kit> getAllKits() {
        return new HashMap<>(kits);
    }

    public boolean hasKit(String id) {
        return kits.containsKey(id.toLowerCase());
    }

    public void applyKit(Player player, Kit kit) {
        if (kit == null || player == null) return;
        
        PlayerInventory inv = player.getInventory();
        
        inv.clear();
        inv.setArmorContents(null);
        
        for (Map.Entry<Integer, ItemStack> entry : kit.getItems().entrySet()) {
            inv.setItem(entry.getKey(), entry.getValue().clone());
        }
        
        ArmorSet armor = kit.getArmor();
        if (armor.helmet() != null) inv.setHelmet(armor.helmet().clone());
        if (armor.chestplate() != null) inv.setChestplate(armor.chestplate().clone());
        if (armor.leggings() != null) inv.setLeggings(armor.leggings().clone());
        if (armor.boots() != null) inv.setBoots(armor.boots().clone());
        
        for (PotionEffect effect : kit.getEffects()) {
            player.addPotionEffect(effect, true);
        }
        
        player.updateInventory();
    }

    public boolean canUseKit(Player player, String kitId) {
        Kit kit = getKit(kitId);
        if (kit == null) return false;
        return player.hasPermission(kit.getPermission()) || player.hasPermission("duelli.kit.*");
    }

    public static class Kit {
        private final String id;
        private final String name;
        private final String permission;
        private final Material icon;
        private final List<String> description;
        private final Map<Integer, ItemStack> items;
        private final ArmorSet armor;
        private final List<PotionEffect> effects;

        public Kit(String id, String name, String permission, Material icon, 
                   List<String> description, Map<Integer, ItemStack> items, 
                   ArmorSet armor, List<PotionEffect> effects) {
            this.id = id;
            this.name = name;
            this.permission = permission;
            this.icon = icon;
            this.description = description;
            this.items = items;
            this.armor = armor;
            this.effects = effects;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getPermission() { return permission; }
        public Material getIcon() { return icon; }
        public List<String> getDescription() { return description; }
        public Map<Integer, ItemStack> getItems() { return items; }
        public ArmorSet getArmor() { return armor; }
        public List<PotionEffect> getEffects() { return effects; }
    }
}
