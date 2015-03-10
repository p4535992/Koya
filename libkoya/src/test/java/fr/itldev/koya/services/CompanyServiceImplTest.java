/**
 * Koya is an alfresco module that provides a corporate orientated dataroom.
 *
 * Copyright (C) Itl Developpement 2014
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see `<http://www.gnu.org/licenses/>`.
 */
package fr.itldev.koya.services;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import junit.framework.TestCase;

import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestClientException;

import fr.itldev.koya.model.impl.Company;
import fr.itldev.koya.model.impl.CompanyProperties;
import fr.itldev.koya.model.impl.Contact;
import fr.itldev.koya.model.impl.ContactItem;
import fr.itldev.koya.model.impl.Preferences;
import fr.itldev.koya.model.impl.SalesOffer;
import fr.itldev.koya.model.impl.User;
import fr.itldev.koya.services.exceptions.AlfrescoServiceException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:koya-services-tests.xml")
public class CompanyServiceImplTest extends TestCase {

    private Logger logger = Logger.getLogger(this.getClass());
    private User admin;
    @Autowired
    UserService userService;
    @Autowired
    private CompanyService companyService;

    @Before
    public void createUser() throws RestClientException,
            AlfrescoServiceException {
        admin = userService.login("admin", "admin");
    }

    /**
     * Suppression des eventuels relicats
     * 
     * @throws fr.itldev.koya.services.exceptions.AlfrescoServiceException
     */
    @After
    public void deleteAll() throws RestClientException,
            AlfrescoServiceException {
//        for (Company s : companyService.list(admin)) {
//            companyService.delete(admin, s);
//        }
    }

    @Test
    public void testListSalesOffer() throws RestClientException,
            AlfrescoServiceException {
        List<SalesOffer> offresCom = companyService.listSalesOffer(admin);
        assertTrue(offresCom.size() > 0);
    }

    @Test
    public void testCreateCompany() throws RestClientException,
            AlfrescoServiceException {
        List<SalesOffer> offresCom = companyService.listSalesOffer(admin);
        assertTrue(offresCom.size() > 0);
        SalesOffer sel = offresCom.get(0);

        Company created = companyService.create(admin, "company_"
                + new Random().nextInt(1000), sel.getName(), "default");
        assertNotNull(created);
    }

    @Test
    public void testListCompanies() throws RestClientException,
            AlfrescoServiceException {
        List<SalesOffer> offresCom = companyService.listSalesOffer(admin);
        assertTrue(offresCom.size() > 0);
        SalesOffer sel = offresCom.get(0);

        Random r = new Random();
        Company created = companyService.create(admin, "company_"
                + new Random().nextInt(1000), sel.getName(), "default");

        List<Company> lst = companyService.list(admin);
        assertTrue(lst.size() > 0);
    }

    @Test
    public void testDelAllCompanies() throws RestClientException,
            AlfrescoServiceException {
        for (Company s : companyService.list(admin)) {
            companyService.delete(admin, s);
        }
        assertEquals(companyService.list(admin).size(), 0);
    }

    //@Test
    public void testGetPrefs() throws IOException, AlfrescoServiceException {

        List<SalesOffer> offresCom = companyService.listSalesOffer(admin);
        assertTrue(offresCom.size() > 0);
        SalesOffer sel = offresCom.get(0);

        Company created = companyService.create(admin, "company_"
                + new Random().nextInt(1000), sel.getName(), "default");

        System.out.println("comp = " + created);

        Preferences p = companyService.getPreferences(admin, created);

        assertTrue(p.size() == 0);
    }

    @Test
    public void testSetPrefs() throws IOException, AlfrescoServiceException {

        List<SalesOffer> offresCom = companyService.listSalesOffer(admin);
        assertTrue(offresCom.size() > 0);
        SalesOffer sel = offresCom.get(0);

        Company created = companyService.create(admin, "company_"
                + new Random().nextInt(1000), sel.getName(), "default");
        Preferences p = companyService.getPreferences(admin, created);
        String testKey = "fr.itldev.test";

        int nbPrefs = p.size();

        /* =============== Add a test preference =========== */
        p.put(testKey, "OK_PREF");
        companyService.commitPreferences(admin, created, p);

        Preferences p2 = companyService.getPreferences(admin, created);

        // one more preference
        assertEquals(nbPrefs + 1, p2.size());

        /* =============== Del test preference ===== */
        p2.remove(testKey);
        companyService.commitPreferences(admin, created, p2);
        Preferences p3 = companyService.getPreferences(admin, created);
        assertEquals(nbPrefs, p3.size());

    } 
    @Test
    public void testcommitProperties() throws IOException,
            AlfrescoServiceException {
        List<SalesOffer> offresCom = companyService.listSalesOffer(admin);
        SalesOffer sel = offresCom.get(0);

        Company created = companyService.create(admin, "company_"
                + new Random().nextInt(1000), sel.getName(), "default");

        
        CompanyProperties cp = companyService.getProperties(admin, created);
                
        cp.setAddress("adress1");
        cp.setAddress2("adress2");
        cp.setCity("alfresco city");
        cp.setZipCode("zip");
        
        ContactItem c = ContactItem.newInstance(ContactItem.TYPE_MAIL, "x@y");
        cp.getContactItems().add(c);       
        ContactItem c2 = ContactItem.newInstance(ContactItem.TYPE_TEL, "0000000");
        cp.getContactItems().add(c2);
       
        
        //get user admin person noderef
        
        Contact co1 = Contact.newInstance(admin);        
        ContactItem cc1 = ContactItem.newInstance(ContactItem.TYPE_TEL, "tel admin");
        co1.getContactItems().add(cc1);
        
        
        cp.getContacts().add(co1);
        
        companyService.commitProperties(admin, created, cp);
        
        companyService.getProperties(admin, created);
        
    }
}
