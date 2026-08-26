package dev.client.api.nullcry.cmdHelper;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class CommandException extends RuntimeException {
    String message;
}
