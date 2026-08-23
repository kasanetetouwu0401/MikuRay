package com.miku.ray.thirdparty.trilead.ssh2.auth;

import java.util.Collection;

public interface AgentProxy {
    public Collection/*<AgentIdentity>*/ getIdentities();
}
