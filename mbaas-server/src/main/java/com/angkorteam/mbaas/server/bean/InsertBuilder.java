package com.angkorteam.mbaas.server.bean;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Created by socheat on 11/25/16.
 */
public class InsertBuilder {

    private final String table;

    private List<String> value = Lists.newArrayList();

    private List<String> field = Lists.newArrayList();

    private List<String> where = Lists.newArrayList();

    public InsertBuilder(String table) {
        this.table = table;
    }

    public void addField(String name, String value) {
        this.field.add(name);
        this.value.add(value);
    }

    public void addWhere(String filter) {
        this.where.add(filter);
    }

    public String toSQL() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("INSERT INTO ").append(this.table);
        buffer.append("(").append(StringUtils.join(this.field, ", ")).append(")");
        buffer.append(" ").append("VALUES").append("(").append(StringUtils.join(this.value, ", ")).append(")");
        if (!this.where.isEmpty()) {
            buffer.append(" WHERE " + StringUtils.join(this.where, " AND "));
        }
        return buffer.toString();
    }

}
