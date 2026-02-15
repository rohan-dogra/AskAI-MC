package com.aichat.util;

import com.aichat.model.AIProvider;
import com.aichat.model.AIResponse;
import com.aichat.model.UserSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class TextFormatter {

    private TextFormatter() {
    }

    public static Component formatResponse(AIProvider provider, AIResponse response) {
        return Component.text("[" + provider.displayName() + "] ").color(providerColor(provider))
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text(response.text()).color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, false));
    }

    public static Component thinking() {
        return Component.text("[AI] Thinking...").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true);
    }

    public static Component error(String message) {
        return Component.text("[AI] ").color(NamedTextColor.RED)
                .append(Component.text(message).color(NamedTextColor.RED));
    }

    public static Component success(String message) {
        return Component.text("[AI] ").color(NamedTextColor.GREEN)
                .append(Component.text(message).color(NamedTextColor.GREEN));
    }

    public static Component info(String message) {
        return Component.text("[AI] ").color(NamedTextColor.YELLOW)
                .append(Component.text(message).color(NamedTextColor.WHITE));
    }

    public static Component formatStatus(UserSettings settings) {
        AIProvider active = settings.activeProvider();
        Component status = Component.text("--- AIChat Status ---").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline());

        status = status.append(Component.text("Active provider: ").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, false)
                .append(Component.text(active.displayName()).color(providerColor(active))))
                .append(Component.newline());

        status = status.append(Component.text("Active model: ").color(NamedTextColor.GRAY)
                .append(Component.text(settings.getModel(active)).color(NamedTextColor.WHITE)))
                .append(Component.newline());

        status = status.append(Component.newline())
                .append(Component.text("Keys configured:").color(NamedTextColor.GRAY))
                .append(Component.newline());

        for (AIProvider provider : AIProvider.values()) {
            boolean hasKey = settings.hasKey(provider);
            Component keyStatus = hasKey
                    ? Component.text(" [SET]").color(NamedTextColor.GREEN)
                    : Component.text(" [NOT SET]").color(NamedTextColor.RED);
            status = status.append(Component.text("  " + provider.displayName()).color(providerColor(provider)))
                    .append(keyStatus)
                    .append(Component.text(" | model: " + settings.getModel(provider)).color(NamedTextColor.GRAY))
                    .append(Component.newline());
        }

        return status;
    }

    public static Component formatServerStatus(UserSettings playerSettings, UserSettings serverSettings) {
        AIProvider active = playerSettings.activeProvider();
        Component status = Component.text("--- AIChat Status ---").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline());

        status = status.append(Component.text("Key mode: ").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, false)
                .append(Component.text("Server (shared keys)").color(NamedTextColor.YELLOW)))
                .append(Component.newline());

        status = status.append(Component.text("Active provider: ").color(NamedTextColor.GRAY)
                .append(Component.text(active.displayName()).color(providerColor(active))))
                .append(Component.newline());

        status = status.append(Component.text("Active model: ").color(NamedTextColor.GRAY)
                .append(Component.text(playerSettings.getModel(active)).color(NamedTextColor.WHITE)))
                .append(Component.newline());

        status = status.append(Component.newline())
                .append(Component.text("Server keys:").color(NamedTextColor.GRAY))
                .append(Component.newline());

        for (AIProvider provider : AIProvider.values()) {
            boolean hasKey = serverSettings.hasKey(provider);
            Component keyStatus = hasKey
                    ? Component.text(" [SET]").color(NamedTextColor.GREEN)
                    : Component.text(" [NOT SET]").color(NamedTextColor.RED);
            status = status.append(Component.text("  " + provider.displayName()).color(providerColor(provider)))
                    .append(keyStatus)
                    .append(Component.text(" | model: " + playerSettings.getModel(provider)).color(NamedTextColor.GRAY))
                    .append(Component.newline());
        }

        return status;
    }

    private static NamedTextColor providerColor(AIProvider provider) {
        return switch (provider) {
            case OPENAI -> NamedTextColor.GREEN;
            case ANTHROPIC -> NamedTextColor.GOLD;
            case GEMINI -> NamedTextColor.AQUA;
        };
    }
}
