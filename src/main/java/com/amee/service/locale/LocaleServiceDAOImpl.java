package com.amee.service.locale;

import com.amee.domain.AMEEStatus;
import com.amee.domain.IAMEEEntityReference;
import com.amee.domain.ObjectType;
import com.amee.domain.data.LocaleName;
import org.hibernate.Criteria;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LocaleServiceDAOImpl implements LocaleServiceDAO {

    private static final String CACHE_REGION = "query.localeService";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @SuppressWarnings(value = "unchecked")
    public List<LocaleName> getLocaleNames(IAMEEEntityReference entity) {
        Session session = (Session) entityManager.getDelegate();
        Criteria criteria = session.createCriteria(LocaleName.class);
        criteria.add(Restrictions.eq("entity.entityUid", entity.getEntityUid()));
        criteria.add(Restrictions.eq("entity.entityType", entity.getObjectType().getName()));
        criteria.add(Restrictions.ne("status", AMEEStatus.TRASH));
        criteria.setCacheable(true);
        criteria.setCacheRegion(CACHE_REGION);
        criteria.setFlushMode(FlushMode.MANUAL);
        return criteria.list();
    }

    /**
     * Note: This can return LocaleNames associated with various types of entities.
     */
    @Override
    @SuppressWarnings(value = "unchecked")
    public List<LocaleName> getLocaleNames(ObjectType objectType, Collection<IAMEEEntityReference> entities) {
        Set<Long> entityIds = new HashSet<Long>();
        entityIds.add(0L);
        for (IAMEEEntityReference entity : entities) {
            entityIds.add(entity.getEntityId());
        }
        Session session = (Session) entityManager.getDelegate();
        Criteria criteria = session.createCriteria(LocaleName.class);
        criteria.add(Restrictions.in("entity.entityId", entityIds));
        criteria.add(Restrictions.eq("entity.entityType", objectType.getName()));
        criteria.add(Restrictions.ne("status", AMEEStatus.TRASH));
        criteria.setFlushMode(FlushMode.MANUAL);
        return criteria.list();
    }

    @Override
    public void persist(LocaleName localeName) {
        entityManager.persist(localeName);
    }

    @Override
    public void remove(LocaleName localeName) {
        localeName.setStatus(AMEEStatus.TRASH);
    }
}
