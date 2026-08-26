package dev.client.api.nullcry.cmdHelper.interfaces;

import java.util.Optional;

public interface Parameters {

    Optional<Integer> asInt(int index);

    Optional<String> asString(int index);

    String collectMessage(int startIndex);

    int count();
}
