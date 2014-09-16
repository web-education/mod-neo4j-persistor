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

package fr.wseduc.neo4j.exception;

import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

public class ExceptionUtils {

	public static JsonObject exceptionToJson(Throwable e) {
		JsonArray stacktrace = new JsonArray();
		for (StackTraceElement s: e.getStackTrace()) {
			stacktrace.add(s.toString());
		}
		return new JsonObject()
			.putString("message", e.getMessage())
			.putString("exception", e.getClass().getSimpleName())
			.putString("fullname", e.getClass().getName())
			.putArray("stacktrace", stacktrace);
	}

}
