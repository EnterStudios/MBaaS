package com.angkorteam.mbaas.server.page;

import com.angkorteam.mbaas.server.Jdbc;
import com.angkorteam.mbaas.server.nashorn.Disk;
import com.angkorteam.mbaas.server.nashorn.Factory;
import com.angkorteam.mbaas.server.wicket.Mount;
import org.apache.wicket.request.cycle.RequestCycle;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by socheat on 5/28/16.
 */
@Mount("/master")
public class MasterPage extends com.angkorteam.mbaas.server.wicket.MasterPage {

    private String masterPageId;

    private Factory factory;

    private Map<String, Object> userModel;

    private Disk disk;

    private String script;

    private boolean stage;

    @Override
    protected void onInitialize() {
        super.onInitialize();
        this.stage = getPageParameters().get("stage").toBoolean(false);
        if (this instanceof PagePage) {
            String pageId = getRequest().getQueryParameters().getParameterValue("pageId").toString("");
            JdbcTemplate jdbcTemplate = getApplicationJdbcTemplate();
            this.masterPageId = jdbcTemplate.queryForObject("SELECT " + Jdbc.Page.MASTER_PAGE_ID + " FROM ", String.class, pageId);
        } else {
            this.masterPageId = getRequest().getQueryParameters().getParameterValue("masterPageId").toString("");
        }
        this.userModel = new HashMap<>();
        JdbcTemplate jdbcTemplate = getApplicationJdbcTemplate();
        Map<String, Object> pageRecord = jdbcTemplate.queryForMap("SELECT * FROM " + Jdbc.MASTER_PAGE + " WHERE " + Jdbc.MasterPage.MASTER_PAGE_ID + " = ?", this.masterPageId);
        this.script = (String) (this.stage ? pageRecord.get(Jdbc.MasterPage.STAGE_JAVASCRIPT) : pageRecord.get(Jdbc.MasterPage.JAVASCRIPT));
        this.disk = new Disk(getApplicationCode(), getSession().getApplicationUserId());
        this.factory = new Factory(this, this.disk, getApplicationCode(), this.script, this.userModel);
        ScriptEngine engine = getScriptEngine();
        try {
            engine.eval(this.script);
        } catch (ScriptException e) {
            e.printStackTrace();
        }
        Invocable invocable = (Invocable) engine;
        IOnInitialize iOnInitialize = invocable.getInterface(IOnInitialize.class);
        if (iOnInitialize != null) {
            iOnInitialize.onInitialize(RequestCycle.get(), this.disk, jdbcTemplate, this.factory, this.userModel);
        }
    }

    @Override
    protected void onBeforeRender() {
        super.onBeforeRender();
        ScriptEngine engine = getScriptEngine();
        try {
            engine.eval(this.script);
        } catch (ScriptException e) {
            e.printStackTrace();
        }
        Invocable invocable = (Invocable) engine;
        IOnBeforeRender iOnBeforeRender = invocable.getInterface(IOnBeforeRender.class);
        if (iOnBeforeRender != null) {
            JdbcTemplate jdbcTemplate = getApplicationJdbcTemplate();
            iOnBeforeRender.onBeforeRender(RequestCycle.get(), this.disk, jdbcTemplate, this.factory, this.userModel);
        }
    }

    @Override
    public String getVariation() {
        if (this.stage) {
            return this.masterPageId + "-stage";
        } else {
            return this.masterPageId;
        }
    }

    public interface IOnBeforeRender extends Serializable {

        void onBeforeRender(RequestCycle requestCycle, Disk disk, JdbcTemplate jdbcTemplate, Factory factory, Map<String, Object> userModel);

    }

    public interface IOnInitialize extends Serializable {

        void onInitialize(RequestCycle requestCycle, Disk disk, JdbcTemplate jdbcTemplate, Factory factory, Map<String, Object> userModel);

    }
}
