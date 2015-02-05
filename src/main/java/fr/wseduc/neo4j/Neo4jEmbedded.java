/*  Copyright © WebServices pour l'Éducation, 2014
 *
 *  This file is part of mod-neo4j-persistor. mod-neo4j-persistor is a vertx module to use neo4j database.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.wseduc.neo4j;

import java.util.List;
import java.util.Map;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

import fr.wseduc.neo4j.exception.ExceptionUtils;

public class Neo4jEmbedded implements GraphDatabase {

	private final GraphDatabaseService gdb;
	private final ExecutionEngine engine;
	private final Logger logger;

	public Neo4jEmbedded(JsonObject config, Logger logger) {
		GraphDatabaseBuilder gdbb = new GraphDatabaseFactory()
		.newEmbeddedDatabaseBuilder(config.getString("datastore-path"));
		JsonObject neo4jConfig = config.getObject("neo4j");
		if (neo4jConfig != null) {
			gdbb.setConfig(GraphDatabaseSettings.node_keys_indexable,
					neo4jConfig.getString("node_keys_indexable", ""));
			gdbb.setConfig(GraphDatabaseSettings.node_auto_indexing,
					neo4jConfig.getString("node_auto_indexing", "false"));
			gdbb.setConfig(GraphDatabaseSettings.allow_store_upgrade,
					neo4jConfig.getString("allow_store_upgrade", "true"));
		}
		gdb = gdbb.newGraphDatabase();
		engine = new ExecutionEngine(gdb);
		this.logger = logger;
	}

	@Override
	public void execute(String query, JsonObject params, Handler<JsonObject> handler) {
		ExecutionResult result;
		try (Transaction tx = gdb.beginTx()) {
			if (params != null){
				result = engine.execute(query, params.toMap());
			} else {
				result = engine.execute(query);
			}
			JsonObject json = new JsonObject().putArray("result",toJson(result));
			tx.success();
			handler.handle(json);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			handler.handle(ExceptionUtils.exceptionToJson(e));
		}
	}

	@Override
	public void executeBatch(JsonArray queries, Handler<JsonObject> handler) {
		ExecutionResult result;
		try (Transaction tx = gdb.beginTx()) {
			JsonArray results = new JsonArray();
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
				results.addObject(new JsonObject().putArray("result", toJson(result))
						.putNumber("idx", i++));
			}
			JsonObject json = new JsonObject().putArray("results", results);
			tx.success();
			handler.handle(json);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			handler.handle(ExceptionUtils.exceptionToJson(e));
		}
	}

	@Override
	public void executeTransaction(JsonArray statements, Integer transactionId,
			boolean commit, Handler<JsonObject> handler) {
		ExecutionResult result;
		try (Transaction tx = gdb.beginTx()) {
			JsonArray results = new JsonArray();
			for (Object o : statements) {
				if (!(o instanceof JsonObject)) continue;
				JsonObject qr = (JsonObject) o;
				String statement = qr.getString("statement");
				JsonObject params = qr.getObject("parameters");
				if (params != null){
					result = engine.execute(statement, params.toMap());
				} else {
					result = engine.execute(statement);
				}
				results.addArray(toJson(result));
			}
			tx.success();
			JsonObject json = new JsonObject().putArray("results", results);
			if (!commit) {
				json.putNumber("transactionId", 0);
			}
			handler.handle(json);
		} catch (Exception e) {
			handler.handle(ExceptionUtils.exceptionToJson(e));
		}
	}

	@Override
	public void resetTransactionTimeout(int transactionId, Handler<JsonObject> handler) {
		handler.handle(new JsonObject());
	}

	@Override
	public void rollbackTransaction(int transactionId, Handler<JsonObject> handler) {
		handler.handle(new JsonObject());
	}

	@Override
	public void close() {
		if (gdb != null) {
			gdb.shutdown();
		}
	}

	@SuppressWarnings("unchecked")
	private JsonArray toJson (ExecutionResult result) {
		JsonArray json = new JsonArray();
		if (result == null) {
			return json;
		}
		for (Map<String, Object> row : result) {
				JsonObject jsonRow = new JsonObject();
				json.addObject(jsonRow);
			for (Map.Entry<String, Object> column : row.entrySet()) {
				Object v = column.getValue();
				if (v == null) {
					jsonRow.putValue(column.getKey(), null);
				} else if (v instanceof Node) {
					JsonObject nodeJson = nodeToJsonObject((Node) v);
					jsonRow.putObject(column.getKey(), nodeJson);
				} else if (isNodeArray(v)) {
					Node[] nodes;
					if (v instanceof Iterable) {
						nodes = ((List<Node>) v).toArray(new Node[((List<Node>) v).size()]);
					} else { //if (v != null && v.getClass().isArray()) {
						nodes = (Node[]) v;
					}
					JsonArray nodesRes = new JsonArray();
					for (Node n: nodes) {
						nodesRes.addObject(nodeToJsonObject(n));
					}
					jsonRow.putArray(column.getKey(), nodesRes);
				} else {
					propertyToJson(jsonRow, column.getKey(), v);
				}
			}
		}
		return json;
	}

	private JsonObject nodeToJsonObject(Node n) {
		JsonObject data = new JsonObject();
		for (String prop : n.getPropertyKeys()) {
			propertyToJson(data, prop, n.getProperty(prop));
		}
		return new JsonObject().putObject("data", data);
	}

	private void propertyToJson(JsonObject jsonRow, String column, Object v) {
		if (v instanceof Iterable) {
			jsonRow.putArray(column, iterableToJsonArray((Iterable) v));
		} else if (v != null && v.getClass().isArray()) {
			jsonRow.putArray(column, arrayToJsonArray((Object[]) v));
		} else if (v instanceof Boolean) {
			jsonRow.putBoolean(column, (Boolean) v);
		} else if (v instanceof Number) {
			jsonRow.putNumber(column, (Number) v);
		} else {
			String value = (v == null) ? "" : v.toString();
			jsonRow.putString(column, value);
		}
	}

	private JsonArray iterableToJsonArray(Iterable i) {
		JsonArray a = new JsonArray();
		for (Object o : i) {
			if (o instanceof Iterable) {
				JsonArray r = iterableToJsonArray((Iterable) o);
				a.addArray(r);
			} else if (o != null && o.getClass().isArray()) {
				JsonArray r = arrayToJsonArray((Object[]) o);
				a.addArray(r);
			} else if (o instanceof Map) {
				a.add(new JsonObject((Map<String, Object>) o));
			} else {
				a.add(o);
			}
		}
		return a;
	}

	private JsonArray arrayToJsonArray(Object[] i) {
		JsonArray a = new JsonArray();
		for (Object o : i) {
			if (o instanceof Iterable) {
				JsonArray r = iterableToJsonArray((Iterable) o);
				a.addArray(r);
			} else if (o != null && o.getClass().isArray()) {
				JsonArray r = arrayToJsonArray((Object[]) o);
				a.addArray(r);
			} else if (o instanceof Map) {
				a.add(new JsonObject((Map<String, Object>) o));
			} else {
				a.add(o);
			}
		}
		return a;
	}

	private boolean isNodeArray(Object o) {
		Object[] objects = null;
		if (o instanceof Iterable) {
			objects = ((List<Object>) o).toArray();
		} else if (o != null && o.getClass().isArray()) {
			objects = (Object[]) o;
		}
		return objects != null && objects.length > 0 && objects[0] instanceof Node;
	}

}
