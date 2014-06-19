package fr.wseduc.neo4j.test.integration.java;

import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;

import java.io.IOException;

import static org.vertx.testtools.VertxAssert.assertEquals;
import static org.vertx.testtools.VertxAssert.testComplete;

public class Neo4jPersistorTest extends TestVerticle {

	private static final String TEST_PERSISTOR = "test.persistor";
	private TemporaryFolder tmpFolder;

	@Override
	public void start() {
		super.start();
		tmpFolder = new TemporaryFolder();
		try {
			tmpFolder.create();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		JsonObject config = new JsonObject();
		config.putString("address", TEST_PERSISTOR);
		config.putString("datastore-path", tmpFolder.getRoot().getAbsolutePath());
		container.deployModule(System.getProperty("vertx.modulename"), config, 1,
				new AsyncResultHandler<String>() {
			public void handle(AsyncResult<String> ar) {
				if (ar.succeeded()) {
					Neo4jPersistorTest.super.start();
				} else {
					ar.cause().printStackTrace();
				}
			}
		});
	}

	@Override
	public void stop() {
		super.stop();
		if (tmpFolder != null) {
			tmpFolder.delete();
		}
	}

	private void execute(String query, JsonObject params, Handler<Message<JsonObject>> handler) {
		JsonObject jo = new JsonObject();
		jo.putString("action", "execute");
		jo.putString("query", query);
		if (params != null) {
			jo.putObject("params", params);
		}
		vertx.eventBus().send(TEST_PERSISTOR, jo, handler);
	}

	@Test
	public void testAddIndexes() {
		String query = "CREATE CONSTRAINT ON (actor:Actor) ASSERT actor.name IS UNIQUE;";
		execute(query, null, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				assertEquals("ok", message.body().getString("status"));
				testComplete();
			}
		});

	}

	@Test
	public void testExecuteQueries() {
		String query =
				"CREATE (:Actor {doctor1})-[:REGENERATED_TO]->" +
				"(:Actor {doctor2})-[:REGENERATED_TO]->" +
				"(:Actor {doctor3})-[:REGENERATED_TO]->" +
				"(:Actor {doctor4})-[:REGENERATED_TO]->" +
				"(:Actor {doctor5})-[:REGENERATED_TO]->" +
				"(:Actor {doctor6})-[:REGENERATED_TO]->" +
				"(:Actor {doctor7})-[:REGENERATED_TO]->" +
				"(:Actor {doctor8})-[:REGENERATED_TO]->" +
				"(:Actor {theWarDoctor})-[:REGENERATED_TO]->" +
				"(:Actor {doctor9})-[:REGENERATED_TO]->" +
				"(:Actor {doctor10})-[:REGENERATED_TO]->" +
				"(:Actor {doctor11})-[:REGENERATED_TO]->" +
				"(:Actor {doctor12})";
		JsonObject params = new JsonObject()
				.putObject("doctor1", new JsonObject()
						.putString("name", "William Hartnell").putNumber("start", 1963).putNumber("end", 1966))
				.putObject("doctor2", new JsonObject()
						.putString("name", "Patrick Troughton").putNumber("start", 1966).putNumber("end", 1969))
				.putObject("doctor3", new JsonObject()
						.putString("name", "Jon Pertwee").putNumber("start", 1970).putNumber("end", 1974))
				.putObject("doctor4", new JsonObject()
						.putString("name", "Tom Baker").putNumber("start", 1974).putNumber("end", 1981))
				.putObject("doctor5", new JsonObject()
						.putString("name", "Peter Davison").putNumber("start", 1981).putNumber("end", 1984))
				.putObject("doctor6", new JsonObject()
						.putString("name", "Colin Baker").putNumber("start", 1984).putNumber("end", 1986))
				.putObject("doctor7", new JsonObject()
						.putString("name", "Sylvester McCoy").putNumber("start", 1987).putNumber("end", 1989))
				.putObject("doctor8", new JsonObject()
						.putString("name", "Paul McGann").putNumber("year", 1996))
				.putObject("theWarDoctor", new JsonObject()
						.putString("name", "John Hurt").putNumber("start", 1963).putNumber("end", 1966))
				.putObject("doctor9", new JsonObject()
						.putString("name", "Christopher Eccleston").putNumber("year", 2005))
				.putObject("doctor10", new JsonObject()
						.putString("name", "David Tennant").putNumber("start", 2005).putNumber("end", 2010))
				.putObject("doctor11", new JsonObject()
						.putString("name", "Matt Smith").putNumber("start", 2010).putNumber("end", 2013))
				.putObject("doctor12", new JsonObject()
						.putString("name", "Peter Capaldi").putNumber("start", 2013));
		execute(query, params, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				assertEquals("ok", message.body().getString("status"));
				String query =
						"CREATE (d:Character {name : 'The Doctor'}) " +
						"WITH d " +
						"MATCH (a:Actor) " +
						"CREATE a-[r:ACT]->d " +
						"RETURN count(r) as nb ";
				execute(query, null, new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> message) {
						assertEquals("ok", message.body().getString("status"));
						JsonObject r = message.body().getArray("result").get(0);
						assertEquals(13, (int) r.getInteger("nb", 0));
						testComplete();
					}
				});
			}
		});

	}

}
