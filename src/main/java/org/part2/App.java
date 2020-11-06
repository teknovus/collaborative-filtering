package org.part2;

import com.opencsv.CSVReader;
import com.rits.cloning.Cloner;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hello Graders
 * I built this project on my personal machine, so it will not build properly on mc18 without maven I think
 * But the resulting jar is in fact compiled from this source
 */
public class App {
    // Boy do I hope this does not change, because I did not implement for any more or less dishes
    private static int NUM_DISHES = 1000;
    // We are not reporting anything above @20, so no need to calculate too many values
    public static final int MAX_REPORTING = 20;

    private static String[] schema;


    // True paths TODO: Set to True paths for final build
    
    private static final String DISH_PATH = "/homes/cs473/project2/data/dishes.csv";
    private static final String TRAINING_PATH = "/homes/cs473/project2/data/user_ratings_train.json";
    private static final String TEST_PATH = "/homes/cs473/project2/data/user_ratings_test.json";
    //*/

    // Local paths
/*
    private static final String DISH_PATH = "src/main/java/org/part1/dishes.csv";
    private static final String TRAINING_PATH = "src/main/java/org/part1/user_ratings_train.json";
    private static final String TEST_PATH = "src/main/java/org/part1/user_ratings_test.json";
    //*/

    public static void main(String[] args) {
        //System.out.println("Memory-Based Collaborative Filtering Using Vector Space Similarity");
        JSONParser parser = new JSONParser();
        ArrayList<User> users = new ArrayList<User>();
        ArrayList<User> testUsers = new ArrayList<User>();
        ArrayList<User> predictions = new ArrayList<User>();
        List<String[]> dishes = null;
        ArrayList<double[]> similarDishes = new ArrayList<double[]>();
        try {
            //Read in data into user arraylist
            //System.out.printf("Reading Training Data at %s ...\n", TRAINING_PATH);
            JSONObject trainingJSON = (JSONObject) parser.parse(new FileReader(TRAINING_PATH));
            int userNum = 0;
            while(true) {
                JSONArray userRatings = (JSONArray) trainingJSON.get(userNum + ""); //array of key value pairs
                if (userRatings == null) break;
                userNum++;
                users.add(new User(userRatings));
            }

            normalizeUserRatings(users);

            CSVReader csvReader = new CSVReader(new FileReader(DISH_PATH));
            dishes = csvReader.readAll();
            schema = dishes.remove(0);
            NUM_DISHES = dishes.size();

            for(int i = 0; i < dishes.size(); i++) {
                double[] similarity = new double[dishes.size()];
                for(int j = 0; j < dishes.size(); j++) {
                    similarity[j] = cosineSimilarity(dishes.get(i), dishes.get(j));
                }
                similarDishes.add(similarity);
            }


            //System.out.printf("Reading Test Data at %s ...\n", TEST_PATH);
            JSONObject testJSON = (JSONObject) parser.parse(new FileReader(TEST_PATH));
            userNum = 0;
            while(userNum < users.size()) {
                JSONArray userRatings = (JSONArray) testJSON.get(userNum + ""); //array of key value pairs
                userNum++;
                if (userRatings == null) continue;
                testUsers.add(new User(userRatings, userNum - 1));
            }

            //System.out.println("Training & Generating Recommendations...");

            for (User testUser: testUsers) {
                User recommendation = generatePredictions(similarDishes, users.get(testUser.getUserNum()));
                recommendation.setUserNum(testUser.getUserNum());
                predictions.add(recommendation);
            }

            reportMAE(predictions, testUsers);
            reportTask2(predictions, testUsers);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void reportMAE(ArrayList<User> predictions, ArrayList<User> actual) {
        double totalMeans = 0;
        int numRecs = 0;
        for (int i = 0; i < actual.size(); i++) {
            assert(predictions.get(i).getUserNum() == actual.get(i).getUserNum());
            int dishes = 0;
            double error = 0;
            for (int dish = 0; dish < NUM_DISHES; dish++) {
                if (actual.get(i).getRatings().containsKey(dish)) {
                    dishes++;
                    error += Math.abs(predictions.get(i).getRatings().get(dish) - actual.get(i).getRatings().get(dish));
                }
            }
            totalMeans += error / dishes;
            numRecs++;

        }
        System.out.printf("Task 1 MAE: %.3f\n", totalMeans / numRecs);
    }

    public static void reportTask2(ArrayList<User> predictions, ArrayList<User> actual) {
        double p10 = 0;
        double p20 = 0;
        double r20 = 0;
        double r10 = 0;
        for (int i = 0; i < actual.size(); i++) {
            assert(predictions.get(i).getUserNum() == actual.get(i).getUserNum());
            int[] recs = generateRecommendations(predictions.get(i));
            double[] eval = evaluateRecs(actual.get(i), predictions.get(i), recs);
            p10 += eval[0];
            r10 += eval[1];
            p20 += eval[2];
            r20 += eval[3];
        }
        System.out.printf("Task 2 Precision@10: %.3f\n", p10 / actual.size());
        System.out.printf("Task 2 Recall@10: %.3f\n", r10 / actual.size());
        System.out.printf("Task 2 Precision@20: %.3f\n", p20 / actual.size());
        System.out.printf("Task 2 Recall@20: %.3f\n", r20 / actual.size());
    }

    public static double[] evaluateRecs(User actual, User recommendation, int[] recs) {
        double[] ret = new double[4];
        int relevant = 0;
        for (double rating: actual.getRatings().values()) {
            if (rating >= 3) relevant++;
        }
        int relevantRetrieved = 0;
        for (int i = 0; i < recs.length; i++) {
            if (i == 10) {
                //report @10
                ret[0] = relevantRetrieved / 10.0;
                ret[1] = relevantRetrieved * 1.0 / relevant;
            }
            if ((actual.getRatings().containsKey(recs[i]) &&
                    recommendation.getRatings().get(recs[i]) >= 3 &&
                    actual.getRatings().get(recs[i]) >= 3)) {
                relevantRetrieved++;
            }
        }
        ret[2] = relevantRetrieved / 20.0;
        ret[3] = relevantRetrieved * 1.0 / relevant;

        return ret;
    }

    /**
     * Run once after init users
     * @param users
     */
    public static void normalizeUserRatings(ArrayList<User> users) {
        for (User user: users) {
            double totalRating = 0.0;
            int numRatings = 0;
            double meanRating;
            for (int i = 0; i < NUM_DISHES; i++) {
                if(user.getRatings().containsKey(i)) {
                    numRatings++;
                    totalRating += user.getRatings().get(i);
                }
            }
            meanRating = totalRating / numRatings;
            for (int i = 0; i < NUM_DISHES; i++) {
                if(user.getRatings().containsKey(i)) {
                    user.getRatings().put(i, user.getRatings().get(i) - meanRating);
                }
            }
            user.setMeanRating(meanRating);
        }
    }

    public static double cosineSimilarity(String[] dish1, String[] dish2) {
        int dish1mag = 0;
        int dish2mag = 0;
        double numerator = 0.0;
        for (int i = 2; i < dish1.length; i++) {
            if(dish1[i].equals("1")) dish1mag++;
            if(dish2[i].equals("1")) dish2mag++;
            if(dish1[i].equals("1") && dish2[i].equals("1")) {
                numerator++;
            }
        }
        if (dish1mag == 0 || dish2mag == 0 || numerator == 0) return 0;
        return numerator / (Math.sqrt(dish1mag) * Math.sqrt(dish2mag));
    }

    public static User generatePredictions(ArrayList<double[]> similarDishes, User user) {
        User predictions = new User();

        for(int dish = 0; dish < NUM_DISHES; dish++) {
            // if the user has not rated the dish, predict the rating
            if (!user.getRatings().containsKey(dish)) {
                double totalRating = 0.0; //of the dish
                int numRatings = 0;
                double numerator = 0.0;
                double denominator = 0.0;
                // iterate through the similarity entry for the current dish
                // find similar dishes that the user has rated
                for(int otherDish = 0; otherDish < similarDishes.get(dish).length; otherDish++) {
                    if (dish == otherDish) continue;
                    if (user.getRatings().containsKey(otherDish)) {
                        // Similarity of other dish to this dish times the rating for the other dish
                        numerator += similarDishes.get(dish)[otherDish] * user.getRatings().get(otherDish);
                        denominator += Math.abs(similarDishes.get(dish)[otherDish]);

                    }
                }

                if (denominator == 0) predictions.getRatings().put(dish, user.getMeanRating()); // no  similarity to any liked dishes, cannot be certain
                else predictions.getRatings().put(dish, user.getMeanRating() + (numerator / denominator));

            }
        }
        return predictions;
    }

    /**
     * Returns an array of the top dishes, denoted by their number, for the specified user,
     * based on predictions in order of descending confidence
     * @param user
     * @return
     */
    public static int[] generateRecommendations(User user) {
        Cloner cloner = new Cloner();
        Map<Integer, Double> clone = cloner.deepClone(user.getRatings());
        int[] recs = new int[MAX_REPORTING];
        for (int i = 0; i < MAX_REPORTING; i++) {
            recs[i] = getMaxKey(clone);
            clone.remove(recs[i]);
        }
        return recs;
    }

    public static int[] generateRecommendations(User user, User test) {
        Cloner cloner = new Cloner();
        Map<Integer, Double> clone = cloner.deepClone(user.getRatings());
        Map<Integer, Double> clone2 = cloner.deepClone(test.getRatings());
        int[] recs = new int[MAX_REPORTING];
        for (int i = 0; i < MAX_REPORTING; i++) {
            recs[i] = getMaxKey(clone, clone2);
            clone.remove(recs[i]);
            clone2.remove(recs[i]);
        }
        return recs;
    }

    public static int getMaxKey(Map<Integer, Double> ratings, Map<Integer, Double> test) {
        int max = test.entrySet().iterator().next().getKey();
        for (Integer key: ratings.keySet()) {
            if (ratings.get(key) > ratings.get(max) && test.containsKey(key)) max = key;
        }
        return max;
    }
    public static int getMaxKey(Map<Integer, Double> ratings) {
        int max = ratings.entrySet().iterator().next().getKey();
        for (Integer key: ratings.keySet()) {
            if (ratings.get(key) > ratings.get(max)) max = key;
        }
        return max;
    }

    public static double getMaxValue(Map<Integer, Double> ratings) {
        double max = ratings.get(ratings.entrySet().iterator().next().getKey());
        for (Integer key: ratings.keySet()) {
            if (ratings.get(key) > max) max = ratings.get(key);
        }
        return max;
    }
}

class User {
    private final Map<Integer, Double> ratings = new HashMap<Integer, Double>();
    private double magnitude = 0.0;
    private double meanRating = 0.0;
    private int userNum = 0;

    User() {}

    User(JSONArray ratings) {
        for (Object rating : ratings) {
            JSONArray review = (JSONArray) rating;
            try {
                this.ratings.put(((Long) review.get(0)).intValue(), (Double) review.get(1));
            } catch (ClassCastException e) { // For some reason some of the 5 ratings are 5 instead of 5.0
                this.ratings.put(((Long) review.get(0)).intValue(), ((Long) review.get(1)).doubleValue());
            }
        }
    }

    User(JSONArray ratings, int userNum) {
        for (Object rating : ratings) {
            JSONArray review = (JSONArray) rating;
            try {
                this.ratings.put(((Long) review.get(0)).intValue(), (Double) review.get(1));
            } catch (ClassCastException e) { // For some reason some of the 5 ratings are 5 instead of 5.0
                this.ratings.put(((Long) review.get(0)).intValue(), ((Long) review.get(1)).doubleValue());
            }
        }
        this.userNum = userNum;
    }

    public int getUserNum() {
        return userNum;
    }

    public void setUserNum(int userNum) {
        this.userNum = userNum;
    }

    public double getMeanRating() {
        return meanRating;
    }

    public void setMeanRating(double meanRating) {
        this.meanRating = meanRating;
    }

    public double getMagnitude() {
        return magnitude;
    }

    public void setMagnitude(double magnitude) {
        this.magnitude = magnitude;
    }

    public Map<Integer, Double> getRatings() {
        return ratings;
    }
}
