package fr.wseduc.neo4j;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

public interface GraphDatabase {

	void execute(String query, JsonObject params, Handler<JsonObject> handler);

	void executeBatch(JsonArray queries, Handler<JsonObject> handler);

	void executeTransaction(JsonArray statements, Integer transactionId,
							boolean commit, Handler<JsonObject> handler);

	void resetTransactionTimeout(int transactionId, Handler<JsonObject> handler);

	void rollbackTransaction(int transactionId, Handler<JsonObject> handler);

	void close();

}
