package org.openlca.jsonld.input;

import org.openlca.core.database.IDatabase;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.RootEntity;
import org.openlca.jsonld.JsonStoreReader;

public class SyncJsonImport extends JsonImport {

	public SyncJsonImport(JsonStoreReader reader, IDatabase db) {
		super(reader, db);
	}

	@Override
	public void run() {
		new UnitGroupImport(this).importAll();
		var typeOrder = new ModelType[] { 
			ModelType.ACTOR,
			ModelType.SOURCE,
			ModelType.CURRENCY,
			ModelType.DQ_SYSTEM,
			ModelType.LOCATION,
			ModelType.FLOW_PROPERTY,
			ModelType.FLOW,
			ModelType.SOCIAL_INDICATOR,
			ModelType.PARAMETER,
			ModelType.PROCESS,
//			ModelType.IMPACT_CATEGORY,
//			ModelType.IMPACT_METHOD,
//			ModelType.PRODUCT_SYSTEM,
//			ModelType.PROJECT,
//			ModelType.RESULT,
//			ModelType.EPD,
		};
		for (var type : typeOrder) {
			var batchSize = BatchImport.batchSizeOf(type);
			if (batchSize > 1) {
				var clazz = (Class<? extends RootEntity>) type.getModelClass();
				new BatchImport<>(this, clazz, batchSize).run();
			} else {
				for(var id: reader.getRefIds(type)) {
					run(type, id);
				}
			}
			
		}
	}

}
