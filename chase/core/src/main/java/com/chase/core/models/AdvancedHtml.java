package com.chase.core.models;

import com.day.cq.wcm.api.AuthoringUIMode;
import com.day.cq.wcm.api.AuthoringUIModeService;
import com.day.cq.wcm.api.WCMMode;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Optional;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.apache.sling.settings.SlingSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTML.Tag;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTMLWriter;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Model(adaptables = { SlingHttpServletRequest.class })
public class AdvancedHtml {
	
	private static final Logger log = LoggerFactory.getLogger(AdvancedHtml.class);
	
	public static final String APPROVE_FLAG_PROP = "advancedHtmlApprovedFlag";
	
	public static final String REJECT_REASON_PROP = "advancedHTMLRejectedReason";
	
	public static final String DEFAULT_REJECT_REASON = "This component has not been reviewed.";
	
	public static final String NOT_APPROVED_MESSAGE = "This Advanced HTML component has not been approved by development team and will not be displayed on a published page.";
	public static final String INVALID_TAGS_MESSAGE = "<P>Unable to validate component. Please make sure it contains well formed HTML5 and no &lt;style&gt; or &lt;script&gt; tags before activating this page.</P>";
	public static final String PARSER_ERROR_MESSAGE = "<P>Unable to validate component due to unexpected parser errors. Please contact CMS administrator.</P>";
	
	private static final HTML.Tag INVALID_HTML_TAGS[] = {
		HTML.Tag.STYLE,
		HTML.Tag.SCRIPT
	};

	@ValueMapValue @Optional @Default(values="")
	private String text;
	
	@ValueMapValue @Optional @Default(values="")
	private String inlineScript;
	
	@ValueMapValue @Optional @Default(values={})
	private String scriptPaths[] = {};
	
	@ValueMapValue @Optional @Default(values="")
	private String mobileCss;
	
	@ValueMapValue @Optional @Default(values="")
	private String desktopCss;
	
	@ValueMapValue @Optional @Default(values="")
	private String allCss;
	
	@Self
	private SlingHttpServletRequest request;
	
	@OSGiService
	private AuthoringUIModeService uiModeService;

	@Inject
	private SlingSettingsService slingSettingsService;
	
	private String cssClass = "";

	private String errorMessage = null;
	
	private boolean valid = true;
	
	private boolean showError = false;
	
	@PostConstruct
	protected void activate() {
		log.debug("activate()");
		
		// Set up placeholder if empty
		if(text==null || text.trim().length()==0) {
			this.cssClass = isTouch()
	                ? "cq-placeholder"
	                : "cq-text-placeholder-ipe";

	        // display placeholder if empty
	        if(isEdit()) {
	        	this.text = isTouch() ? "" : "Edit text";
	        } else {
	        	this.text = "";
	        } 
		} else if(!isApproved()) {
			if(isPublished()) {
				// Component is not approved. We don't display it if published.
				String path = "";
				if(request!=null && request.getResource() !=null) {
					path = this.request.getResource().getPath();
				}
				log.warn("Unapproved Adavanced HTML component {} has been published but will not be rendered.", path);
				this.text = null;
			}
		}
	}
	
	@PostConstruct
	protected void validate() {
		log.debug("validate()");
		
		if(text==null || text.trim().length()<1)
			return;
		
		boolean isEdit = isEdit();
		boolean modify = !isEdit;
		
		HTMLEditorKit.Parser parser = new ParserDelegator();
		ParserCallback callback = new ParserCallback();
        
		StringBuilder errors = new StringBuilder();
		
		if(!isApproved()) {
			errors.append("<P>").append(NOT_APPROVED_MESSAGE).append(' ').append(getReason()).append("</P>");
		}
		
		try {
			
			InputStream in = new java.io.ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
			log.debug("Parsing document");
			parser.parse(new InputStreamReader(in), callback, false);
						
			if(callback.hasInvalid) {
				this.valid = false;
				if(modify) {
					this.text = removeInvalidElements();
				}
			}
			
		} catch (IOException e) {
			log.error("Unable to validate advancedhtml text.", e);
			errors.append(PARSER_ERROR_MESSAGE);
		} catch (BadLocationException e) {
			log.error("Unable to validate advancedhtml text.", e);
			errors.append(PARSER_ERROR_MESSAGE);
		} finally {
			
			if(!this.valid) {
				errors.append(INVALID_TAGS_MESSAGE);
			}
			
			if(errors.length()>0 && isEdit) {
				this.errorMessage = errors.toString();
				showError = true;
			}
			
		}
		
	}
	
	private int[] getDocumentOffsets(HTMLDocument doc) {
		int ret[] = null;
		
		Element e = doc.getElement(doc.getDefaultRootElement(),StyleConstants.NameAttribute, HTML.Tag.BODY);
		if(e!=null) {
			ret = new int[] {e.getStartOffset(), e.getEndOffset()};
		} else {
			ret = new int[] {0, doc.getLength()};
		}
		
		return ret;
	}
	
	private String removeInvalidElements() throws IOException, BadLocationException {
		String html = text;
		boolean modified = false;
		
		HTMLDocument doc = new HTMLDocument();
		HTMLEditorKit kit = new HTMLEditorKit();
		InputStream in = new java.io.ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
		kit.read(in, doc, 0);
		
		for(HTML.Tag tag : INVALID_HTML_TAGS) {
			HTMLDocument.Iterator it = doc.getIterator(tag);
			
			while(it!=null && it.isValid()) {
				int pos = it.getStartOffset();
				log.debug("Removing invalid tag {} at pos={}", tag, pos);
				Element elem = doc.getParagraphElement(pos);
				doc.removeElement(elem);
				it.next();
				modified = true;
			}
		}
		
		if(modified) {
			int pos[] = getDocumentOffsets(doc);
			StringWriter out = new StringWriter();
			HTMLWriter writer = new HTMLWriter(out, doc, pos[0], pos[1]);
			writer.write();
			html = out.toString();
			html = removeExtraTags(html);
		}
		
		return html;
	}
	
	private String removeExtraTags(String text) {
		String txt = text;
		
		String open = "<body>";
		String close = "</body>";		
		int beginIndex =  txt.indexOf(open);
		if(beginIndex>-1) {
			beginIndex = beginIndex + open.length();
			int endIndex = txt.indexOf(close);
			if(endIndex>-1) {
				txt = txt.substring(beginIndex, endIndex);
			} else {
				txt = txt.substring(beginIndex);
			}
		}
				
		return txt;		
	}
	
	private String getReason() {
		String advancedHTMLRejectedReason = DEFAULT_REJECT_REASON;
		if(request != null) {
			Resource resource = request.getResource();
			ValueMap properties = resource.adaptTo( ValueMap.class );
			advancedHTMLRejectedReason = properties.get(REJECT_REASON_PROP, DEFAULT_REJECT_REASON);
		}
		return advancedHTMLRejectedReason;
	}
	
	private boolean isApproved() {
		/*if non-production environment then skip workflow so always return true here */
		if(!isProduction()) {
			return true;
		}
		/*else check node property*/
		Boolean advancedHtmlApprovedFlag = Boolean.FALSE;
		if(request != null) {
			Resource resource = request.getResource();
			ValueMap properties = resource.adaptTo( ValueMap.class );
			advancedHtmlApprovedFlag = properties.get(APPROVE_FLAG_PROP, Boolean.FALSE);
		}
		return advancedHtmlApprovedFlag;
	}
	
	private boolean isTouch() {
		AuthoringUIMode uiMode = uiModeService.getAuthoringUIMode(request);
		if(uiMode == AuthoringUIMode.TOUCH) {
			return true;
		}
		return false;
	}
	
	private boolean isEdit() {
		WCMMode wcmMode = WCMMode.fromRequest(request);
		if(wcmMode == WCMMode.EDIT || wcmMode == WCMMode.DESIGN) {
			return true;
		}
		return false;
	}
	
	private boolean isPublished() {
		WCMMode wcmMode = WCMMode.fromRequest(request);
		if(wcmMode == WCMMode.DISABLED) {
			return true;
		}
		return false;
	}
	
	public String getText() {
		log.info("text = {}", text);
		return text;
	}
	
	public String getInlineScript() {
		return inlineScript;
	}
	
	public String[] getScriptPaths() {
		return scriptPaths;
	}
	
	public String getMobileCss() {
		return mobileCss;
	}

	public String getDesktopCss() {
		return desktopCss;
	}

	public String getAllCss() {
		return allCss;
	}

	public String getCssClass() {
		return cssClass;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public boolean isShowError() {
		return showError;
	}
	
	private class ParserCallback extends HTMLEditorKit.ParserCallback {
		boolean hasInvalid = false;

		@Override
		public void handleStartTag(Tag t, MutableAttributeSet a, int pos) {
			if(t == HTML.Tag.SCRIPT || t == HTML.Tag.STYLE) {
				log.debug("handleStartTag found invalid tag {} at pos={}",t.toString(),pos);
				this.hasInvalid = true;
			} 
		}

		@Override
		public void handleSimpleTag(Tag t, MutableAttributeSet a, int pos) {
			if(t == HTML.Tag.SCRIPT || t == HTML.Tag.STYLE) {
				log.debug("found simple script or style tag at pos={}", pos);
				this.hasInvalid = true;
			} 
		}
		
	}

	private boolean isProduction() {
        Set<String> runModes = slingSettingsService.getRunModes();
		for(String mode : runModes) {
			if("production".equals(mode)) {
				return true;
			}
		}
		return false;
	}

}
