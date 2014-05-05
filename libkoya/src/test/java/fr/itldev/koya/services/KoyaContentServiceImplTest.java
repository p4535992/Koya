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

import fr.itldev.koya.model.Content;
import fr.itldev.koya.model.impl.Document;
import fr.itldev.koya.model.impl.Dossier;
import fr.itldev.koya.model.impl.Space;
import fr.itldev.koya.model.impl.Directory;
import fr.itldev.koya.model.impl.Company;
import fr.itldev.koya.model.impl.User;
import fr.itldev.koya.services.exceptions.AlfrescoServiceException;
import java.util.List;
import java.util.Random;
import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestClientException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:koya-services-tests.xml")
public class KoyaContentServiceImplTest extends TestCase {

    private Logger logger = Logger.getLogger(this.getClass());

    @Autowired
    UserService userService;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private SpaceService spaceService;

    @Autowired
    private DossierService dossierService;

    @Autowired
    private KoyaContentService koyaContentService;

    @Autowired
    private ApplicationContext applicationContext;

    private Company companyTests;
    private Space spaceTests;
    private Dossier dossierTests;
    private User admin;

    @Before
    public void createSpace() throws RestClientException, AlfrescoServiceException {
        admin = userService.login("admin", "admin");
        companyTests = companyService.create(admin, new Company("societe" + new Random().nextInt(1000), companyService.listSalesOffer(admin).get(0)), "default");
        spaceTests = spaceService.create(admin, new Space("Esptests", companyTests));
        dossierTests = dossierService.create(admin, new Dossier("doss1", spaceTests));
    }

    @After
    public void deleteCompany() throws RestClientException, AlfrescoServiceException {
        companyService.delete(admin, companyTests);
    }

    @Test
    public void testCreateDir() throws AlfrescoServiceException {

        Content rep = koyaContentService.create(admin, new Directory("rep", dossierTests));
        assertNotNull("erreur de creation du répertoire", rep);
        koyaContentService.delete(admin, rep);
    }

    @Test
    public void testListDir() throws AlfrescoServiceException {
        Content rep = koyaContentService.create(admin, new Directory("rep", dossierTests));
        koyaContentService.create(admin, new Directory("sousrep", (Directory) rep));
        koyaContentService.create(admin, new Directory("rep2", dossierTests));

        List<Content> lst = koyaContentService.list(admin, dossierTests);

        assertEquals(2, lst.size());

    }

    @Test
    public void testListDirectChildren() throws AlfrescoServiceException {
        Content rep = koyaContentService.create(admin, new Directory("rep", dossierTests));
        koyaContentService.create(admin, new Directory("sousrep", (Directory) rep));
        koyaContentService.create(admin, new Directory("rep2", dossierTests));

        Resource toUpload = applicationContext.getResource("classpath:docs/testupload.txt");
        koyaContentService.upload(admin, toUpload, dossierTests);

        List<Content> lst = koyaContentService.list(admin, dossierTests, 2);

        assertEquals(3, lst.size());

        assertEquals(1, koyaContentService.list(admin, (Directory) rep, 2).size());

    }

    @Test
    public void testUpload() throws AlfrescoServiceException {

        Integer sizeBefore = koyaContentService.list(admin, dossierTests).size();

        Resource toUpload = applicationContext.getResource("classpath:docs/testupload.txt");
        koyaContentService.upload(admin, toUpload, dossierTests);

        List<Content> lstC = koyaContentService.list(admin, dossierTests);
        //il doit y avoir un element en plus
        assertEquals(sizeBefore + 1, lstC.size());

    }

    @Test
    public void testMoveFile() throws AlfrescoServiceException {

        Directory rep = (Directory) koyaContentService.create(admin, new Directory("rep1", dossierTests));

        Integer sizeBefore = koyaContentService.list(admin, dossierTests).size();

        Resource toUpload = applicationContext.getResource("classpath:docs/testupload.txt");
        Document doc = koyaContentService.upload(admin, toUpload, rep);

        assertEquals(sizeBefore.intValue(), koyaContentService.list(admin, dossierTests).size());

        //deplacement du doc dans la racine
        koyaContentService.move(admin, doc, dossierTests);

        //il doit y avoir un element en plus
        //TODO pas probant car la liste retourne l'ensemble des elements recursivement
        //et donc ca ne change rien .... TODO idem avec structure hiérarchique
        assertEquals(sizeBefore + 1, koyaContentService.list(admin, dossierTests).size());

    }

    @Test
    public void testGetParent() throws AlfrescoServiceException {
        Content rep3 = koyaContentService.create(admin, new Directory("rep3", dossierTests));
        Content srep = koyaContentService.create(admin, new Directory("sousrep", (Directory) rep3));

        assertEquals(rep3, koyaContentService.getParent(admin, srep));
    }

    @Test
    public void testGetParentDoc() throws AlfrescoServiceException {

        Resource toUpload = applicationContext.getResource("classpath:docs/testupload.txt");
        Document doc = koyaContentService.upload(admin, toUpload, dossierTests);

        assertEquals(dossierTests, koyaContentService.getParent(admin, doc));
    }

    @Test
    public void testDocByteSize() throws AlfrescoServiceException {

        Resource toUpload = applicationContext.getResource("classpath:docs/testupload.txt");
        Document doc = koyaContentService.upload(admin, toUpload, dossierTests);

        assertEquals(new Long(854), doc.getByteSize());
    }

    @Test
    public void testDirDiskSize() throws AlfrescoServiceException {

        Directory rep4 = (Directory) koyaContentService.create(admin, new Directory("rep4", dossierTests));

        Directory rep5 = (Directory) koyaContentService.create(admin, new Directory("rep5", rep4));

        Resource toUpload = applicationContext.getResource("classpath:docs/testupload.txt");
        Document doc1 = koyaContentService.upload(admin, toUpload, rep4);

        Document doc2 = koyaContentService.upload(admin, toUpload, rep5);

        assertEquals(new Long(854 * 2), koyaContentService.getDiskSize(admin, rep4));
    }

    @Test
    public void testDeleteDir() throws AlfrescoServiceException {

        int nbDir = koyaContentService.list(admin, dossierTests).size();
        Directory repdel = (Directory) koyaContentService.create(admin, new Directory("repdel", dossierTests));

        assertEquals(nbDir + 1, koyaContentService.list(admin, dossierTests).size());

        koyaContentService.delete(admin, repdel);

        assertEquals(nbDir, koyaContentService.list(admin, dossierTests).size());
    }

    @Test
    public void testDeleteContent() throws AlfrescoServiceException {

        int nbDir = koyaContentService.list(admin, dossierTests).size();

        Resource toUpload = applicationContext.getResource("classpath:docs/testupload.txt");
        Document upDoc = koyaContentService.upload(admin, toUpload, dossierTests);

        assertEquals(nbDir + 1, koyaContentService.list(admin, dossierTests).size());

        koyaContentService.delete(admin, upDoc);

        assertEquals(nbDir, koyaContentService.list(admin, dossierTests).size());
    }

    @Test
    public void testRenameDir() throws AlfrescoServiceException {

        Directory repRename = (Directory) koyaContentService.create(admin, new Directory("oldName", dossierTests));

        for (Content c : koyaContentService.list(admin, dossierTests)) {
            if (c.getName().equals("newName")) {
                fail();
            }
        }
        koyaContentService.rename(admin, repRename, "newName");

        for (Content c : koyaContentService.list(admin, dossierTests)) {
            if (c.getName().equals("oldName")) {
                fail();
            }
        }

    }

    @Test
    public void testRenameContent() throws AlfrescoServiceException {

        Resource toUpload = applicationContext.getResource("classpath:docs/testupload.txt");
        Document upDoc = koyaContentService.upload(admin, toUpload, dossierTests);

        for (Content c : koyaContentService.list(admin, dossierTests)) {
            if (c.getName().equals("newName")) {
                fail();
            }
        }
        koyaContentService.rename(admin, upDoc, "newName");

        for (Content c : koyaContentService.list(admin, dossierTests)) {
            if (c.getName().equals("testupload.txt")) {
                fail();
            }
        }

    }

}
