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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonElement;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

public class Neo4jRest implements GraphDatabase {

	private final HttpClient client;
	private final boolean ro;
	private final Logger logger;
	private final String basePath;
	private Pattern writingClausesPattern = Pattern.compile(
			"(\\s+set\\s+|create\\s+|merge\\s+|delete\\s+|remove\\s+|foreach)", Pattern.CASE_INSENSITIVE);

	public Neo4jRest(URI uri, boolean ro, Vertx vertx, Logger logger, int poolSize) {
		this.client = vertx.createHttpClient()
				.setHost(uri.getHost())
				.setPort(uri.getPort())
				.setMaxPoolSize(poolSize)
				.setKeepAlive(false);
		this.ro = ro;
		String path = uri.getPath();
		if (path != null && path.endsWith("/")) {
			this.basePath  = path.substring(0, path.length() - 1);
		} else {
			this.basePath = path;
		}
		this.logger = logger;
	}

	@Override
	public void execute(String query, JsonObject p, final Handler<JsonObject> handler) {
		JsonObject params = p;
		if (params == null) {
			params = new JsonObject();
		}
		JsonObject body = new JsonObject()
				.putString("query", query)
				.putObject("params", params);
		logger.debug(body.encode());
		sendRequest("/cypher", body, true, new Handler<HttpClientResponse>() {

			@Override
			public void handle(final HttpClientResponse resp) {
				resp.bodyHandler(new Handler<Buffer>() {

					@Override
					public void handle(Buffer b) {
						logger.debug(b.toString());
						if (resp.statusCode() != 404 && resp.statusCode() != 500) {
							JsonObject json = new JsonObject(b.toString("UTF-8"));
							if (resp.statusCode() == 200) {
								handler.handle(new JsonObject().putArray("result", transformJson(json)));
							} else {
								handler.handle(json);
							}
						} else {
							handler.handle(new JsonObject().putString("message",
									resp.statusMessage() + " : " + b.toString()));
						}
					}
				});
			}
		});
	}

	@Override
	public void executeBatch(JsonArray queries, final Handler<JsonObject> handler) {
		JsonArray body = new JsonArray();
		int i = 0;
		for (Object q : queries) {
			JsonObject query = new JsonObject()
					.putString("method", "POST")
					.putString("to", "/cypher")
					.putObject("body", (JsonObject) q)
					.putNumber("id", i++);
			body.add(query);
		}
		logger.debug(body.encode());
		sendRequest("/batch", body, new Handler<HttpClientResponse>() {
			@Override
			public void handle(final HttpClientResponse resp) {
				resp.bodyHandler(new Handler<Buffer>() {

					@Override
					public void handle(Buffer b) {
						logger.debug(b.toString());
						if (resp.statusCode() != 404 && resp.statusCode() != 500) {
							JsonArray json = new JsonArray(b.toString("UTF-8"));
							JsonArray out = new JsonArray();
							for (Object j : json) {
								JsonObject qr = (JsonObject) j;
								out.add(new JsonObject().putArray("result",
										transformJson(qr.getObject("body", new JsonObject())))
										.putNumber("idx", qr.getNumber("id")));
							}
							handler.handle(new JsonObject().putArray("results", out));
						} else {
							handler.handle(new JsonObject().putString("message",
									resp.statusMessage() + " : " + b.toString()));
						}
					}
				});
			}
		});
	}

	@Override
	public void executeTransaction(JsonArray statements, Integer transactionId,
								   boolean commit, final Handler<JsonObject> handler) {
		String uri = "/transaction";
		if (transactionId != null) {
			uri += "/" +transactionId;
		}
		if (commit) {
			uri += "/commit";
		}
		sendRequest(uri, new JsonObject().putArray("statements", statements), new Handler<HttpClientResponse>() {
			@Override
			public void handle(final HttpClientResponse resp) {
				resp.bodyHandler(new Handler<Buffer>() {

					@Override
					public void handle(Buffer b) {
						logger.debug(b.toString());
						if (resp.statusCode() != 404 && resp.statusCode() != 500) {
							JsonObject json = new JsonObject(b.toString("UTF-8"));
							JsonArray results = json.getArray("results");
							if (json.getArray("errors", new JsonArray()).size() == 0 &&
									results != null) {
								JsonArray out = new JsonArray();
								for (Object o : results) {
									if (!(o instanceof JsonObject)) continue;
									out.add(transformJson((JsonObject) o));
								}
								json.putArray("results", out);
								String commit = json.getString("commit");
								if (commit != null) {
									String[] c = commit.split("/");
									if (c.length > 2) {
										json.putNumber("transactionId", Integer.parseInt(c[c.length - 2]));
									}
								}
								json.removeField("errors");
								handler.handle(json);
							} else {
								handler.handle(new JsonObject().putString("message",
										json.getArray("errors", new JsonArray()).encode()));
							}
						} else {
							handler.handle(new JsonObject().putString("message",
									resp.statusMessage() + " : " + b.toString()));
						}
					}
				});
			}
		});
	}

	@Override
	public void resetTransactionTimeout(int transactionId, Handler<JsonObject> handler) {
		executeTransaction(new JsonArray(), transactionId, false, handler);
	}

	@Override
	public void rollbackTransaction(int transactionId, final Handler<JsonObject> handler) {
		HttpClientRequest req = client.delete(basePath + "/transaction/" + transactionId, new Handler<HttpClientResponse>() {
			@Override
			public void handle(final HttpClientResponse resp) {
				resp.bodyHandler(new Handler<Buffer>() {

					@Override
					public void handle(Buffer b) {
						logger.debug(b.toString());
						if (resp.statusCode() != 404 && resp.statusCode() != 500) {
							JsonObject json = new JsonObject(b.toString("UTF-8"));
							if (json.getArray("errors", new JsonArray()).size() == 0) {
								json.removeField("errors");
								handler.handle(json);
							} else {
								handler.handle(new JsonObject().putString("message",
										json.getArray("errors", new JsonArray()).encode()));
							}
						} else {
							handler.handle(new JsonObject().putString("message", resp.statusMessage()));
						}
					}
				});
			}
		});
		req.headers().add("Accept", "application/json; charset=UTF-8");
		req.end();
	}

	@Override
	public void close() {
		if (client != null) {
			client.close();
		}
	}

	private JsonArray transformJson(JsonObject json) {
		final JsonArray columns = json.getArray("columns");
		final JsonArray data = json.getArray("data");
		final JsonArray out = new JsonArray();

		if (data != null && columns != null) {
			for (Object r: data) {
				JsonArray row;
				if (r instanceof JsonArray) {
					row = (JsonArray) r;
				} else if (r instanceof JsonObject) {
					row = ((JsonObject) r).getArray("row");
				} else {
					continue;
				}
				JsonObject outRow = new JsonObject();
				out.addObject(outRow);
				for (int j = 0; j < row.size(); j++) {
					Object value = row.get(j);
					if (value == null) {
						outRow.putValue((String) columns.get(j), null);
					} else if (value instanceof String) {
						outRow.putString((String) columns.get(j), (String) value);
					} else if (value instanceof JsonArray) {
						outRow.putArray((String) columns.get(j), (JsonArray) value);
					} else if (value instanceof JsonObject) {
						outRow.putObject((String) columns.get(j), (JsonObject) value);
					} else if (value instanceof Boolean) {
						outRow.putBoolean((String) columns.get(j), (Boolean) value);
					} else if (value instanceof Number) {
						outRow.putNumber((String) columns.get(j), (Number) value);
					} else {
						outRow.putString((String) columns.get(j), value.toString());
					}
				}
			}
		}
		return out;
	}

	private void sendRequest(String path, JsonElement body, final Handler<HttpClientResponse> handler) {
		sendRequest(path, body, false, handler);
	}

	private void sendRequest(String path, JsonElement body, boolean checkReadOnly,
							 final Handler<HttpClientResponse> handler) {
		HttpClientRequest req = client.post(basePath + path, handler);
		req.headers()
				.add("Content-Type", "application/json")
				.add("Accept", "application/json; charset=UTF-8");
		if (checkReadOnly && ro) {
			String query = ((JsonObject) body).getString("query");
			Matcher m = writingClausesPattern.matcher(query);
			if (query != null && !m.find()) {
				req.headers().add("Neo4j-Type", "readonly");
			}
		}
		if (body.isArray()) {
			req.end(body.asArray().encode());
		} else {
			req.end(body.asObject().encode());
		}
	}

}
