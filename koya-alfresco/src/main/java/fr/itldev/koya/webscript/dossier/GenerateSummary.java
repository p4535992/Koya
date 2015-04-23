package fr.itldev.koya.webscript.dossier;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.transform.ContentTransformer;
import org.alfresco.repo.dictionary.RepositoryLocation;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.template.TemplateNode;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.repository.TemplateService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.namespace.NamespaceService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

import fr.itldev.koya.alfservice.KoyaNodeService;
import fr.itldev.koya.exception.KoyaServiceException;
import fr.itldev.koya.model.impl.Dossier;
import fr.itldev.koya.services.exceptions.KoyaErrorCodes;
import fr.itldev.koya.webscript.KoyaWebscript;

/**
 * This Service generates a PDF showing Dossier's repositories tree.
 * 
 * This document is placed on dossier root
 * 
 */
public class GenerateSummary extends AbstractWebScript implements
		InitializingBean {

	private final static String TPL_SUMMARY = "//app:company_home/app:dictionary/cm:koya/cm:templates/cm:dossier-summary.html.ftl";
	private final static String DEFAULT_DOCNAME = "dossier-summary";

	private KoyaNodeService koyaNodeService;
	private TemplateService templateService;
	private NamespaceService namespaceService;
	private FileFolderService fileFolderService;
	private SearchService searchService;
	private NodeService nodeService;
	protected ServiceRegistry serviceRegistry;
	private ContentService contentService;
	private VersionService versionService;

	private BehaviourFilter policyBehaviourFilter;

	public void setKoyaNodeService(KoyaNodeService koyaNodeService) {
		this.koyaNodeService = koyaNodeService;
	}

	public void setTemplateService(TemplateService templateService) {
		this.templateService = templateService;
	}

	public void setNamespaceService(NamespaceService namespaceService) {
		this.namespaceService = namespaceService;
	}

	public void setFileFolderService(FileFolderService fileFolderService) {
		this.fileFolderService = fileFolderService;
	}

	public void setSearchService(SearchService searchService) {
		this.searchService = searchService;
	}

	public void setNodeService(NodeService nodeService) {
		this.nodeService = nodeService;
	}

	public void setServiceRegistry(ServiceRegistry serviceRegistry) {
		this.serviceRegistry = serviceRegistry;
	}

	public void setContentService(ContentService contentService) {
		this.contentService = contentService;
	}

	public void setPolicyBehaviourFilter(BehaviourFilter policyBehaviourFilter) {
		this.policyBehaviourFilter = policyBehaviourFilter;
	}

	public void setVersionService(VersionService versionService) {
		this.versionService = versionService;
	}

	protected RepositoryLocation templateLocation;
	private Logger logger = Logger.getLogger(this.getClass());

	@Override
	public void afterPropertiesSet() throws Exception {
		templateLocation = new RepositoryLocation(
				StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, TPL_SUMMARY, "xpath");
	}

	@Override
	public void execute(WebScriptRequest req, WebScriptResponse res)
			throws IOException {
		Map<String, String> urlParams = KoyaWebscript.getUrlParamsMap(req);

		NodeRef htmlSummaryNodeRef;
		NodeRef pdfSummaryNodeRef;
		try {

			/**
			 * =================================================
			 * 
			 * Find Dossier
			 */
			Dossier d = koyaNodeService
					.getSecuredItem(koyaNodeService
							.getNodeRef((String) urlParams
									.get(KoyaWebscript.WSCONST_NODEREF)),
							Dossier.class);
			/**
			 * =================================================
			 * 
			 * Build Summary doc name
			 */
			String documentName = urlParams.get("documentName");
			if (documentName == null || documentName.isEmpty()) {
				documentName = DEFAULT_DOCNAME;
			}

			/**
			 * TODO generated document extra arguments : * types :
			 * pdf,html,xlsx,docx, etc
			 */

			/**
			 * =================================================
			 * 
			 * Get Localized Template
			 * 
			 * TODO try to read template from company settings if exists
			 * 
			 */
			List<NodeRef> nodeRefs = searchService.selectNodes(
					nodeService.getRootNode(templateLocation.getStoreRef()),
					templateLocation.getPath(), null, namespaceService, false);

			if (nodeRefs.isEmpty()) {
				throw new KoyaServiceException(
						KoyaErrorCodes.SUMMARY_TEMPLATE_NOT_FOUND);
			}
			NodeRef summarytemplate = fileFolderService
					.getLocalizedSibling(nodeRefs.get(0));
			if (summarytemplate == null) {
				throw new KoyaServiceException(
						KoyaErrorCodes.SUMMARY_TEMPLATE_NOT_FOUND);
			}

			/**
			 * =================================================
			 * 
			 * Generate summary html from template
			 */
			String htmlText = null;
			Map<String, Serializable> paramsTemplate = new HashMap<>();
			paramsTemplate.put("dossier",
					new TemplateNode(d.getNodeRefasObject(), serviceRegistry,
							null));
			try {
				htmlText = templateService.processTemplate("freemarker",
						summarytemplate.toString(), paramsTemplate);
			} catch (Exception templateEx) {
				throw new KoyaServiceException(
						KoyaErrorCodes.SUMMARY_TEMPLATE_PROCESS_ERROR);
			}

			/**
			 * ==================================================
			 * 
			 * Disable behaviours: last modification update on parent dossier
			 * mail sending
			 */

			policyBehaviourFilter
					.disableBehaviour(ContentModel.ASPECT_AUDITABLE);

			/**
			 * ===================================================
			 * 
			 * TODO Disable new content notification rule applied
			 * documentLibrary > no mail sent
			 * 
			 * 
			 * NodeRef should a different type that doesn't triggers
			 * notification
			 * 
			 */

			/**
			 * =================================================
			 * 
			 * Write html file
			 */

			htmlSummaryNodeRef = nodeService.getChildByName(
					d.getNodeRefasObject(), ContentModel.ASSOC_CONTAINS,
					documentName + ".html");

			if (htmlSummaryNodeRef == null) {
				htmlSummaryNodeRef = fileFolderService.create(
						d.getNodeRefasObject(), documentName + ".html",
						ContentModel.TYPE_CONTENT).getNodeRef();
			}

			ContentWriter fileWriter = fileFolderService
					.getWriter(htmlSummaryNodeRef);
			fileWriter.setEncoding("UTF-8");
			fileWriter.putContent(htmlText);
			// creates new revision
			versionService.createVersion(htmlSummaryNodeRef, null);

			/**
			 * =================================================
			 * 
			 * Convert html to pdf
			 */

			pdfSummaryNodeRef = nodeService.getChildByName(
					d.getNodeRefasObject(), ContentModel.ASSOC_CONTAINS,
					documentName + ".pdf");

			if (pdfSummaryNodeRef == null) {
				pdfSummaryNodeRef = fileFolderService.create(
						d.getNodeRefasObject(), documentName + ".pdf",
						ContentModel.TYPE_CONTENT).getNodeRef();
			}

			ContentReader htmlSummaryReader = fileFolderService
					.getReader(htmlSummaryNodeRef);
			htmlSummaryReader.setMimetype("text/html");

			ContentWriter pdfSummaryWriter = fileFolderService
					.getWriter(pdfSummaryNodeRef);
			pdfSummaryWriter.setEncoding("UTF-8");
			pdfSummaryWriter.setMimetype("application/pdf");
			
			ContentTransformer transformer = contentService.getTransformer("text/html", "application/pdf");
			logger.debug("convert html to pdf with :"+transformer.getName());
			//TODO use appropriate transformer
			transformer.transform(htmlSummaryReader, pdfSummaryWriter);
			// creates new revision
			versionService.createVersion(pdfSummaryNodeRef, null);
							
		} catch (KoyaServiceException ex) {
			throw new WebScriptException("KoyaError : "
					+ ex.getErrorCode().toString());
		}

		res.setContentEncoding("UTF-8");
		res.setContentType("application/json");

		/**
		 * Write summary nodeRefs on response
		 */
		Map<String, String> response = new HashMap<String, String>();
		if (htmlSummaryNodeRef != null) {
			response.put("htmlSummaryNodeRef", htmlSummaryNodeRef.toString());
		}
		if (pdfSummaryNodeRef != null) {
			response.put("pdfSummaryNodeRef", pdfSummaryNodeRef.toString());
		}
		res.getWriter().write(KoyaWebscript.getObjectAsJson(response));

	}
}