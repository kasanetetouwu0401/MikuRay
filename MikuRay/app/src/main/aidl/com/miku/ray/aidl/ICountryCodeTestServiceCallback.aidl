package com.miku.ray.aidl;

oneway interface ICountryCodeTestServiceCallback {
    void onCountryCodeSuccess(String guid);
    void onCountryCodeProgress(String json);
    void onCountryCodeFinish();
}
