package com.viaversion.viafabricplus.api;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.util.function.BiConsumer;

public interface ViaFabricPlusBase {
    ProtocolVersion getTargetVersion();
    void setTargetVersion(ProtocolVersion version);
    void registerOnChangeProtocolVersionCallback(BiConsumer<ProtocolVersion, ProtocolVersion> callback);
}
