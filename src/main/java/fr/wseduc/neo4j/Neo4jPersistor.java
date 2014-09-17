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

import java.net.URI;
import java.net.URISyntaxException;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;


public class Neo4jPersistor extends BusModBase implements Handler<Message<JsonObject>> {

	private GraphDatabase db;

	@Override
	public void start() {
		super.start();
		String serverUri = config.getString("server-uri");
		if (serverUri != null && !serverUri.trim().isEmpty()) {
			try {
				db = new Neo4jRest(new URI(serverUri),  config.getBoolean("slave-readonly", false), vertx, logger,
						config.getInteger("poolsize", 32));
			} catch (URISyntaxException e) {
				logger.error(e.getMessage(), e);
			}
		} else {
			db = new Neo4jEmbedded(config, logger);
		}

		eb.registerHandler(config.getString("address", "neo4j.persistor"),this);
		logger.info("BusModBase: Neo4jPersistor starts on address: " + config.getString("address"));
	}

	@Override
	public void stop() {
		super.stop();
		if (db != null) {
			db.close();
		}
	}

	@Override
	public void handle(Message<JsonObject> m) {
		switch(m.body().getString("action")) {
			case "execute" :
				execute(m);
				break;
			case "executeBatch" :
				executeBatch(m);
				break;
			case "executeTransaction" :
				executeTransaction(m);
				break;
			case "resetTransactionTimeout" :
				resetTransaction(m);
				break;
			case "rollbackTransaction" :
				rollbackTransaction(m);
				break;
			default :
				sendError(m, "Invalid or missing action");
		}
	}

	private void executeBatch(Message<JsonObject> m) {
		db.executeBatch(m.body().getArray("queries"), resultHandler(m));
	}

	private void execute(final Message<JsonObject> m) {
		db.execute(m.body().getString("query"), m.body().getObject("params"), resultHandler(m));
	}

	private void executeTransaction(Message<JsonObject> m) {
		if (m.body().getArray("statements") == null) {
			sendError(m, "Invalid statements.");
			return;
		}
		db.executeTransaction(m.body().getArray("statements"),
				m.body().getInteger("transactionId"),
				m.body().getBoolean("commit", false), resultHandler(m));
	}

	private void resetTransaction(Message<JsonObject> m) {
		if (m.body().getInteger("transactionId") == null) {
			sendError(m, "Invalid transaction id.");
			return;
		}
		db.resetTransactionTimeout(m.body().getInteger("transactionId"), resultHandler(m));
	}

	private void rollbackTransaction(Message<JsonObject> m) {
		if (m.body().getInteger("transactionId") == null) {
			sendError(m, "Invalid transaction id.");
			return;
		}
		db.rollbackTransaction(m.body().getInteger("transactionId"), resultHandler(m));
	}

	private Handler<JsonObject> resultHandler(final Message<JsonObject> m) {
		return new Handler<JsonObject>() {

			@Override
			public void handle(JsonObject res) {
				String error = res.getString("message");
				if (error == null) {
					sendOK(m, res);
				} else {
					logger.error(res.getString("exception") + " : " + error);
					sendError(m, error);
				}
			}
		};
	}

}
