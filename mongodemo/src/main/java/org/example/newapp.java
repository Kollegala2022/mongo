package org.example;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

import static spark.Spark.*;

public class newapp {
    private static MongoDatabase database;

    public static void main(String[] args) {
        port(8081);

        MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017");
        database = mongoClient.getDatabase("auctionDB");

        before((req, res) -> {
            res.header("Access-Control-Allow-Origin", "*");
            res.header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            res.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        });

        options("/*", (req, res) -> "OK");

        post("/auction", (req, res) -> {
            Document auction = Document.parse(req.body());
            auction.append("bids", new ArrayList<>());
            database.getCollection("auctions").insertOne(auction);
            res.status(201);
            return "Auction created";
        });

        get("/auction", (req, res) -> {
            Document auction = database.getCollection("auctions").find().first();
            if (auction != null) {
                res.type("application/json");
                return auction.toJson();
            }
            res.status(404);
            return "No auction found";
        });

        post("/bids", (req, res) -> {
            Document bid = Document.parse(req.body());
            database.getCollection("bids").insertOne(bid);
            res.status(201);
            return "Bid placed";
        });

        put("/change-bid", (req, res) -> {
            Document bid = Document.parse(req.body());
            String email = bid.getString("email");
            Double newPrize = bid.getDouble("prize");

            Document existingBid = database.getCollection("bids").find(new Document("email", email)).first();

            if (existingBid != null && newPrize > existingBid.getDouble("prize")) {
                database.getCollection("bids").updateOne(
                        new Document("email", email),
                        new Document("$set", new Document("prize", newPrize))
                );
                return "Bid updated";
            }

            res.status(400);
            return "Failed to update bid";
        });

//        get("/bids", (req, res) -> {
//            List<Document> bids = database.getCollection("bids").find().into(new ArrayList<>());
//            res.type("application/json");
//            return bids.toString();
//        });
        get("/bids", (req, res) -> {
            List<Document> bids = database.getCollection("bids").find().into(new ArrayList<>());
            res.type("application/json");
            return bids.toString();
        });

        get("/winner", (req, res) -> {
            Document winner = database.getCollection("bids").find()
                    .sort(new Document("prize", -1))
                    .first();

            if (winner != null) {
                res.type("application/json");
                return winner.toJson();
            }
            res.status(404);
            return "No winner found";
        });

        exception(Exception.class, (exception, req, res) -> {
            exception.printStackTrace();
            res.status(500);
            res.body("Internal server error: " + exception.getMessage());
        });
    }
}

