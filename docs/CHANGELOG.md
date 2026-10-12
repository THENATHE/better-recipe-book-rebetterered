# 1.11.1+26.3

- Keep ownership of locally unlocked recipes across dimension changes, so disabling unlock-all removes them while retaining learned recipes.
- Redistribute ordinary ingredients stacked in crafting-grid slots when placing an undiscovered recipe, with inventory-capacity preflight and valuable-item protection.
- Close the vanilla alternatives popup when toggling instant crafting and prevent clicks through an open popup from crafting an underlying recipe.
- Pin the hovered recipe before falling back to keyboard focus in native books, alternatives popups and workstation panels.
- Run the standalone sequence, ingredient, pin persistence and creative rule checks through Gradle `check`; extend actual-client QA for these regressions.

Requirements are unchanged: Minecraft 26.3, Java 25+, Fabric Loader 0.19.5+, Fabric API 0.161.0+26.3. Cloth Config 26.3.159 remains bundled. Optional Mod Menu 21.0.0 is supported.
