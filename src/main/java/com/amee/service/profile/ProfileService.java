package com.amee.service.profile;

import com.amee.domain.Pager;
import com.amee.domain.ProfileItemService;
import com.amee.domain.auth.User;
import com.amee.domain.cache.CacheableFactory;
import com.amee.domain.profile.Profile;
import com.amee.domain.sheet.Sheet;
import com.amee.service.data.DataService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProfileService {

    private final Log log = LogFactory.getLog(getClass());

    @Autowired
    private DataService dataService;

    @Autowired
    private ProfileServiceDAO dao;

    @Autowired
    private ProfileSheetService profileSheetService;

    @Autowired
    private ProfileItemService profileItemService;

    // Profiles

    /**
     * Fetches a Profile based on the supplied UID.
     *
     * @param uid UID to search for.
     * @return the matching Profile
     */
    public Profile getProfileByUid(String uid) {
        return dao.getProfileByUid(uid);
    }

    public List<Profile> getProfiles(User user, Pager pager) {
        return dao.getProfiles(user, pager);
    }

    public void persist(Profile p) {
        dao.persist(p);
    }

    public void remove(Profile profile) {
        dao.remove(profile);
    }

    public void clearCaches(Profile profile) {
        log.debug("clearCaches()");
        profileSheetService.removeSheets(profile);
    }

    // Profile DataCategories

    public Set<Long> getProfileDataCategoryIds(Profile profile) {
        Set<Long> dataCategoryIds = new HashSet<Long>();
        // Get Data Category IDs for Profile Items.
        dataCategoryIds.addAll(profileItemService.getProfileDataCategoryIds(profile));
        // Get parent Data Category IDs based on existing Data Category IDs.
        dataCategoryIds.addAll(dataService.getParentDataCategoryIds(dataCategoryIds));
        return dataCategoryIds;
    }

    // Sheets

    public Sheet getSheet(CacheableFactory sheetFactory) {
        return profileSheetService.getSheet(sheetFactory);
    }
}