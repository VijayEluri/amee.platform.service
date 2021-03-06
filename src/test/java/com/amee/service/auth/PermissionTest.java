package com.amee.service.auth;

import com.amee.domain.APIVersion;
import com.amee.domain.auth.Group;
import com.amee.domain.auth.User;
import com.amee.domain.item.data.DataItem;
import com.amee.domain.item.profile.ProfileItem;
import com.amee.domain.profile.Profile;
import com.amee.service.ServiceTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
public class PermissionTest extends ServiceTest {

    @Autowired
    private PermissionService permissionService;

    @Test
    public void areValidPrincipals() {
        assertTrue("Should be a valid principal", permissionService.isValidPrincipal(new Group()));
        assertTrue("Should be a valid principal", permissionService.isValidPrincipal(new User()));
    }

    @Test
    public void areNotValidPrincipals() {
        assertFalse("Should not be a valid principal", permissionService.isValidPrincipal(new DataItem()));
        assertFalse("Should not be a valid principal", permissionService.isValidPrincipal(new Profile()));
    }

    @Test
    public void areValidPrincipalsToEntities() {
        assertTrue("Should be a valid principal-to-entity", permissionService.isValidPrincipalToEntity(new Group(), new DataItem()));
        assertTrue("Should be a valid principal-to-entity", permissionService.isValidPrincipalToEntity(new User(), new ProfileItem()));
    }

    @Test
    public void areNotValidPrincipalsToEntities() {
        assertFalse("Should not be a valid principal-to-entity", permissionService.isValidPrincipalToEntity(new DataItem(), new Profile()));
        assertFalse("Should not be a valid principal-to-entity", permissionService.isValidPrincipalToEntity(new User(), new APIVersion()));
    }
}