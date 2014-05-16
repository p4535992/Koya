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
package fr.itldev.koya.alfservice;

import fr.itldev.koya.exception.KoyaServiceException;
import fr.itldev.koya.model.Content;
import fr.itldev.koya.model.KoyaModel;
import fr.itldev.koya.model.SecuredItem;
import fr.itldev.koya.model.impl.Company;
import fr.itldev.koya.model.impl.Directory;
import fr.itldev.koya.model.impl.Document;
import fr.itldev.koya.model.impl.Dossier;
import fr.itldev.koya.model.impl.Space;
import fr.itldev.koya.services.exceptions.KoyaErrorCodes;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.favourites.FavouritesService;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.preference.PreferenceService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.InvalidNodeRefException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.namespace.QName;
import org.apache.log4j.Logger;

/**
 * Koya Nodes Util Service.
 */
public class KoyaNodeService {

    private static final String FAVOURITES_PREF_FOLDERS = "org.alfresco.share.folders.favourites";
    private static final String FAVOURITES_PREF_DOCS = "org.alfresco.share.documents.favourites";
    private static final String FAVOURITES_PREF_COMPANIES = "org.alfresco.share.sites.favourites";

    private Logger logger = Logger.getLogger(KoyaNodeService.class);

    private NodeService nodeService;
    private FavouritesService favouritesService;
    private PreferenceService preferenceService;
    private DictionaryService dictionaryService;
    private CompanyService companyService;
    private FileFolderService fileFolderService;
    private AuthenticationService authenticationService;
    private KoyaAclService koyaAclService;

    // <editor-fold defaultstate="collapsed" desc="getters/setters">
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setFavouritesService(FavouritesService favouritesService) {
        this.favouritesService = favouritesService;
    }

    public void setPreferenceService(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    public void setDictionaryService(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    public void setCompanyService(CompanyService companyService) {
        this.companyService = companyService;
    }

    public void setFileFolderService(FileFolderService fileFolderService) {
        this.fileFolderService = fileFolderService;
    }

    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public void setKoyaAclService(KoyaAclService koyaAclService) {
        this.koyaAclService = koyaAclService;
    }

    // </editor-fold>
    /**
     * ===== Activity Handling methods ==========.
     *
     */
    /**
     *
     * @param n
     * @param activeValue
     */
    public void setActiveStatus(NodeRef n, Boolean activeValue) {

        //TODO limit actions to activable nodes (check model)
        if (nodeService.hasAspect(n, KoyaModel.QNAME_KOYA_ACTIVABLE)) {
            //if node exists with activable aspect, update value.
            nodeService.setProperty(n, KoyaModel.QNAME_PROPASPECT_KOYA_ISACTIVE, activeValue);
        } else {
            //add aspect with value
            Map<QName, Serializable> props = new HashMap<>();
            props.put(KoyaModel.QNAME_PROPASPECT_KOYA_ISACTIVE, activeValue);
            nodeService.addAspect(n, KoyaModel.QNAME_KOYA_ACTIVABLE, props);
        }
    }

    /**
     *
     * Active Node has aspect KoyaModel.QNAME_KOYA_ACTIVABLE AND active property
     * is true.
     *
     * @param n
     * @return
     */
    public Boolean isActive(NodeRef n) {
        return nodeService.hasAspect(n, KoyaModel.QNAME_KOYA_ACTIVABLE)
                && (Boolean) nodeService.getProperty(n, KoyaModel.QNAME_PROPASPECT_KOYA_ISACTIVE);
    }

    /**
     * ====== Favourites Handling methods ======.
     *
     * TODO add a ehcache cache
     */
    /**
     *
     * @return @throws fr.itldev.koya.exception.KoyaServiceException
     */
    public List<SecuredItem> getFavourites() throws KoyaServiceException {

        List<SecuredItem> favourites = new ArrayList<>();

        Map<String, Serializable> prefs = preferenceService.getPreferences(authenticationService.getCurrentUserName());

        //favourites folders
        String foldersFavourites = (String) prefs.get(FAVOURITES_PREF_FOLDERS);

        if (foldersFavourites != null && !foldersFavourites.isEmpty()) {
            for (String favStr : foldersFavourites.split(",")) {
                try {
                    NodeRef n = new NodeRef(favStr);
                    //Build object according to type                
                    QName typeFav = nodeService.getType(n);

                    if (typeFav.equals(KoyaModel.QNAME_KOYA_SPACE)) {
                        favourites.add(nodeSpaceBuilder(n));
                    } else if (typeFav.equals(KoyaModel.QNAME_KOYA_DOSSIER)) {
                        favourites.add(nodeDossierBuilder(n));
                    } else {
                        favourites.add(nodeDirBuilder(n));
                    }

                } catch (InvalidNodeRefException e) {
                }
            }
        }
        //favourites documents
        String docsFavourites = (String) prefs.get(FAVOURITES_PREF_DOCS);
        if (docsFavourites != null && !docsFavourites.isEmpty()) {
            for (String favStr : docsFavourites.split(",")) {
                try {
                    NodeRef n = new NodeRef(favStr);
                    favourites.add(nodeDocumentBuilder(n));
                } catch (InvalidNodeRefException e) {
                }
            }
        }

        //favourites companies
        for (String k : prefs.keySet()) {
            if (k.startsWith(FAVOURITES_PREF_COMPANIES) && ((Boolean) prefs.get(k)).equals(Boolean.TRUE)) {

                String compName = k.substring(FAVOURITES_PREF_COMPANIES.length() + 1);
                try {
                    SiteInfo s = companyService.getSiteInfo(compName);
                    favourites.add(siteCompanyBuilder(s));
                } catch (Exception e) {//In case of non existant company
                }

            }
        }

        //TODO do it with favourites service ?
        //  PagingResults<PersonFavourite> favsPaged = favouritesService.getPagedFavourites(userName, null, null, null);
        return favourites;
    }

    /**
     * Change users favourite status for a node.
     *
     * @param item
     * @param status
     */
    public void setFavouriteStatus(NodeRef item, Boolean status) {
        if (status) {
            favouritesService.addFavourite(authenticationService.getCurrentUserName(), item);
        } else {
            favouritesService.removeFavourite(authenticationService.getCurrentUserName(), item);
        }
    }

    /**
     * Checks if node n is a favourite for current user
     *
     * @param n
     * @return
     */
    public Boolean isFavourite(NodeRef n) {
        return favouritesService.isFavourite(authenticationService.getCurrentUserName(), n);
    }

    /**
     *
     * ===== byte size methods =====.
     *
     */
    /**
     * Byte size getting method.
     *
     * returns Element size for Object.
     *
     * return recussive size for Containers Objects.
     *
     * @param n
     * @return
     */
    public Long getByteSize(NodeRef n) {

        try {
            FileInfo fi = fileFolderService.getFileInfo(n);

            if (!fi.isFolder()) {
                return fi.getContentData().getSize();
            } else {//return recusive size
                long size = 0;
                for (ChildAssociationRef car : nodeService.getChildAssocs(n)) {
                    size += getByteSize(car.getChildRef());
                }
                return size;
            }
        } catch (InvalidNodeRefException e) {
            return (long) 0;
        }
    }

    /**
     *
     * ==== Objects Builder Methods =====.
     *
     */
    /**
     *
     * Get typed SecuredItem from Noderef String.
     *
     * @param strNodeRef
     * @return
     * @throws fr.itldev.koya.exception.KoyaServiceException
     */
    public SecuredItem nodeRef2SecuredItem(String strNodeRef) throws KoyaServiceException {
        NodeRef nr = null;
        try {
            nr = new NodeRef(strNodeRef);
        } catch (InvalidNodeRefException ex) {
            throw new KoyaServiceException(KoyaErrorCodes.INVALID_NODEREF);
        }
        return nodeRef2SecuredItem(nr);
    }

    /**
     *
     * Get typed SecuredItem from Noderef
     *
     * @param nodeRef
     * @return
     * @throws fr.itldev.koya.exception.KoyaServiceException
     */
    public SecuredItem nodeRef2SecuredItem(NodeRef nodeRef) throws KoyaServiceException {
        SecuredItem si = null;
        QName type = nodeService.getType(nodeRef);
        if (type.equals(KoyaModel.QNAME_KOYA_COMPANY)) {
            si = nodeCompanyBuilder(nodeRef);
        } else if (type.equals(KoyaModel.QNAME_KOYA_SPACE)) {
            si = nodeSpaceBuilder(nodeRef);
        } else if (type.equals(KoyaModel.QNAME_KOYA_DOSSIER)) {
            si = nodeDossierBuilder(nodeRef);
        } else if (type.equals(ContentModel.TYPE_FOLDER)) {
            si = nodeDirBuilder(nodeRef);
        } else if (type.equals(ContentModel.TYPE_CONTENT)) {
            si = nodeDocumentBuilder(nodeRef);
        } else {
            logger.warn("Invalid noderef type (" + type + ") given for sharing  : ignored");
            throw new KoyaServiceException(KoyaErrorCodes.INVALID_SECUREDITEM_NODEREF);
        }
        return si;
    }

    /**
     *
     * @param s
     * @return
     */
    public Company siteCompanyBuilder(SiteInfo s) {
        Company c = new Company(s);
        c.setUserFavourite(isFavourite(c.getNodeRefasObject()));
        c.setShared(koyaAclService.listUsersAccess(c).size() > 0);
        return c;
    }

    /**
     *
     * @param n
     * @return
     */
    public Company nodeCompanyBuilder(NodeRef n) {
        return siteCompanyBuilder(companyService.getSiteInfo(n));
    }

    /**
     *
     * @param spaceNodeRef
     * @return
     * @throws fr.itldev.koya.exception.KoyaServiceException
     */
    public Space nodeSpaceBuilder(NodeRef spaceNodeRef) throws KoyaServiceException {
        Space e = new Space();
        e.setNodeRef(spaceNodeRef.toString());
        e.setName((String) nodeService.getProperty(spaceNodeRef, ContentModel.PROP_NAME));

        //parent node ref definition
        NodeRef directParent = nodeService.getPrimaryParent(spaceNodeRef).getParentRef();
        NodeRef realParent = null;

        if (nodeService.getType(directParent).equals(KoyaModel.QNAME_KOYA_SPACE)) {
            realParent = directParent;
        } else if (nodeService.getProperty(directParent, ContentModel.PROP_NAME).equals(SpaceService.DOCLIB_NAME)) {
            //parent's parent
            realParent = nodeService.getPrimaryParent(directParent).getParentRef();
        } else {
            throw new KoyaServiceException(KoyaErrorCodes.SPACE_INVALID_PARENT);
        }

        //activity status
        e.setActive(isActive(spaceNodeRef));

        e.setUserFavourite(isFavourite(spaceNodeRef));
        e.setShared(koyaAclService.listUsersAccess(e).size() > 0);

        return e;
    }

    /**
     *
     * @param dossierNodeRef
     * @return
     */
    public Dossier nodeDossierBuilder(NodeRef dossierNodeRef) {
        Dossier c = new Dossier();

        c.setNodeRef(dossierNodeRef.toString());
        c.setName((String) nodeService.getProperty(dossierNodeRef, ContentModel.PROP_NAME));

        NodeRef directParent = nodeService.getPrimaryParent(dossierNodeRef).getParentRef();
        NodeRef realParent = null;
        if (nodeService.getType(directParent).equals(KoyaModel.QNAME_KOYA_SPACE)) {
            realParent = directParent;
        } else {
            logger.warn("Error in space parent hierarchy");
            //TODO exception      
        }
        c.setLastModifiedDate((Date) nodeService.getProperty(dossierNodeRef, ContentModel.PROP_MODIFIED));
        c.setActive(isActive(dossierNodeRef));

        c.setUserFavourite(isFavourite(dossierNodeRef));
        c.setShared(koyaAclService.listUsersAccess(c).size() > 0);

        return c;
    }

    /**
     * Builds Content according to the type.
     *
     * @param nodeRef
     * @return
     */
    public Content nodeContentBuilder(NodeRef nodeRef) {
        if (nodeIsFolder(nodeRef)) {
            return nodeDirBuilder(nodeRef);
        } else {
            return nodeDocumentBuilder(nodeRef);
        }
    }

    /**
     *
     * @param dirNodeRef
     * @return
     */
    public Directory nodeDirBuilder(NodeRef dirNodeRef) {
        Directory r = new Directory();
        r.setNodeRef(dirNodeRef.toString());
        r.setName((String) nodeService.getProperty(dirNodeRef, ContentModel.PROP_NAME));
        r.setUserFavourite(isFavourite(dirNodeRef));

        //not used
        r.setShared(Boolean.FALSE);
        return r;
    }

    /**
     *
     * @param docNodeRef
     * @return
     */
    public Document nodeDocumentBuilder(NodeRef docNodeRef) {
        Document d = new Document();
        d.setNodeRef(docNodeRef.toString());
        d.setName((String) nodeService.getProperty(docNodeRef, ContentModel.PROP_NAME));
        d.setUserFavourite(isFavourite(docNodeRef));
        d.setByteSize(getByteSize(docNodeRef));
        //not used
        d.setShared(Boolean.FALSE);

        return d;
    }

    /**
     *
     * ===== Type checking methods ======
     *
     */
    /**
     * Return true if node is type ContentModel.TYPE_FOLDER or subtype.
     *
     *
     * @param nodeRef
     * @return
     */
    public Boolean nodeIsFolder(NodeRef nodeRef) {
        QName qNameType = nodeService.getType(nodeRef);
        return qNameType.equals(ContentModel.TYPE_FOLDER)
                || (dictionaryService.isSubClass(qNameType, ContentModel.TYPE_FOLDER));
    }

    /**
     * Koya company is a site with activable aspect.
     *
     * @param n
     * @return
     */
    public Boolean isKoyaCompany(NodeRef n) {

        return nodeService.getType(n).equals(KoyaModel.QNAME_KOYA_COMPANY)
                && nodeService.hasAspect(n, KoyaModel.QNAME_KOYA_ACTIVABLE);
    }

    /**
     *
     * ==== Global secured item methods =====.
     *
     */
    /**
     *
     * @param n
     * @param newName
     * @return
     */
    public SecuredItem rename(NodeRef n, String newName) {
        //todo check new name validity and user acl
        nodeService.setProperty(n, ContentModel.PROP_NAME, newName);
        return null;

    }

    /**
     *
     * @param n
     * @throws KoyaServiceException
     */
    public void delete(NodeRef n) throws KoyaServiceException {
        //todo check user acl to delete node.
        nodeService.deleteNode(n);

    }

    /**
     *
     *
     */
    /**
     * Return parent nodes list.
     *
     * @param s
     * @param userName
     * @return
     * @throws fr.itldev.koya.exception.KoyaServiceException
     */
    public List<SecuredItem> getParentsList(SecuredItem s) throws KoyaServiceException {
        List<SecuredItem> parents = new ArrayList<>();

        if (s.getClass().isAssignableFrom(Company.class)) {
            parents.add(s);
            return parents;
        } else {

            NodeRef nrParent = nodeService.getPrimaryParent(s.getNodeRefasObject()).getParentRef();

            if (s.getClass().isAssignableFrom(Space.class)) {

                //parent can be a space or documentLibrary node (child of company)
                if (nodeService.getType(nrParent).equals(KoyaModel.QNAME_KOYA_SPACE)) {

                    Space sParent = nodeSpaceBuilder(nrParent);
                    parents.add(sParent);
                    parents.addAll(getParentsList(sParent));
                } else if (nodeService.getProperty(nrParent, ContentModel.PROP_NAME).equals(SpaceService.DOCLIB_NAME)) {
                    Company c = nodeCompanyBuilder(nodeService.getPrimaryParent(nrParent).getParentRef());
                    //do not add company -> done by recursive end condition.
                    parents.addAll(getParentsList(c));
                }

            } else if (s.getClass().isAssignableFrom(Dossier.class)) {
                Space sParent = nodeSpaceBuilder(nrParent);
                parents.add(sParent);
                //Dossier parent must be a Space
                parents.addAll(getParentsList(sParent));

            } else if (s.getClass().isAssignableFrom(Content.class)) {
                //parent can be a Directory or Dossier                
                if (nodeService.getType(nrParent).equals(KoyaModel.QNAME_KOYA_DOSSIER)) {

                    Dossier dParent = nodeDossierBuilder(nrParent);
                    parents.add(dParent);

                    parents.addAll(getParentsList(dParent));
                } else {
                    Directory dirParent = nodeDirBuilder(nrParent);
                    parents.add(dirParent);
                    parents.addAll(getParentsList(dirParent));
                }
            }
        }
        return parents;
    }

    /**
     * Returns company whose node belongs to. Null is node is not a comapny
     * child
     *
     *
     * @param n
     * @return
     */
    public Company getNodeCompany(NodeRef n) {
        if (n == null) {
            return null;
        } else if (nodeService.getType(n).equals(KoyaModel.QNAME_KOYA_COMPANY)) {
            return nodeCompanyBuilder(n);
        } else {
            return getNodeCompany(nodeService.getPrimaryParent(n).getParentRef());
        }
    }

}
