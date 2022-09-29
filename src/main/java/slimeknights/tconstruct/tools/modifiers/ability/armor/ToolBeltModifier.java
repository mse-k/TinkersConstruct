package slimeknights.tconstruct.tools.modifiers.ability.armor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.data.GenericLoaderRegistry.IGenericLoader;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.hooks.IArmorInteractModifier;
import slimeknights.tconstruct.library.modifiers.impl.InventoryModifier;
import slimeknights.tconstruct.library.modifiers.util.ModifierLevelDisplay;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;

import javax.annotation.Nullable;

import static slimeknights.tconstruct.library.tools.capability.ToolInventoryCapability.isBlacklisted;

public class ToolBeltModifier extends InventoryModifier implements IArmorInteractModifier {
  private static final Pattern PATTERN = new Pattern(TConstruct.MOD_ID, "tool_belt");

  /** Loader instance */
  public static final IGenericLoader<ToolBeltModifier> LOADER = new IGenericLoader<>() {
    @Override
    public ToolBeltModifier deserialize(JsonObject json) {
      JsonArray slotJson = GsonHelper.getAsJsonArray(json, "level_slots");
      int[] slots = new int[slotJson.size()];
      for (int i = 0; i < slots.length; i++) {
        slots[i] = GsonHelper.convertToInt(slotJson.get(i), "level_slots["+i+"]");
        if (i > 0 && slots[i] <= slots[i-1]) {
          throw new JsonSyntaxException("level_slots must be increasing");
        }
      }
      return new ToolBeltModifier(slots);
    }

    @Override
    public ToolBeltModifier fromNetwork(FriendlyByteBuf buffer) {
      return new ToolBeltModifier(buffer.readVarIntArray());
    }

    @Override
    public void serialize(ToolBeltModifier object, JsonObject json) {
      JsonArray jsonArray = new JsonArray();
      for (int i : object.counts) {
        jsonArray.add(i);
      }
      json.add("level_slots", jsonArray);
    }

    @Override
    public void toNetwork(ToolBeltModifier object, FriendlyByteBuf buffer) {
      buffer.writeVarIntArray(object.counts);
    }
  };

  private final int[] counts;
  public ToolBeltModifier(int[] counts) {
    super(counts[0]);
    this.counts = counts;
  }

  @Override
  public IGenericLoader<? extends Modifier> getLoader() {
    return LOADER;
  }

  @Override
  public Component getDisplayName(int level) {
    return ModifierLevelDisplay.PLUSES.nameForLevel(this, level);
  }

  @Override
  public int getPriority() {
    return 85; // after pockets, before shield strap
  }

  @Override
  public int getSlots(IToolContext tool, int level) {
    if (level > counts.length) {
      return 9;
    }
    return counts[level - 1];
  }

  @Override
  public boolean startArmorInteract(IToolStackView tool, int level, Player player, EquipmentSlot equipmentSlot) {
    if (!player.isShiftKeyDown()) {
      if (player.level.isClientSide) {
        return false; // TODO: see below
      }

      boolean didChange = false;
      int slots = getSlots(tool, level);
      ModDataNBT persistentData = tool.getPersistentData();
      ListTag list = new ListTag();
      boolean[] swapped = new boolean[slots];
      // if we have existing items, swap stacks at each index
      Inventory inventory = player.getInventory();
      ResourceLocation key = getInventoryKey();
      if (persistentData.contains(key, Tag.TAG_LIST)) {
        ListTag original = persistentData.get(key, GET_COMPOUND_LIST);
        if (!original.isEmpty()) {
          for (int i = 0; i < original.size(); i++) {
            CompoundTag compoundNBT = original.getCompound(i);
            int slot = compoundNBT.getInt(TAG_SLOT);
            if (slot < slots) {
              // ensure we can store the hotbar item
              ItemStack hotbar = inventory.getItem(slot);
              if (hotbar.isEmpty() || !isBlacklisted(hotbar)) {
                // swap the two items
                ItemStack parsed = ItemStack.of(compoundNBT);
                inventory.setItem(slot, parsed);
                if (!hotbar.isEmpty()) {
                  list.add(write(hotbar, slot));
                }
                didChange = true;
              } else {
                list.add(compoundNBT);
              }
              swapped[slot] = true;
            }
          }
        }
      }

      // list is empty, makes loop simplier
      for (int i = 0; i < slots; i++) {
        if (!swapped[i]) {
          ItemStack hotbar = player.getInventory().getItem(i);
          if (!hotbar.isEmpty() && !isBlacklisted(hotbar)) {
            list.add(write(hotbar, i));
            inventory.setItem(i, ItemStack.EMPTY);
            didChange = true;
          }
        }
      }

      // sound effect
      if (didChange) {
        persistentData.put(key, list);
        player.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1.0f, 1.0f);
      }
      //return true; TODO: tuning to make this a blocking interaction
    }
    return false;
  }

  @Nullable
  @Override
  public Pattern getPattern(IToolStackView tool, int level, int slot, boolean hasStack) {
    return hasStack ? null : PATTERN;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public <T> T getModule(Class<T> type) {
    if (type == IArmorInteractModifier.class) {
      return (T) this;
    }
    return super.getModule(type);
  }
}
