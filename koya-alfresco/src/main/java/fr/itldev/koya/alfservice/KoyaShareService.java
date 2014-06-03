package fr.itldev.koya.alfservice;

import fr.itldev.koya.exception.KoyaServiceException;
import fr.itldev.koya.model.SecuredItem;
import fr.itldev.koya.model.impl.Company;
import fr.itldev.koya.model.impl.Dossier;
import fr.itldev.koya.model.impl.Space;
import fr.itldev.koya.model.impl.User;
import fr.itldev.koya.model.json.SharingWrapper;
import java.util.ArrayList;
import java.util.List;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.AccessPermission;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.site.SiteInfo;
import org.apache.log4j.Logger;

public class KoyaShareService extends KoyaAclService {

    private Logger logger = Logger.getLogger(KoyaShareService.class);

    private SpaceService spaceService;
    private DossierService dossierService;

    public void setSpaceService(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    public void setDossierService(DossierService dossierService) {
        this.dossierService = dossierService;
    }

    /**
     *
     *
     * @param sharingWrapper
     * @throws KoyaServiceException
     */
    public void shareItems(SharingWrapper sharingWrapper) throws KoyaServiceException {

        List<SecuredItem> sharedItems = getSharedItems(sharingWrapper);

        //share elements to users specified by email
        for (String userMail : sharingWrapper.getSharingUsersMails()) {
            User u = null;

            try {
                u = userService.getUser(userMail);
            } catch (KoyaServiceException kex) {
                //do nothing if exception thrown
            }

            if (u == null) {

                break;//TODO replace with user creation process
                // u = createUserForSharing(sharedItems, userMail);//TODO user creation process
            }

            logger.debug("share " + sharedItems.size() + " elements to existing : " + u.getEmail());

            //give permissions to user on nodes
            for (SecuredItem si : sharedItems) {
                if (Dossier.class.isAssignableFrom(si.getClass())) {
                    grantDossierShare((Dossier) si, u);
                } else {
                    logger.error("Unsupported sharing type " + si.getClass().getSimpleName());
                }
            }

        }
    }

    public void unShareItems(SharingWrapper sharingWrapper) throws KoyaServiceException {

        List<SecuredItem> sharedItems = getSharedItems(sharingWrapper);

        //share elements to users specified by email
        for (String userMail : sharingWrapper.getSharingUsersMails()) {

            try {
                User u = userService.getUser(userMail);
                for (SecuredItem si : sharedItems) {
                    if (Dossier.class.isAssignableFrom(si.getClass())) {
                        revokeDossierShare((Dossier) si, u);
                    } else {
                        logger.error("Unsupported unsharing type " + si.getClass().getSimpleName());
                    }
                }
            } catch (KoyaServiceException kex) {
                //do nothing if exception thrown
            }
        }

    }

    /**
     * List all secured items shared with user on the system.
     *
     *
     * @param u
     * @return
     * @throws fr.itldev.koya.exception.KoyaServiceException
     */
    public List<SecuredItem> listItemsShared(User u) throws KoyaServiceException {
        List<SecuredItem> items = new ArrayList<>();
        for (SiteInfo si : siteService.listSites(u.getLogin())) {
            items.addAll(listItemsShared(u.getLogin(), si.getShortName()));
        }
        return items;
    }

    /**
     * List shared elements with specified user. ie user has Read permissions on
     * each element
     *
     * @param userName
     * @param companyName
     * @return
     * @throws fr.itldev.koya.exception.KoyaServiceException
     */
    public List<SecuredItem> listItemsShared(String userName, String companyName) throws KoyaServiceException {
        return listItemSharedRecursive(userName, spaceService.list(companyName, Integer.MAX_VALUE));
    }

    private List<SecuredItem> listItemSharedRecursive(String userName, List<Space> spaces) throws KoyaServiceException {
        List<SecuredItem> items = new ArrayList<>();

        for (Space s : spaces) {
            items.addAll(listItemSharedRecursive(userName, s.getChildSpaces()));
            //check if current space is shared with user as site consumer
            for (AccessPermission ap : permissionService.getAllSetPermissions(s.getNodeRefasObject())) {
                if (ap.getAuthority().equals(userName) && ap.getPermission().equals(PERMISSION_READ)) {
                    items.add(s);
                }
            }
                
            //check if current space children (ie dossiers) are shared with user as site consumer
            for (Dossier d : dossierService.list(s.getNodeRefasObject())) {
                for (AccessPermission ap : permissionService.getAllSetPermissions(d.getNodeRefasObject())) {
                    if (ap.getAuthority().equals(userName) && ap.getPermission().equals(PERMISSION_READ)) {
                        items.add(d);
                    }
                }
            }
        }
        return items;
    }

    /**
     * List all users who can access specified SecuredItem.
     *
     *
     * @param s
     * @return
     */
    public List<User> listUsersAccessShare(SecuredItem s) {
        return listUsersAccessShare(s.getNodeRefasObject());
    }

    /**
     * List all users who can access specified SecuredItem.
     *
     *
     * TODO add users who belong to groups listed by getAllAuthorities.
     * currently lists only public share access
     *
     * TODO check inherance possibilities
     *
     * @param n
     * @return
     */
    public List<User> listUsersAccessShare(NodeRef n) {
        List<User> users = new ArrayList<>();
        for (AccessPermission ap : permissionService.getAllSetPermissions(n)) {
            User u = userService.getUserByUsername(ap.getAuthority());
            if (u != null) {
                users.add(u);
            }
        }
        return users;
    }

    private User createUserForSharing(List<SecuredItem> sharedItems, String newUserMail) {
        logger.error("create user : " + newUserMail + " and share " + sharedItems.size() + " elements");
        //create user 
        //give permissions
        //send email

        return new User();//returns created user
    }

    private List<SecuredItem> getSharedItems(SharingWrapper sharingWrapper) {
        List<SecuredItem> sharedItems = new ArrayList<>();
        //extract shared elements
        for (String n : sharingWrapper.getSharedNodeRefs()) {
            try {
                sharedItems.add(koyaNodeService.nodeRef2SecuredItem(n));
            } catch (KoyaServiceException kex) {
                logger.error("Error creating element for sharing : " + kex.toString());
            }
        }
        return sharedItems;

    }

    /**
     *
     * ====== Dossier Specific sharing methods ===========
     *
     */
    private void grantDossierShare(Dossier dossier, User user) throws KoyaServiceException {
        grantShare(dossier, user.getLogin());
        for (SecuredItem si : koyaNodeService.getParentsList(dossier.getNodeRefasObject(), KoyaNodeService.NB_ANCESTOR_INFINTE)) {
            grantShare(si, user.getLogin());
        }
    }

    private void revokeDossierShare(Dossier dossier, User user) throws KoyaServiceException {
        revokeShare(dossier, user.getLogin());
        for (SecuredItem si : koyaNodeService.getParentsList(dossier.getNodeRefasObject(), KoyaNodeService.NB_ANCESTOR_INFINTE)) {
            if (listChildrenItemsShared(si, user.getLogin()).isEmpty()) {
                revokeShare(si, user.getLogin());
            } else {
                return;
            }
        }
    }

    /**
     * ======== Basic sharing specific methods =============
     *
     */
    /**
     *
     * @param si
     * @param userName
     */
    private void grantShare(SecuredItem si, String userName) {
        if (Company.class.isAssignableFrom(si.getClass())) {
            siteService.setMembership(si.getName(), userName, ROLE_SITE_CONSUMER);
        } else if (Space.class.isAssignableFrom(si.getClass())
                || Dossier.class.isAssignableFrom(si.getClass())) {
            permissionService.setPermission(si.getNodeRefasObject(), userName, PermissionService.READ, true);
        } else {
            //Nothing to do for other types
        }
    }

    /**
     *
     * @param si
     * @param userName
     */
    private void revokeShare(SecuredItem si, String userName) {
        if (Company.class.isAssignableFrom(si.getClass())) {
            siteService.removeMembership(si.getName(), userName);
        } else if (Space.class.isAssignableFrom(si.getClass())
                || Dossier.class.isAssignableFrom(si.getClass())) {
            permissionService.deletePermission(si.getNodeRefasObject(), userName, PermissionService.READ);
        } else {
            //Nothing to do for other types
        }
    }

    /**
     * Lists securedItem Childrens shared with user.
     *
     * Shared means user has specific read permission
     *
     * @param s
     * @param userName
     * @return
     */
    private List<SecuredItem> listChildrenItemsShared(final SecuredItem s, String userName) throws KoyaServiceException {

        return AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork< List<SecuredItem>>() {
            @Override
            public List<SecuredItem> doWork() throws Exception {
                List<SecuredItem> items = new ArrayList<>();
                if (Company.class.isAssignableFrom(s.getClass())) {
                    items.addAll(spaceService.list(s.getName(), 1));
                } else {
                    for (FileInfo fi : fileFolderService.list(s.getNodeRefasObject())) {
                        try {
                            items.add(koyaNodeService.nodeRef2SecuredItem(fi.getNodeRef()));
                        } catch (KoyaServiceException kex) {

                        }
                    }
                }
                return items;
            }
        }, userName);

    }

}
