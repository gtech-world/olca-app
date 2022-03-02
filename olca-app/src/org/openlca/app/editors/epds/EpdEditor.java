package org.openlca.app.editors.epds;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.openlca.app.M;
import org.openlca.app.components.ModelLink;
import org.openlca.app.editors.InfoSection;
import org.openlca.app.editors.ModelEditor;
import org.openlca.app.editors.ModelPage;
import org.openlca.app.rcp.images.Icon;
import org.openlca.app.util.Controls;
import org.openlca.app.util.Desktop;
import org.openlca.app.util.ErrorReporter;
import org.openlca.app.util.MsgBox;
import org.openlca.app.util.UI;
import org.openlca.core.model.Actor;
import org.openlca.core.model.Epd;
import org.openlca.core.model.Source;
import org.openlca.util.Strings;

public class EpdEditor extends ModelEditor<Epd> {

	public EpdEditor() {
		super(Epd.class);
	}

	@Override
	protected void addPages() {
		try {
			addPage(new EpdPage(this));
		} catch (Exception e) {
			ErrorReporter.on("Failed to open EPD", e);
		}
	}

	private static class EpdPage extends ModelPage<Epd> {

		private final EpdEditor editor;

		EpdPage(EpdEditor editor) {
			super(editor, "EpdInfoPage", M.GeneralInformation);
			this.editor = editor;
		}

		@Override
		protected void createFormContent(IManagedForm mForm) {
			var form = UI.formHeader(this);
			var tk = mForm.getToolkit();
			var body = UI.formBody(form, tk);

			var info = new InfoSection(editor).render(body, tk);
			UI.filler(info.composite());
			var exportBtn = tk.createButton(
				info.composite(), "Upload as draft to EC3", SWT.NONE);
			exportBtn.setImage(Icon.BUILDING.get());
			Controls.onSelect(exportBtn, $ -> {
				var check = EpdConverter.validate(epd());
				if (check.hasError()) {
					MsgBox.error("Validation error",
						"EPD cannot be converted to an openEPD document: " + check.error());
					return;
				}
				var ec3Epd = EpdConverter.convert(epd());
				ExportDialog.show(ec3Epd);
			});

			new EpdProductSection(editor).render(body, tk);
			referenceSection(body, tk);
			new EpdModulesSection(editor).render(body, tk);
			body.setFocus();
			form.reflow(true);
		}

		private void referenceSection(Composite body, FormToolkit tk) {
			var comp = UI.formSection(body, tk, "References", 2);
			ModelLink.of(Actor.class)
				.renderOn(comp, tk, "Manufacturer")
				.setModel(epd().manufacturer)
				.onChange(actor -> {
					epd().manufacturer = actor;
					editor.setDirty();
				});

			ModelLink.of(Actor.class)
				.renderOn(comp, tk, "Program operator")
				.setModel(epd().programOperator)
				.onChange(actor -> {
					epd().programOperator = actor;
					editor.setDirty();
				});

			ModelLink.of(Source.class)
				.renderOn(comp, tk, "PCR")
				.setModel(epd().pcr)
				.onChange(source -> {
					epd().pcr = source;
					editor.setDirty();
				});

			ModelLink.of(Actor.class)
				.renderOn(comp, tk, "Verifier")
				.setModel(epd().verifier)
				.onChange(actor -> {
					epd().verifier = actor;
					editor.setDirty();
				});

			UI.formLabel(comp, tk, "URN");
			var link = tk.createImageHyperlink(comp, SWT.NONE);
			link.setText(Strings.nullOrEmpty(epd().urn)
				? "- none -"
				: epd().urn
			);
			Controls.onClick(link, $ -> {
				var urn = epd().urn;
				if (Strings.nullOrEmpty(urn))
					return;
				if (urn.startsWith("openEPD:")) {
					var extId = urn.substring(8);
					var url = "https://buildingtransparency.org/ec3/epds/" + extId;
					Desktop.browse(url);
				}
			});

		}

		private Epd epd() {
			return getModel();
		}
	}
}
