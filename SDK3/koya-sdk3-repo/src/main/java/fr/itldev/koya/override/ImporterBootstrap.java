package fr.itldev.koya.override;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;

import org.alfresco.error.AlfrescoRuntimeException;
import org.springframework.extensions.surf.util.I18NUtil;
import org.alfresco.repo.importer.ACPImportPackageHandler;
import org.alfresco.repo.importer.ImportTimerProgress;
import org.alfresco.repo.security.authentication.AuthenticationContext;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.security.authentication.AuthenticationUtil.RunAsWork;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.view.ImporterBinding;
import org.alfresco.service.cmr.view.ImporterContentCache;
import org.alfresco.service.cmr.view.ImporterException;
import org.alfresco.service.cmr.view.ImporterProgress;
import org.alfresco.service.cmr.view.ImporterService;
import org.alfresco.service.cmr.view.Location;
import org.alfresco.service.cmr.view.ImporterBinding.UUID_BINDING;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.springframework.extensions.surf.util.AbstractLifecycleBean;
import org.alfresco.util.PropertyCheck;
import org.alfresco.util.TempFileProvider;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.FileCopyUtils;

/**
 * Bootstrap Repository store.
 * 
 * @author David Caruana
 */
public class ImporterBootstrap implements InitializingBean//extends AbstractLifecycleBean
{
    // View Properties (used in setBootstrapViews)
    public static final String VIEW_PATH_PROPERTY = "path";
    public static final String VIEW_CHILDASSOCTYPE_PROPERTY = "childAssocType";
    public static final String VIEW_MESSAGES_PROPERTY = "messages";
    public static final String VIEW_LOCATION_VIEW = "location";
    public static final String VIEW_ENCODING = "encoding";
    public static final String VIEW_UUID_BINDING = "uuidBinding";
    
    // Logger
    private static final Log logger = LogFactory.getLog(ImporterBootstrap.class);
//    private boolean logEnabled = false;

    private boolean allowWrite = true;
    private boolean useExistingStore = false;
    private UUID_BINDING uuidBinding = null;
    // Dependencies
    private TransactionService transactionService;
    private RetryingTransactionHelper retryingTransactionHelper;
    private NamespaceService namespaceService;
    private NodeService nodeService;
    private ImporterService importerService;
    private List<Properties> bootstrapViews;
    private List<Properties> extensionBootstrapViews;
    private StoreRef storeRef = null;
    private List<String> mustNotExistStoreUrls = null;
    private Properties configuration = null;
    private String strLocale = null;
    private Locale locale = null;
    private AuthenticationContext authenticationContext;
    
    // Bootstrap performed?
    private boolean bootstrapPerformed = false;
    
    
    /**
     * Set whether we write or not
     * 
     * @param write true (default) if the import must go ahead, otherwise no import will occur
     */
    public void setAllowWrite(boolean write)
    {
        this.allowWrite = write;
    }

    /**
     * Set whether the importer bootstrap should only perform an import if the store
     * being referenced doesn't already exist.
     * 
     * @param useExistingStore true to allow imports into an existing store,
     *      otherwise false (default) to only import if the store doesn't exist.
     */
    public void setUseExistingStore(boolean useExistingStore)
    {
        this.useExistingStore = useExistingStore;
    }

    /**
     * Set the behaviour for generating UUIDs in the import.  Values are set by the
     * {@link UUID_BINDING} enum and default to {@link UUID_BINDING#CREATE_NEW_WITH_UUID}.
     * <p/>
     * This setting overrides the UUID binding behaviour specified in the view properties.
     * 
     * @param uuidBinding       the UUID generation behaviour
     */
    public void setUuidBinding(UUID_BINDING uuidBinding)
    {
        this.uuidBinding = uuidBinding;
    }    
    
    /**
     * Sets the Transaction Service
     * 
     * @param transactionService the transaction service
     */
    public void setTransactionService(TransactionService transactionService)
    {
        this.transactionService = transactionService;
    }

    /**
     * Sets the retrying transaction helper specific to the importer bootstrap. This transaction helper's parameters are
     * tuned to the longer-running import transaction.
     * 
     * @param retryingTransactionHelper
     *            the retrying transaction helper
     */
    public void setRetryingTransactionHelper(RetryingTransactionHelper retryingTransactionHelper)
    {
        this.retryingTransactionHelper = retryingTransactionHelper;
    }

    /**
     * Sets the namespace service
     * 
     * @param namespaceService the namespace service
     */
    public void setNamespaceService(NamespaceService namespaceService)
    {
        this.namespaceService = namespaceService;
    }

    /**
     * Sets the node service
     * 
     * @param nodeService the node service
     */
    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }

    /**
     * Sets the importer service
     * 
     * @param importerService the importer service
     */
    public void setImporterService(ImporterService importerService)
    {
        this.importerService = importerService;
    }
        
    /**
     * Set the authentication component
     * 
     * @param authenticationContext AuthenticationContext
     */
    public void setAuthenticationContext(AuthenticationContext authenticationContext)
    {
        this.authenticationContext = authenticationContext;
    }

    /**
     * Sets the bootstrap views
     * 
     */
    public void setBootstrapViews(List<Properties> bootstrapViews)
    {
        this.bootstrapViews = bootstrapViews;
    }

    /**
     * Sets the bootstrap views
     * 
     */
    public void addBootstrapViews(List<Properties> bootstrapViews)
    {
        if (this.extensionBootstrapViews == null)
        {
            this.extensionBootstrapViews = new ArrayList<Properties>();
        }
        this.extensionBootstrapViews.addAll(bootstrapViews);
    }

    /**
     * Sets the Store Ref to bootstrap into
     * 
     * @param storeUrl String
     */
    public void setStoreUrl(String storeUrl)
    {
        this.storeRef = new StoreRef(storeUrl);
    }

    /**
     * If any of the store urls exist, the bootstrap does not take place
     * 
     * @param storeUrls  the list of store urls to check
     */
    public void setMustNotExistStoreUrls(List<String> storeUrls)
    {
        this.mustNotExistStoreUrls = storeUrls;
    }
    
    /**
     * Gets the Store Reference
     * 
     * @return store reference
     */
    public StoreRef getStoreRef()
    {
        return this.storeRef;
    }
    
    /**
     * Sets the Configuration values for binding place holders
     * 
     * @param configuration Properties
     */
    public void setConfiguration(Properties configuration)
    {
        this.configuration = configuration;
    }

    /**
     * Gets the Configuration values for binding place holders
     * 
     * @return configuration
     */
    public Properties getConfiguration()
    {
        return configuration;
    }
    
    /**
     * Sets the Locale
     * 
     * @param locale  (language_country_variant)
     */
    public void setLocale(String locale)
    {
        // construct locale
        this.locale = I18NUtil.parseLocale(locale);
        
        // store original
        strLocale = locale;
    }

    /**
     * Get Locale
     * 
     * @return  locale
     */
    public String getLocale()
    {
        return strLocale; 
    }
    
    /**
     * @deprecated          Was never used
     */
    public void setLog(boolean logEnabled)
    {
        // Ignore
    }

    /**
     * Determine if bootstrap was performed?
     * 
     * @return  true => bootstrap was performed
     */
    public boolean hasPerformedBootstrap()
    {
        return bootstrapPerformed;
    }
    
    /**
     * Bootstrap the Repository
     */
    public void bootstrap()
    {
        PropertyCheck.mandatory(this, "transactionService", transactionService);
        PropertyCheck.mandatory(this, "retryingTransactionHelper", retryingTransactionHelper);
        PropertyCheck.mandatory(this, "namespaceService", namespaceService);
        PropertyCheck.mandatory(this, "nodeService", nodeService);
        PropertyCheck.mandatory(this, "importerService", importerService);
       
        if (storeRef == null)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("No Store URL - bootstrap import ignored");
            }
            return;
        }
        
        try
        {
            // import the content - note: in MT case, this will run in System context of tenant domain
            RunAsWork<Object> importRunAs = new RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    RetryingTransactionCallback<Object> doImportCallback = new RetryingTransactionCallback<Object>()
                    {
                        public Object execute() throws Throwable
                        {   
                            doImport();
                            return null;
                        }
                    };
                    return retryingTransactionHelper.doInTransaction(doImportCallback, transactionService.isReadOnly(), false);
                }
            };
            AuthenticationUtil.runAs(importRunAs, authenticationContext.getSystemUserName());
        }
        catch(Throwable e)
        {
            throw new AlfrescoRuntimeException("Bootstrap failed", e);
        }
    }
    
    /**
     * Perform the actual import work.  This is just separated to allow for simpler TXN demarcation.
     */
    private void doImport() throws Throwable
    {
        // check the repository exists, create if it doesn't
        if (!performBootstrap())
        {
            if (logger.isDebugEnabled())
                logger.debug("Store exists - bootstrap ignored: " + storeRef);
        }
        else if (!allowWrite)
        {
            // we're in read-only node
            logger.warn("Store does not exist, but mode is read-only: " + storeRef);
        }
        else
        {
            // create the store if necessary
            if (!nodeService.exists(storeRef))
            {
                storeRef = nodeService.createStore(storeRef.getProtocol(), storeRef.getIdentifier());
                if (logger.isDebugEnabled())
                    logger.debug("Created store: " + storeRef);
            }

            // bootstrap the store contents
            if (bootstrapViews != null)
            {
                // add-in any extended views
                if (extensionBootstrapViews != null)
                {
                    bootstrapViews.addAll(extensionBootstrapViews);
                }
                
                for (Properties bootstrapView : bootstrapViews)
                {
                    String view = bootstrapView.getProperty(VIEW_LOCATION_VIEW);
                    if (view == null || view.length() == 0)
                    {
                        throw new ImporterException("View file location must be provided");
                    }
                    String encoding = bootstrapView.getProperty(VIEW_ENCODING);
                    
                    // Create appropriate view reader
                    Reader viewReader = null;
                    ACPImportPackageHandler acpHandler = null; 
                    if (view.endsWith(".acp"))
                    {
                        File viewFile = getFile(view);
                        acpHandler = new ACPImportPackageHandler(viewFile, encoding);
                    }
                    else
                    {
                        viewReader = getReader(view, encoding);
                    }
                    
                    // Create import location
                    Location importLocation = new Location(storeRef);
                    String path = bootstrapView.getProperty(VIEW_PATH_PROPERTY);
                    if (path != null && path.length() > 0)
                    {
                        importLocation.setPath(path);
                    }
                    String childAssocType = bootstrapView.getProperty(VIEW_CHILDASSOCTYPE_PROPERTY);
                    if (childAssocType != null && childAssocType.length() > 0)
                    {
                        importLocation.setChildAssocType(QName.createQName(childAssocType, namespaceService));
                    }
                    
                    // Create import binding
                    BootstrapBinding binding = new BootstrapBinding();
                    binding.setConfiguration(configuration);
                    binding.setLocation(importLocation);
                    String messages = bootstrapView.getProperty(VIEW_MESSAGES_PROPERTY);
                    if (messages != null && messages.length() > 0)
                    {
                        Locale bindingLocale = (locale == null) ? I18NUtil.getLocale() : locale;
                        ResourceBundle bundle = ResourceBundle.getBundle(messages, bindingLocale);
                        binding.setResourceBundle(bundle);
                    }
                        
                    String viewUuidBinding = bootstrapView.getProperty(VIEW_UUID_BINDING);
                    if (viewUuidBinding != null && viewUuidBinding.length() > 0)
                    {
                        try
                        {
                        	binding.setUUIDBinding(UUID_BINDING.valueOf(UUID_BINDING.class, viewUuidBinding));
                        }
                        catch(IllegalArgumentException e)
                        {
                            throw new ImporterException("The value " + viewUuidBinding + " is an invalid uuidBinding");
                        }
                    }
                    // Override the UUID binding with the bean's
                    if (this.uuidBinding != null)
                    {
                        binding.setUUIDBinding(this.uuidBinding);
                    }

                    // Now import...
                    ImporterProgress importProgress = null;
                    if (logger.isDebugEnabled())
                    {
                        importProgress = new ImportTimerProgress(logger);
                        logger.debug("Importing " + view);
                    }
                    
                    if (viewReader != null)
                    {
                        importerService.importView(viewReader, importLocation, binding, importProgress);
                    }
                    else
                    {
                        importerService.importView(acpHandler, importLocation, binding, importProgress);
                    }
                }
            }
            
            // a bootstrap was performed
            bootstrapPerformed = !useExistingStore;
        }
    }
    
    /**
     * Get a Reader onto an XML view
     * 
     * @param view  the view location
     * @param encoding  the encoding of the view
     * @return  the reader
     */
    private Reader getReader(String view, String encoding)
    {
        // Get Input Stream
        ResourceLoader resourceLoader = new DefaultResourceLoader(getClass().getClassLoader());
        Resource viewResource = resourceLoader.getResource(view);
        if (viewResource == null)
        {
            throw new ImporterException("Could not find view file " + view);
        }

        // Wrap in buffered reader
        try
        {
            InputStream viewStream = viewResource.getInputStream();
            InputStreamReader inputReader = (encoding == null) ? new InputStreamReader(viewStream) : new InputStreamReader(viewStream, encoding);
            BufferedReader reader = new BufferedReader(inputReader);
            return reader;
        }
        catch (UnsupportedEncodingException e)
        {
            throw new ImporterException("Could not create reader for view " + view + " as encoding " + encoding + " is not supported");
        }
        catch (IOException e)
        {
            throw new ImporterException("Could not open resource for view " + view);
        }
    }
    
    /**
     * Get a File representation of an XML view
     * 
     * @param view  the view location
     * @return  the file
     */
    public static File getFile(String view)
    {
    	// Try as a file location
        File file = new File(view);
        if ((file != null) && (file.exists()))
        {
        	return file;
        }
        else
        {
            // Try as a classpath location
        	
	        // Get input stream
	        InputStream viewStream = ImporterBootstrap.class.getClassLoader().getResourceAsStream(view);
	        if (viewStream == null)
	        {
	            throw new ImporterException("Could not find view file " + view);
	        }
	        
	        // Create output stream
	        File tempFile = TempFileProvider.createTempFile("acpImport", ".tmp");
	        try
	        {
	            FileOutputStream os = new FileOutputStream(tempFile);
	            FileCopyUtils.copy(viewStream, os);
	        }
	        catch (FileNotFoundException e)
	        {
	            throw new ImporterException("Could not import view " + view, e);
	        }
	        catch (IOException e)
	        {
	            throw new ImporterException("Could not import view " + view, e);
	        }
	        return tempFile;
        }
    }
    
    
    /**
     * Bootstrap Binding
     */
    private static class BootstrapBinding implements ImporterBinding
    {
        private Properties configuration = null;
        private ResourceBundle resourceBundle = null;
        private Location bootstrapLocation = null;
        /** by default, use create new strategy for bootstrap import */
        private UUID_BINDING uuidBinding = UUID_BINDING.CREATE_NEW_WITH_UUID;
        
        private static final String IMPORT_LOCATION_UUID = "bootstrap.location.uuid";
        private static final String IMPORT_LOCATION_NODEREF = "bootstrap.location.noderef";
        private static final String IMPORT_LOCATION_PATH = "bootstrap.location.path";
        
        /**
         * Set Import Configuration
         * 
         * @param configuration Properties
         */
        public void setConfiguration(Properties configuration)
        {
            this.configuration = configuration;
        }

        /**
         * Get Import Configuration
         * 
         * @return  configuration
         */
        public Properties getConfiguration()
        {
            return this.configuration;
        }
        
        /**
         * Set Resource Bundle
         * 
         * @param resourceBundle ResourceBundle
         */
        public void setResourceBundle(ResourceBundle resourceBundle)
        {
            this.resourceBundle = resourceBundle;
        }
        
        /**
         * Set Location
         * 
         * @param location Location
         */
        public void setLocation(Location location)
        {
            this.bootstrapLocation = location;
        }
        
        /* (non-Javadoc)
         * @see org.alfresco.service.cmr.view.ImporterBinding#getValue(java.lang.String)
         */
        public String getValue(String key)
        {
            String value = null;
            if (configuration != null)
            {
                value = configuration.getProperty(key);
            }
            if (value == null && resourceBundle != null)
            {
                value = resourceBundle.getString(key);
            }
            if (value == null && bootstrapLocation != null)
            {
                if (key.equals(IMPORT_LOCATION_UUID))
                {
                    value = bootstrapLocation.getNodeRef().getId();
                }
                else if (key.equals(IMPORT_LOCATION_NODEREF))
                {
                    value = bootstrapLocation.getNodeRef().toString();
                }
                else if (key.equals(IMPORT_LOCATION_PATH))
                {
                    value = bootstrapLocation.getPath();
                }
            }
            
            return value;
        }

        @Override
        public UUID_BINDING getUUIDBinding()
        {
        	return uuidBinding;
        }

        private void setUUIDBinding(UUID_BINDING uuidBinding)
        {
        	this.uuidBinding = uuidBinding;
        }

        @Override
        public boolean allowReferenceWithinTransaction()
        {
            return true;
        }

        @Override
        public QName[] getExcludedClasses()
        {
            // Note: Do not exclude any classes, we want to import all
            return new QName[] {};
        }
        
        @Override
        public ImporterContentCache getImportConentCache()
        {
            return null;
        }
    }

    /**
     * Determine if bootstrap should take place
     * 
     * @return  true => yes, it should
     */
    private boolean performBootstrap()
    {
        if (useExistingStore)
        {
            // it doesn't matter if the store exists or not
            return true;
        }
        else if (nodeService.exists(storeRef))
        {
            return false;
        }
        else if (mustNotExistStoreUrls != null)
        {
            for (String storeUrl : mustNotExistStoreUrls)
            {
                StoreRef storeRef = new StoreRef(storeUrl);
                if (nodeService.exists(storeRef))
                {
                    return false;
                }
            }
        }
        
        return true;
    }

//    @Override
//    protected void onBootstrap(ApplicationEvent event)
//    {
//        bootstrap();
//    }
//
//    @Override
//    protected void onShutdown(ApplicationEvent event)
//    {
//        // NOOP
//    }

	@Override
	public void afterPropertiesSet() throws Exception {
		if(alfrescoGlobalProperties!=null && configuration!=null) {
			configuration.put("alfresco_user_store.adminusername", alfrescoGlobalProperties.getProperty("alfresco_user_store.adminusername"));
			configuration.put("alfresco_user_store.adminpassword", alfrescoGlobalProperties.getProperty("alfresco_user_store.adminpassword"));
			configuration.put("alfresco_user_store.adminsalt", alfrescoGlobalProperties.getProperty("alfresco_user_store.adminsalt"));
			configuration.put("alfresco_user_store.adminpassword2", alfrescoGlobalProperties.getProperty("alfresco_user_store.adminpassword2"));
			configuration.put("alfresco_user_store.system_container.childname", alfrescoGlobalProperties.getProperty("alfresco_user_store.system_container.childname"));
			configuration.put("alfresco_user_store.user_container.childname", alfrescoGlobalProperties.getProperty("alfresco_user_store.user_container.childname"));
		}
		bootstrap();		
	}
	
	Properties alfrescoGlobalProperties = null;


	public void setAlfrescoGlobalProperties(Properties alfrescoGlobalProperties) {
		this.alfrescoGlobalProperties = alfrescoGlobalProperties;
	}
	
	
    
}
