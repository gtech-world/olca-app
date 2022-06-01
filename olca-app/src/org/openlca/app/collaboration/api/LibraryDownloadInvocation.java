package org.openlca.app.collaboration.api;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.openlca.app.collaboration.util.Valid;
import org.openlca.app.collaboration.util.WebRequests.Type;

class LibraryDownloadInvocation extends Invocation<InputStream, InputStream> {

	private final String library;

	LibraryDownloadInvocation(String library) {
		super(Type.GET, "libraries", InputStream.class);
		this.library = library;
	}

	@Override
	protected void checkValidity() {
		Valid.checkNotEmpty(library, "library");
	}

	@Override
	protected String query() {
		return "/" + URLEncoder.encode(library, StandardCharsets.UTF_8);
	}

}
