package dev.client.api.nullcry.events.core.other;

import com.mojang.datafixers.util.Pair;
import dev.client.api.nullcry.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.text.Text;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class ScoreBoardEvent extends Event {
    private List<Pair<ScoreboardEntry, Text>> list;
}
