package com.miku.ray.aidl;

// Replaces MessageUtil.sendMsg2UI(...). "key" reuses the existing AppConfig.MSG_* constants,
// "content" carries either plain text or a JSON payload (see AppConfig for which).
oneway interface IMikuRayCallback {
    void onEvent(int key, String content);
}
