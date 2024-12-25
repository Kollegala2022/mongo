package org.example;

import com.google.gson.Gson;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.descending;
import static spark.Spark.*;

public class updateApp {
    private static final ConcurrentHashMap<String, String> otpStore = new ConcurrentHashMap<>();

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
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        });

        MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017");
        MongoDatabase db = mongoClient.getDatabase("mproject");
        MongoCollection<Document> auctionCollection = db.getCollection("auction_item");
        MongoCollection<Document> bidsCollection = db.getCollection("user");
        MongoCollection<Document> countersCollection = db.getCollection("counters");

        Gson gson = new Gson();

        // Initialize counters for bidder IDs
        if (countersCollection.find(eq("_id", "bidderId")).first() == null) {
            countersCollection.insertOne(new Document("_id", "bidderId").append("seq", 0));
        }

        // Get recent auction
        get("/auction", (req, res) -> {
            Document auction = auctionCollection.find().sort(descending("_id")).first();
            if (auction == null) {
                res.status(404);
                return "No auction found";
            }
            return gson.toJson(auction);
        });

        // Create a new auction
        post("/auction", (req, res) -> {
            Document auction = Document.parse(req.body());
            auctionCollection.insertOne(auction);
            bidsCollection.drop(); // Clear bids for new auction
            res.status(201);
            return "Auction created successfully.";
        });

        // Fetch all bids for a specific auction
        get("/bids/:auctionId", (req, res) -> {
            String auctionId = req.params(":auctionId");
            List<Document> bids = bidsCollection.find(eq("auctionId", auctionId)).into(new ArrayList<>());
            return gson.toJson(bids);
        });

        // Submit a bid
        post("/bids", (req, res) -> {
            Document bid = Document.parse(req.body());
            String auctionId = bid.getString("auctionId");

            // Validate bid amount
            Document highestBid = bidsCollection.find(eq("auctionId", auctionId)).sort(descending("prize")).first();
            if (highestBid != null && bid.getDouble("prize") <= highestBid.getDouble("prize")) {
                res.status(400);
                return "Bid amount must be higher than the current highest bid.";
            }

            int bidderId = getNextBidderId(countersCollection); // Make sure function is called correctly
            bid.append("bidderId", bidderId);
            bidsCollection.insertOne(bid);
            res.status(201);
            return "Bid submitted successfully.";
        });

        // Send OTP
        post("/send-otp", (req, res) -> {
            Document request = Document.parse(req.body());
            String phone = request.getString("phone");
            String otp = generateOTP();
            otpStore.put(phone, otp);
            sendOtpToPhone(phone, otp);
            return "OTP sent successfully.";
        });

        // Verify OTP
        post("/verify-otp", (req, res) -> {
            Document request = Document.parse(req.body());
            String phone = request.getString("phone");
            String enteredOtp = request.getString("otp");
            String actualOtp = otpStore.get(phone);

            if (actualOtp != null && actualOtp.equals(enteredOtp)) {
                otpStore.remove(phone);
                return "OTP verified successfully.";
            } else {
                res.status(400);
                return "Invalid OTP.";
            }
        });

        // Fetch winner
        get("/winner", (req, res) -> {
            Document winner = bidsCollection.find().sort(descending("prize")).first();
            if (winner == null) {
                res.status(404);
                return "No winner found.";
            }

            String email = winner.getString("email");
            String message = "Congratulations! You won the auction with a bid of " + winner.getDouble("prize") + ".";
            sendEmail(email, "Auction Winner Notification", message);

            return gson.toJson(winner);
        });

        // Add the following endpoint to handle bid changes:

// Change an existing bid
        put("/change-bid", (req, res) -> {
            Document updatedBid = Document.parse(req.body());
            String email = updatedBid.getString("email");
            double newPrize = updatedBid.getDouble("prize");

            Document existingBid = bidsCollection.find(eq("email", email)).first();
            if (existingBid == null) {
                res.status(404);
                return "No bid found for the provided email.";
            }

            // Validate new bid amount
            if (newPrize <= existingBid.getDouble("prize")) {
                res.status(400);
                return "New bid amount must be higher than the current bid.";
            }

            bidsCollection.updateOne(eq("email", email), new Document("$set", new Document("prize", newPrize)));
            return "Bid updated successfully.";
        });

    }

    // Get next bidder ID
    private static int getNextBidderId(MongoCollection<Document> countersCollection) {
        Document updatedCounter = countersCollection.findOneAndUpdate(
                eq("_id", "bidderId"),
                new Document("$inc", new Document("seq", 1))
        );
        return updatedCounter.getInteger("seq");
    }


    private static String generateOTP() {
        Random random = new Random();
        return String.valueOf(100000 + random.nextInt(900000));
    }

    private static void sendOtpToPhone(String phone, String otp) {
        System.out.println("Sending OTP " + otp + " to phone " + phone);
    }

    private static void sendEmail(String email, String subject, String message) {
        System.out.println("Sending email to " + email + ": " + subject + " - " + message);
    }
}
