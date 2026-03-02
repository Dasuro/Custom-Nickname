# Custom Nickname

A client-side Fabric mod that lets you assign custom nicknames to any player. Nicknames are displayed everywhere — nametags, tab list, chat, and scoreboard sidebar.

## Features

- **Custom Nicknames** — Set a personalized nickname for any player by username or UUID
- **Color Code Support** — Use `&a` formatting codes and hex colors `&#FF0000` for colorful nicknames
- **Rainbow Wave Effect** — Apply an animated rainbow wave to any nickname with adjustable speed
- **Team Prefix/Suffix Toggle** — Choose whether to show or hide scoreboard team prefixes and suffixes per player
- **Nickname Indicator** — Optional ✎ indicator appended to nicknames so you can tell them apart from real names
- **Global & Local Storage** — Store nicknames globally (shared across all instances) or locally (per modpack)
- **In-Game Config GUI** — Tabbed configuration screen with Add, Nicknames, and Options tabs (accessible via Mod Menu or keybind)
- **Live Preview** — See how your nickname will look before saving
- **Search & Filter** — Quickly find entries in the nickname list
- **Keybind Support** — Press `N` (rebindable) to open the nickname config screen at any time
- **Nametag Replacement** — Nicknames replace the player's nametag above their head
- **Tab List Replacement** — Nicknames are shown in the player list (Tab menu)
- **Chat Replacement** — Player names in chat messages are automatically replaced with their nickname
- **Scoreboard Sidebar Replacement** — Nicknames appear in the scoreboard sidebar as well
- **Mojang UUID Lookup** — Automatically resolves player UUIDs via Mojang API for offline nickname assignment
- **Auto Username Update** — Detects player name changes and keeps stored entries up to date
- **Persistent Config** — All nicknames are saved to a JSON config file and persist across sessions
- **Mod Compatibility** — Works with Entity Culling, LabyMod, and other common client-side mods

## Requirements

- [Fabric Loader](https://fabricmc.net/) `>= 0.18.4`
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Mod Menu](https://modrinth.com/mod/modmenu)

## Contributing

If you have feature ideas or find any bugs, feel free to [open an issue](https://github.com/Dasuro/Custom-Nickname/issues) or [submit a pull request](https://github.com/Dasuro/Custom-Nickname/pulls) on GitHub!

## License

This project is licensed under the [MIT License](LICENSE.txt).

