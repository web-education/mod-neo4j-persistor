# Neo4j Persistor

This module allows data to be saved, retrieved, searched for, and deleted in a Neo4j instance.

####To use this module you can have a Neo4j instance running on your network or you can use Neo4j embedded with this module.


## Dependencies

This module can be used with a Neo4j server to be available on the network.

## Name

The module name is `neo4j-persistor`.

## Configuration

### Mode server

The neo4j-persistor module takes the following configuration:

    {
        "address": <address>,
        "server-uri": "http://localhost:7474/db/data/",
        "poolsize": <pool_size>
    }

For example:

    {
        "address": "neo4j.persistor",
        "server-uri": "http://localhost:7474/db/data/",
        "poolsize": 32
    }

Let's take a look at each field in turn:

* `address` The main address for the module. Every module has a main address. Defaults to `neo4j.persistor`.
* `server-uri` uri of the Neo4j instance.
* `pool_size` The number of socket connections the module instance should maintain to the Neo4j server. Default is 32.

#### HA

If you want to use the rest mode with Neo4j cluster. You can specify all server address. The persistor manage the check and change of master or slave.
For example : `"server-uris": ["http://10.13.2.244:7474/db/data/","http://10.13.2.245:7474/db/data/","http://10.13.2.246:7474/db/data/"]`

### Mode embedded

The neo4j-persistor module takes the following configuration:

    {
        "address": <address>,
        "datastore-path": <datastore>,
        "neo4j" : {
            "allow_store_upgrade" : <allow_store_upgrade>,
            "node_auto_indexing" :  <node_auto_indexing>,
            "node_keys_indexable" : <node_keys_indexable>
        }
    }

For example:

    {
        "address": "wse.neo4j.persistor",
        "datastore-path": "./data/tests/neo4j",
        "neo4j" : {
            "allow_store_upgrade" : "true",
            "node_auto_indexing" : "true",
            "node_keys_indexable" : "externalId"
        }
    }

Let's take a look at each field in turn:

* `address` The main address for the module. Every module has a main address. Defaults to `neo4j.persistor`.
* `datastore-path` path in filesystem to store neo4j data.
* `allow_store_upgrade` allow neo4j to migrate data when upgrade version.
* `node_auto_indexing` allow neo4j to auto index properties with lucene.
* `node_keys_indexable` specify properties to index with lucene.

## Operations

The module can execute Cypher queries in some modes

####Transactions are only availables in server mode. In embedded mode, queries are execute without transaction.

### Execute simple query

To execute a single query in the database.

    {
        "action" : "execute",
        "query" : <query>,
        "params" : <params>
    }

Where:
* `query` is the cypher query. This field is mandatory.
* `params` is the JSON object with query params.

An example would be:

    {
        "action" : "execute",
        "query" : "MATCH (u:User {name : {name}}) RETURN u",
        "params" : {
            "name" : "Dalek"
        }
    }

When the query complete successfully, a reply message is sent back to the sender with the following data:

    {
        "status": "ok",
        "result" : <result>
    }

Where
* `result` is an JsonArray of JsonObject.

If an error occurs in saving the document a reply is returned:

    {
        "status": "error",
        "message": <message>
    }

Where
* `message` is an error message.

### Execute batch queries

To execute a batch queries in the database.

    {
        "action" : "executeBatch",
        "queries" : <queries>,
    }

Where:
* `queries` is a JsonArray. This field is mandatory.

An example would be:

    {
        "action" : "executeBatch",
        "queries" : [
            {
                "query" : "MATCH (u:User {name : {name}}) RETURN u",
                "params" : {
                    "name" : "Dalek"
                }
            },
            {
                "query" : "MATCH (u:User {name : 'toto'}) RETURN u"
            }
        ]
    }

When the save complete successfully, a reply message is sent back to the sender with the following data:

    {
        "status": "ok",
        "results" : <result>
    }

Where
* `result` is an JsonArray of JsonObject.

If an error occurs in saving the document a reply is returned:

    {
        "status": "error",
        "message": <message>
    }

Where
* `message` is an error message.

### Execute transactions

To execute queries with transactions in the database.

    {
        "action" : "executeTransaction",
        "statements" : <statements>,
        "commit" : <commit>,
        "transactionId" : <transactionId>
    }

Where:
* `statements` is a JsonArray. This field is mandatory.
* `commit` is a boolean. If is true the transaction be commit. Else, return transactionId without commit.
* `transactionId` is a Integer to retrieve transaction.

An example would be:

    {
        "action" : "executeTransaction",
        "statements" : [
            {
                "statement" : "MATCH (u:User {name : {name}}) RETURN u",
                "parameters" : {
                    "name" : "Dalek"
                }
            },
            {
                "statement" : "MATCH (u:User {name : 'toto'}) RETURN u"
            }
        ],
        "commit" : true
    }

When the save complete successfully, a reply message is sent back to the sender with the following data:

    {
        "status": "ok",
        "results" : <result>,
        "transactionId" : <transactionId>
    }

Where
* `result` is an JsonArray of JsonObject.
* `transactionId` is an integer. Is returned only if transaction are not committed.

If an error occurs in saving the document a reply is returned:

    {
        "status": "error",
        "message": <message>
    }

Where
* `message` is an error message.

### Reset Transaction Timeout

All transaction are a timeout value. You can use this method to reset timeout without commit transaction.

    {
        "action" : "resetTransactionTimeout",
        "transactionId" : <transactionId>
    }

Where:
* `transactionId` is a Integer to retrieve transaction.

An example would be:

    {
        "action" : "resetTransactionTimeout",
        "transactionId" : 42
    }

When the save complete successfully, a reply message is sent back to the sender with the following data:

    {
        "status": "ok",
        "results" : <result>,
        "transactionId" : <transactionId>
    }

Where
* `result` is an JsonArray of JsonObject.
* `transactionId` is an integer. Is returned only if transaction are not committed.

If an error occurs in saving the document a reply is returned:

    {
        "status": "error",
        "message": <message>
    }

Where
* `message` is an error message.

### Rollback Transaction

You can use this method to rollback transaction.

    {
        "action" : "rollbackTransaction",
        "transactionId" : <transactionId>
    }

Where:
* `transactionId` is a Integer to retrieve transaction.

An example would be:

    {
        "action" : "rollbackTransaction",
        "transactionId" : 42
    }

When the save complete successfully, a reply message is sent back to the sender with the following data:

    {
        "status": "ok",
    }

If an error occurs in saving the document a reply is returned:

    {
        "status": "error",
        "message": <message>
    }

Where
* `message` is an error message.
