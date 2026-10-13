# Creative grouping rules

The default rules live at `assets/brb/creative-groups.json`. A resource pack may replace that file with its own complete JSON array. The first eligible matching rule owns each item; unmatched items remain individually visible. Typed creative search, inventory and saved-hotbar tabs retain their normal presentation.

Each rule has `id`, anchored item-ID `pattern`, `key` and `label`. Numeric regular-expression captures such as `$1` may be used in `key` and `label`. The group identity is `<id>:<key>`.

Optional `tabs` limits a rule to those exact creative-tab IDs. Omitting it makes the rule available in every supported tab. Natural Blocks uses `minecraft:natural_blocks`; its leaves/log rules precede general wood-family rules.

Optional `icon` chooses a representative from the group's existing members. If the requested item is absent, the first member remains the representative. This changes only the header icon, preserving every original child stack and its order.

```json
{
  "id": "eggs",
  "pattern": "^minecraft:.*_spawn_egg$",
  "key": "spawn_eggs",
  "label": "Spawn eggs",
  "icon": "minecraft:creeper_spawn_egg"
}
```

Collapsed headers never take items. Expanded headers darken and display the same recipe-book minus badge; clicking them collapses the group. Individual children use normal creative pickup and retain their item components.

Chalk rules target the known `chalk:<color>_chalk` and `chalk:<color>_glow_chalk` IDs. They do not require Chalk to be installed. Unknown namespaces remain individual unless explicitly matched by a rule.
