package org.example;

import com.google.gson.Gson;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.descending;
import static spark.Spark.*;

public class AuctionApp {
    public static void main(String[] args) {
        port(8081);

        options("/*", (request, response) -> {
            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }

            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }
            return "OK";
        });

        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*"); // Allow all origins or specify allowed ones
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        });


        MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017");
        MongoDatabase db = mongoClient.getDatabase("mproject");
        MongoCollection<Document> auctionCollection = db.getCollection("auction_item");
        MongoCollection<Document> bidsCollection = db.getCollection("user");

        Gson gson = new Gson();

        // Get auction item
//        get("/auction", (req, res) -> {
//            Document auction = auctionCollection.find().first();
//            if (auction == null) {
//                res.status(404);
//                return "No auction found";
//            }
//            return gson.toJson(auction);
//        });
        get("/auction", (req, res) -> {
            Document auction = auctionCollection.find().sort(descending("_id")).first();
            if (auction == null) {
                res.status(404);
                return "No auction found";
            }
            return gson.toJson(auction);
        });



        // Get all bids
        get("/bids", (req, res) -> {
            List<Document> bids = bidsCollection.find().into(new ArrayList<>());
            return gson.toJson(bids);
        });

//        // Add a new bid
//        post("/bids", (req, res) -> {
//            Document bid = Document.parse(req.body());
//
////            Document bid = new Document("id",1);
////            bid.append("prize",3000);
//            bidsCollection.insertOne(bid);
////            System.out.println("success");
//            res.status(201);
//            return "Bid submitted";
//        });
        post("/bids", (req, res) -> {
            Document bid = Document.parse(req.body());
            if (!bid.containsKey("name") || !bid.containsKey("prize")) {
                res.status(400);
                return "Invalid bid data.";
            }

            bidsCollection.insertOne(bid);

            res.status(201);
            return "Bid submitted successfully.";
        });

        // Get the winner
        get("/winner", (req, res) -> {
            Document winner = bidsCollection.find().sort(descending("prize")).first();
            if (winner == null) {
                res.status(404);
                return "No winner found";
            }
            return gson.toJson(winner);
        });

        post("/auction", (req, res) -> {
            Document auction = Document.parse(req.body());
            //auctionCollection.drop(); // Remove any previous auction
            bidsCollection.drop(); // Remove any previous auction
            auctionCollection.insertOne(auction);
            res.status(201);
            return "Auction created";
        });

//        put("/change-bid", (req, res) -> {
//            Document updatedBid = Document.parse(req.body());
//            String email = updatedBid.getString("email");
//            double newPrize = updatedBid.getDouble("prize");
//
//            Document existingBid = bidsCollection.find(eq("email", email)).first();
//            if (existingBid == null) {
//                res.status(404);
//                return "No bid found for the provided email.";
//            }
//
//            // Validate new bid amount
//            if (newPrize <= existingBid.getDouble("prize")) {
//                res.status(400);
//                return "New bid amount must be higher than the current bid.";
//            }
//
//            bidsCollection.updateOne(eq("email", email), new Document("$set", new Document("prize", newPrize)));
//            return "Bid updated successfully.";
//        });
        put("/change-bid", (req, res) -> {
            Document updatedBid = Document.parse(req.body());
            String email = updatedBid.getString("email");
            double newPrize = updatedBid.getDouble("prize");

            // Validate the input
            if (email == null || email.isEmpty()) {
                res.status(400);
                return "Email is required.";
            }
            if (newPrize <= 0) {
                res.status(400);
                return "Bid amount must be greater than zero.";
            }

            // Check if the bid exists for the given email
            Document existingBid = bidsCollection.find(eq("email", email)).first();
            if (existingBid == null) {
                res.status(404);
                return "No bid found for the provided email.";
            }

            // Validate the new bid amount
            double currentPrize = existingBid.getDouble("prize");
            if (newPrize <= currentPrize) {
                res.status(400);
                return "New bid amount must be higher than the current bid.";
            }

            // Update the bid in the database
            bidsCollection.updateOne(eq("email", email), new Document("$set", new Document("prize", newPrize)));
            res.status(200);
            return "Bid updated successfully.";
        });



    }
}
