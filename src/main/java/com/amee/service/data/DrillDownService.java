package com.amee.service.data;

import com.amee.domain.IDataCategoryReference;
import com.amee.domain.cache.CacheHelper;
import com.amee.domain.data.DataCategory;
import com.amee.domain.data.ItemDefinition;
import com.amee.domain.sheet.Choice;
import com.amee.domain.sheet.Choices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@Service
public class DrillDownService {

    @Autowired
    private DataServiceDAO dataServiceDao;

    @Autowired
    private DrillDownDAO drillDownDao;

    private CacheHelper cacheHelper = CacheHelper.getInstance();

    public Choices getChoices(IDataCategoryReference dc, List<Choice> selections) {

        // Get Data Category.
        DataCategory dataCategory = dataServiceDao.getDataCategory(dc);

        // we can do a drill down if an itemDefinition is attached to current dataCategory
        ItemDefinition itemDefinition = dataCategory.getItemDefinition();
        List<Choice> drillDownChoices = null;
        List<Choice> values = new ArrayList<Choice>();
        if (itemDefinition != null) {

            // obtain drill down choices
            drillDownChoices = itemDefinition.getDrillDownChoices();

            // fix-up selections and drill downs
            matchSelectionOrderToDrillDownChoices(drillDownChoices, selections);
            removeSelectionsNotInDrillDownChoices(drillDownChoices, selections);
            removeDrillDownChoicesThatHaveBeenSelected(drillDownChoices, selections);

            // get drill down values
            values = getDataItemChoices(dataCategory, selections, drillDownChoices);
        }

        // work out name
        String name;
        if ((drillDownChoices != null) && (drillDownChoices.size() > 0)) {
            name = drillDownChoices.get(0).getName();
        } else {
            name = "uid";
        }

        // skip ahead if we only have one value that is not "uid"
        if (!name.equals("uid") && (values.size() == 1)) {
            selections.add(new Choice(name, values.get(0).getValue()));
            return getChoices(dataCategory, selections);
        } else {
            // wrap result in Choices object
            return new Choices(name, values);
        }
    }

    public void clearDrillDownCache() {
        cacheHelper.clearCache("DrillDownChoices");
    }

    @SuppressWarnings("unchecked")
    protected List<Choice> getDataItemChoices(
            IDataCategoryReference dataCategory,
            List<Choice> selections,
            List<Choice> drillDownChoices) {
        // Get Choices and sort.
        List<Choice> choices = ((List<Choice>) cacheHelper.getCacheable(
                new DrillDownFactory(drillDownDao, dataCategory, selections, drillDownChoices)));
        Collections.sort(choices);
        return choices;
    }

    protected void matchSelectionOrderToDrillDownChoices(List<Choice> drillDownChoices, List<Choice> selections) {
        for (Choice c : drillDownChoices) {
            int selectionIndex = selections.indexOf(c);
            if (selectionIndex >= 0) {
                selections.add(selections.remove(selectionIndex));
            }
        }
    }

    protected void removeDrillDownChoicesThatHaveBeenSelected(List<Choice> drillDownChoices, List<Choice> selections) {
        Iterator<Choice> iterator;
        Choice choice;
        iterator = drillDownChoices.iterator();
        while (iterator.hasNext()) {
            choice = iterator.next();
            if (selections.contains(choice)) {
                iterator.remove();
            }
        }
    }

    protected void removeSelectionsNotInDrillDownChoices(List<Choice> drillDownChoices, List<Choice> selections) {
        Iterator<Choice> iterator;
        Choice choice;
        iterator = selections.iterator();
        while (iterator.hasNext()) {
            choice = iterator.next();
            if (!drillDownChoices.contains(choice)) {
                iterator.remove();
            }
        }
    }
}