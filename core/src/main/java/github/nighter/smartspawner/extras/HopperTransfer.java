package github.nighter.smartspawner.extras;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.utils.BlockPos;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class HopperTransfer {

    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final SpawnerGuiViewManager guiManager;

    public HopperTransfer(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.guiManager = plugin.getSpawnerGuiViewManager();
    }

    public void process(BlockPos hopperPos) {

        Location hopperLoc = hopperPos.toLocation();
        Block hopperBlock = hopperLoc.getBlock();

        if (hopperBlock.getType() != Material.HOPPER) return;

        Block spawnerBlock = hopperBlock.getRelative(BlockFace.UP);
        if (spawnerBlock.getType() != Material.SPAWNER) return;

        transferItems(hopperLoc, spawnerBlock.getLocation());
    }

    private void transferItems(Location hopperLoc, Location spawnerLoc) {
        SpawnerData spawner = spawnerManager.getSpawnerByLocation(spawnerLoc);
        if (spawner == null) return;

        ReentrantLock lock = spawner.getInventoryLock();
        if (!lock.tryLock()) return;

        try {
            VirtualInventory virtualInv = spawner.getVirtualInventory();
            if (virtualInv == null) return;

            Hopper hopper = (Hopper) hopperLoc.getBlock().getState(false);
            if (hopper == null) return;

            Map<Integer, ItemStack> displayItems = virtualInv.getDisplayInventory();
            if (displayItems == null || displayItems.isEmpty()) return;

            Inventory hopperInv = hopper.getInventory();

            int maxTransfers = plugin.getConfig().getInt("hopper.stack_per_transfer", 5);
            int transferred = 0;

            List<ItemStack> removed = new ArrayList<>();

            for (ItemStack item : displayItems.values()) {
                if (transferred >= maxTransfers) break;
                if (item == null || item.getType() == Material.AIR) continue;

                int stackAmount = item.getAmount();
                int maxStackSize = item.getMaxStackSize();

                int availableSpace = getAvailableSpace(hopperInv, item);
                if (availableSpace <= 0) continue;

                int toMove = Math.min(stackAmount, availableSpace);

                addAmountToInventory(hopperInv, item, toMove);

                ItemStack removeStack = item.clone();
                removeStack.setAmount(toMove);

                removed.add(removeStack);
                transferred++;
            }

            if (!removed.isEmpty()) {
                spawner.removeItemsAndUpdateSellValue(removed);
                guiManager.updateSpawnerMenuViewers(spawner);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error transferring items from spawner to hopper at " + hopperLoc, e);
        } finally {
            lock.unlock();
        }
    }

    private int getAvailableSpace(Inventory inventory, ItemStack target) {
        int maxStackSize = target.getMaxStackSize();
        int space = 0;

        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                space += maxStackSize;
                continue;
            }

            if (item.isSimilar(target)) {
                space += Math.max(maxStackSize - item.getAmount(), 0);
            }
        }

        return space;
    }

    private void addAmountToInventory(Inventory inventory, ItemStack target, int amount) {
        int maxStackSize = target.getMaxStackSize();

        // merge first
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null) continue;
            if (!item.isSimilar(target)) continue;

            int available = Math.max(maxStackSize - item.getAmount(), 0);
            int take = Math.min(available, amount);

            if (take > 0) {
                item.setAmount(item.getAmount() + take);
                amount -= take;
            }

            if (amount <= 0) return;
        }

        // then empty slots
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack slot = inventory.getItem(i);

            if (slot == null || slot.getType() == Material.AIR) {

                int take = Math.min(maxStackSize, amount);

                ItemStack newStack = target.clone();
                newStack.setAmount(take);

                inventory.setItem(i, newStack);
                amount -= take;
            }

            if (amount <= 0) return;
        }
    }
}
