package dev.client.api.nullcry.cmdHelper;

import dev.client.api.nullcry.cmdHelper.interfaces.Parameters;
import dev.client.api.nullcry.cmdHelper.interfaces.ParametersFactory;

public class ParametersFactoryImpl implements ParametersFactory {

    @Override
    public Parameters createParameters(String message, String delimiter) {
        return new ParametersImpl(message.split(delimiter));
    }
}
