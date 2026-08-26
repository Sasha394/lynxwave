package com.viaversion.viaversion.api.protocol.version;

public class ProtocolVersion {
    public static final ProtocolVersion v1_17 = new ProtocolVersion();
    public int getVersion() { return 0; }
    public boolean olderThan(ProtocolVersion other) { return false; }
    public boolean newerThan(ProtocolVersion other) { return false; }
}
