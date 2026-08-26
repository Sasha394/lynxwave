package dev.client.api.nullcry.helper.other;

import lombok.experimental.UtilityClass;

import java.security.SecureRandom;
import java.util.List;

@UtilityClass
public class NameGenerator {

    private final SecureRandom random = new SecureRandom();

    private final List<String> ADJECTIVES = List.of(
            "Cool", "Dark", "Silent", "Shadow", "Crazy", "Epic", "Toxic", "Lucky",
            "Angry", "Happy", "Wild", "Savage", "Mystic", "Frozen", "Burning", "Royal",
            "Evil", "Hidden", "Sneaky", "Swift", "Stormy", "Cyber", "Neon", "Pixel",
            "Iron", "Golden", "Silver", "Crystal", "Phantom", "Ghost", "Quick", "Brave",
            "Mighty", "Noble", "Rogue", "Rusty", "Fierce", "Lone", "Psycho", "Atomic",
            "Cosmic", "Electric", "Glitch", "Void", "Solar", "Lunar", "Arctic", "Blazing"
    );

    private final List<String> NOUNS = List.of(
            "Wolf", "Tiger", "Dragon", "Ninja", "Samurai", "Ghost", "Demon", "Knight",
            "Hunter", "Wizard", "Warrior", "Viking", "Ranger", "Sniper", "Reaper", "Warden",
            "Phoenix", "Eagle", "Raven", "Hawk", "Lion", "Bear", "Fox", "Cobra",
            "Shark", "Panda", "Falcon", "Viper", "Titan", "Hero", "Slayer", "Striker",
            "Legend", "Gamer", "King", "Lord", "Prince", "Hacker", "Pilot", "Pirate",
            "Zombie", "Skeleton", "Goblin", "Beast", "Monster", "Ninja", "Rebel", "Outlaw"
    );

    public String generate() {
        String adj = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
        String noun = NOUNS.get(random.nextInt(NOUNS.size()));

        StringBuilder name = new StringBuilder(adj.length() + noun.length() + 4);

        int style = random.nextInt(5);
        switch (style) {
            case 0 -> name.append(adj).append(noun);
            case 1 -> name.append(adj).append('_').append(noun);
            case 2 -> name.append(adj).append(noun).append(random.nextInt(100));
            case 3 -> name.append(noun).append(random.nextInt(1000));
            default -> name.append(adj).append(noun).append('_').append(random.nextInt(10));
        }

        if (name.length() > 16) {
            name.setLength(16);
        }
        return name.toString();
    }
}
