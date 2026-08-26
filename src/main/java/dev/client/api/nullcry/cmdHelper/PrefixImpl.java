package dev.client.api.nullcry.cmdHelper;

import dev.client.api.nullcry.cmdHelper.interfaces.Prefix;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class PrefixImpl implements Prefix {
    volatile String prefix = ".";

    @Override
    public synchronized void set(String prefix) {
        if (prefix == null || prefix.length() != 1)
            throw new IllegalArgumentException("Префикс должен быть одним символом.");
        char c = prefix.charAt(0);
        if (c == '/' || Character.isLetterOrDigit(c) || Character.isWhitespace(c))
            throw new IllegalArgumentException("Буквы/цифры/пробел недопустимы в префиксе.");
        this.prefix = prefix;
    }

    @Override
    public String get() {
        return prefix;
    }
}
