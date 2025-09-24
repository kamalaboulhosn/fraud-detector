package data_generator;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Generates simulated credit card transactions and publishes them to Google
 * Cloud Pub/Sub with an ordering key.
 * This code was generated with Gemini.
 */
public class TransactionGenerator {

    // --- Configuration ---
    private static final String PROJECT_ID = "my-project";
    private static final String TOPIC_ID = "raw-transactions";

    // --- Fraud Injection Configuration ---
    private static final double FRAUD_PROBABILITY = 0.02;
    private static final double FRAUD_MIN_AMOUNT = 2000.0;
    private static final double FRAUD_MAX_AMOUNT = 7000.0;

    // --- Sticky IP Configuration ---
    private static final double IP_CHANGE_PROBABILITY = 0.005; // 0.5%

    // --- Time Increment Configuration ---
    private static final long MIN_TIME_INCREMENT_MS = 1000L; // 1 second
    private static final long MAX_TIME_INCREMENT_MS = 3600000L; // 1 hour (60 * 60 * 1000)

    // --- Data Pool Generators ---

    // Static list of ~300 real receivers
    private static final List<String> RECEIVERS = Arrays.asList(
            // Retail (General)
            "Walmart", "Target", "Costco Wholesale", "Kmart", "Meijer", "Kroger", "Publix", "Safeway",
            "Albertsons", "Whole Foods Market", "Trader Joe's", "Aldi", "Lidl", "Wegmans", "H-E-B",
            "Stop & Shop", "Giant Food", "Food Lion", "Winn-Dixie", "Piggly Wiggly", "Sprouts Farmers Market",

            // Retail (Hardware/Home)
            "The Home Depot", "Lowe's", "Ace Hardware", "True Value", "Menards", "Harbor Freight Tools",
            "Tractor Supply Co.", "Bed Bath & Beyond", "IKEA", "Crate & Barrel", "Williams-Sonoma",
            "Pottery Barn", "Restoration Hardware", "At Home", "Floor & Decor",

            // Retail (Electronics/Office)
            "Best Buy", "Micro Center", "Apple Store", "Microsoft Store", "GameStop", "Staples",
            "Office Depot", "OfficeMax", "CDW", "Newegg.com",

            // Retail (Apparel)
            "Macy's", "Nordstrom", "Dillard's", "Kohl's", "JCPenney", "Saks Fifth Avenue", "Neiman Marcus",
            "Bloomingdale's", "Gap", "Old Navy", "Banana Republic", "J.Crew", "H&M", "Zara", "Uniqlo",
            "Forever 21", "American Eagle Outfitters", "Abercrombie & Fitch", "Hollister Co.", "Lululemon",
            "Nike", "Adidas", "Puma", "Under Armour", "Reebok", "Dick's Sporting Goods", "Academy Sports + Outdoors",
            "REI", "Cabela's", "Bass Pro Shops", "Foot Locker", "Victoria's Secret", "Bath & Body Works",
            "The Children's Place", "Carter's",

            // Retail (Pharmacies)
            "CVS Pharmacy", "Walgreens", "Rite Aid", "GoodRx",

            // Retail (Discount)
            "Dollar General", "Dollar Tree", "Family Dollar", "Five Below", "Big Lots", "Ollie's Bargain Outlet",

            // Retail (Online)
            "Amazon.com", "eBay", "Etsy", "Wayfair", "Overstock.com", "Zappos", "Chewy", "Wish.com",

            // Restaurants (Fast Food)
            "McDonald's", "Burger King", "Wendy's", "Taco Bell", "Chick-fil-A", "Subway", "KFC",
            "Popeyes", "Arby's", "Jack in the Box", "Sonic Drive-In", "Whataburger", "In-N-Out Burger",
            "Five Guys", "Shake Shack", "Pizza Hut", "Domino's", "Papa John's", "Little Caesars",
            "Panda Express", "Chipotle Mexican Grill", "Qdoba", "Moe's Southwest Grill", "Del Taco",

            // Restaurants (Casual/Coffee)
            "Starbucks", "Dunkin'", "Panera Bread", "Tim Hortons", "Peet's Coffee", "The Coffee Bean & Tea Leaf",
            "Applebee's", "Chili's Grill & Bar", "TGI Fridays", "Olive Garden", "Red Lobster", "Outback Steakhouse",
            "Texas Roadhouse", "LongHorn Steakhouse", "The Cheesecake Factory", "Red Robin", "Buffalo Wild Wings",
            "Denny's", "IHOP", "Cracker Barrel", "Waffle House", "P.F. Chang's",

            // Tech & Services
            "Google", "Microsoft", "Apple Inc.", "Meta Platforms", "Amazon Web Services", "Netflix", "Spotify",
            "Hulu", "Disney+", "Salesforce", "Oracle", "IBM", "Intel", "AMD", "Nvidia", "Dell Technologies",
            "HP Inc.", "Cisco Systems", "Adobe", "Zoom Video", "Uber", "Lyft", "DoorDash", "Grubhub",
            "Instacart", "Airbnb", "PayPal", "Block (Square)", "Stripe", "Shopify", "GoDaddy", "Intuit",
            "Dropbox", "Slack", "X (Twitter)",

            // Travel & Auto
            "Delta Air Lines", "American Airlines", "United Airlines", "Southwest Airlines", "JetBlue",
            "Alaska Airlines", "Spirit Airlines", "Frontier Airlines", "Marriott International", "Hilton",
            "Hyatt Hotels", "IHG Hotels & Resorts", "Wyndham Hotels", "Choice Hotels", "Best Western",
            "Expedia", "Booking.com", "Enterprise Rent-A-Car", "Hertz", "Avis", "Budget", "AutoZone",
            "O'Reilly Auto Parts", "Advance Auto Parts", "NAPA Auto Parts", "Pep Boys",

            // Charities & Non-Profits
            "American Red Cross", "Doctors Without Borders", "UNICEF", "Habitat for Humanity",
            "St. Jude Children's Research Hospital", "The Humane Society", "WWF (World Wildlife Fund)",
            "Sierra Club", "The Nature Conservancy", "Feeding America", "Goodwill Industries",
            "The Salvation Army", "United Way", "Boys & Girls Clubs of America", "Make-A-Wish Foundation",
            "Susan G. Komen", "American Cancer Society", "American Heart Association", "Save the Children",
            "Shriners Hospitals for Children", "Wounded Warrior Project", "ASPCA", "Charity: Water",

            // Utilities & Telecom
            "AT&T", "Verizon", "T-Mobile", "Comcast (Xfinity)", "Charter (Spectrum)", "Cox Communications",
            "Duke Energy", "NextEra Energy", "Southern Company", "Dominion Energy", "Exelon",
            "Pacific Gas and Electric (PG&E)", "Con Edison",

            // Finance & Insurance
            "Bank of America", "JPMorgan Chase", "Wells Fargo", "Citigroup", "Goldman Sachs", "Morgan Stanley",
            "U.S. Bank", "PNC", "Capital One", "American Express", "Visa", "Mastercard", "Discover",
            "Geico", "Progressive", "State Farm", "Allstate", "Liberty Mutual",

            // Miscellaneous
            "7-Eleven", "Circle K", "Shell", "ExxonMobil", "BP", "Chevron", "Marathon Petroleum",
            "Sheetz", "Wawa", "The LEGO Group", "Mattel", "Hasbro", "The Walt Disney Company",
            "Paramount", "Warner Bros.", "Sony Pictures", "Universal Pictures");

    // --- THIS IS THE FIX ---
    // Initialize 'random' *before* it is used by generateCardNumbers.
    private static final Random random = new Random();

    // Programmatic generation for CARD_NUMBERS is retained
    private static final List<String> CARD_NUMBERS = generateCardNumbers(10000);

    private static final Gson gson = new Gson();

    // Map to store the "home" IP for each credit card
    private static final Map<String, String> cardIpMap = new HashMap<>();

    // Simulated Global Clock (as epoch milliseconds)
    private static long simulatedCurrentTime = System.currentTimeMillis() - (262800L * 60 * 1000);

    /**
     * Generates a random string of digits of a given length.
     */
    private static String generateRandomDigits(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * Programmatically generates a list of fake credit card numbers.
     */
    private static List<String> generateCardNumbers(int count) {
        List<String> cardNumbers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int type = random.nextInt(10); // 40% Visa, 40% MC, 20% Amex
            if (type < 4) {
                // Visa-like (16 digits)
                cardNumbers.add("4200" + generateRandomDigits(12));
            } else if (type < 8) {
                // Mastercard-like (16 digits)
                cardNumbers.add("5500" + generateRandomDigits(12));
            } else {
                // Amex-like (15 digits)
                cardNumbers.add("3700" + generateRandomDigits(11));
            }
        }
        System.out.println("Generated " + cardNumbers.size() + " card numbers.");
        return cardNumbers;
    }

    /**
     * POJO for the transaction event.
     */
    static class TransactionEvent {
        String credit_card_number;
        String receiver;
        double amount;
        String ip_address;
        String timestamp;

        public TransactionEvent(String creditCardNumber, String receiver, double amount, String ipAddress,
                String timestamp) {
            this.credit_card_number = creditCardNumber;
            this.receiver = receiver;
            this.amount = amount;
            this.ip_address = ipAddress;
            this.timestamp = timestamp;
        }
    }

    // --- Helper Methods ---

    private static String getRandomCardNumber() {
        return CARD_NUMBERS.get(random.nextInt(CARD_NUMBERS.size()));
    }

    private static String getRandomReceiver() {
        return RECEIVERS.get(random.nextInt(RECEIVERS.size()));
    }

    private static double getRandomAmount() {
        double amount = 1.0 + (500.0 - 1.0) * random.nextDouble();
        return Math.round(amount * 100.0) / 100.0;
    }

    private static double getRandomFraudAmount() {
        double amount = FRAUD_MIN_AMOUNT + (FRAUD_MAX_AMOUNT - FRAUD_MIN_AMOUNT) * random.nextDouble();
        return Math.round(amount * 100.0) / 100.0;
    }

    private static String generateNewRandomIp() {
        return random.nextInt(256) + "." + random.nextInt(256) + "." +
                random.nextInt(256) + "." + random.nextInt(256);
    }

    private static String getIpForCard(String cardNumber) {
        if (!cardIpMap.containsKey(cardNumber)) {
            String homeIp = generateNewRandomIp();
            cardIpMap.put(cardNumber, homeIp);
            return homeIp;
        } else {
            if (random.nextDouble() < IP_CHANGE_PROBABILITY) {
                String newIp = generateNewRandomIp();
                cardIpMap.put(cardNumber, newIp);
                return newIp;
            } else {
                return cardIpMap.get(cardNumber);
            }
        }
    }

    // --- Main Execution ---

    public static void main(String[] args) throws Exception {
        ProjectTopicName topicName = ProjectTopicName.of(PROJECT_ID, TOPIC_ID);
        Publisher publisher = null;

        try {
            publisher = Publisher.newBuilder(topicName)
                    .setEnableMessageOrdering(true)
                    .build();

            System.out.println("Starting transaction generation for topic: " + topicName);
            System.out.println("Using static list of " + RECEIVERS.size() + " real receivers.");
            System.out.println("Injecting fraud transactions with " + (FRAUD_PROBABILITY * 100) + "% probability.");
            System.out.println("Press Ctrl+C to stop.");

            while (true) {
                // --- 0. Simulate Time Passing ---
                // Advance the simulated clock by 1 second to 1 hour (in milliseconds)
                // Formula: random.nextInt(max - min + 1) + min
                int incrementRange = (int) (MAX_TIME_INCREMENT_MS - MIN_TIME_INCREMENT_MS + 1);
                long timeIncrement = MIN_TIME_INCREMENT_MS + random.nextInt(incrementRange);
                simulatedCurrentTime += timeIncrement;

                // --- CONVERT TO BQ DATETIME STRING ---
                Instant instant = Instant.ofEpochMilli(simulatedCurrentTime);
                // We use UTC as the zone for the DATETIME string
                LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
                // ISO_LOCAL_DATE_TIME produces "YYYY-MM-DD'T'HH:MM:SS"
                String eventTimestamp = ldt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

                String cardNumber;
                String receiver;
                double amount;
                String ipAddress;

                if (random.nextDouble() < FRAUD_PROBABILITY) {
                    // --- 1. Generate FRAUDULENT Transaction ---
                    System.out.println(">>> Injecting FRAUDULENT transaction...");
                    cardNumber = getRandomCardNumber();
                    receiver = getRandomReceiver();
                    amount = getRandomFraudAmount(); // High value
                    ipAddress = generateNewRandomIp(); // New, non-sticky IP

                    System.out.printf("  -> FRAUD Data: [Card: ...%s, IP: %s, Amt: $%.2f]%n",
                            cardNumber.substring(cardNumber.length() - 4), ipAddress, amount);

                } else {
                    // --- 2. Generate NORMAL Transaction ---
                    cardNumber = getRandomCardNumber();
                    receiver = getRandomReceiver();
                    amount = getRandomAmount(); // Normal value
                    ipAddress = getIpForCard(cardNumber); // Use "sticky" IP logic
                }

                // --- 3. Common Publishing Logic ---
                // Create the event POJO *with the new timestamp string*
                TransactionEvent event = new TransactionEvent(
                        cardNumber, receiver, amount, ipAddress, eventTimestamp);
                String jsonMessage = gson.toJson(event);

                System.out.println("Publishing: " + jsonMessage);

                // 4. Convert to ByteString
                ByteString data = ByteString.copyFromUtf8(jsonMessage);

                // 5. Build the message with the ordering key
                PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                        .setData(data)
                        .setOrderingKey(cardNumber) // Set ordering key
                        .build();

                // 6. Publish the message
                ApiFuture<String> future = publisher.publish(pubsubMessage);

                // 7. Add a callback to log success/failure
                ApiFutures.addCallback(future, new ApiFutureCallback<String>() {
                    @Override
                    public void onFailure(Throwable t) {
                        System.err.println("  -> Error publishing message: " + t.getMessage());
                    }

                    @Override
                    public void onSuccess(String messageId) {
                        System.out.println("  -> Published message with ID: " + messageId);
                    }
                }, MoreExecutors.directExecutor());

                // 8. Wait for 1 second (real time)
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            System.out.println("Transaction generator interrupted. Shutting down.");
        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 9. Shut down the publisher gracefully
            if (publisher != null) {
                System.out.println("Shutting down publisher...");
                publisher.shutdown();
                publisher.awaitTermination(1, TimeUnit.MINUTES);
                System.out.println("Publisher shut down.");
            }
        }
    }
}