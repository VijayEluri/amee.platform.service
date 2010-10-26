/**
 * This file is part of AMEE.
 *
 * AMEE is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * AMEE is free software and is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Created by http://www.dgen.net.
 * Website http://www.amee.cc
 */
package com.amee.service.item;

import com.amee.domain.AMEEStatus;
import com.amee.domain.data.DataCategory;
import com.amee.domain.item.BaseItem;
import com.amee.domain.item.BaseItemValue;
import com.amee.domain.item.profile.NuProfileItem;
import com.amee.domain.item.profile.ProfileItemNumberValue;
import com.amee.domain.item.profile.ProfileItemTextValue;
import com.amee.domain.profile.LegacyProfileItem;
import com.amee.domain.profile.Profile;
import com.amee.platform.science.StartEndDate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class ProfileItemServiceDAOImpl extends ItemServiceDAOImpl implements ProfileItemServiceDAO {

    private final Log log = LogFactory.getLog(getClass());

    @Override
    public Class getEntityClass() {
        return NuProfileItem.class;
    }

    /**
     * Returns the ProfileItem matching the specified UID.
     *
     * @param uid for the requested ProfileItem
     * @return the matching ProfileItem or null if not found
     */
    @Override
    public NuProfileItem getItemByUid(String uid) {
        return (NuProfileItem) super.getItemByUid(uid);
    }

    // ItemValues.

    @Override
    public Set<BaseItemValue> getAllItemValues(BaseItem item) {
        if (!NuProfileItem.class.isAssignableFrom(item.getClass())) throw new IllegalStateException();
        return getProfileItemValues((NuProfileItem) item);
    }

    @Override
    public Set<BaseItemValue> getProfileItemValues(NuProfileItem profileItem) {
        Set<BaseItemValue> rawItemValues = new HashSet<BaseItemValue>();
        rawItemValues.addAll(getProfileItemNumberValues(profileItem));
        rawItemValues.addAll(getProfileItemTextValues(profileItem));
        return rawItemValues;
    }

    /**
     * TODO: Would caching here be useful?
     *
     * @param profileItem
     * @return
     */
    public List<ProfileItemNumberValue> getProfileItemNumberValues(NuProfileItem profileItem) {
        Session session = (Session) entityManager.getDelegate();
        Criteria criteria = session.createCriteria(ProfileItemNumberValue.class);
        criteria.add(Restrictions.eq("profileItem.id", profileItem.getId()));
        criteria.add(Restrictions.ne("status", AMEEStatus.TRASH));
        return criteria.list();
    }

    /**
     * TODO: Would caching here be useful?
     *
     * @param profileItem
     * @return
     */
    public List<ProfileItemTextValue> getProfileItemTextValues(NuProfileItem profileItem) {
        Session session = (Session) entityManager.getDelegate();
        Criteria criteria = session.createCriteria(ProfileItemTextValue.class);
        criteria.add(Restrictions.eq("profileItem.id", profileItem.getId()));
        criteria.add(Restrictions.ne("status", AMEEStatus.TRASH));
        return criteria.list();
    }

    @Override
    public Set<BaseItemValue> getItemValuesForItems(Collection<BaseItem> items) {
        Set<BaseItemValue> itemValues = new HashSet<BaseItemValue>();
        itemValues.addAll(getValuesForDataItems(items, ProfileItemNumberValue.class));
        itemValues.addAll(getValuesForDataItems(items, ProfileItemTextValue.class));
        return itemValues;
    }

    @Override
    public int getProfileItemCount(Profile profile, DataCategory dataCategory) {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public List<NuProfileItem> getProfileItems(Profile profile, DataCategory dataCategory, Date profileDate) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<NuProfileItem> getProfileItems(Profile profile, DataCategory dataCategory, StartEndDate startDate, StartEndDate endDate) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings(value = "unchecked")
    public boolean equivalentProfileItemExists(NuProfileItem profileItem) {
        Session session = (Session) entityManager.getDelegate();
        Criteria criteria = session.createCriteria(NuProfileItem.class);
        criteria.add(Restrictions.eq("profile.id", profileItem.getProfile().getId()));
        criteria.add(Restrictions.ne("uid", profileItem.getUid()));
        criteria.add(Restrictions.eq("dataCategory.id", profileItem.getDataCategory().getId()));
        criteria.add(Restrictions.eq("dataItem.id", profileItem.getDataItem().getId()));
        criteria.add(Restrictions.eq("startDate", profileItem.getStartDate()));
        criteria.add(Restrictions.eq("name", profileItem.getName()));
        criteria.add(Restrictions.ne("status", AMEEStatus.TRASH));
        List<LegacyProfileItem> profileItems = criteria.list();
        if (profileItems.size() > 0) {
            log.debug("equivalentProfileItemExists() - found ProfileItem(s)");
            return true;
        } else {
            log.debug("equivalentProfileItemExists() - no ProfileItem(s) found");
            return false;
        }
    }

    @Override
    public Collection<Long> getProfileDataCategoryIds(Profile profile) {
        Session session = (Session) entityManager.getDelegate();
        Criteria criteria = session.createCriteria(NuProfileItem.class);

        criteria.add(Restrictions.eq("type", "PI"));
        criteria.add(Restrictions.eq("profile.id", profile.getId()));
        criteria.add(Restrictions.ne("status", AMEEStatus.TRASH));
        criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);

        List<NuProfileItem> profileItems = criteria.list();
        List<Long> ids = new ArrayList<Long>();
        for(NuProfileItem item : profileItems) {
            ids.add(item.getDataCategory().getId());
        }

        return ids;
    }

    @Override
    public void persist(NuProfileItem profileItem) {
        entityManager.persist(profileItem);
    }
}