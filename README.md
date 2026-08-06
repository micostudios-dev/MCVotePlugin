# MCVote

Vote plugin for Minecraft servers: Votifier-compatible receiver, daily streaks,
VoteParty and offline vote delivery across a network.

There is one jar for each of Spigot, Paper, Folia, BungeeCord and Velocity.
Your server needs Minecraft 1.21 or newer. To build it yourself you need
Java 21.

## Features

Players build a streak by voting on consecutive days. You define the milestone
tiers and the timezone the day rolls over in, and each player also keeps a
best-streak record.

The VoteParty bar is shared by the whole network. When it hits the goal,
everyone online gets a reward.

A reward can run a console command, run a command as the player, send a message,
broadcast, give an item or play a sound. You set them separately for a normal
vote, for a milestone and for the party. If the player is offline the reward
waits in the queue and is handed out when they join.

The menus in `menu/*.yml` and the messages in `lang/*.yml` are yours to edit.
English and Spanish are included. Everything is MiniMessage, on Spigot too.

There is also a per-site cooldown and a daily cap, both on the server clock,
SQLite or MySQL/MariaDB storage, PlaceholderAPI support and an API for other
plugins.

## Votifier compatibility

MCVote acts as a Votifier server. It greets every connection with
`VOTIFIER 2 <challenge>` and then tells the framing apart by its magic bytes:

| Protocol | Payload | Credential |
|----------|---------|------------|
| Votifier v2 *(recommended)* | `s:` + length + `{"payload","signature"}`, HMAC-SHA256 | token (`token.txt`, or `receiver.tokens` to override) |
| Votifier v1 *(classic 1.9)* | 256 raw bytes, RSA/PKCS#1 of `VOTE\n<service>\n<user>\n<address>\n<time>` | key pair in `rsa/` |
| MCVote legacy | `MV` + length + `{"payload","signature"}` | `receiver.api-key` |

Both credentials are generated the first time you start the plugin, the token
in `token.txt` and the RSA pair in `rsa/`, and printed to the console. On the
listing site, choose
*Votifier*, enter host and port, and paste whichever credential it asks for.
To migrate from another Votifier plugin, copy its `rsa/` folder in.

Credentials are never transmitted, only used to sign. Replays are blocked by the
per-connection challenge (v2 and legacy) and by a block cache for v1, which has
no challenge (`replay-window-seconds`). Sender-supplied timestamps are ignored:
streak days, cooldowns and daily caps use the server clock.

## Getting started

```bash
./gradlew build   # builds every platform JAR into out/
```

```
out/MCVote-Spigot-1.0.0.jar     out/MCVote-Bungee-1.0.0.jar
out/MCVote-Paper-1.0.0.jar      out/MCVote-Velocity-1.0.0.jar
out/MCVote-Folia-1.0.0.jar
```

### Single server

1. Drop `MCVote-Paper.jar` (or Spigot / Folia) into `plugins/` and start once to
   generate the files.
2. Set `receiver.enabled: true` in `config.yml`. SQLite and the Votifier
   credentials need no configuration.
3. Restart. The console prints the listening port, the token and the public key.
4. Open that port in the firewall and point the listing sites at it.

### Network with a proxy

1. **On the proxy**, install `MCVote-Bungee.jar` or `MCVote-Velocity.jar`. It
   listens on port 8192 with generated credentials and a SQLite file. Run
   `/mcvoteproxy info` to get what you have to paste on the sites. This is the
   only node that listens for votes.
2. **On each backend**, install the Paper/Spigot/Folia JAR with
   `database.type: proxy` and `receiver.enabled: false`.
3. Configure rewards, menus and messages on the **backends**, not on the proxy.

Streaks, VoteParty and pending votes live on the proxy, so the whole network
shares the same state and each reward is delivered once.

## Configuration

| File | Contents |
|------|----------|
| `config.yml` | Database, receiver, anti-abuse, streaks, VoteParty, vote links and rewards. |
| `lang/<language>.yml` | Every message, in MiniMessage. Selected with `language`. |
| `menu/*.yml` | One file per menu: items, slots, lore and click actions. |
| `token.txt` | Votifier v2 token, generated on first start. |
| `rsa/` | Votifier v1 key pair, generated on first start. |

Rewards are a list of `type: argument` entries:

| Type | Example |
|------|---------|
| `command` | `command: give %player% diamond 1` |
| `player` | `player: spawn` |
| `message` | `message: <green>Thanks for voting!` |
| `broadcast` | `broadcast: <gold>%player% just voted` |
| `item` | `item: DIAMOND 3` |
| `sound` | `sound: entity.player.levelup 1 1` |

Placeholders in rewards and messages: `%player%`, `%streak%`, `%best_streak%`,
`%votes%`, `%party_progress%`, `%party_goal%`, `%party_remaining%`,
`%next_tier%`, `%next_tier_required%` and `%next_tier_in%`. With
**PlaceholderAPI** installed, any placeholder from another plugin works in a
reward line too.

MCVote's own expansion, for use elsewhere: `%mcvote_streak%`,
`%mcvote_best_streak%`, `%mcvote_votes%`, `%mcvote_party_progress%`,
`%mcvote_party_goal%`, `%mcvote_party_remaining%`, `%mcvote_next_tier%`,
`%mcvote_next_tier_required%` and `%mcvote_next_tier_in%`.

## Commands and permissions

`/mcvote` is an alias of `/vote`. Admin subcommands require `mcvote.admin`
(default: op).

| Command | Description |
|---------|-------------|
| `/vote` | Opens the menu, or prints the vote links in console. |
| `/vote links` | The vote links as text. |
| `/vote streak` | Your streak and the next milestone. |
| `/vote top` | Top voters. |
| `/vote party` | VoteParty progress. |
| `/vote admin` | Admin menu. *(`mcvote.admin`)* |
| `/vote reload` | Reloads config, language and menus. *(`mcvote.admin`)* |
| `/vote forceparty` | Fires a VoteParty immediately. *(`mcvote.admin`)* |
| `/vote test <player>` | Queues a test vote. *(`mcvote.admin`)* |

On the proxy the command is `/mcvoteproxy`, alias `/mcvp`, also `mcvote.admin`.
The name differs on purpose: a proxy command shadows the backend one, and
`/vote` belongs to the backends.

| Command | Description |
|---------|-------------|
| `/mcvoteproxy info` | Port, Votifier token and public key. |
| `/mcvoteproxy reload` | Re-reads `config.yml` and restarts the receiver. |

## API

Add the `api` module as a dependency:

```java
MCVoteAPI api = MCVoteAPI.get();

api.addVoteListener(vote ->
        getLogger().info(vote.username() + " voted on " + vote.serviceName()));

int progress = api.votePartyProgress();
int goal     = api.votePartyGoal();
int streak   = api.streakOf(player.getUniqueId());
```

Listeners fire only for votes that passed anti-abuse, on the node that received
them.

## Architecture

```
                    (Votifier v1 / v2 over TCP)
   Listing sites ─────────────────────────────────►  Proxy (or lone Paper)
                                                     ├─ checks the vote
                                                     ├─ streak + VoteParty
                                                     └─ SQLite next to config.yml
                                                            ▲
                                  storage calls over        │
                                  plugin messaging          │
   Paper / Spigot / Folia backends ─────────────────────────┘
   (no database, they just run the rewards)
```

One node listens and owns the state. On a network that is the proxy, and on a
single server it is the server itself. The backends have no database of their
own. They ask for rewards, streaks and party progress over the `mcvote:sync`
plugin messaging channel, and each reward is claimed atomically on the owner so
it only goes out once.

The proxy only answers frames that arrive over a backend server connection,
never one that comes from a player.

| Module | Contents |
|--------|----------|
| `api` | Public surface: `MCVoteAPI`, `Vote`, `VoteListener`. |
| `commons` | Votifier protocol, RSA/HMAC, storage, streak and party domain. |
| `server` | Shared Bukkit-family logic: config, rewards, menus, commands. |
| `spigot` | Spigot bootstrap + bundled Adventure. |
| `paper` | Paper bootstrap + native Adventure. |
| `folia` | Folia bootstrap + region scheduler. |
| `bungee` | BungeeCord / Waterfall listener node. |
| `velocity` | Velocity listener node. |

`spigot`, `paper` and `folia` share nearly all of their code through `server`.
They only differ in two things, both behind interfaces: the scheduler, because
Folia works by regions, and how a component reaches the player, which is native
on Paper and Folia and goes through `BukkitAudiences` on Spigot.

HikariCP and the MariaDB driver are bundled and relocated to
`org.mcvote.libs`, and so is Adventure on Spigot and BungeeCord.

## Limitations

Plugin messages travel on a player's connection, so a backend with nobody
online cannot reach the proxy. Its rewards stay queued on the proxy until
someone joins.

When the proxy does not answer, storage calls give up after 3 seconds and fall
back to empty values. `/vote top` comes back empty and the party progress reads
0 until the link recovers. Nothing is actually lost, because the proxy is the
one holding the data, but the backend shows zeros in the meantime. The console
warns about it once a minute at most.

A backend with `database.type: proxy` pointed at a proxy that does not run
MCVote will not fail at startup. It enables normally and then every call times
out the same way.

`vote-links` is just a list in `config.yml`. Nothing tracks who voted on which
site, only the service name that the site sends along with the vote.

The anti-abuse cooldown is per player and service name. A site that changes the
service name it reports counts as a different site.

`/vote reload` re-reads the config, the language file and the menus, but it
does not touch `database`. If you change the storage backend you have to
restart the server.

## License

[MIT](LICENSE). Votifier and NuVotifier are third-party projects. MCVote is an
independent implementation of their wire protocols and is not affiliated with
them.
