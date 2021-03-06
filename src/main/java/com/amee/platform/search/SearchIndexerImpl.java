package com.amee.platform.search;

import com.amee.base.transaction.AMEETransaction;
import com.amee.domain.*;
import com.amee.domain.data.DataCategory;
import com.amee.domain.item.BaseItemValue;
import com.amee.domain.item.data.DataItem;
import com.amee.platform.science.Amount;
import com.amee.service.data.DataService;
import com.amee.service.invalidation.InvalidationService;
import com.amee.service.tag.TagService;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.perf4j.slf4j.Slf4JStopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringReader;
import java.util.*;

/**
 * Encapsulates all logic for creating the Lucene search index. Implements {@link SearchIndexer} to provide the only
 * public methods, handleSearchIndexerContext and clear.
 */
public class SearchIndexerImpl implements SearchIndexer {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Logger searchLog = LoggerFactory.getLogger("search");

    public final static DateTimeFormatter DATE_TO_SECOND = DateTimeFormat.forPattern("yyyyMMddHHmmss");

    // Count of successfully indexed DataCategories.
    private static long COUNT = 0L;

    @Autowired
    private DataService dataService;

    @Autowired
    private DataItemService dataItemService;

    @Autowired
    private MetadataService metadataService;

    @Autowired
    private LocaleService localeService;

    @Autowired
    private TagService tagService;

    @Autowired
    private SearchQueryService searchQueryService;

    @Autowired
    private LuceneService luceneService;

    @Autowired
    private InvalidationService invalidationService;

    // A wrapper object encapsulating the context of the current indexing operation.
    private SearchIndexerContext searchIndexerContext;

    // The DataCategory currently being indexed.
    private DataCategory dataCategory;

    // The DataItems for the current DataCategory.
    private List<DataItem> dataItems;

    @Override
    public void clear() {
        searchIndexerContext = null;
        dataCategory = null;
        dataItems = null;
    }

    @AMEETransaction
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public void handleSearchIndexerContext(SearchIndexerContext searchIndexerContext) {
        this.searchIndexerContext = searchIndexerContext;
        try {
            searchLog.info(this.searchIndexerContext.dataCategoryUid + "|Started processing DataCategory.");

            // Get the DataCategory from the database and handle.
            dataCategory = dataService.getDataCategoryByUid(this.searchIndexerContext.dataCategoryUid, null);
            if (dataCategory != null) {
                updateDataCategory();
            } else {
                searchLog.warn(this.searchIndexerContext.dataCategoryUid + "|DataCategory not found.");
            }
        } catch (LuceneServiceException e) {
            searchLog.warn(this.searchIndexerContext.dataCategoryUid + "|Abandoned processing DataCategory.");
            log.warn("run() Caught LuceneServiceException: " + e.getMessage());
        } catch (InterruptedException e) {
            searchLog.warn(this.searchIndexerContext.dataCategoryUid + "|DataCategory may not completed.");
            log.warn("run() Caught InterruptedException: " + e.getMessage());
        } catch (Throwable t) {
            searchLog.error(this.searchIndexerContext.dataCategoryUid + "|Error processing DataCategory.");
            log.error("run() Caught Throwable: " + t.getMessage(), t);
        } finally {

            // We're done!
            searchLog.info(this.searchIndexerContext.dataCategoryUid + "|Completed processing DataCategory.");
        }
    }

    /**
     * Insert, update or remove the Data Category & Data Items from the search index.
     *
     * @throws InterruptedException might be thrown if application is shutdown whilst indexing
     */
    private void updateDataCategory() throws InterruptedException {
        Slf4JStopWatch stopWatch = new Slf4JStopWatch("updateDataCategory");
        if (!dataCategory.isTrash()) {

            // First check if we have the data category in the index.
            Document document = searchQueryService.getDocument(dataCategory, true);
            if (document != null) {

                // Check the indexed document is up to date and re-index it if necessary.
                checkDataCategoryDocument(document);
            } else {

                // We don't have the document in the index, so add it.
                searchLog.info(searchIndexerContext.dataCategoryUid + "|DataCategory not in index, adding for the first time.");
                searchIndexerContext.handleDataItems = true;
                handleDataCategory();
            }
        } else {
            searchLog.info(searchIndexerContext.dataCategoryUid + "|DataCategory needs to be removed.");
            searchQueryService.removeDataCategory(dataCategory);
            searchQueryService.removeDataItems(dataCategory);

            // Send message stating that the DataCategory has been re-indexed.
            invalidationService.add(dataCategory, "dataCategoryIndexed");
        }
        stopWatch.stop();
    }

    /**
     * Check the Lucene Document for the current DataCategory to see if it needs updating.
     *
     * @param document of the current DataCategory
     */
    private void checkDataCategoryDocument(Document document) {
        Slf4JStopWatch stopWatch = new Slf4JStopWatch("checkDataCategoryDocument");
        boolean doUpdate = false;
        // Has a re-index been requested?
        if (searchIndexerContext.handleDataCategories || searchIndexerContext.handleDataItems) {
            // DataCategory and/or DataItem index requested, force an update.
            searchLog.info(searchIndexerContext.dataCategoryUid + "|A DataCategory re-index was requested.");
            doUpdate = true;
        } else {
            // Document must have a modified date.
            if (isDocumentModifiedFieldAvailable(document)) {
                // If the Document is out-of-date then update it.
                if (isDocumentOutOfDateForDataCategory(document)) {
                    // DataCategory in index is out-of-date, force an update.
                    searchLog.info(searchIndexerContext.dataCategoryUid + "|DataCategory has been modified or re-index requested, updating.");
                    doUpdate = true;
                } else {
                    // No need to re-index.
                    searchLog.info(searchIndexerContext.dataCategoryUid + "|DataCategory is up-to-date, skipping.");
                }
            } else {
                // Modified field missing in Document, force an update.
                searchLog.warn(searchIndexerContext.dataCategoryUid + "|The DataCategory modified field was missing, updating.");
                doUpdate = true;
            }
        }
        // Should we do a detailed check of the Data Item Documents?
        if (!doUpdate && !searchIndexerContext.handleDataItems && searchIndexerContext.checkDataItems) {
            if (areDataCategoryDataItemsInconsistent()) {
                // Something is wrong with the DataItems in the index, force an update.
                searchLog.warn(searchIndexerContext.dataCategoryUid + "|DataItems were inconsistent in the index for the DataCategory, updating.");
                doUpdate = searchIndexerContext.handleDataItems = true;
            }
        }
        // Update required?
        if (doUpdate) {
            // Have Data Items been updated?
            searchIndexerContext.handleDataItems = searchIndexerContext.handleDataItems || isDocumentOutOfDateForDataItems(document);
            // Update the Data Category Document, and perhaps the Data Items documents too.
            handleDataCategory();
        }
        stopWatch.stop();
    }

    /**
     * Returns true if the Lucene Document has an documentModified field.
     *
     * @param document Lucene Document to check
     * @return true if the Lucene Document has an documentModified field.
     */
    private boolean isDocumentModifiedFieldAvailable(Document document) {
        return document.getField("documentModified") != null;
    }

    /**
     * Returns a modified {@link DateTime} from the documentModified field in the supplied Document.
     *
     * @param document to extract modified date from
     * @return modified {@link DateTime}
     */
    private DateTime getDocumentModified(Document document) {
        Field modifiedField = document.getField("documentModified");
        return DATE_TO_SECOND.parseDateTime(modifiedField.stringValue());
    }

    /**
     * Returns true if the DataCategory Document is out-of-date. Checks the modified timestamp of the
     * DataCategory and all dependent entities.
     *
     * @param document Document to check
     * @return true if the DataCategory Document is out-of-date for the DataCategory
     */
    private boolean isDocumentOutOfDateForDataCategory(Document document) {
        DateTime modifiedInIndex = getDocumentModified(document);
        DateTime modifiedInDatabase =
                new DateTime(dataService.getDataCategoryModifiedDeep(dataCategory)).withMillisOfSecond(0);
        return (modifiedInDatabase.isAfter(modifiedInIndex));
    }

    /**
     * Returns true if the DataCategory Document is out-of-date. Checks the modified timestamp of the
     * DataItems and all dependent entities.
     *
     * @param document Document to check
     * @return true if the DataCategory Document is out-of-date for the DataItems
     */
    private boolean isDocumentOutOfDateForDataItems(Document document) {
        DateTime modifiedInIndex = getDocumentModified(document);
        DateTime modifiedInDatabase =
                new DateTime(dataService.getDataItemsModifiedDeep(dataCategory)).withMillisOfSecond(0);
        return (modifiedInDatabase.isAfter(modifiedInIndex));
    }

    /**
     * Add Documents for the supplied Data Category and any associated Data Items to the Lucene index.
     */
    private void handleDataCategory() {
        Slf4JStopWatch stopWatch = new Slf4JStopWatch("handleDataCategory");
        log.debug("handleDataCategory() {}", dataCategory.toString());
        // Handle Data Items (Create, store & update documents).
        if (searchIndexerContext.handleDataItems) {
            handleDataItems();
        }
        // Get Data Category Document.
        Document dataCategoryDoc = getDocumentForDataCategory(dataCategory);
        // Are we working with an existing index?
        if (!luceneService.getClearIndex()) {
            // Update the Data Category Document (remove old Documents).
            luceneService.updateDocument(
                    dataCategoryDoc,
                    new Term("entityType", ObjectType.DC.getName()),
                    new Term("entityUid", dataCategory.getUid()));
        } else {
            // Add the new Document.
            luceneService.addDocument(dataCategoryDoc);
        }
        // Send message stating that the DataCategory has been re-indexed.
        invalidationService.add(dataCategory, "dataCategoryIndexed");
        // Increment count for DataCategories successfully indexed.
        incrementCount();
        stopWatch.stop();
    }

    /**
     * Create all DataItem documents for the supplied DataCategory.
     */
    private void handleDataItems() {
        Slf4JStopWatch stopWatch = new Slf4JStopWatch("handleDataItems");
        searchIndexerContext.dataItemDoc = null;
        searchIndexerContext.dataItemDocs = null;
        // There are only Data Items for a Data Category if there is an Item Definition.
        if (dataCategory.getItemDefinition() != null) {
            log.info("handleDataItems() Starting... (" + dataCategory.toString() + ")");
            // Pre-cache metadata and locales for the Data Items.
            metadataService.loadMetadatasForItemValueDefinitions(dataCategory.getItemDefinition().getItemValueDefinitions());
            localeService.loadLocaleNamesForItemValueDefinitions(dataCategory.getItemDefinition().getItemValueDefinitions());
            List<DataItem> dataItems = getDataItems();
            metadataService.loadMetadatasForDataItems(dataItems);
            // Iterate over all Data Items and create Documents.
            searchIndexerContext.dataItemDocs = new ArrayList<Document>();
            Slf4JStopWatch stopWatch2 = new Slf4JStopWatch("handleDataItems:dataItemsLoop");
            for (DataItem dataItem : dataItems) {
                searchIndexerContext.dataItem = dataItem;
                // Create new Data Item Document.
                searchIndexerContext.dataItemDoc = getDocumentForDataItem(dataItem);
                searchIndexerContext.dataItemDocs.add(searchIndexerContext.dataItemDoc);
                // Handle the Data Item Values.
                handleDataItemValues(searchIndexerContext);
            }
            stopWatch2.stop();
            // Clear caches.
            metadataService.clearMetadatas();
            localeService.clearLocaleNames();
            // Are we working with an existing index?
            if (!luceneService.getClearIndex()) {
                // Clear existing Data ItemItem Documents for this DataCategory.
                searchQueryService.removeDataItems(dataCategory);
            }
            // Add the new Data Item Documents to the index (if any).
            luceneService.addDocuments(searchIndexerContext.dataItemDocs);
            log.info("handleDataItems() ...done (" + dataCategory.toString() + ").");
        } else {
            log.debug("handleDataItems() DataCategory does not have items: {}", dataCategory.toString());
            // Ensure we clear any Data Item Documents for this Data Category.
            searchQueryService.removeDataItems(dataCategory);
        }
        stopWatch.stop();
    }

    // Lucene Document creation.

    /**
     * Get the Lucene Document for a DataCategory.
     *
     * @param dataCategory DataCategory to create Document for
     * @return the Document
     */
    private Document getDocumentForDataCategory(DataCategory dataCategory) {
        Slf4JStopWatch stopWatch = new Slf4JStopWatch("getDocumentForDataCategory");
        Document doc = getDocumentForAMEEEntity(dataCategory);
        doc.add(new Field("name", dataCategory.getName().toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
        doc.add(new Field("path", dataCategory.getPath().toLowerCase(), Field.Store.NO, Field.Index.NOT_ANALYZED));
        doc.add(new Field("fullPath", dataCategory.getFullPath().toLowerCase(), Field.Store.NO, Field.Index.NOT_ANALYZED));
        doc.add(new Field("wikiName", dataCategory.getWikiName().toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
        doc.add(new Field("byWikiName", dataCategory.getWikiName().toLowerCase(), Field.Store.NO, Field.Index.NOT_ANALYZED));
        doc.add(new Field("wikiDoc", dataCategory.getWikiDoc().toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
        doc.add(new Field("provenance", dataCategory.getProvenance().toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
        doc.add(new Field("authority", dataCategory.getAuthority().toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
        if (dataCategory.getDataCategory() != null) {
            doc.add(new Field("parentUid", dataCategory.getDataCategory().getUid(), Field.Store.YES, Field.Index.NOT_ANALYZED));
            doc.add(new Field("parentWikiName", dataCategory.getDataCategory().getWikiName().toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
        }
        if (dataCategory.getItemDefinition() != null) {
            doc.add(new Field("itemDefinitionUid", dataCategory.getItemDefinition().getUid(), Field.Store.YES, Field.Index.NOT_ANALYZED));
            doc.add(new Field("itemDefinitionName", dataCategory.getItemDefinition().getName().toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
        }
        doc.add(new Field("tags", new SearchService.TagTokenizer(new StringReader(tagService.getTagsCSV(dataCategory).toLowerCase()))));
        stopWatch.stop();
        return doc;
    }

    /**
     * Get the Lucene Document for a DataItem.
     *
     * @param dataItem DataItem to create Document for
     * @return the Document
     */
    private Document getDocumentForDataItem(DataItem dataItem) {
        Slf4JStopWatch stopWatch = new Slf4JStopWatch("getDocumentForDataItem");
        Document doc = getDocumentForAMEEEntity(dataItem);
        doc.add(new Field("name", dataItem.getName().toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
        doc.add(new Field("path", dataItem.getPath().toLowerCase(), Field.Store.NO, Field.Index.NOT_ANALYZED));
        doc.add(new Field("fullPath", dataItem.getFullPath().toLowerCase() + "/" + dataItem.getDisplayPath().toLowerCase(), Field.Store.NO, Field.Index.NOT_ANALYZED));
        doc.add(new Field("wikiDoc", dataItem.getWikiDoc().toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
        doc.add(new Field("provenance", dataItem.getProvenance().toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
        doc.add(new Field("categoryUid", dataItem.getDataCategory().getUid(), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field("categoryWikiName", dataItem.getDataCategory().getWikiName().toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
        doc.add(new Field("itemDefinitionUid", dataItem.getItemDefinition().getUid(), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field("itemDefinitionName", dataItem.getItemDefinition().getName().toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
        for (BaseItemValue itemValue : dataItemService.getItemValues(dataItem)) {
            if (itemValue.isUsableValue()) {
                if (itemValue.getItemValueDefinition().isDrillDown()) {
                    doc.add(new Field(itemValue.getDisplayPath(), itemValue.getValueAsString().toLowerCase(), Field.Store.NO, Field.Index.NOT_ANALYZED));
                    doc.add(new Field(itemValue.getDisplayPath() + "_drill", itemValue.getValueAsString(), Field.Store.YES, Field.Index.NO));
                } else {
                    if (itemValue.isDouble()) {
                        try {
                            doc.add(new NumericField(itemValue.getDisplayPath()).setDoubleValue(new Amount(itemValue.getValueAsString()).getValue()));
                        } catch (NumberFormatException e) {
                            log.warn("getDocumentForDataItem() Could not parse '" + itemValue.getDisplayPath() + "' value '" + itemValue.getValueAsString() + "' for DataItem " + dataItem.toString() + ".");
                            doc.add(new Field(itemValue.getDisplayPath(), itemValue.getValueAsString().toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
                        }
                    } else {
                        doc.add(new Field(itemValue.getDisplayPath(), itemValue.getValueAsString().toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
                    }
                }
            }
        }
        doc.add(new Field("label", dataItemService.getLabel(dataItem).toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
        doc.add(new Field("byLabel", dataItemService.getLabel(dataItem).toLowerCase(), Field.Store.NO, Field.Index.NOT_ANALYZED));
        doc.add(new Field("tags", new SearchService.TagTokenizer(new StringReader(tagService.getTagsCSV(dataItem.getDataCategory()).toLowerCase()))));
        stopWatch.stop();
        return doc;
    }

    /**
     * Get the Lucene Document for an IAMEEEntity.
     *
     * @param entity IAMEEEntity to create Document for
     * @return the Document
     */
    private Document getDocumentForAMEEEntity(IAMEEEntity entity) {
        Slf4JStopWatch stopWatch = new Slf4JStopWatch("getDocumentForAMEEEntity");
        Document doc = new Document();
        doc.add(new Field("entityType", entity.getObjectType().getName(), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field("entityId", entity.getId().toString(), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field("entityUid", entity.getUid(), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field("entityCreated",
                new DateTime(entity.getCreated()).toString(DATE_TO_SECOND), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field("entityModified",
                new DateTime(entity.getModified()).toString(DATE_TO_SECOND), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field("documentModified",
                DateTime.now().toString(DATE_TO_SECOND), Field.Store.YES, Field.Index.NOT_ANALYZED));
        stopWatch.stop();
        return doc;
    }

    /**
     * Add Lucene fields to the Document for BaseItemValues from the current DataItem.
     *
     * @param context the current SearchIndexerContext
     */
    private void handleDataItemValues(SearchIndexerContext context) {
        Slf4JStopWatch stopWatch = new Slf4JStopWatch("handleDataItemValues");
        for (BaseItemValue itemValue : dataItemService.getItemValues(context.dataItem)) {
            if (itemValue.isUsableValue()) {
                if (itemValue.getItemValueDefinition().isDrillDown()) {
                    context.dataItemDoc.add(new Field(itemValue.getDisplayPath(), itemValue.getValueAsString().toLowerCase(), Field.Store.NO, Field.Index.NOT_ANALYZED));
                } else {
                    if (itemValue.isDouble()) {
                        try {
                            context.dataItemDoc.add(new NumericField(itemValue.getDisplayPath()).setDoubleValue(new Amount(itemValue.getValueAsString()).getValue()));
                        } catch (NumberFormatException e) {
                            log.warn("handleDataItemValues() Could not parse '" + itemValue.getDisplayPath() + "' value '" + itemValue.getValueAsString() + "' for DataItem " + context.dataItem.toString() + ".");
                            context.dataItemDoc.add(new Field(itemValue.getDisplayPath(), itemValue.getValueAsString().toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
                        }
                    } else {
                        context.dataItemDoc.add(new Field(itemValue.getDisplayPath(), itemValue.getValueAsString().toLowerCase(), Field.Store.NO, Field.Index.ANALYZED));
                    }
                }
            }
        }
        stopWatch.stop();
    }

    /**
     * Checks the DataItems for the current DataCategory for inconsistency in the index. The index is
     * inconsistent if the number of database and index DataItems is different or the most recently
     * modified timestamp for the database DataItems and index DataItems are different.
     *
     * @return true if the index is inconsistent, otherwise false
     */
    private boolean areDataCategoryDataItemsInconsistent() {

        Slf4JStopWatch stopWatch = new Slf4JStopWatch("areDataCategoryDataItemsInconsistent");

        try {

            // Create Query for all Data Items within the current DataCategory.
            BooleanQuery query = new BooleanQuery();
            query.add(new TermQuery(new Term("entityType", ObjectType.DI.getName())), BooleanClause.Occur.MUST);
            query.add(new TermQuery(new Term("categoryUid", dataCategory.getEntityUid())), BooleanClause.Occur.MUST);

            List<Document> dataItemDocuments = luceneService.doSearch(query).getResults();

            // First: Are the correct number of Data Items in the index?
            long dataItemCount = dataItemService.getDataItemCount(dataCategory);
            if (dataItemCount != dataItemDocuments.size()) {
                log.warn("isDataCategoryCorrupt() Inconsistent DataItem count (DB=" + dataItemCount + ", Index=" + dataItemDocuments.size() + ")");
                return true;
            }

            // Second: Check to see if modified timestamps of the most recently updated database and index items
            // match. If they do, we consider the index to correctly reflect the database.

            // Get the most recent DataItem modified date.
            Date dataItemsModified = dataItemService.getDataItemsModified(dataCategory);

            // Sort the DataItem documents by modified timestamp.
            try {
                Collections.sort(dataItemDocuments, new Comparator<Document>() {
                    public int compare(Document doc1, Document doc2) {
                        // Find modified fields.
                        Field doc1Mf = doc1.getField("entityModified");
                        Field doc2Mf = doc2.getField("entityModified");
                        // All docs should have a valid entityModified.
                        if (doc1Mf == null || doc2Mf == null) {
                            // Trigger a rebuild if this is not the case.
                            throw new RuntimeException("areDataCategoryDataItemsInconsistent() A null entityModified of DataItem detected.");
                        } else {
                            // Compare the modified values.
                            DateTime doc1Mod = DATE_TO_SECOND.parseDateTime(doc1Mf.stringValue());
                            DateTime doc2Mod = DATE_TO_SECOND.parseDateTime(doc2Mf.stringValue());
                            return doc1Mod.compareTo(doc2Mod);
                        }
                    }
                });
            } catch (RuntimeException e) {
                // Trigger a rebuild on error. Was the entityModified missing?
                log.warn("isDataCategoryCorrupt() Seems like an entityModified field was missing for a DataItem Document.");
                return true;
            }

            // Get the most recent Data Item document.
            Document mostRecentDocument = dataItemDocuments.get(dataItemDocuments.size() - 1);
            Field modField = mostRecentDocument.getField("entityModified");
            Date dataItemDocumentModified = DATE_TO_SECOND.parseDateTime(modField.stringValue()).toDate();

            // Inconsistent if the actual Data Item and Data Item Document have different modified timestamps.
            return !dataItemsModified.equals(dataItemDocumentModified);

        } finally {
            stopWatch.stop();
        }
    }

    /**
     * Get the DatItem List for the current DataCategory. Will internally cache the Data Item List for
     * repeated calls.
     *
     * @return List of DataItems
     */
    private List<DataItem> getDataItems() {
        if (dataItems == null) {
            dataItems = dataItemService.getDataItems(dataCategory, false);
        }
        return dataItems;
    }

    /**
     * Return the count of DataCategories currently indexed.
     *
     * @return count of DataCategories that have been indexed
     */
    public static long getCount() {
        return COUNT;
    }

    /**
     * Reset the DataCategory index count.
     */
    public synchronized static void resetCount() {
        COUNT = 0;
    }

    /**
     * Increment the DataCategory index count.
     */
    private synchronized static void incrementCount() {
        COUNT++;
    }
}
