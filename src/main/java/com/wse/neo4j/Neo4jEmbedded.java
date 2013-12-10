package com.wse.neo4j;

import java.util.List;
import java.util.Map;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

import com.wse.neo4j.exception.ExceptionUtils;

public class Neo4jEmbedded implements GraphDatabase {

	private final GraphDatabaseService gdb;
	private final ExecutionEngine engine;
	private final Logger logger;

	public Neo4jEmbedded(JsonObject config, Logger logger) {
		GraphDatabaseBuilder gdbb = new GraphDatabaseFactory()
		.newEmbeddedDatabaseBuilder(config.getString("datastore-path"));
		JsonObject neo4jConfig = config.getObject("neo4j");
		if (neo4jConfig != null) {
			gdbb.setConfig(GraphDatabaseSettings.node_keys_indexable, neo4jConfig.getString("node_keys_indexable", ""))
			.setConfig(GraphDatabaseSettings.node_auto_indexing, neo4jConfig.getString("node_auto_indexing", "false"));
		}
		gdb = gdbb.newGraphDatabase();
		engine = new ExecutionEngine(gdb);
		this.logger = logger;
	}

	@Override
	public void execute(String query, JsonObject params, Handler<JsonObject> handler) {
		ExecutionResult result = null;
		try {
			if (params != null){
				result = engine.execute(query, params.toMap());
			} else {
				result = engine.execute(query);
			}
		} catch (Exception e) {
			handler.handle(ExceptionUtils.exceptionToJson(e));
		}
		JsonObject json = toJson(result);
		handler.handle(json);
	}

	@Override
	public void executeBatch(JsonArray queries, Handler<JsonObject> handler) {
		ExecutionResult result;
		JsonArray results = new JsonArray();
		try {
			int i = 0;
			for (Object q: queries) {
				JsonObject qr = (JsonObject) q;
				String query = qr.getString("query");
				JsonObject params = qr.getObject("params");
				if (params != null){
					result = engine.execute(query, params.toMap());
				} else {
					result = engine.execute(query);
				}
				results.addObject(toJson(result).putNumber("idx", i++));
			}
		} catch (Exception e) {
			handler.handle(ExceptionUtils.exceptionToJson(e));
		}
		JsonObject json = new JsonObject().putArray("results", results);
		handler.handle(json);
	}

	@Override
	public void close() {
		if (gdb != null) {
			gdb.shutdown();
		}
	}

	@SuppressWarnings("unchecked")
	private JsonObject toJson (ExecutionResult result) {
		JsonObject json = new JsonObject();
		// TODO avoid "if null programming"
		if (result == null) {
			return json;
		}
		int i = 0;
		for (Map<String, Object> row : result) {
				JsonObject jsonRow = new JsonObject();
				json.putObject(String.valueOf(i++), jsonRow);
			for (Map.Entry<String, Object> column : row.entrySet()) {
				Object v = column.getValue();
				if (v instanceof Iterable) {
					jsonRow.putArray(column.getKey(), new JsonArray((List<Object>) v));
				} else {
					String value = (v == null) ? "" : v.toString();
					jsonRow.putString(column.getKey(), value);
				}
			}
		}
		return new JsonObject().putObject("result", json) ;
	}

}
