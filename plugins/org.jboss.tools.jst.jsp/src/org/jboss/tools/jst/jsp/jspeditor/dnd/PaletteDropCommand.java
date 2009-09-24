/*******************************************************************************
 * Copyright (c) 2007 Exadel, Inc. and Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Exadel, Inc. and Red Hat, Inc. - initial API and implementation
 ******************************************************************************/ 
package org.jboss.tools.jst.jsp.jspeditor.dnd;

import java.util.Properties;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.wst.xml.core.internal.document.ElementImpl;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMElement;
import org.jboss.tools.common.model.XModelObject;
import org.jboss.tools.common.model.options.PreferenceModelUtilities;
import org.jboss.tools.common.model.ui.ModelUIPlugin;
import org.jboss.tools.common.model.ui.dnd.DnDUtil;
import org.jboss.tools.common.model.ui.editor.IModelObjectEditorInput;
import org.jboss.tools.common.model.ui.editors.dnd.DropData;
import org.jboss.tools.common.model.ui.editors.dnd.DropWizard;
import org.jboss.tools.common.model.ui.editors.dnd.IDropWizard;
import org.jboss.tools.common.model.ui.editors.dnd.IDropWizardModel;
import org.jboss.tools.common.model.ui.editors.dnd.PaletteDropWizardModel;
import org.jboss.tools.common.model.ui.editors.dnd.composite.TagAttributesComposite.AttributeDescriptorValue;
import org.jboss.tools.common.model.ui.views.palette.PaletteInsertHelper;
import org.jboss.tools.common.model.ui.views.palette.PaletteInsertManager;
import org.jboss.tools.common.model.util.EclipseResourceUtil;
import org.jboss.tools.jst.web.tld.IWebProject;
import org.jboss.tools.jst.web.tld.TLDToPaletteHelper;
import org.jboss.tools.jst.web.tld.URIConstants;
import org.jboss.tools.jst.web.tld.WebProjectFactory;

/**
 * 
 * @author eskimo
 */
public class PaletteDropCommand extends FileDropCommand {
	Properties initialValues = new Properties();
	String startText;
	String endText;	
	String newLine;
	String reformat = "no"; //$NON-NLS-1$

	Properties properties = new Properties();
	boolean callPaletteWizard = false;

	/**
	 * 
	 */
	protected IDropWizardModel createSpecificModel() {
		return new PaletteDropWizardModel(tagProposalFactory);
	}

	protected void addCustomProperties(Properties runningProperties) {		
		newLine = properties.getProperty(PaletteInsertHelper.PROPOPERTY_NEW_LINE);
		if (newLine == null) newLine="true"; //$NON-NLS-1$
		runningProperties.setProperty(PaletteInsertHelper.PROPOPERTY_NEW_LINE, newLine);
		String addTaglib = properties.getProperty(PaletteInsertHelper.PROPOPERTY_ADD_TAGLIB);
		if(addTaglib == null) addTaglib = "true"; //$NON-NLS-1$
		runningProperties.setProperty(PaletteInsertHelper.PROPOPERTY_ADD_TAGLIB, addTaglib);
	}
	
	public void execute() {
		if(getDefaultModel().getTagProposal() == IDropWizardModel.UNDEFINED_TAG_PROPOSAL) {
			if(startText == null && endText == null) return;
			int pos = ((ITextSelection)getDefaultModel().getDropData().getSelectionProvider().getSelection()).getOffset();
			getDefaultModel().getDropData().getSourceViewer().setSelectedRange(pos, 0);
			if(startText != null) properties.setProperty(PaletteInsertHelper.PROPOPERTY_START_TEXT, startText);
			if(endText != null) properties.setProperty(PaletteInsertHelper.PROPOPERTY_END_TEXT, endText);
			if(reformat != null) properties.setProperty(PaletteInsertHelper.PROPOPERTY_REFORMAT_BODY, reformat);
			if(newLine != null) properties.setProperty(PaletteInsertHelper.PROPOPERTY_NEW_LINE, newLine);
			PaletteInsertHelper.getInstance().insertIntoEditor(
					getDefaultModel().getDropData().getSourceViewer(),
					properties
			);
		} else {
			DropData data = getDefaultModel().getDropData();
			ISourceViewer viewer = data.getSourceViewer();
			if(data.getContainer() != null){
				if (data.getContainer() instanceof ElementImpl) {
					ElementImpl container = (ElementImpl)data.getContainer();
					if(!container.hasEndTag()){
						try{
							IDocument document = viewer.getDocument();
							int containerOffset = container.getStartOffset();
							int containerLenght = container.getStartEndOffset()-containerOffset;
							String containerString = document.get(containerOffset, containerLenght);
							int slashPosition = containerString.lastIndexOf("/"); //$NON-NLS-1$
							if(slashPosition >= 0){
								int deltaOffset =  (containerString.length()-1)-slashPosition;
								String text = ""; //$NON-NLS-1$
								for(int i=0; i < deltaOffset;i++) text += " "; //$NON-NLS-1$
								text += "></"+container.getNodeName()+">"; //$NON-NLS-1$ //$NON-NLS-2$
								document.replace(containerOffset+slashPosition, containerString.length()-slashPosition, text);
							}
						}catch(BadLocationException ex){
							ModelUIPlugin.getPluginLog().logError(ex);
						}
					}
				}
			}		
			super.execute();
		}
	}

	public void initialize() {
		DropData data = getDefaultModel().getDropData();		
		
		IEditorInput input = data.getEditorInput();
		XModelObject target = null;
		IFile f = null;
		if(input instanceof IFileEditorInput) {
			f = ((IFileEditorInput)input).getFile();
			target = EclipseResourceUtil.getObjectByResource(f);
			if(target == null && f.exists()) {
				target = EclipseResourceUtil.createObjectForResource(f);
			}
		} else if(input instanceof IModelObjectEditorInput) {
			target = ((IModelObjectEditorInput)input).getXModelObject();
		}
		if(target == null) {
			initialize2();
		} else {
			ISourceViewer viewer = data.getSourceViewer();
			
			properties = new Properties();
			properties.put("viewer", viewer); //$NON-NLS-1$
			properties.setProperty("text", viewer.getDocument().get()); //$NON-NLS-1$
			properties.setProperty("isDrop", "true"); //$NON-NLS-1$ //$NON-NLS-2$
			properties.setProperty("actionSourceGUIComponentID", "editor"); //$NON-NLS-1$ //$NON-NLS-2$
			properties.setProperty("accepsAsString", "true"); //$NON-NLS-1$ //$NON-NLS-2$
				
			if(f != null) {
				properties.put("file", f); //$NON-NLS-1$
			}
			ISelection selection = data.getSelectionProvider().getSelection();
			
			int offset = 0;
			//int length = 0;
			if(selection instanceof ITextSelection) {
				offset = ((ITextSelection)selection).getOffset();
				//length = ((ITextSelection)selection).getLength();
			} else {
				offset = viewer.getTextWidget().getCaretOffset();
			}
			properties.setProperty("pos", "" + offset); //$NON-NLS-1$ //$NON-NLS-2$
			if(selection instanceof IStructuredSelection && !selection.isEmpty()) {
				Object so = ((IStructuredSelection)selection).getFirstElement();
				if(so instanceof IDOMElement) {
					String en = ((IDOMElement)so).getNodeName();
					properties.setProperty("context:tagName", en); //$NON-NLS-1$
					String attrName = data.getAttributeName();
					if(attrName != null) {
						properties.setProperty("context:attrName", attrName); //$NON-NLS-1$
					}
				}
			}
			try {
				if(DnDUtil.isPasteEnabled(target)) {
					DnDUtil.paste(target, properties);
				} else {
					XModelObject s = PreferenceModelUtilities.getPreferenceModel().getModelBuffer().source();
					if(s != null) {
						properties.setProperty("start text", "" + getDefaultText(s)); //$NON-NLS-1$ //$NON-NLS-2$
						properties.setProperty("end text", ""); //$NON-NLS-1$ //$NON-NLS-2$
						properties.setProperty("new line", "newLine"); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
			} catch (CoreException e) {
				ModelUIPlugin.getPluginLog().logError(e);
			}
			startText = properties.getProperty(TLDToPaletteHelper.START_TEXT);
			endText = properties.getProperty(TLDToPaletteHelper.END_TEXT);
			reformat = properties.getProperty(TLDToPaletteHelper.REFORMAT);
			String uri = properties.getProperty(URIConstants.LIBRARY_URI);
			String libraryVersion = properties.getProperty(URIConstants.LIBRARY_VERSION);
			String defaultPrefix = properties.getProperty(URIConstants.DEFAULT_PREFIX);
			String tagname = properties.getProperty("tag name"); //$NON-NLS-1$
			
			callPaletteWizard = PaletteInsertManager.getInstance().getWizardName(properties) != null;
			
			boolean isWizardEnabled = (!"FileJAVA".equals(target.getModelEntity().getName())); //$NON-NLS-1$
			if(getDefaultModel() instanceof PaletteDropWizardModel) {
				((PaletteDropWizardModel)getDefaultModel()).setWizardEnabled(isWizardEnabled);
			}
			
			if(uri == null || tagname == null) {
				getDefaultModel().setTagProposal(IDropWizardModel.UNDEFINED_TAG_PROPOSAL);
			} else {
				getDefaultModel().setTagProposal(new TagProposal(uri, libraryVersion, defaultPrefix, tagname));
				insertInitialValues();
			}
		}		
	}

	private void initialize2() {
		XModelObject object = PreferenceModelUtilities.getPreferenceModel().getModelBuffer().source();
		String tagname = object.getAttributeValue("name"); //$NON-NLS-1$
		XModelObject parent = object.getParent();
		String uri = (parent == null) ? "" : parent.getAttributeValue(URIConstants.LIBRARY_URI); //$NON-NLS-1$
		String libraryVersion = (parent == null) ? "" : parent.getAttributeValue(URIConstants.LIBRARY_VERSION); //$NON-NLS-1$
		String defaultPrefix = (parent == null) ? "" : parent.getAttributeValue(URIConstants.DEFAULT_PREFIX); //$NON-NLS-1$
		this.getDefaultModel().setTagProposal(new TagProposal(uri, libraryVersion,defaultPrefix,tagname));
		startText = object.getAttributeValue("start text"); //$NON-NLS-1$
		endText = object.getAttributeValue("end text"); //$NON-NLS-1$
	}
	
	private void insertInitialValues() {
		parseInitialValues(startText);
		AttributeDescriptorValue[] vs = getDefaultModel().getAttributeValueDescriptors();
		for (int i = 0; i < vs.length; i++) {
			String v = initialValues.getProperty(vs[i].getName());
			if(v != null) vs[i].setValue(v);
		}
	}
	
	private void parseInitialValues(String startText) {
		if(startText == null || startText.length() == 0) return;
		int bi = startText.indexOf('<');
		if(bi < 0) return;
		int ei = startText.indexOf('>', bi);
		if(ei < 0) return;
		String header = startText.substring(bi + 1, ei);
		int NOTHING = 0;
		int ATT_NAME = 1;
		int ATT_VALUE = 2;
		char quote = '\0';
		int state = NOTHING;
		String name = null;
		String value = null;
		for (int i = 0; i < header.length(); i++) {
			char c = header.charAt(i);
			if(state == NOTHING) {
				if(Character.isJavaIdentifierStart(c)) {
					name = "" + c; //$NON-NLS-1$
					state = ATT_NAME;
				}
			} else if(state == ATT_NAME) {
				if(Character.isJavaIdentifierPart(c) || c == ':') {
					name += c;
				} else if(c == '=') {
					state = ATT_VALUE;
					quote = '\0';
				}
			} else if(state == ATT_VALUE) {
				if(c == quote) {
					initialValues.setProperty(name, value);
					name = null;
					value = null;
					state = NOTHING;
					quote = '\0';
				} else if(c == '"' || c == '\'') {
					quote = c;
					value = ""; //$NON-NLS-1$
				} else if(quote != '\0') {
					value += c;
				}
			}
		}
	}	

	protected String generateStartText() {
		startText = properties.getProperty("start text"); //$NON-NLS-1$
		if(getDefaultModel().getTagProposal()==IDropWizardModel.UNDEFINED_TAG_PROPOSAL
			|| getDefaultModel().getTagProposal().getDetails().length() == 0) {
			return startText;
		}
		String s1 = super.generateStartText();
		String s2 = startText;
		if(s2 == null) return s1;
		if(s1.indexOf('=') < 0) return s2; // no input
		int bi1 = s1.indexOf('<');
		int bi2 = s2.indexOf('<');
		if(bi2 < 0 || bi1 < 0) return s2;
		int ei1 = s1.indexOf('>', bi1);
		int ei2 = s2.indexOf('>', bi2);
		if(ei1 < 0 || ei2 < 0) return s1;
		boolean slash1 = s1.charAt(ei1 - 1) == '/';
		boolean slash2 = s2.charAt(ei2 - 1) == '/';
		if(slash1 && !slash2) {
			s2 = s2.substring(0, bi2) + s1.substring(bi1, ei1 - 1) + s2.substring(ei2);
		} else if(!slash1 && slash2) {
			s2 = s2.substring(0, bi2) + s1.substring(bi1, ei1) + s2.substring(ei2 - 1);
		} else {
			s2 = s2.substring(0, bi2) + s1.substring(bi1, ei1) + s2.substring(ei2);
		}
		return s2;
	}

	protected String generateEndText() {
		endText = properties.getProperty("end text"); //$NON-NLS-1$
		return (endText != null) ? endText : ""; //$NON-NLS-1$
	}

	protected String getReformatBodyProperty() {
		return reformat;
	}

	protected IDropWizard createDropWizard() {
		String wizardName = PaletteInsertManager.getInstance().getWizardName(properties);
		
		IDropWizard wizard = null;
		if(wizardName != null) {
			wizard = (IDropWizard)PaletteInsertManager.getInstance().createWizardInstance(properties);
		}
		if(wizard == null) wizard =	new DropWizard();
		wizard.setCommand(this);
		return wizard;
	}
	
	public Properties getProperties() {
		return properties;
	}
	
	private String getDefaultText(XModelObject o) {
		if(o == null) return ""; //$NON-NLS-1$
		if(o.getFileType() != XModelObject.FILE) return o.getPresentationString();
		IWebProject p = WebProjectFactory.instance.getWebProject(o.getModel());
		String path = p.getPathInWebRoot(o);
		return path == null ? o.getPresentationString() : path;
	}
}