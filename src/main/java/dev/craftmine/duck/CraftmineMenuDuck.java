/*
 * Duck interface for the craftmine additions to AbstractContainerMenu
 * (the fork added updateData/setAdditionalData for menu data sync).
 */
package dev.craftmine.duck;

import java.util.List;

public interface CraftmineMenuDuck {
    void updateData(List<Integer> list);

    /**
     * The server-side snapshot of the menu's syncable data - the payload the
     * fork puts into ClientboundOpenWindowPacket.additionalData at menu-open
     * time (CraftmineServerHooks.openCraftmineMenu) and into
     * ClientboundUpdateScreenPacket on data changes.
     */
    List<Integer> craftmine$getAdditionalData();
}
