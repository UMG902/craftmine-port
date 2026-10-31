/*
 * Ported from 25w14craftmine (dev.craftmine.unlock.DisplayInfoBuilder).
 * 26.3's DisplayInfo is a record without a Builder and without the "hint" field,
 * so the builder lives standalone here and keeps the hint alongside the built record.
 */
package dev.craftmine.unlock;

import java.util.Optional;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;

public class DisplayInfoBuilder {
    private ItemStack icon;
    private Component title;
    private Component description;
    private Component hint;
    private Optional<ClientAsset> background = Optional.empty();
    private AdvancementType type;
    private boolean showToast = false;
    private boolean announceChat = false;
    private boolean hidden = false;

    public DisplayInfoBuilder() {
    }

    public DisplayInfoBuilder withIcon(ItemStack itemStack) {
        this.icon = itemStack;
        return this;
    }

    public DisplayInfoBuilder withTitle(Component component) {
        this.title = component;
        return this;
    }

    public DisplayInfoBuilder withTitleStyle(Style style) {
        this.title = java.util.Objects.requireNonNull(this.title).copy().withStyle(style);
        return this;
    }

    public DisplayInfoBuilder withDescription(Component component) {
        this.description = component;
        return this;
    }

    public DisplayInfoBuilder withDescriptionStyle(Style style) {
        this.description = java.util.Objects.requireNonNull(this.description).copy().withStyle(style);
        return this;
    }

    public DisplayInfoBuilder withHint(Component component) {
        this.hint = component;
        return this;
    }

    public DisplayInfoBuilder withBackground(ClientAsset clientAsset) {
        this.background = Optional.ofNullable(clientAsset);
        return this;
    }

    public DisplayInfoBuilder withType(AdvancementType advancementType) {
        this.type = advancementType;
        return this;
    }

    public DisplayInfoBuilder withShowToast(boolean bl) {
        this.showToast = bl;
        return this;
    }

    public DisplayInfoBuilder withAnnounceChat(boolean bl) {
        this.announceChat = bl;
        return this;
    }

    public DisplayInfoBuilder withHidden(boolean bl) {
        this.hidden = bl;
        return this;
    }

    public DisplayInfo build() {
        ItemStackTemplate template = this.icon == null ? new ItemStackTemplate(net.minecraft.world.item.Items.AIR) : ItemStackTemplate.fromStack(this.icon);
        Optional<ClientAsset.ResourceTexture> bg = this.background
            .flatMap(asset -> asset instanceof ClientAsset.ResourceTexture res ? Optional.of(res) : Optional.empty());
        return new DisplayInfo(template,
            this.title == null ? Component.empty() : this.title,
            this.description == null ? Component.empty() : this.description,
            bg,
            this.type == null ? AdvancementType.TASK : this.type,
            this.showToast,
            this.announceChat,
            this.hidden);
    }

    public Component hint() {
        return this.hint;
    }
}
