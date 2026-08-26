package dev.client.api.nullcry.cmdHelper.interfaces;

import dev.client.api.nullcry.cmdHelper.DispatchResult;

public interface CommandDispatcher {
    DispatchResult dispatch(String command);
}
