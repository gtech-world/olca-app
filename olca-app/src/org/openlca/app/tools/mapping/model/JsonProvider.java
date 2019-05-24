package org.openlca.app.tools.mapping.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openlca.core.database.FlowDao;
import org.openlca.core.database.IDatabase;
import org.openlca.core.model.Flow;
import org.openlca.core.model.FlowType;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.descriptors.BaseDescriptor;
import org.openlca.core.model.descriptors.FlowDescriptor;
import org.openlca.io.maps.FlowMap;
import org.openlca.io.maps.FlowMapEntry;
import org.openlca.io.maps.FlowRef;
import org.openlca.jsonld.Json;
import org.openlca.jsonld.ZipStore;
import org.openlca.jsonld.input.JsonImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JsonProvider implements IProvider {

	private final File file;

	private JsonProvider(File file) {
		this.file = file;
	}

	public static JsonProvider of(String path) {
		return new JsonProvider(new File(path));
	}

	public static JsonProvider of(File file) {
		return new JsonProvider(file);
	}

	public List<FlowMap> getFlowMaps() {
		try (ZipStore store = ZipStore.open(file)) {
			List<String> files = store.getFiles("flow_mappings");
			List<FlowMap> maps = new ArrayList<>();
			for (String f : files) {
				byte[] data = store.get(f);
				String json = new String(data, "utf-8");
				JsonObject obj = new Gson().fromJson(json, JsonObject.class);
				FlowMap map = asFlowMap(obj);
				maps.add(map);
			}
			return maps;
		} catch (Exception e) {
			Logger log = LoggerFactory.getLogger(getClass());
			log.error("failed to read mapping files", e);
			return Collections.emptyList();
		}
	}

	@Override
	public List<FlowRef> getFlowRefs() {
		return null;
	}

	@Override
	public void persist(List<FlowRef> refs, IDatabase db) {
		if (refs == null || db == null)
			return;
		try (ZipStore store = ZipStore.open(file)) {
			FlowDao dao = new FlowDao(db);
			JsonImport imp = new JsonImport(store, db);
			for (FlowRef ref : refs) {
				Flow flow = dao.getForRefId(ref.flow.refId);
				if (flow != null)
					continue;
				imp.run(ModelType.FLOW, ref.flow.refId);
			}
		} catch (Exception e) {
			Logger log = LoggerFactory.getLogger(getClass());
			log.error("failed persist flows", e);
		}
	}

	private FlowMap asFlowMap(JsonObject obj) {
		FlowMap map = new FlowMap();
		map.name = Json.getString(obj, "name");
		map.description = Json.getString(obj, "name");

		map.source = new BaseDescriptor();
		mapDescriptor(Json.getObject(obj, "source"), map.source);
		map.target = new BaseDescriptor();
		mapDescriptor(Json.getObject(obj, "target"), map.target);

		JsonArray array = Json.getArray(obj, "mappings");
		if (array != null) {
			for (JsonElement e : array) {
				if (!e.isJsonObject())
					continue;
				JsonObject eObj = e.getAsJsonObject();
				FlowMapEntry entry = new FlowMapEntry();
				entry.sourceFlow = asFlowRef(
						Json.getObject(eObj, "from"));
				entry.targetFlow = asFlowRef(
						Json.getObject(eObj, "to"));
				entry.factor = Json.getDouble(
						eObj, "conversionFactor", 1.0);
				map.entries.add(entry);
			}
		}
		return map;
	}

	private FlowRef asFlowRef(JsonObject obj) {
		if (obj == null)
			return null;
		FlowRef ref = new FlowRef();
		ref.flow = new FlowDescriptor();
		JsonObject flowObj = Json.getObject(obj, "flow");
		if (flowObj == null)
			return null;

		mapDescriptor(flowObj, ref.flow);
		ref.categoryPath = categoryPath(flowObj);

		JsonObject fp = Json.getObject(obj, "flowProperty");
		if (fp != null) {
			ref.property = new BaseDescriptor();
			mapDescriptor(fp, ref.property);
		}
		JsonObject u = Json.getObject(obj, "unit");
		if (u != null) {
			ref.unit = new BaseDescriptor();
			mapDescriptor(u, ref.unit);
		}

		return ref;
	}

	private void mapDescriptor(JsonObject obj, BaseDescriptor d) {
		if (obj == null || d == null)
			return;
		d.name = Json.getString(obj, "name");
		d.description = Json.getString(obj, "description");
		d.refId = Json.getString(obj, "@id");
		if (d instanceof FlowDescriptor) {
			FlowDescriptor fd = (FlowDescriptor) d;
			fd.flowType = Json.getEnum(obj, "flowType", FlowType.class);
			if (fd.flowType == null) {
				fd.flowType = FlowType.ELEMENTARY_FLOW;
			}
		}
	}

	private String categoryPath(JsonObject obj) {
		if (obj == null)
			return null;
		JsonArray array = Json.getArray(obj, "categoryPath");
		if (array == null)
			return null;
		StringBuilder path = new StringBuilder();
		for (JsonElement elem : array) {
			if (!elem.isJsonPrimitive())
				continue;
			if (path.length() > 0) {
				path.append(" / ");
			}
			path.append(elem.getAsString());
		}
		return path.toString();
	}
}
