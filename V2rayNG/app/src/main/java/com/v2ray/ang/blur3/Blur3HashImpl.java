package com.v2ray.ang.blur3;

import com.v2ray.ang.blur3.capture.IBlur3Hash;

/**
 * Ported from Telegram's blur3 package. Upstream combines values via
 * MediaDataController.calcHash (an internal Telegram content-hash used
 * elsewhere in their app); that class isn't part of MikuRay, so this uses
 * a plain 64-bit rolling hash instead. It's only ever used as a cheap
 * cache-key to decide whether the nine-patch shadow needs regenerating,
 * not for anything security- or correctness-sensitive.
 */
public class Blur3HashImpl implements IBlur3Hash {
    private long hash;
    private boolean unsupported;

    public void start() {
        hash = 0;
        unsupported = false;
    }

    public long get() {
        return unsupported ? -1 : hash;
    }

    public boolean isUnsupported() {
        return unsupported;
    }

    @Override
    public void add(long value) {
        hash = hash * 1000003L + value;
    }

    public void unsupported() {
        unsupported = true;
    }
}
