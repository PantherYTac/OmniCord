package com.omnicord.plugin.discord.webhook;

import com.google.gson.JsonObject;
import com.omnicord.plugin.OmniCord;
import com.omnicord.plugin.utils.SkinUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance asynchronous Webhook dispatcher.
 */
public class WebhookManager {

    private final OmniCord plugin;
    private final OkHttpClient httpClient;
    private final Map<String, String> cachedWebhookUrls = new ConcurrentHashMap<>();

    public WebhookManager(OmniCord plugin) {
        this.plugin = plugin;
        this.httpClient = new OkHttpClient();
    }

    /**
     * Sends a message via Webhook with custom player head avatar and username.
     */
    public void sendWebhookMessage(TextChannel channel, UUID playerUuid, String playerName, String message) {
        if (channel == null || message == null || message.trim().isEmpty()) return;

        // Truncate messages exceeding Discord's 2000 character limit
        final String payloadMessage = message.length() > 1950 ? message.substring(0, 1947) + "..." : message;

        getOrCreateWebhookUrl(channel).thenAccept(webhookUrl -> {
            if (webhookUrl == null) {
                // Fallback to bot message
                channel.sendMessage("**" + playerName + "**: " + payloadMessage).queue(null, err -> {});
                return;
            }

            String avatarService = plugin.getConfigManager().getConfig().getString("chat_bridge.avatar_service", "crafthead");
            String avatarUrl = SkinUtil.getAvatarUrl(avatarService, playerUuid, playerName);

            String safeName = playerName != null ? playerName.trim() : "Player";
            if (safeName.length() > 80) safeName = safeName.substring(0, 80);
            if (safeName.isEmpty()) safeName = "Player";

            JsonObject json = new JsonObject();
            json.addProperty("username", safeName);
            json.addProperty("avatar_url", avatarUrl);
            json.addProperty("content", payloadMessage);

            // Disable ping abuse via webhook
            JsonObject allowedMentions = new JsonObject();
            allowedMentions.add("parse", new com.google.gson.JsonArray());
            json.add("allowed_mentions", allowedMentions);

            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    plugin.getLogger().warning("Failed to dispatch Discord webhook: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    response.close();
                }
            });
        });
    }

    private CompletableFuture<String> getOrCreateWebhookUrl(TextChannel channel) {
        String channelId = channel.getId();
        if (cachedWebhookUrls.containsKey(channelId)) {
            return CompletableFuture.completedFuture(cachedWebhookUrls.get(channelId));
        }

        CompletableFuture<String> future = new CompletableFuture<>();

        if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
            future.complete(null);
            return future;
        }

        channel.retrieveWebhooks().queue(webhooks -> {
            for (Webhook wh : webhooks) {
                if ("OmniCord Bridge".equalsIgnoreCase(wh.getName())) {
                    cachedWebhookUrls.put(channelId, wh.getUrl());
                    future.complete(wh.getUrl());
                    return;
                }
            }

            // Create new webhook
            channel.createWebhook("OmniCord Bridge").queue(createdWh -> {
                cachedWebhookUrls.put(channelId, createdWh.getUrl());
                future.complete(createdWh.getUrl());
            }, err -> {
                plugin.getLogger().warning("Could not create webhook: " + err.getMessage());
                future.complete(null);
            });
        }, err -> {
            plugin.getLogger().warning("Could not retrieve webhooks: " + err.getMessage());
            future.complete(null);
        });

        return future;
    }

    public void clearCache() {
        cachedWebhookUrls.clear();
    }
}
