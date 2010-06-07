package com.amee.platform.search;

import com.amee.domain.data.ItemDefinition;
import org.apache.lucene.search.Query;

public class DataItemFilter extends QueryFilter {

    private ItemDefinition itemDefinition;

    public DataItemFilter() {
        super();
    }

    public DataItemFilter(ItemDefinition itemDefinition) {
        this();
        setItemDefinition(itemDefinition);
    }

    public Query getUid() {
        return getQueries().get("uid");
    }

    public void setUid(Query uid) {
        getQueries().put("uid", uid);
    }

    public Query getName() {
        return getQueries().get("name");
    }

    public void setName(Query name) {
        getQueries().put("name", name);
    }

    public Query getPath() {
        return getQueries().get("path");
    }

    public void setPath(Query path) {
        getQueries().put("path", path);
    }

    public Query getWikiDoc() {
        return getQueries().get("wikiDoc");
    }

    public void setWikiDoc(Query wikiDoc) {
        getQueries().put("wikiDoc", wikiDoc);
    }

    public Query getProvenance() {
        return getQueries().get("provenance");
    }

    public void setProvenance(Query provenance) {
        getQueries().put("provenance", provenance);
    }

    public Query getCategoryUid() {
        return getQueries().get("categoryUid");
    }

    public void setCategoryUid(Query categoryUid) {
        getQueries().put("categoryUid", categoryUid);
    }

    public Query getCategoryWikiName() {
        return getQueries().get("categoryWikiName");
    }

    public void setCategoryWikiName(Query categoryWikiName) {
        getQueries().put("categoryWikiName", categoryWikiName);
    }

    public Query getItemDefinitionUid() {
        return getQueries().get("itemDefinitionUid");
    }

    public void setItemDefinitionUid(Query itemDefinitionUid) {
        getQueries().put("itemDefinitionUid", itemDefinitionUid);
    }

    public Query getItemDefinitionName() {
        return getQueries().get("itemDefinitionName");
    }

    public void setItemDefinitionName(Query definitionName) {
        getQueries().put("itemDefinitionName", definitionName);
    }

    public Query getLabel() {
        return getQueries().get("label");
    }

    public void setLabel(Query label) {
        getQueries().put("label", label);
    }

    public ItemDefinition getItemDefinition() {
        return itemDefinition;
    }

    public void setItemDefinition(ItemDefinition itemDefinition) {
        this.itemDefinition = itemDefinition;
    }

    public int getResultLimitDefault() {
        return 50;
    }

    public int getResultLimitMax() {
        return 100;
    }
}