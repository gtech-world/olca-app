package org.openlca.app.navigation;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.navigator.ILinkHelper;
import org.openlca.app.App;
import org.openlca.app.editors.Editors;
import org.openlca.app.editors.ModelEditorInput;
import org.slf4j.LoggerFactory;

public class NavigationLinkHelper implements ILinkHelper {

	@Override
	public IStructuredSelection findSelection(IEditorInput input) {
		if (!(input instanceof ModelEditorInput))
			return new StructuredSelection();
		var mInput = (ModelEditorInput) input;
		var elem = Navigator.findElement(mInput.getDescriptor());
		return elem == null
				? new StructuredSelection()
				: new StructuredSelection(elem);
	}

	@Override
	public void activateEditor(IWorkbenchPage page, IStructuredSelection selection) {
		if (!(selection.getFirstElement() instanceof ModelElement))
			return;
		ModelElement element = (ModelElement) selection.getFirstElement();
		for (IEditorReference ref : Editors.getReferences()) {
			try {
				if (!(ref.getEditorInput() instanceof ModelEditorInput))
					continue;
				ModelEditorInput input = (ModelEditorInput) ref.getEditorInput();
				if (element.getContent().equals(input.getDescriptor())) {
					App.openEditor(element.getContent());
				}
			} catch (PartInitException e) {
				var log = LoggerFactory.getLogger(getClass());
				log.error("Error activating editor", e);
			}
		}
	}
}
