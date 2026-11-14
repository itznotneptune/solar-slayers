# Wolf slayer (Moonfang Alpha): what the client sees

Source: Claude Control recording 2026-11-14 23:29–23:32 on p2w.solarskies.net (one T3 attempt, failed by knockout; one T2 kill).
Raw files: `recordings/20261114-232940-*` (git-ignored).

Text uses small-caps Unicode (e.g. `ʙᴏѕѕ ɪɴ`), and scoreboard lines carry `§x` suffixes and `[item/...@items]` icon
prefixes from a resource pack. Strip both before matching.

## Chat (exact text, leading spaces as received)
| Event | Message |
|---|---|
| Quest start | `  SLAYER QUEST STARTED!` then `   » Slay 100 Combat XP worth of Moonfang Alpha mobs.` (T3: 150) |
| Miniboss | `MINIBOSS! A Moonlit Hunter spawned. It is worth a lot of Combat XP.` |
| Boss spawn | `  SLAYER BOSS SPAWNED!` then `   » Slay the Moonfang Alpha II within 3m.` |
| Ward on | `[SLAYER] The Moonfang Alpha is warded! Your abilities are neutralised until you hit 10% of its health off with your weapon.` (also sent for the Moonlit Hunter) |
| Ward off | `[SLAYER] The Moonfang Alpha's ward shatters. Your abilities work again.` |
| Pups (T3, ~50% HP) | `[SLAYER] The Moonfang Alpha calls its pups. Kill them to hurt it.` + `[!] Kill the minions first, they are shielding the Moonfang Alpha.` |
| Kill | `  NICE! SLAYER BOSS SLAIN!` / `   » The Moonfang Alpha II fell in 9s.` / `   » +25 Slayer XP • +79 Essence • +16 Moon Fang` / `   » Pity Meter [\|\|\|…] 50% (125/250 XP) (250 Slayer Essence)` |
| Death | `[!] The Moonfang Alpha knocked you out.` / `  SLAYER QUEST FAILED!` / `   » You were knocked out. Try again.` |

## Sidebar (title `ꐟ`)
- No quest: `Your Quest` / `ɴᴏ ǫᴜᴇѕᴛ` / `ꜱᴛᴀʀᴛ ᴏɴᴇ: /slayer`.
- Grinding: `Moonfang Alpha ᴀʀᴇᴀ III`, `ʟᴇᴠᴇʟ: 3 (45 ᴛᴏ ɢᴏ)`, `ᴇѕѕᴇɴᴄᴇ: 1.8k`, `Moonfang Alpha III`, `ʙᴏѕѕ ɪɴ: 55/150 xᴘ`,
  `Pity Meter` / `250 Slayer Essence` / `ᴍᴇᴛᴇʀ: 100/250 (40%)`.
- Boss alive: the `ʙᴏѕѕ ɪɴ` line becomes `❤ 2.5k/5k (02:50)` (boss HP and time left).

## Entities (all vanilla wolf/stray with a custom name)
- Area mobs: T2 `Feral Moonfang 125/125❤`, `Feral Stray 125/125❤`; T3 `Savage Moonfang 375/375❤`, `Savage Stray 375/375❤`.
- Miniboss: wolf `Moonlit Hunter 250/250❤`, prefixed `WARDED ` while warded.
- Boss: wolf `☠ Moonfang Alpha 5k❤` (T3 5k HP, T2 1.5k HP), prefixed `WARDED ` while warded.
- Boss label: a text_display just above it: `☠ Moonfang Alpha 1.5k❤\n03:00\nSpawned by: <player>`. The owner and timer
  are here. Other players' bosses carry their name.
- Pups: wolf `Moonfang 250/250❤`.
- Damage numbers: short-lived text_display with only digits (`450`, `225`, `2`).
- Slayer hub portals: text_display `GRAVE TYRANT\nᴢᴏᴍʙɪᴇ ꜱʟᴀʏᴇʀ`, `VENOMWEAVER\nꜱᴘɪᴅᴇʀ ꜱʟᴀʏᴇʀ` (requires Grave Tyrant LVL 2).

## Not observed
Bossbar and titles were not captured (the bridge has no route for them). Drop messages beyond the kill summary
did not appear in this run.
