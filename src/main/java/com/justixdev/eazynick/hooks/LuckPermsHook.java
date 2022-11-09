package com.justixdev.eazynick.hooks;

import com.justixdev.eazynick.EazyNick;
import com.justixdev.eazynick.utilities.Utils;
import com.justixdev.eazynick.utilities.configuration.yaml.SetupYamlFile;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("UnstableApiUsage")
public class LuckPermsHook {

    private final Utils utils;
    private final SetupYamlFile setupYamlFile;

    private final Player player;

    public LuckPermsHook(Player player) {
        EazyNick eazyNick = EazyNick.getInstance();
        this.utils = eazyNick.getUtils();
        this.setupYamlFile = eazyNick.getSetupYamlFile();

        this.player = player;
    }

    public void updateNodes(String prefix, String suffix, String groupName) {
        net.luckperms.api.LuckPerms api = net.luckperms.api.LuckPermsProvider.get();
        net.luckperms.api.model.user.User user = api.getUserManager().getUser(player.getUniqueId());

        if(user == null) return;

        if (setupYamlFile.getConfiguration().getBoolean("ChangeLuckPermsPrefixAndSufix")) {
            // Create new prefix and suffix nodes
            net.luckperms.api.node.NodeBuilderRegistry nodeFactory = api.getNodeBuilderRegistry();
            net.luckperms.api.node.Node prefixNode = nodeFactory.forPrefix().priority(99).prefix(prefix).expiry(24 * 30, TimeUnit.HOURS).build();
            net.luckperms.api.node.Node suffixNode = nodeFactory.forSuffix().priority(99).suffix(suffix).expiry(24 * 30, TimeUnit.HOURS).build();

            user.transientData().add(prefixNode);
            user.transientData().add(suffixNode);

            utils.getLuckPermsPrefixes().put(player.getUniqueId(), prefixNode);
            utils.getLuckPermsSuffixes().put(player.getUniqueId(), suffixNode);
        }

        if (setupYamlFile.getConfiguration().getBoolean("SwitchLuckPermsGroupByNicking") && !(groupName.equalsIgnoreCase("NONE")) && !(user.getPrimaryGroup().isEmpty())) {
            // Update group nodes
            utils.getOldLuckPermsGroups().put(player.getUniqueId(), user.getNodes(net.luckperms.api.node.NodeType.INHERITANCE));

            removeAllGroups(user);

            user.data().add(InheritanceNode.builder(groupName).build());
        }

        api.getUserManager().saveUser(user);
    }

    public void resetNodes() {
        net.luckperms.api.LuckPerms api = net.luckperms.api.LuckPermsProvider.get();
        net.luckperms.api.model.user.User user = api.getUserManager().getUser(player.getUniqueId());

        if(user == null) return;

        if (setupYamlFile.getConfiguration().getBoolean("ChangeLuckPermsPrefixAndSufix") && utils.getLuckPermsPrefixes().containsKey(player.getUniqueId()) && utils.getLuckPermsSuffixes().containsKey(player.getUniqueId())) {
            // Remove prefix and suffix nodes
            user.transientData().remove((net.luckperms.api.node.Node) utils.getLuckPermsPrefixes().get(player.getUniqueId()));
            user.transientData().remove((net.luckperms.api.node.Node) utils.getLuckPermsSuffixes().get(player.getUniqueId()));

            utils.getLuckPermsPrefixes().remove(player.getUniqueId());
            utils.getLuckPermsSuffixes().remove(player.getUniqueId());
        }

        if (setupYamlFile.getConfiguration().getBoolean("SwitchLuckPermsGroupByNicking") && utils.getOldLuckPermsGroups().containsKey(player.getUniqueId())) {
            // Reset group nodes
            removeAllGroups(user);

            //noinspection unchecked
            ((Collection<InheritanceNode>) utils.getOldLuckPermsGroups().get(player.getUniqueId())).forEach(node -> user.data().add(node));

            utils.getOldLuckPermsGroups().remove(player.getUniqueId());
        }

        api.getUserManager().saveUser(user);
    }

    private void removeAllGroups(net.luckperms.api.model.user.User user) {
        new HashSet<>(user.data().toMap().values()).forEach(node ->
                node.stream()
                        .filter(node2 -> (node2 instanceof net.luckperms.api.node.types.InheritanceNode))
                        .forEach(node2 -> user.data().remove(node2)));
    }

}
