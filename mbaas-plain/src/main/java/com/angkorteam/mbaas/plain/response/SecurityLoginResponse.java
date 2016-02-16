package com.angkorteam.mbaas.plain.response;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by socheat on 2/16/16.
 */
public class SecurityLoginResponse extends Response<Map<String, Object>> {

    public SecurityLoginResponse() {
        this.data = new LinkedHashMap<>();
    }

}
