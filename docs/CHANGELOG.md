# 1.11.2+26.3

- Restyle brewing and smithing books with vanilla textures, category tabs, recipe slots, paging arrows, search and craftable filtering.
- Simplify settings to one plain list with cycling recipe/creative mode buttons, shorter labels and clearer tooltips. Preserve existing settings and Cloth Config.
- Merge recipe families producing the same item and components in expandable mode; retain exact ingredient variants, availability and pins.
- Inset and center the recipe-group + / − badge inside its tile.
- Remember brewing and smithing book visibility independently in saved settings, including changes made with Escape.
- Prevent clicks through narrow workstation books, preserve inventory shortcuts after leaving search, and test controls at multiple GUI sizes.

# 1.11.1+26.3

- Keep ownership of locally unlocked recipes across dimension changes, so disabling unlock-all removes them while retaining learned recipes.
- Redistribute ordinary ingredients stacked in crafting-grid slots when placing an undiscovered recipe, with inventory-capacity preflight and valuable-item protection.
- Close the vanilla alternatives popup when toggling instant crafting and prevent clicks through an open popup from crafting an underlying recipe.
- Pin the hovered recipe before falling back to keyboard focus in native books, alternatives popups and workstation panels.
- Run the standalone sequence, ingredient, pin persistence and creative rule checks through Gradle `check`; extend actual-client QA for these regressions.

Requirements are unchanged: Minecraft 26.3, Java 25+, Fabric Loader 0.19.5+, Fabric API 0.161.0+26.3. Cloth Config 26.3.159 remains bundled. Optional Mod Menu 21.0.0 is supported.
