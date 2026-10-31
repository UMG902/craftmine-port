package dev.craftmine.duck;

/**
 * Task 60: per-layer glint kind for the mine item's won/lost colored glint.
 * 0 = normal, 1 = won (green), 2 = lost (red). Set by
 * CuboidItemModelWrapperMixin at resolve time, consumed by
 * ItemStackRenderStateLayerMixin at submit time (the Submit record itself
 * carries no stack context in 26.3).
 */
public interface CraftmineItemLayerDuck {
    int craftmine$glintKind();

    void craftmine$setGlintKind(int kind);
}
