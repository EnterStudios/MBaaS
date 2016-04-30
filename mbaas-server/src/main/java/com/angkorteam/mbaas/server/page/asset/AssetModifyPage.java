package com.angkorteam.mbaas.server.page.asset;

import com.angkorteam.framework.extension.wicket.feedback.TextFeedbackPanel;
import com.angkorteam.framework.extension.wicket.markup.html.form.Button;
import com.angkorteam.mbaas.model.entity.Tables;
import com.angkorteam.mbaas.model.entity.tables.AssetTable;
import com.angkorteam.mbaas.model.entity.tables.AttributeTable;
import com.angkorteam.mbaas.model.entity.tables.CollectionTable;
import com.angkorteam.mbaas.model.entity.tables.pojos.AttributePojo;
import com.angkorteam.mbaas.model.entity.tables.pojos.CollectionPojo;
import com.angkorteam.mbaas.model.entity.tables.records.AssetRecord;
import com.angkorteam.mbaas.model.entity.tables.records.CollectionRecord;
import com.angkorteam.mbaas.plain.enums.AttributeExtraEnum;
import com.angkorteam.mbaas.plain.request.document.DocumentModifyRequest;
import com.angkorteam.mbaas.server.function.DocumentFunction;
import com.angkorteam.mbaas.server.template.TextFieldPanel;
import com.angkorteam.mbaas.server.wicket.MasterPage;
import com.angkorteam.mbaas.server.wicket.Mount;
import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.authroles.authorization.strategies.role.annotations.AuthorizeInstantiation;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.jooq.DSLContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by socheat on 3/11/16.
 */
@AuthorizeInstantiation({"administrator", "backoffice", "registered"})
@Mount("/asset/modify")
public class AssetModifyPage extends MasterPage {

    private String assetId;
    private CollectionPojo collection;
    private String collectionId;
    private String documentId;

    private String name;
    private TextField<String> nameField;
    private TextFeedbackPanel nameFeedback;

    private Integer length;
    private Label lengthLabel;

    private String mime;
    private Label mimeLabel;

    private String extension;
    private Label extensionLabel;

    private String pathText;
    private Label pathLabel;

    private Button saveButton;

    private Form<Void> form;

    @Override
    public String getPageHeader() {
        return "Modify Asset";
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        DSLContext context = getDSLContext();

        PageParameters parameters = getPageParameters();
        this.assetId = parameters.get("assetId").toString();
        this.documentId = this.assetId;

        AssetTable assetTable = Tables.ASSET.as("assetTable");

        CollectionTable collectionTable = Tables.COLLECTION.as("collectionTable");
        this.collection = context.select(collectionTable.fields()).from(collectionTable).where(collectionTable.NAME.eq(Tables.ASSET.getName())).fetchOneInto(CollectionPojo.class);
        this.collectionId = this.collection.getCollectionId();

        AssetRecord assetRecord = context.select(assetTable.fields()).from(assetTable).where(assetTable.ASSET_ID.eq(assetId)).fetchOneInto(assetTable);

        this.form = new Form<>("form");
        add(this.form);

        this.name = assetRecord.getLabel();
        this.nameField = new TextField<>("nameField", new PropertyModel<>(this, "name"));
        this.nameField.setRequired(true);
        this.form.add(this.nameField);
        this.nameFeedback = new TextFeedbackPanel("nameFeedback", this.nameField);
        this.form.add(this.nameFeedback);

        this.length = assetRecord.getLength();
        this.lengthLabel = new Label("lengthLabel", new PropertyModel<>(this, "length"));
        this.form.add(lengthLabel);

        this.mime = assetRecord.getMime();
        this.mimeLabel = new Label("mimeLabel", new PropertyModel<>(this, "mime"));
        this.form.add(mimeLabel);

        this.extension = assetRecord.getExtension();
        this.extensionLabel = new Label("extensionLabel", new PropertyModel<>(this, "extension"));
        this.form.add(extensionLabel);

        this.pathText = assetRecord.getPath();
        this.pathLabel = new Label("pathLabel", new PropertyModel<>(this, "pathText"));
        this.form.add(pathLabel);

        this.saveButton = new Button("saveButton");
        this.saveButton.setOnSubmit(this::saveButtonOnSubmit);

        this.form.add(this.saveButton);
    }

    private void saveButtonOnSubmit(Button button) {
        DSLContext context = getDSLContext();
        CollectionTable collectionTable = Tables.COLLECTION.as("collectionTable");

        CollectionRecord collectionRecord = context.select(collectionTable.fields()).from(collectionTable).where(collectionTable.COLLECTION_ID.eq(collectionId)).fetchOneInto(collectionTable);

        Map<String, Object> fields = new HashMap<>();
        DocumentModifyRequest requestBody = new DocumentModifyRequest();
        fields.put(Tables.ASSET.LABEL.getName(), this.name);
        requestBody.setDocument(fields);

        DocumentFunction.modifyDocument(getDSLContext(), getJdbcTemplate(), collectionRecord.getName(), this.documentId, requestBody);

        setResponsePage(AssetManagementPage.class);
    }
}
