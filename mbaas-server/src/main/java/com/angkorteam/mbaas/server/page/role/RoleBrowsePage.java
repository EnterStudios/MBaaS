package com.angkorteam.mbaas.server.page.role;

import com.angkorteam.framework.extension.wicket.extensions.markup.html.repeater.data.table.DataTable;
import com.angkorteam.framework.extension.wicket.extensions.markup.html.repeater.data.table.DefaultDataTable;
import com.angkorteam.framework.extension.wicket.extensions.markup.html.repeater.data.table.filter.*;
import com.angkorteam.mbaas.server.page.MBaaSPage;
import com.angkorteam.mbaas.server.provider.RoleProvider;
import com.google.common.collect.Maps;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.filter.FilterForm;
import org.apache.wicket.markup.html.border.Border;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by socheat on 10/24/16.
 */
public class RoleBrowsePage extends MBaaSPage {

    @Override
    public String getPageUUID() {
        return RoleBrowsePage.class.getName();
    }

    @Override
    protected void doInitialize(Border layout) {
        add(layout);

        RoleProvider provider = new RoleProvider();
        provider.selectField("roleId", String.class);

        FilterForm<Map<String, String>> filterForm = new FilterForm<>("filter-form", provider);
        layout.add(filterForm);

        List<IColumn<Map<String, Object>, String>> columns = new ArrayList<>();
        columns.add(new TextFilterColumn(provider, ItemClass.String, Model.of("name"), "name", this::modelValue));
        columns.add(new TextFilterColumn(provider, ItemClass.String, Model.of("description"), "description", this::modelValue));
        columns.add(new TextFilterColumn(provider, ItemClass.Boolean, Model.of("system"), "system", this::modelValue));
        columns.add(new ActionFilterColumn(Model.of("action"), this::actions, this::clickable, this::itemCss, this::itemClick));

        DataTable<Map<String, Object>, String> dataTable = new DefaultDataTable<>("table", columns, provider, 20);
        dataTable.addTopToolbar(new FilterToolbar(dataTable, filterForm));
        filterForm.add(dataTable);

        BookmarkablePageLink<Void> refreshLink = new BookmarkablePageLink<>("refreshLink", RoleBrowsePage.class);
        layout.add(refreshLink);

        BookmarkablePageLink<Void> createLink = new BookmarkablePageLink<>("createLink", RoleCreatePage.class);
        layout.add(createLink);
    }

    private Object modelValue(String name, Map<String, Object> stringObjectMap) {
        return stringObjectMap.get(name);
    }

    private Map<String, IModel<String>> actions() {
        Map<String, IModel<String>> actions = Maps.newHashMap();
        actions.put("Edit", Model.of("Edit"));
        return actions;
    }

    private void itemClick(String link, Map<String, Object> object, AjaxRequestTarget ajaxRequestTarget) {
        String roleId = (String) object.get("roleId");
        PageParameters parameters = new PageParameters();
        parameters.add("roleId", roleId);
        setResponsePage(RoleModifyPage.class, parameters);
    }

    private Boolean clickable(String link, Map<String, Object> object) {
        Boolean system = (Boolean) object.get("system");
        if ("Edit".equals(link)) {
            if (system) {
                return false;
            }
            return true;
        }
        return false;
    }

    private ItemCss itemCss(String link, Map<String, Object> model) {
        if ("Edit".equals(link)) {
            return ItemCss.INFO;
        }
        return ItemCss.NONE;
    }

}
