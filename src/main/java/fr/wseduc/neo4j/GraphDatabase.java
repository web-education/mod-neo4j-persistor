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
