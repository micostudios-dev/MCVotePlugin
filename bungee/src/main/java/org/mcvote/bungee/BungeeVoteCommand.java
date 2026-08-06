package org.mcvote.bungee;

import net.kyori.adventure.audience.Audience;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import org.mcvote.common.net.VoteReceiver;
import org.mcvote.common.text.MiniMessageText;

public final class BungeeVoteCommand extends Command {

    private final BungeeMCVotePlugin plugin;

    public BungeeVoteCommand(BungeeMCVotePlugin plugin) {
        super("mcvoteproxy", "mcvote.admin", "mcvp");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Audience audience = plugin.audiences().sender(sender);
        String sub = args.length == 0 ? "help" : args[0].toLowerCase();

        switch (sub) {
            case "reload" -> {
                if (plugin.reload()) {
                    send(audience, "<green>MCVote reloaded. The receiver is running with the new settings.");
                    send(audience, "<gray>Storage changes still need a full restart.");
                } else {
                    send(audience, "<red>Could not read config.yml. The old settings are still running.");
                }
            }
            case "info" -> info(audience);
            default -> {
                send(audience, "<gold>/mcvoteproxy reload <gray>- re-read config.yml");
                send(audience, "<gold>/mcvoteproxy info <gray>- port and credentials for the listing sites");
            }
        }
    }

    private void info(Audience audience) {
        VoteReceiver receiver = plugin.receiver();

        if (receiver == null) {
            send(audience, "<red>The vote receiver is not running. Check the console.");

            return;
        }

        send(audience, "<gold>MCVote <gray>(proxy, storage: " + plugin.storageLabel() + ")");
        send(audience, "<yellow>Port: <white>" + receiver.boundPort());
        send(audience, "<yellow>Votifier v2 token:");
        send(audience, copyable(receiver.defaultToken()));
        send(audience, "<yellow>Votifier v1 public key:");
        send(audience, copyable(receiver.publicKey()));
    }

    static String copyable(String value) {
        String safe = MiniMessageText.escape(value);

        return "<click:copy_to_clipboard:'" + safe + "'>"
                + "<hover:show_text:'<gray>Click to copy'><white>" + safe + "</white></hover></click>";
    }

    private static void send(Audience audience, String message) {
        audience.sendMessage(MiniMessageText.render(message));
    }
}
