package dev.other.scoreboard;

import net.minecraft.text.Text;

public record SidebarEntry(Text name, Text score, int scoreWidth) {
}