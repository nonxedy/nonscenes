package com.nonxedy.database.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.nonxedy.database.exception.DatabaseException;
import com.nonxedy.model.Cutscene;

/**
 * MongoDB implementation of CutsceneDatabaseService
 */
public class MongoDBCutsceneDatabaseService extends BaseCutsceneDatabaseService {

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> cutscenesCollection;
    private final String connectionString;
    private final String databaseName;
    private final String collectionName;

    public MongoDBCutsceneDatabaseService(String connectionString, String databaseName, String collectionName) {
        this.connectionString = connectionString;
        this.databaseName = databaseName != null ? databaseName : "minecraft";
        this.collectionName = collectionName != null ? collectionName : "cutscenes";
    }

    @Override
    public void initialize() throws DatabaseException {
        try {
            this.mongoClient = MongoClients.create(connectionString);
            this.database = mongoClient.getDatabase(databaseName);
            this.cutscenesCollection = database.getCollection(collectionName);

            // Create indexes for better performance
            cutscenesCollection.createIndex(Indexes.ascending("name"));
            cutscenesCollection.createIndex(Indexes.ascending("frames.frame_index"));

            logger.log(Level.INFO, "MongoDB database initialized with connection: {0}, database: {1}, collection: {2}",
                      new Object[]{connectionString, databaseName, collectionName});

        } catch (MongoException e) {
            throw new DatabaseException("Failed to initialize MongoDB database", e);
        }
    }

    @Override
    protected void performSaveCutscene(Cutscene cutscene) throws DatabaseException {
        try {
            CutsceneData data = convertFromCutscene(cutscene);

            // Create frames array
            List<Document> framesArray = new ArrayList<>();
            for (int i = 0; i < data.frames.size(); i++) {
                FrameData frame = data.frames.get(i);
                Document frameDoc = new Document()
                    .append("frame_index", i)
                    .append("world", frame.world)
                    .append("x", frame.x)
                    .append("y", frame.y)
                    .append("z", frame.z)
                    .append("yaw", frame.yaw)
                    .append("pitch", frame.pitch);
                framesArray.add(frameDoc);
            }

            // Create cutscene document
            Document cutsceneDoc = new Document()
                .append("_id", data.name.toLowerCase())
                .append("name", data.name)
                .append("frames", framesArray)
                .append("created_at", System.currentTimeMillis())
                .append("updated_at", System.currentTimeMillis());

            // Upsert the document
            Bson filter = Filters.eq("_id", data.name.toLowerCase());
            ReplaceOptions options = new ReplaceOptions().upsert(true);
            cutscenesCollection.replaceOne(filter, cutsceneDoc, options);

            logger.log(Level.FINE, "Successfully saved cutscene: {0}", data.name);

        } catch (MongoException e) {
            throw new DatabaseException("Failed to save cutscene: " + cutscene.getName(), e);
        }
    }

    @Override
    protected CutsceneData performLoadCutscene(String name) throws DatabaseException {
        try {
            Bson filter = Filters.eq("_id", name.toLowerCase());
            Document cutsceneDoc = cutscenesCollection.find(filter).first();

            if (cutsceneDoc == null) {
                return null;
            }

            List<Document> framesArray = cutsceneDoc.getList("frames", Document.class);
            if (framesArray == null || framesArray.isEmpty()) {
                return new CutsceneData(name, new ArrayList<>());
            }

            List<FrameData> frames = new ArrayList<>();
            for (Document frameDoc : framesArray) {
                FrameData frame = new FrameData(
                    frameDoc.getString("world"),
                    frameDoc.getDouble("x"),
                    frameDoc.getDouble("y"),
                    frameDoc.getDouble("z"),
                    frameDoc.getDouble("yaw").floatValue(),
                    frameDoc.getDouble("pitch").floatValue()
                );
                frames.add(frame);
            }

            return new CutsceneData(name, frames);

        } catch (MongoException e) {
            throw new DatabaseException("Failed to load cutscene: " + name, e);
        }
    }

    @Override
    protected List<CutsceneData> performLoadAllCutscenes() throws DatabaseException {
        try {
            List<CutsceneData> cutscenes = new ArrayList<>();

            for (Document cutsceneDoc : cutscenesCollection.find()) {
                String name = cutsceneDoc.getString("name");
                List<Document> framesArray = cutsceneDoc.getList("frames", Document.class);

                List<FrameData> frames = new ArrayList<>();
                if (framesArray != null) {
                    for (Document frameDoc : framesArray) {
                        FrameData frame = new FrameData(
                            frameDoc.getString("world"),
                            frameDoc.getDouble("x"),
                            frameDoc.getDouble("y"),
                            frameDoc.getDouble("z"),
                            frameDoc.getDouble("yaw").floatValue(),
                            frameDoc.getDouble("pitch").floatValue()
                        );
                        frames.add(frame);
                    }
                }

                cutscenes.add(new CutsceneData(name, frames));
            }

            return cutscenes;

        } catch (MongoException e) {
            throw new DatabaseException("Failed to load all cutscenes", e);
        }
    }

    @Override
    protected void performDeleteCutscene(String name) throws DatabaseException {
        try {
            Bson filter = Filters.eq("_id", name.toLowerCase());
            long deletedCount = cutscenesCollection.deleteOne(filter).getDeletedCount();

            if (deletedCount == 0) {
                throw new DatabaseException("Cutscene not found for deletion: " + name);
            }

        } catch (MongoException e) {
            throw new DatabaseException("Failed to delete cutscene: " + name, e);
        }
    }

    @Override
    protected boolean performCutsceneExists(String name) throws DatabaseException {
        try {
            Bson filter = Filters.eq("_id", name.toLowerCase());
            return cutscenesCollection.countDocuments(filter) > 0;

        } catch (MongoException e) {
            throw new DatabaseException("Failed to check if cutscene exists: " + name, e);
        }
    }

    @Override
    protected List<String> performGetCutsceneNames() throws DatabaseException {
        try {
            List<String> names = new ArrayList<>();

            for (Document cutsceneDoc : cutscenesCollection.find().projection(new Document("name", 1))) {
                names.add(cutsceneDoc.getString("name"));
            }

            return names;

        } catch (MongoException e) {
            throw new DatabaseException("Failed to get cutscene names", e);
        }
    }

    @Override
    public void shutdown() {
        if (mongoClient != null) {
            mongoClient.close();
            logger.info("MongoDB connection closed");
        }
    }
}
