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

import fr.wseduc.neo4j.exception.Neo4jConnectionException;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.net.URI;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class Neo4jRestNodeClient {

	private final Vertx vertx;
	private final HttpClient[] clients;
	private final AtomicInteger master = new AtomicInteger(0);
	private final CopyOnWriteArrayList<Integer> slaves;
	private final long checkTimerId;
	private final Random rnd;
	private static final Logger logger = LoggerFactory.getLogger(Neo4jRestNodeClient.class);

	public Neo4jRestNodeClient(URI[] uris, Vertx vertx, long delay, int poolSize) {
		this.vertx = vertx;
		clients = new HttpClient[uris.length];
		for (int i = 0; i < uris.length; i++) {
			clients[i] = vertx.createHttpClient()
					.setHost(uris[i].getHost())
					.setPort(uris[i].getPort())
					.setMaxPoolSize(poolSize)
					.setKeepAlive(false);
		}

		if (uris.length > 1) {
			slaves = new CopyOnWriteArrayList<>();
			checkHealth();
			checkTimerId = vertx.setPeriodic(delay, new Handler<Long>() {
				@Override
				public void handle(Long event) {
					checkHealth();
				}
			});
		} else {
			slaves = null;
			checkTimerId = -1;
		}
		rnd = new Random();
	}

	private void checkHealth() {
		for (int i = 0; i < clients.length; i++) {
			final int idx = i;
			HttpClient client = clients[i];
			if (client != null) {
				client.getNow("/db/manage/server/ha/available", new Handler<HttpClientResponse>() {
					@Override
					public void handle(HttpClientResponse resp) {
						if (resp.statusCode() == 200) {
							resp.bodyHandler(new Handler<Buffer>() {
								@Override
								public void handle(Buffer body) {
									if ("master".equals(body.toString())) {
										masterNode(idx);
									} else {
										slaveNode(idx);
									}
								}
							});
						} else {
							unavailableNode(idx);
						}
					}
				});
			} else {
				unavailableNode(idx);
			}
		}
	}

	private void masterNode(int idx) {
		int oldMaster = master.getAndSet(idx);
		if (oldMaster != idx) {
			slaves.remove(Integer.valueOf(idx));
			if (logger.isDebugEnabled()) {
				logger.debug("Neo4j new master node " + idx + " (" + clients[idx].getHost() + ").");
			}
		}
	}

	private void slaveNode(int idx) {
		master.compareAndSet(idx, -1);
		final boolean newSlave = slaves.addIfAbsent(idx);
		if (logger.isDebugEnabled() && newSlave) {
			logger.debug("Neo4j new slave node " + idx + " (" + clients[idx].getHost() + ").");
		}
	}

	private void unavailableNode(int idx) {
		master.compareAndSet(idx, -1);
		slaves.remove(Integer.valueOf(idx));
	}

	public HttpClient getClient() throws Neo4jConnectionException {
		try {
			return clients[master.get()];
		} catch (RuntimeException e) {
			throw new Neo4jConnectionException("Can't get master connection.", e);
		}
	}

	public HttpClient getSlaveClient() throws Neo4jConnectionException {
		if (slaves == null || slaves.size() < 1) {
			return getClient();
		}
		try {
			return clients[slaves.get(rnd.nextInt(slaves.size()))];
		} catch (RuntimeException e) {
			throw new Neo4jConnectionException("Can't get master connection.", e);
		}
	}

	public void close() {
		if (checkTimerId > 0) {
			vertx.cancelTimer(checkTimerId);
		}
		if (clients != null && clients.length > 0) {
			for (HttpClient client : clients) {
				if (client != null) {
					client.close();
				}
			}
		}
	}

}
