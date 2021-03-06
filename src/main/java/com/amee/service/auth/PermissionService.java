package com.amee.service.auth;

import com.amee.domain.IAMEEEntity;
import com.amee.domain.IAMEEEntityReference;
import com.amee.domain.ObjectType;
import com.amee.domain.auth.AuthorizationContext;
import com.amee.domain.auth.Permission;
import com.amee.domain.auth.PermissionEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PermissionService {

    /**
     * Defines which 'principals' (keys) can relate to which 'entities' (values).
     */
    public final static Map<ObjectType, Set<ObjectType>> PRINCIPAL_ENTITY = new HashMap<ObjectType, Set<ObjectType>>();

    /**
     * Define which principals can relate to which entities.
     */ {
        // Users <--> Entities
        addPrincipalAndEntity(ObjectType.USR, ObjectType.ENV);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.PR);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.DC);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.PI);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.DI);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.PINV);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.PITV);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.DINV);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.DINVH);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.DITV);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.DITVH);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.AL);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.ID);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.IVD);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.RVD);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.ALC);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.USR);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.GRP);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.VD);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.TA);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.UN);
        addPrincipalAndEntity(ObjectType.USR, ObjectType.UT);

        // Groups <--> Entities
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.ENV);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.PR);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.DC);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.PI);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.DI);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.PINV);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.PITV);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.DINV);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.DINVH);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.DITV);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.DITVH);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.AL);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.ID);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.IVD);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.RVD);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.ALC);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.USR);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.GRP);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.VD);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.TA);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.UN);
        addPrincipalAndEntity(ObjectType.GRP, ObjectType.UT);
    }

    @Autowired
    private PermissionServiceDAO dao;

    public Permission getPermissionByUid(String uid) {
        return dao.getPermissionByUid(uid);
    }

    public void persist(Permission permission) {
        dao.persist(permission);
    }

    public void remove(Permission permission) {
        dao.remove(permission);
    }

    public List<Permission> getPermissionsForEntity(IAMEEEntityReference entity) {
        if ((entity == null) || !isValidEntity(entity)) {
            throw new IllegalArgumentException();
        }
        return dao.getPermissionsForEntity(entity);
    }

    public Permission getPermissionForEntity(IAMEEEntityReference entity, PermissionEntry entry) {
        List<Permission> permissions = getPermissionsForEntity(entity);
        for (Permission permission : permissions) {
            if (permission.getEntries().contains(entry)) {
                return permission;
            }
        }
        return null;
    }

    public List<Permission> getPermissionsForPrincipals(Collection<IAMEEEntityReference> principals) {
        if ((principals == null) || principals.isEmpty()) {
            throw new IllegalArgumentException();
        }
        List<Permission> permissions = new ArrayList<Permission>();
        for (IAMEEEntityReference principal : principals) {
            permissions.addAll(getPermissionsForPrincipal(principal));
        }
        return permissions;
    }

    public List<Permission> getPermissionsForPrincipal(IAMEEEntityReference principal) {
        if ((principal == null) || !isValidPrincipal(principal)) {
            throw new IllegalArgumentException();
        }
        return dao.getPermissionsForPrincipal(principal, null);
    }

    /**
     * Fetch a List of all available Permissions matching the principals in the authorizationContext and entity.
     *
     * @param authorizationContext to get principals to match on
     * @param entityReference      to match on
     * @return list of matching permissions
     */
    public List<Permission> getPermissionsForEntity(AuthorizationContext authorizationContext, IAMEEEntityReference entityReference) {
        // Parameters must not be null.
        if ((authorizationContext == null) || (entityReference == null)) {
            throw new IllegalArgumentException("Either authorizationContext or entityReference is null.");
        }
        // Check principals are valid.
        for (IAMEEEntityReference principal : authorizationContext.getPrincipals()) {
            if (!isValidPrincipalToEntity(principal, entityReference)) {
                throw new IllegalArgumentException("A principal was not valid for the entity.");
            }
        }
        // Collect permissions for principals and entity.
        IAMEEEntity entity = getEntity(entityReference);
        List<Permission> permissions = new ArrayList<Permission>();
        permissions.addAll(entity.handleAuthorizationContext(authorizationContext));
        for (IAMEEEntityReference principal : authorizationContext.getPrincipals()) {
            permissions.addAll(dao.getPermissionsForEntity(principal, entity));
        }
        return permissions;
    }

    public void trashPermissionsForEntity(IAMEEEntityReference entity) {
        dao.trashPermissionsForEntity(entity);
    }

    private void addPrincipalAndEntity(ObjectType principal, ObjectType entity) {
        Set<ObjectType> entities = PRINCIPAL_ENTITY.get(principal);
        if (entities == null) {
            entities = new HashSet<ObjectType>();
            PRINCIPAL_ENTITY.put(principal, entities);
        }
        entities.add(entity);
    }

    public boolean isValidPrincipal(IAMEEEntityReference principal) {
        if (principal == null) throw new IllegalArgumentException();
        return PRINCIPAL_ENTITY.keySet().contains(principal.getObjectType());
    }

    public boolean isValidEntity(IAMEEEntityReference entity) {
        if (entity == null) throw new IllegalArgumentException();
        for (Set<ObjectType> entities : PRINCIPAL_ENTITY.values()) {
            if (entities.contains(entity.getObjectType())) {
                return true;
            }
        }
        return false;
    }

    public boolean isValidPrincipalToEntity(IAMEEEntityReference principal, IAMEEEntityReference entity) {
        if ((principal == null) || (entity == null)) throw new IllegalArgumentException();
        return isValidPrincipal(principal) &&
                PRINCIPAL_ENTITY.get(principal.getObjectType()).contains(entity.getObjectType());
    }

    /**
     * Fetch the entity referenced by the IAMEEEntityReference from the database. Copies the
     * AccessSpecification from the entityReference to the entity.
     *
     * @param entityReference to fetch
     * @return fetched entity
     */
    public IAMEEEntity getEntity(IAMEEEntityReference entityReference) {
        IAMEEEntity entity = entityReference.getEntity();
        if (entity == null) {
            entity = dao.getEntity(entityReference);
            if (entity == null) {
                throw new RuntimeException("An entity was not found for the entityReference.");
            }
            if (entity.getAccessSpecification() == null) {
                entity.setAccessSpecification(entityReference.getAccessSpecification());
            }
            entityReference.setEntity(entity);
        }
        return entity;
    }
}
