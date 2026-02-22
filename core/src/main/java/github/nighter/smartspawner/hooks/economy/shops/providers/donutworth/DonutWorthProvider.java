package github.nighter.smartspawner.hooks.economy.shops.providers.donutworth;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.shops.providers.ShopProvider;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

@RequiredArgsConstructor
public class DonutWorthProvider implements ShopProvider {
    private final SmartSpawner plugin;

    @Override
    public String getPluginName() {
        return "DonutWorth";
    }

    @Override
    public boolean isAvailable() {
        try {
            Plugin donutWorthPlugin = Bukkit.getPluginManager().getPlugin("DonutWorth");
            if (donutWorthPlugin != null && donutWorthPlugin.isEnabled()) {
                // Check if the DonutWorth API classes are available
                Class.forName("me.serbob.donutworth.api.util.Prices");

                return true;
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            plugin.debug("DonutWorth API not found: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Error initializing DonutWorth integration: " + e.getMessage());
        }
        return false;
    }

    @Override
    public double getSellPrice(Material material) {
        try {
            ItemStack itemStack = new ItemStack(material);

            double price = me.serbob.donutworth.api.util.Prices.getPrice(itemStack);
            return price > 0 ? price : 0.0;
        } catch (Exception e) {
            plugin.debug("Error getting sell price for " + material + " from DonutWorth: " + e.getMessage());
            return 0.0;
        }
    }
}