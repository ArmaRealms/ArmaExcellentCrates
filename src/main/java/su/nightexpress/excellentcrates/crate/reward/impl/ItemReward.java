package su.nightexpress.excellentcrates.crate.reward.impl;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.excellentcrates.CratesPlugin;
import su.nightexpress.excellentcrates.Placeholders;
import su.nightexpress.excellentcrates.api.crate.RewardType;
import su.nightexpress.excellentcrates.config.Lang;
import su.nightexpress.excellentcrates.crate.impl.Crate;
import su.nightexpress.excellentcrates.crate.impl.Rarity;
import su.nightexpress.excellentcrates.crate.reward.AbstractReward;
import su.nightexpress.excellentcrates.util.CrateUtils;
import su.nightexpress.excellentcrates.util.ItemHelper;
import su.nightexpress.nightcore.bridge.item.AdaptedItem;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.util.ItemNbt;
import su.nightexpress.nightcore.util.ItemUtil;
import su.nightexpress.nightcore.util.Players;
import su.nightexpress.nightcore.util.placeholder.Replacer;
import su.nightexpress.nightcore.util.problem.ProblemReporter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class ItemReward extends AbstractReward {

    private boolean           customPreview;
    private boolean           allowItemPlaceholders;
    private @Nullable List<String> previewDescription;
    private List<AdaptedItem> items;

    public ItemReward(@NotNull CratesPlugin plugin, @NotNull Crate crate, @NotNull String id, @NotNull Rarity rarity) {
        super(plugin, crate, id, rarity);
        this.items = new ArrayList<>();
    }

    @Override
    protected void loadAdditional(@NotNull FileConfig config, @NotNull String path) {
        if (config.contains(path + ".Items")) {
            int count = 0;
            for (String encoded : config.getStringList(path + ".Items")) {
                ItemStack itemStack = ItemNbt.decompress(encoded);
                if (itemStack == null) return;

                AdaptedItem provider = ItemHelper.vanilla(itemStack);
                config.set(path + ".ItemsData." + count++, provider);
            }
            config.remove(path + ".Items");
        }

        this.setCustomPreview(ConfigValue.create(path + ".Custom_Preview", false).read(config));
        this.setAllowItemPlaceholders(config.getBoolean(path + ".Placeholder_Apply"));
        this.previewDescription = config.isList(path + ".Description") ? List.copyOf(config.getStringList(path + ".Description")) : null;

        config.getSection(path + ".ItemsData").forEach(sId -> {
            AdaptedItem item = ItemHelper.read(config, path + ".ItemsData." + sId).orElse(null);
            if (item == null) {
                this.plugin.error("Invalid/Unknown item data at '" + config.getPath() + "' -> '" + path + ".ItemsData." + sId + "'.");
                return;
            }

            this.items.add(item);
        });
    }

    @Override
    protected void writeAdditional(@NotNull FileConfig config, @NotNull String path) {
        config.set(path + ".Custom_Preview", this.customPreview);
        config.set(path + ".Placeholder_Apply", this.allowItemPlaceholders);
        config.set(path + ".Description", this.previewDescription);
        config.remove(path + ".ItemsData");

        int count = 0;
        for (AdaptedItem provider : this.items) {
            config.set(path + ".ItemsData." + count++, provider);
        }
    }

    @Override
    protected void collectAdditionalProblems(@NotNull ProblemReporter reporter) {
        if (!this.hasContent()) {
            reporter.report(Lang.INSPECTIONS_REWARD_NO_ITEMS.text());
        }
        else if (this.hasInvalidItems()) {
            reporter.report(Lang.INSPECTIONS_REWARD_ITEMS.get(false));
        }
        if (this.customPreview && !this.preview.isValid()) {
            reporter.report(Lang.INSPECTIONS_REWARD_PREVIEW.get(false));
        }
    }

    @Override
    @NotNull
    public RewardType getType() {
        return RewardType.ITEM;
    }

    @Override
    public boolean hasContent() {
        return !this.items.isEmpty();
    }

    public boolean hasInvalidItems() {
        return this.items.stream().anyMatch(Predicate.not(AdaptedItem::isValid));
    }

    public int countItems() {
        return this.items.size();
    }

    @Override
    public void giveContent(@NotNull Player player) {
        Replacer replacer = this.createContentReplacer(player)
            .replace(Placeholders.forRewardPlayer(player))
            .replacePlaceholderAPI(player);

        this.getItems().forEach(provider -> {
            ItemStack itemStack = provider.getItemStack();
            if (itemStack == null) return;

            if (this.allowItemPlaceholders) {
                itemStack = itemStack.clone();
                ItemUtil.editMeta(itemStack, meta -> {
                    String itemName = ItemUtil.getItemNameSerialized(meta);
                    if (itemName != null) {
                        String resolved = replacer.apply(itemName);
                        if (!resolved.equals(itemName)) ItemUtil.setItemName(meta, resolved);
                    }
                    String customName = ItemUtil.getCustomNameSerialized(meta);
                    if (customName != null) {
                        String resolved = replacer.apply(customName);
                        if (!resolved.equals(customName)) ItemUtil.setCustomName(meta, resolved);
                    }
                    if (meta.hasLore()) {
                        List<String> lore = ItemUtil.getLoreSerialized(meta);
                        List<String> resolved = replacer.apply(lore);
                        if (!resolved.equals(lore)) ItemUtil.setLore(meta, resolved);
                    }
                });
            }

            Players.addItem(player, itemStack);
        });
    }

    public boolean isCustomPreview() {
        return this.customPreview;
    }

    public void setCustomPreview(boolean customPreview) {
        this.customPreview = customPreview;
    }

    public void setAllowItemPlaceholders(boolean allowItemPlaceholders) {
        this.allowItemPlaceholders = allowItemPlaceholders;
    }

    public boolean isAllowItemPlaceholders() {
        return this.allowItemPlaceholders;
    }

    @NotNull
    public String getName() {
        return ItemUtil.getNameSerialized(this.getPreviewItem());
    }

    @Override
    @NotNull
    public List<String> getDescription() {
        if (this.customPreview && this.previewDescription != null) return this.previewDescription;

        return ItemUtil.getLoreSerialized(this.getPreviewItem());
    }

    @Override
    @NotNull
    public ItemStack getPreviewItem() {
        return ItemHelper.toItemStack(this.getPreview());
    }

    @NotNull
    public AdaptedItem getPreview() {
        if (this.customPreview || this.items.isEmpty()) {
            return super.getPreview();
        }

        return this.items.getFirst();
    }

    @NotNull
    public List<AdaptedItem> getItems() {
        return this.items;
    }

    public void setItems(@NotNull List<AdaptedItem> items) {
        this.items = new ArrayList<>(items.stream().filter(AdaptedItem::isValid).limit(CrateUtils.REWARD_ITEMS_LIMIT).toList());
    }

    public void addItem(@NotNull AdaptedItem provider) {
        if (this.items.size() >= CrateUtils.REWARD_ITEMS_LIMIT) return;
        if (!provider.isValid()) return;

        this.items.add(provider);
    }
}
