package org.mcvote.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import org.mcvote.common.net.VoteReceiver;
import org.mcvote.common.text.MiniMessageText;

public final class VelocityVoteCommand implements SimpleCommand {

    private final VelocityMCVotePlugin plugin;

    public VelocityVoteCommand(VelocityMCVotePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("mcvote.admin");
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase();

        switch (sub) {
            case "reload" -> {
                if (plugin.reload()) {
                    send(source, "<green>MCVote reloaded. The receiver is running with the new settings.");
                    send(source, "<gray>Storage changes still need a full restart.");
                } else {
                    send(source, "<red>Could not read config.yml. The old settings are still running.");
                }
            }
            case "info" -> info(source);
            default -> {
                send(source, "<gold>/mcvoteproxy reload <gray>- re-read config.yml");
                send(source, "<gold>/mcvoteproxy info <gray>- port and credentials for the listing sites");
            }
        }
    }

    private void info(CommandSource source) {
        VoteReceiver receiver = plugin.receiver();
        if (receiver == null) {
            send(source, "<red>The vote receiver is not running. Check the console.");
            return;
        }

        send(source, "<gold>MCVote <gray>(proxy, storage: " + plugin.storageLabel() + ")");
        send(source, "<yellow>Port: <white>" + receiver.boundPort());
        send(source, "<yellow>Votifier v2 token:");
        send(source, copyable(receiver.defaultToken()));
        send(source, "<yellow>Votifier v1 public key:");
        send(source, copyable(receiver.publicKey()));
    }

    private static String copyable(String value) {
        String safe = MiniMessageText.escape(value);
        return "<click:copy_to_clipboard:'" + safe + "'>"
                + "<hover:show_text:'<gray>Click to copy'><white>" + safe + "</white></hover></click>";
    }

    private static void send(CommandSource source, String message) {
        source.sendMessage(MiniMessageText.render(message));
    }
}
